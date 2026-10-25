package dev.nocturne.module.impl;

import dev.nocturne.gui.Theme;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import dev.nocturne.util.RenderUtil;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class KillAura extends Module {
    // --- targeting ---
    private final ModeSetting mode = add(new ModeSetting("Mode", "Single", "Single", "Switch", "Multi"));
    private final ModeSetting sort = add(new ModeSetting("Sort", "Distance", "Distance", "Health", "Angle"));
    private final NumSetting range = add(new NumSetting("Range", 3.0, 1.0, 6.0, 0.05));
    private final NumSetting aimRange = add(new NumSetting("Aim range", 5.0, 1.0, 10.0, 0.5));
    private final NumSetting wallRange = add(new NumSetting("Wall range", 0.0, 0.0, 6.0, 0.25));
    private final NumSetting fov = add(new NumSetting("FOV", 360, 30, 360, 5));
    private final NumSetting maxTargets = add(new NumSetting("Max targets", 3, 1, 10, 1));
    private final NumSetting switchDelay = add(new NumSetting("Switch delay", 4, 0, 20, 1));
    // --- timing ---
    private final ModeSetting timing = add(new ModeSetting("Timing", "Cooldown", "Cooldown", "CPS"));
    private final NumSetting minCooldown = add(new NumSetting("Min cooldown", 0.92, 0.5, 1.0, 0.01));
    private final NumSetting cps = add(new NumSetting("CPS", 12, 1, 20, 1));
    private final NumSetting maxHurt = add(new NumSetting("Max hurt time", 10, 0, 10, 1));
    // --- rotation ---
    private final ModeSetting rotation = add(new ModeSetting("Rotations", "Silent", "Silent", "Smooth", "Snap", "None"));
    private final NumSetting rotSpeed = add(new NumSetting("Rotation speed", 0.7, 0.1, 1.0, 0.05));
    private final ModeSetting aimPoint = add(new ModeSetting("Aim point", "Nearest", "Nearest", "Head", "Chest", "Feet"));
    private final BoolSetting raytrace = add(new BoolSetting("Raytrace", false));
    // --- combat rules ---
    private final BoolSetting onlyCrits = add(new BoolSetting("Only crits", false));
    private final BoolSetting autoJump = add(new BoolSetting("Auto jump", false));
    private final BoolSetting weaponOnly = add(new BoolSetting("Weapon only", false));
    private final BoolSetting autoWeapon = add(new BoolSetting("Auto weapon", false));
    private final BoolSetting pauseUsing = add(new BoolSetting("Pause while using", true));
    private final ModeSetting swing = add(new ModeSetting("Swing", "Normal", "Normal", "Packet", "None"));
    private final BoolSetting targetEsp = add(new BoolSetting("Target ESP", true));
    // --- targets ---
    private final BoolSetting players = add(new BoolSetting("Players", true));
    private final BoolSetting hostile = add(new BoolSetting("Hostile mobs", true));
    private final BoolSetting passive = add(new BoolSetting("Passive mobs", false));
    private final BoolSetting invisible = add(new BoolSetting("Invisible", false));
    private final BoolSetting ignoreTeam = add(new BoolSetting("Ignore teammates", true));

    private record Cand(LivingEntity e, double dist, double angle) {}

    private LivingEntity locked, primary;
    private int switchIndex, switchTimer;
    private double cpsAcc;
    private long last;
    private boolean jumpHeld;

    public KillAura() {
        super("KillAura", "Automatically attacks entities around you", Category.COMBAT);
        minCooldown.showIf(() -> timing.is("Cooldown"));
        cps.showIf(() -> timing.is("CPS"));
        rotSpeed.showIf(() -> rotation.is("Smooth"));
        raytrace.showIf(() -> !rotation.is("Silent"));
        maxTargets.showIf(() -> mode.is("Multi"));
        switchDelay.showIf(() -> mode.is("Switch"));
        autoJump.showIf(onlyCrits::get);
    }

    @Override
    protected void onEnable() { locked = null; primary = null; switchIndex = 0; switchTimer = 0; cpsAcc = 0; last = 0; }

    @Override
    protected void onDisable() {
        locked = null;
        primary = null;
        if (jumpHeld) { jumpHeld = false; KeyBinding.updatePressedStates(); }
    }

    // ---------------- helpers ----------------
    private boolean gate(PlayerEntity p) {
        if (weaponOnly.get() && !Combat.isWeapon(p.getMainHandStack())) return false;
        return !(pauseUsing.get() && p.isUsingItem());
    }

    private boolean isTarget(PlayerEntity p, Entity e) {
        if (e == p || !(e instanceof LivingEntity le) || !le.isAlive() || e.isSpectator()) return false;
        if (e instanceof PlayerEntity) { if (!players.get()) return false; }
        else if (e instanceof Monster) { if (!hostile.get()) return false; }
        else if (e instanceof MobEntity) { if (!passive.get()) return false; }
        else return false;
        if (!invisible.get() && e.isInvisible()) return false;
        return !(ignoreTeam.get() && e.isTeammate(p));
    }

    private static double distToBox(Vec3d p, Box b) {
        double x = MathHelper.clamp(p.x, b.minX, b.maxX);
        double y = MathHelper.clamp(p.y, b.minY, b.maxY);
        double z = MathHelper.clamp(p.z, b.minZ, b.maxZ);
        return p.distanceTo(new Vec3d(x, y, z));
    }

    private Vec3d aim(Entity e, Vec3d eye, float td) {
        Vec3d base = e.getLerpedPos(td);
        Box b = e.getBoundingBox().offset(base.subtract(e.getPos()));
        return switch (aimPoint.get()) {
            case "Head" -> base.add(0, e.getStandingEyeHeight(), 0);
            case "Chest" -> base.add(0, e.getHeight() * 0.65, 0);
            case "Feet" -> base.add(0, 0.25, 0);
            default -> new Vec3d(
                    MathHelper.clamp(eye.x, b.minX + 0.1, b.maxX - 0.1),
                    MathHelper.clamp(eye.y, b.minY + 0.1, b.maxY - 0.1),
                    MathHelper.clamp(eye.z, b.minZ + 0.1, b.maxZ - 0.1));
        };
    }

    /** {yaw difference, pitch difference} from the current look direction to the aim point */
    private float[] angles(PlayerEntity p, Vec3d eye, Entity e, float td) {
        Vec3d t = aim(e, eye, td);
        double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{MathHelper.wrapDegrees(yaw - p.getYaw()), pitch - p.getPitch()};
    }

    private List<Cand> candidates(PlayerEntity p, Vec3d eye, double maxRange, float td) {
        List<Cand> out = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!isTarget(p, e)) continue;
            double d = distToBox(p.getEyePos(), e.getBoundingBox());
            if (d > maxRange) continue;
            if (d > wallRange.get() && !p.canSee(e)) continue;
            float[] a = angles(p, eye, e, td);
            double ang = Math.hypot(a[0], a[1]);
            if (fov.get() < 360 && ang > fov.get() / 2.0) continue;
            out.add(new Cand((LivingEntity) e, d, ang));
        }
        Comparator<Cand> cmp;
        switch (sort.get()) {
            case "Health" -> cmp = Comparator.comparingDouble((Cand c) -> c.e().getHealth());
            case "Angle" -> cmp = Comparator.comparingDouble(Cand::angle);
            default -> cmp = Comparator.comparingDouble(Cand::dist);
        }
        out.sort(cmp);
        return out;
    }

    private LivingEntity choose(List<Cand> c) {
        if (c.isEmpty()) { locked = null; return null; }
        if (mode.is("Switch")) {
            List<Cand> near = new ArrayList<>();
            for (Cand x : c) if (x.dist() <= range.get()) near.add(x);
            if (near.isEmpty()) return c.get(0).e();
            return near.get(Math.floorMod(switchIndex, near.size())).e();
        }
        if (mode.is("Multi")) return c.get(0).e();
        if (locked != null) for (Cand x : c) if (x.e() == locked) return locked;
        locked = c.get(0).e();
        return locked;
    }

    private void equipBest(PlayerEntity p) {
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (s.isEmpty() || !Combat.isWeapon(s)) continue;
            String id = Registries.ITEM.getId(s.getItem()).getPath();
            int mat = id.startsWith("netherite") ? 6 : id.startsWith("diamond") ? 5 : id.startsWith("iron") ? 4
                    : id.startsWith("stone") ? 3 : id.startsWith("golden") ? 2 : id.startsWith("wooden") ? 1 : 0;
            int type = s.isIn(ItemTags.SWORDS) ? 2 : s.isIn(ItemTags.AXES) ? 1 : 0;
            int score = mat * 10 + type;
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best >= 0 && best != p.getInventory().selectedSlot) p.getInventory().selectedSlot = best;
    }

    private static java.lang.reflect.Constructor<?> LOOK_CTOR;

    /** Sends a rotation-only packet. Built by reflection so it works with either constructor layout. */
    private void sendLook(PlayerEntity p, float yaw, float pitch) {
        if (mc.getNetworkHandler() == null) return;
        try {
            if (LOOK_CTOR == null) {
                for (java.lang.reflect.Constructor<?> c : PlayerMoveC2SPacket.LookAndOnGround.class.getConstructors()) {
                    Class<?>[] t = c.getParameterTypes();
                    if (t.length >= 3 && t[0] == float.class && t[1] == float.class && t[2] == boolean.class) { LOOK_CTOR = c; break; }
                }
            }
            if (LOOK_CTOR == null) return;
            Object pkt = LOOK_CTOR.getParameterCount() == 3
                    ? LOOK_CTOR.newInstance(yaw, pitch, p.isOnGround())
                    : LOOK_CTOR.newInstance(yaw, pitch, p.isOnGround(), p.horizontalCollision);
            mc.getNetworkHandler().sendPacket((Packet<?>) pkt);
        } catch (Exception ignored) {}
    }

    private void doSwing(PlayerEntity p) {
        switch (swing.get()) {
            case "None" -> {}
            case "Packet" -> {
                if (mc.getNetworkHandler() != null) mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            }
            default -> p.swingHand(Hand.MAIN_HAND);
        }
    }

    // ---------------- per-frame: rotation ----------------
    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;
        primary = null;

        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || Combat.blocked() || !gate(p)) return;

        float td = RenderUtil.tickDelta;
        Vec3d eye = p.getLerpedPos(td).add(0, p.getEyeY() - p.getY(), 0);
        List<Cand> cands = candidates(p, eye, Math.max(range.get(), aimRange.get()), td);
        LivingEntity t = choose(cands);
        primary = t;
        if (t == null || rotation.is("None") || rotation.is("Silent")) return; // silent = packets only, camera never moves

        float[] a = angles(p, eye, t, td);
        float s = rotSpeed.getFloat();
        float f = rotation.is("Snap") || s >= 0.995f ? 1f : 1f - (float) Math.exp(-s * 30.0 * dt);
        p.changeLookDirection(a[0] * f / 0.15, a[1] * f / 0.15);
    }

    // ---------------- before the player ticks: crit jump ----------------
    @Override
    public void onPreTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        boolean want = false;
        if (autoJump.get() && onlyCrits.get() && p != null && p.isOnGround() && mc.currentScreen == null
                && !Combat.blocked() && gate(p)) {
            want = !candidates(p, p.getEyePos(), range.get() + 0.5, 1f).isEmpty();
        }
        if (want) {
            mc.options.jumpKey.setPressed(true);
            jumpHeld = true;
        } else if (jumpHeld) {
            jumpHeld = false;
            KeyBinding.updatePressedStates();
        }
    }

    // ---------------- after the player ticked: attack ----------------
    @Override
    public void onTick(MinecraftClient mc) {
        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null || Combat.blocked()) return;
        switchTimer++;
        cpsAcc = Math.min(cpsAcc + cps.get() / 20.0, 2.0);
        if (!gate(p)) return;

        Vec3d eye = p.getEyePos();
        List<Cand> all = candidates(p, eye, Math.max(range.get(), aimRange.get()), 1f);
        LivingEntity main = choose(all);
        if (main == null) return;

        List<LivingEntity> hit = new ArrayList<>();
        if (mode.is("Multi")) {
            for (Cand c : all) {
                if (c.dist() <= range.get()) hit.add(c.e());
                if (hit.size() >= maxTargets.getInt()) break;
            }
        } else {
            for (Cand c : all) if (c.e() == main && c.dist() <= range.get()) hit.add(main);
        }
        if (maxHurt.getInt() < 10) hit.removeIf(e -> e.hurtTime > maxHurt.getInt());
        if (raytrace.get() && !rotation.is("Silent")) {
            Entity look = mc.crosshairTarget instanceof EntityHitResult r ? r.getEntity() : null;
            hit.removeIf(e -> e != look);
        }
        if (hit.isEmpty()) return;

        if (autoWeapon.get()) equipBest(p);
        boolean ready = timing.is("Cooldown") ? p.getAttackCooldownProgress(0.5f) >= minCooldown.get() : cpsAcc >= 1.0;
        if (!ready) return;
        if (onlyCrits.get() && !Combat.canCrit(p)) return; // tip: AutoSprint keeps you sprinting => no crits

        if (rotation.is("Silent")) {
            // face the target on the server only: look packet -> attack -> back to the real look
            for (LivingEntity e : hit) {
                float[] a = angles(p, eye, e, 1f);
                sendLook(p, p.getYaw() + a[0], MathHelper.clamp(p.getPitch() + a[1], -90f, 90f));
                mc.interactionManager.attackEntity(p, e);
            }
            sendLook(p, p.getYaw(), p.getPitch());
        } else {
            for (LivingEntity e : hit) mc.interactionManager.attackEntity(p, e);
        }
        doSwing(p);
        if (timing.is("CPS")) cpsAcc -= 1.0;
        if (mode.is("Switch") && switchTimer >= switchDelay.getInt()) { switchIndex++; switchTimer = 0; }
    }

    // ---------------- target marker ----------------
    @Override
    public void onRender3d() {
        LivingEntity t = primary;
        if (!targetEsp.get() || t == null || !t.isAlive() || !RenderUtil.valid) return;
        int rgb = Theme.accent() & 0xFFFFFF;
        RenderUtil.setup(2f);
        BufferBuilder b = RenderUtil.begin(VertexFormat.DrawMode.QUADS);
        RenderUtil.boxFill(b, RenderUtil.lerpedBox(t), RenderUtil.argb(rgb, 0.18f));
        RenderUtil.finish(b);
        BufferBuilder l = RenderUtil.begin(VertexFormat.DrawMode.DEBUG_LINES);
        RenderUtil.boxOutline(l, RenderUtil.lerpedBox(t), RenderUtil.argb(rgb, 1f));
        RenderUtil.finish(l);
        RenderUtil.restore();
    }
}
