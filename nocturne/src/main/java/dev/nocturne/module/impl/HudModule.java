package dev.nocturne.module.impl;

import dev.nocturne.module.*;
import dev.nocturne.module.Module;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HudModule extends Module {
    public final ColorSetting accent = add(new ColorSetting("Accent", 0.76f));
    public final BoolSetting rainbow = add(new BoolSetting("Rainbow", false));
    private final BoolSetting watermark = add(new BoolSetting("Watermark", true));
    private final BoolSetting list = add(new BoolSetting("Module list", true));

    public HudModule() {
        super("HUD", "Watermark, module list and theme colour", Category.CLIENT);
        accent.showIf(() -> !rainbow.get());
    }

    public int color(int index) {
        if (!rainbow.get()) return accent.rgb();
        float h = ((System.currentTimeMillis() % 5000L) / 5000f + index * 0.04f) % 1f;
        return 0xFF000000 | MathHelper.hsvToRgb(h, 0.7f, 1f);
    }

    @Override
    public void onRenderHud(DrawContext ctx) {
        TextRenderer tr = mc.textRenderer;
        if (watermark.get()) {
            String a = "NOCTURNE", b = "  |  " + mc.getCurrentFps() + " fps";
            int w = tr.getWidth(a) + tr.getWidth(b) + 10;
            ctx.fill(4, 4, 4 + w, 18, 0xB00E0E12);
            ctx.fill(4, 4, 6, 18, color(0));
            ctx.drawText(tr, a, 10, 7, color(0), true);
            ctx.drawText(tr, b, 10 + tr.getWidth(a), 7, 0xFF9A9AA6, true);
        }
        if (list.get()) {
            List<Module> on = new ArrayList<>();
            for (Module m : ModuleManager.ALL) if (m.isEnabled() && m != this) on.add(m);
            on.sort(Comparator.comparingInt((Module m) -> -tr.getWidth(m.name)));
            int sw = mc.getWindow().getScaledWidth();
            int y = 4, i = 0;
            for (Module m : on) {
                int w = tr.getWidth(m.name);
                int x = sw - w - 8;
                ctx.fill(x - 3, y, sw - 2, y + 11, 0xA00E0E12);
                ctx.fill(sw - 3, y, sw - 1, y + 11, color(i));
                ctx.drawText(tr, m.name, x, y + 2, color(i), true);
                y += 11; i++;
            }
        }
    }
}
