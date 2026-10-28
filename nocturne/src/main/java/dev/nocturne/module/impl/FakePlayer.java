package dev.nocturne.module.impl;

import com.mojang.authlib.GameProfile;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.StringSetting;
import dev.nocturne.util.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * A motionless client-side dummy in full netherite. It takes damage from your hits and from end crystals that
 * explode next to it, and "pops a totem" (back to full health) every time it would die - so it never dies.
 */
public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    private static final int FAKE_ID = -696969;
    private static final float ARMOR = 20f, TOUGHNESS = 12f; // full netherite

    private final StringSetting name = add(new StringSetting("Name", "Dummy", 16));

    private OtherClientPlayerEntity fake;
    private double fx, fy, fz;
    private float fyaw;
    private String spawnedName = "";
    private int pops;
    private final Map<Integer, Vec3d> crystals = new HashMap<>();

    public FakePlayer() {
        super("FakePlayer", "Standing dummy: takes damage, pops infinite totems (visible only to you)", Category.MISC);
        INSTANCE = this;
    }

    @Override public boolean persistEnabled() { return false; }

    private String cleanName() {
        String n = name.get().replaceAll("[^A-Za-z0-9_]", "");
        if (n.isEmpty()) n = "Dummy";
        return n.length() > 16 ? n.substring(0, 16) : n;
    }

    @Override
    protected void onEnable() {
        if (mc.player == null || mc.world == null) return;
        fx = mc.player.getX();
        fy = mc.player.getY();
        fz = mc.player.getZ();
        fyaw = mc.player.getYaw() + 180f; // faces you
        pops = 0;
        crystals.clear();
        spawn();
    }

    private void spawn() {
        spawnedName = cleanName();
        fake = new OtherClientPlayerEntity(mc.world, new GameProfile(UUID.randomUUID(), spawnedName));
        fake.setId(FAKE_ID);
        fake.refreshPositionAndAngles(fx, fy, fz, fyaw, 0f);
        fake.setHeadYaw(fyaw);
        fake.setBodyYaw(fyaw);
        fake.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.NETHERITE_HELMET));
        fake.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.NETHERITE_CHESTPLATE));
        fake.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.NETHERITE_LEGGINGS));
        fake.equipStack(EquipmentSlot.FEET, new ItemStack(Items.NETHERITE_BOOTS));
        fake.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.NETHERITE_SWORD));
        fake.equipStack(EquipmentSlot.OFFHAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        fake.setHealth(fake.getMaxHealth());
        mc.world.addEntity(fake);
    }

    private void despawn() {
        if (fake != null && mc.world != null) mc.world.removeEntity(fake.getId(), Entity.RemovalReason.DISCARDED);
        fake = null;
    }

    @Override protected void onDisable() { despawn(); crystals.clear(); }

    // ---------------- damage model ----------------
    private static float armorReduce(float dmg) {
        float red = Math.min(20f, Math.max(ARMOR / 5f, ARMOR - dmg / (2f + TOUGHNESS / 4f)));
        return dmg * (1f - red / 25f);
    }

    private static float weaponDamage(ItemStack s) {
        String id = Registries.ITEM.getId(s.getItem()).getPath();
        boolean sword = id.endsWith("_sword"), axe = id.endsWith("_axe");
        if (!sword && !axe) return id.equals("mace") ? 6f : id.equals("trident") ? 9f : 1f;
        if (sword) {
            if (id.startsWith("netherite")) return 8f;
            if (id.startsWith("diamond")) return 7f;
            if (id.startsWith("iron")) return 6f;
            if (id.startsWith("stone")) return 5f;
            return 4f;
        }
        if (id.startsWith("netherite")) return 10f;
        if (id.startsWith("wooden") || id.startsWith("golden")) return 7f;
        return 9f;
    }

    private void hurt(float raw) {
        if (fake == null) return;
        float dmg = armorReduce(raw);
        fake.handleStatus((byte) 2); // hurt flash + sound
        float h = fake.getHealth() - dmg;
        if (h <= 0f) {
            fake.setHealth(fake.getMaxHealth());
            fake.handleStatus((byte) 35); // totem pop
            pops++;
            if (mc.player != null) mc.player.sendMessage(Text.literal(spawnedName + " popped a totem  (" + pops + ")"), true);
        } else {
            fake.setHealth(h);
        }
    }

    /** called for every attack the local player makes */
    public static void onAttack(PlayerEntity attacker, Entity target) {
        FakePlayer m = INSTANCE;
        if (m == null || m.fake == null || target != m.fake) return;
        float cd = attacker.getAttackCooldownProgress(0.5f);
        float dmg = weaponDamage(attacker.getMainHandStack()) * (0.2f + cd * cd * 0.8f);
        if (cd > 0.9f && Combat.canCrit(attacker)) dmg *= 1.5f;
        m.hurt(dmg);
    }

    @Override
    public void onTick(MinecraftClient mc) {
        if (fake == null || fake.getWorld() != mc.world) {
            fake = null;
            setEnabled(false);
            return;
        }
        if (!spawnedName.equals(cleanName())) { despawn(); spawn(); } // name changed in the GUI

        // end crystals that vanish near the dummy = an explosion: apply vanilla explosion damage (power 6)
        Set<Integer> now = new HashSet<>();
        for (Entity e : mc.world.getEntities()) {
            if (e instanceof EndCrystalEntity) { now.add(e.getId()); crystals.put(e.getId(), e.getPos()); }
        }
        Iterator<Map.Entry<Integer, Vec3d>> it = crystals.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Vec3d> en = it.next();
            if (now.contains(en.getKey())) continue;
            double d = fake.getPos().distanceTo(en.getValue()) / 12.0;
            if (d <= 1.0) {
                double impact = 1.0 - d;
                hurt((float) ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0));
            }
            it.remove();
        }
        fake.setVelocity(Vec3d.ZERO); // never moves
    }
}
