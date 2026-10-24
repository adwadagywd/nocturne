package dev.nocturne.gui;

import dev.nocturne.config.ConfigManager;
import dev.nocturne.module.Category;
import dev.nocturne.module.Module;
import dev.nocturne.module.ModuleManager;
import dev.nocturne.setting.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.*;

public class ClickGui extends Screen {
    private static final int W = 124, HEAD = 18, ROW = 15, SROW = 14, TALL = 21;
    /** per category: x, y, open(1/0) - static so the layout survives re-opening */
    private static final Map<Category, int[]> STATE = new EnumMap<>(Category.class);

    private interface Click { void on(int button, int mx, int my); }
    private record Hit(int x, int y, int w, int h, Click click) {}

    private final List<Hit> hits = new ArrayList<>();
    private Category dragPanel;
    private int dragOx, dragOy;
    private Slidable dragSlider;
    private int dragX;

    public ClickGui() { super(Text.literal("Nocturne")); }

    @Override public boolean shouldPause() { return false; }
    @Override public void removed() { ConfigManager.save(); }

    private static boolean in(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        hits.clear();
        ctx.fillGradient(0, 0, width, height, 0xA0000000, 0xD0000000);
        if (dragSlider != null) dragSlider.setFraction((mx - dragX) / (double) (W - 12));

        TextRenderer tr = textRenderer;
        Module hovered = null;

        for (Category c : Category.values()) {
            int[] st = STATE.computeIfAbsent(c, k -> new int[]{20 + k.ordinal() * (W + 10), 20, 1});
            int x = st[0], y = st[1];

            ctx.fill(x, y, x + W, y + HEAD, Theme.HEADER);
            ctx.fill(x, y, x + W, y + 2, Theme.accent());
            ctx.drawText(tr, c.label, x + 6, y + 6, Theme.TEXT, false);
            ctx.drawText(tr, st[2] == 1 ? "-" : "+", x + W - 10, y + 6, Theme.DIM, false);
            hits.add(new Hit(x, y, W, HEAD, (b, hx, hy) -> {
                if (b == 0) { dragPanel = c; dragOx = hx - st[0]; dragOy = hy - st[1]; }
                else if (b == 1) st[2] ^= 1;
            }));
            if (st[2] == 0) continue;

            int cy = y + HEAD;
            for (Module m : ModuleManager.byCategory(c)) {
                boolean hov = in(mx, my, x, cy, W, ROW);
                if (hov) hovered = m;
                ctx.fill(x, cy, x + W, cy + ROW, m.isEnabled() ? Theme.accentDim() : (hov ? Theme.HOVER : Theme.BG));
                ctx.drawText(tr, m.name, x + 6, cy + 4, m.isEnabled() ? Theme.TEXT : Theme.DIM, false);
                if (!m.settings.isEmpty())
                    ctx.drawText(tr, m.expanded ? "v" : ">", x + W - 11, cy + 4, Theme.DIM, false);
                hits.add(new Hit(x, cy, W, ROW, (b, hx, hy) -> {
                    if (b == 0) m.toggle(); else if (b == 1) m.expanded = !m.expanded;
                }));
                cy += ROW;
                if (m.expanded) for (Setting<?> s : m.settings) if (s.visible()) cy = drawSetting(ctx, tr, s, x, cy, mx, my);
            }
            ctx.fill(x, cy, x + W, cy + 1, Theme.LINE);
        }
        if (hovered != null) ctx.drawTooltip(tr, Text.literal(hovered.description), mx, my);
    }

    private int drawSetting(DrawContext ctx, TextRenderer tr, Setting<?> s, int x, int y, int mx, int my) {
        boolean tall = s instanceof Slidable;
        int h = tall ? TALL : SROW;
        boolean hov = in(mx, my, x, y, W, h);
        ctx.fill(x, y, x + W, y + h, hov ? Theme.HOVER : Theme.BG2);
        ctx.fill(x, y, x + 2, y + h, Theme.accentDim());

        if (s instanceof BoolSetting b) {
            ctx.drawText(tr, b.name, x + 7, y + 3, Theme.TEXT, false);
            int bx = x + W - 14, by = y + 3;
            ctx.fill(bx - 1, by - 1, bx + 9, by + 9, Theme.LINE);
            ctx.fill(bx, by, bx + 8, by + 8, b.get() ? Theme.accent() : Theme.BG);
            hits.add(new Hit(x, y, W, h, (btn, a, c) -> b.toggle()));
        } else if (s instanceof ModeSetting m) {
            ctx.drawText(tr, m.name, x + 7, y + 3, Theme.TEXT, false);
            String v = m.get();
            ctx.drawText(tr, v, x + W - tr.getWidth(v) - 6, y + 3, Theme.accent(), false);
            hits.add(new Hit(x, y, W, h, (btn, a, c) -> m.cycle(btn == 1 ? -1 : 1)));
        } else if (s instanceof Slidable sl) {
            ctx.drawText(tr, s.name, x + 7, y + 3, Theme.TEXT, false);
            String v = s.display();
            if (!v.isEmpty()) ctx.drawText(tr, v, x + W - tr.getWidth(v) - 6, y + 3, Theme.DIM, false);
            int tx = x + 6, ty = y + h - 7, tw = W - 12;
            if (s instanceof ColorSetting) {
                for (int i = 0; i < tw; i += 2)
                    ctx.fill(tx + i, ty, tx + i + 2, ty + 4, 0xFF000000 | MathHelper.hsvToRgb(i / (float) tw, 0.75f, 1f));
                int mk = tx + (int) (sl.fraction() * (tw - 2));
                ctx.fill(mk - 1, ty - 1, mk + 2, ty + 5, 0xFFFFFFFF);
            } else {
                ctx.fill(tx, ty, tx + tw, ty + 4, Theme.BG);
                ctx.fill(tx, ty, tx + (int) (sl.fraction() * tw), ty + 4, Theme.accent());
            }
            hits.add(new Hit(x, y, W, h, (btn, a, c) -> {
                if (btn != 0) return;
                dragSlider = sl; dragX = tx;
                sl.setFraction((a - tx) / (double) tw);
            }));
        }
        return y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (in((int) mx, (int) my, h.x, h.y, h.w, h.h)) { h.click.on(button, (int) mx, (int) my); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragPanel = null; dragSlider = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragPanel != null) {
            int[] st = STATE.get(dragPanel);
            st[0] = MathHelper.clamp((int) mx - dragOx, 0, width - W);
            st[1] = MathHelper.clamp((int) my - dragOy, 0, height - HEAD);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_RIGHT_SHIFT) { close(); return true; }
        return super.keyPressed(key, scan, mods);
    }
}
