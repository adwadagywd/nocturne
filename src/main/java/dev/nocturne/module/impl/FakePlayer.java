package dev.nocturne.module.impl;

import com.mojang.authlib.GameProfile;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.StringSetting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

import java.util.UUID;

/** A motionless client-side dummy that "pops" a totem every time you hit it - it can never die. */
public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    private static final int FAKE_ID = -696969;

    private final StringSetting name = add(new StringSetting("Name", "Dummy", 16));

    private OtherClientPlayerEntity fake;
    private double fx, fy, fz;
    private float fyaw;
    private String spawnedName = "";

    public FakePlayer() {
        super("FakePlayer", "Spawns a standing dummy with infinite totems (visible only to you)", Category.MISC);
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

    @Override protected void onDisable() { despawn(); }

    @Override
    public void onTick(MinecraftClient mc) {
        if (fake == null || fake.getWorld() != mc.world) {
            fake = null;
            setEnabled(false);
            return;
        }
        if (!spawnedName.equals(cleanName())) { despawn(); spawn(); } // name changed in the GUI
        fake.setHealth(fake.getMaxHealth());
        fake.setVelocity(Vec3d.ZERO);
    }

    /** called for every attack the local player makes; returns true when the dummy was hit */
    public static boolean onAttack(Entity e) {
        FakePlayer m = INSTANCE;
        if (m == null || m.fake == null || e != m.fake) return false;
        e.handleStatus((byte) 35); // the totem-pop animation + sound
        return true;
    }
}
