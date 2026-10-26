package dev.nocturne.module;

import dev.nocturne.module.impl.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static HudModule HUD;
    public static ThemeModule THEME;

    public static void init() {
        HUD = new HudModule();
        THEME = new ThemeModule();
        // combat
        ALL.add(new Hitboxes());
        ALL.add(new AimAssist());
        ALL.add(new TriggerBot());
        ALL.add(new AutoTotem());
        ALL.add(new NoFriendsDamage());
        // render
        ALL.add(new Tracers());
        ALL.add(new Esp());
        ALL.add(new StorageEsp());
        ALL.add(new Nametags());
        ALL.add(new XRay());
        ALL.add(new Glow());
        ALL.add(new BlockHighlight());
        ALL.add(new Fullbright());
        ALL.add(new Zoom());
        // world
        ALL.add(new WorldFog());
        ALL.add(new TimeChanger());
        ALL.add(new Weather());
        // movement
        ALL.add(new AutoSprint());
        ALL.add(new Freecam());
        // misc
        ALL.add(new FakePlayer());
        // client
        ALL.add(new TargetHud());
        ALL.add(new Keystrokes());
        ALL.add(new ArmorHud());
        ALL.add(HUD);
        ALL.add(THEME);
        HUD.setEnabled(true);
        THEME.setEnabled(true);
    }

    /** called every frame: toggles modules whose bound key was just pressed */
    public static void pollBinds() {
        MinecraftClient mc = MinecraftClient.getInstance();
        long h = mc.getWindow().getHandle();
        for (Module m : ALL) {
            if (m.key < 0) { m.keyDown = false; continue; }
            boolean down = InputUtil.isKeyPressed(h, m.key);
            if (down && !m.keyDown && mc.currentScreen == null) m.toggle();
            m.keyDown = down;
        }
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }
}
