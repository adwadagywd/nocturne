package dev.nocturne.module.impl;

import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.setting.BoolSetting;
import dev.nocturne.util.Friends;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;

/** Cancels every attack you make on a player from your friends list (manual hits and bots alike). */
public class NoFriendsDamage extends Module {
    public static NoFriendsDamage INSTANCE;
    private final BoolSetting notify = add(new BoolSetting("Notify", true));
    private long lastNotify;

    public NoFriendsDamage() {
        super("NoFriendsDamage", "Blocks your attacks on players in the Friends tab", Category.COMBAT);
        INSTANCE = this;
    }

    public static boolean blocks(Entity e) {
        NoFriendsDamage m = INSTANCE;
        if (m == null || !m.isEnabled() || !Friends.isFriend(e)) return false;
        long now = System.currentTimeMillis();
        if (m.notify.get() && now - m.lastNotify > 1000) {
            m.lastNotify = now;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) mc.player.sendMessage(Text.literal("Friend: " + e.getName().getString() + " - attack blocked"), true);
        }
        return true;
    }
}
