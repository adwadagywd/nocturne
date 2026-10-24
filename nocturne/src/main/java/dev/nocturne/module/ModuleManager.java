package dev.nocturne.module;

import dev.nocturne.module.impl.*;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final List<Module> ALL = new ArrayList<>();
    public static HudModule HUD;

    public static void init() {
        HUD = new HudModule();
        ALL.add(new Hitboxes());
        ALL.add(new Tracers());
        ALL.add(new Esp());
        ALL.add(new Nametags());
        ALL.add(new AutoSprint());
        ALL.add(new Fullbright());
        ALL.add(HUD);
        HUD.setEnabled(true);
    }

    public static List<Module> byCategory(Category c) {
        List<Module> out = new ArrayList<>();
        for (Module m : ALL) if (m.category == c) out.add(m);
        return out;
    }
}
