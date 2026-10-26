package dev.nocturne.module;

public enum Category {
    COMBAT("Combat"), RENDER("Render"), WORLD("World"), MOVEMENT("Movement"), MISC("Misc"), CLIENT("Client");
    public final String label;
    Category(String label) { this.label = label; }
}
