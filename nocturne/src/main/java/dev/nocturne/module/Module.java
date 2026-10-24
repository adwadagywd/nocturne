package dev.nocturne.module;

import dev.nocturne.setting.Setting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

public abstract class Module {
    protected final MinecraftClient mc = MinecraftClient.getInstance();
    public final String name, description;
    public final Category category;
    public final List<Setting<?>> settings = new ArrayList<>();
    public boolean expanded;
    private boolean enabled;

    protected Module(String name, String description, Category category) {
        this.name = name; this.description = description; this.category = category;
    }

    public <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public boolean isEnabled() { return enabled; }
    public void toggle() { setEnabled(!enabled); }
    public void setEnabled(boolean e) {
        if (e == enabled) return;
        enabled = e;
        if (e) onEnable(); else onDisable();
    }

    protected void onEnable() {}
    protected void onDisable() {}
    public void onTick(MinecraftClient mc) {}
    public void onRender3d() {}
    public void onRenderHud(DrawContext ctx) {}
}
