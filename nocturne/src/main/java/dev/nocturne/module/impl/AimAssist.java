package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import dev.nocturne.util.Combat;
import dev.nocturne.util.RenderUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {
    private final NumSetting strength = add(new NumSetting("Strength", 0.4, 0.05, 1.0, 0.05));
    private final NumSetting fov = add(new NumSetting("FOV", 90, 10, 360, 5));
    private final NumSetting range = add(new NumSetting("Range", 4.5, 2.0, 8.0, 0.1));
    private final ModeSetting aimAt = add(new ModeSetting("Aim at", "Chest", "Head", "Chest", "Feet"));
    private final ModeSetting priority = add(new ModeSetting("Priority", "Angle", "Angle", "Distance", "Health"));
    private final BoolSetting vertical = add(new BoolSetting("Vertical", true));
    private final BoolSetting clickOnly = add(new BoolSetting("Only while clicking", true));
    private final BoolSetting weaponOnly = add(new BoolSetting("Weapon only", false));
    private final BoolSetting visibleOnly = add(new BoolSetting("Visible only", true));
    private final TargetSettings targets = new TargetSettings(this, true, false, false, false);

    private long last;

    public AimAssist() { super("AimAssist", "Smoothly pulls your aim towards nearby targets", Category.COMBAT); }

    @Override protected void onEnable() { last = 0; }

    @Override
    public void onFrame() {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1.0e9f);
        last = now;

        PlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null || Combat.blocked()) return;
        if (clickOnly.get() && !mc.options.attackKey.isPressed()) return;
        if (weaponOnly.get() && !Combat.isWeapon(p.getMainHandStack())) return;

        float td = RenderUtil.tickDelta;
        Vec3d eye = p.getLerpedPos(td).add(0, p.getEyeY() - p.getY(), 0);

        double bestScore = Double.MAX_VALUE;
        float bestYaw = 0, bestPitch = 0;
        boolean found = false;

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || !le.isAlive() || !targets.test(e)) continue;
            if (p.distanceTo(e) > range.get()) continue;
            if (visibleOnly.get() && !p.canSee(e)) continue;

            Vec3d base = e.getLerpedPos(td);
            Vec3d t = switch (aimAt.get()) {
                case "Head" -> base.add(0, e.getStandingEyeHeight(), 0);
                case "Feet" -> base.add(0, 0.25, 0);
                default -> base.add(0, e.getHeight() * 0.65, 0);
            };
            double dx = t.x - eye.x, dy = t.y - eye.y, dz = t.z - eye.z;
            double h = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
            float dyaw = MathHelper.wrapDegrees(yaw - p.getYaw());
            float dpitch = pitch - p.getPitch();
            double angle = Math.hypot(dyaw, dpitch);
            if (angle > fov.get() / 2.0) continue;

            double score = switch (priority.get()) {
                case "Distance" -> p.distanceTo(e);
                case "Health" -> le.getHealth();
                default -> angle;
            };
            if (score < bestScore) { bestScore = score; bestYaw = dyaw; bestPitch = dpitch; found = true; }
        }
        if (!found) return;

        float f = 1f - (float) Math.exp(-strength.get() * 18.0 * dt);
        float stepYaw = bestYaw * f;
        float stepPitch = vertical.get() ? bestPitch * f : 0f;
        // changeLookDirection keeps prevYaw/prevPitch in sync => no camera judder
        p.changeLookDirection(stepYaw / 0.15, stepPitch / 0.15);
    }
}
