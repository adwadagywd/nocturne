package dev.nocturne.gui;

import dev.nocturne.module.ModuleManager;

/** Tweak the look of the whole client from here. */
public final class Theme {
    public static final int BG = 0xE60E0E12, BG2 = 0xE6141419, HEADER = 0xF0111116, HOVER = 0xE61E1E26;
    public static final int TEXT = 0xFFE8E8EE, DIM = 0xFF8C8C98, LINE = 0xFF2A2A34;

    public static int accent() {
        return ModuleManager.HUD == null ? 0xFFB06CFF : ModuleManager.HUD.color(0);
    }
    public static int accentDim() { return (accent() & 0xFFFFFF) | 0x70000000; }
}
