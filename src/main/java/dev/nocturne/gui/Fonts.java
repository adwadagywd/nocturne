package dev.nocturne.gui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Nocturne typography: Inter (the closest free match to Apple's SF Pro, which Catlean uses).
 * Regular for body text, SemiBold for headings. Both fall back to the vanilla font for missing glyphs.
 */
public final class Fonts {
    public static final Identifier REGULAR = Identifier.of("nocturne", "inter");
    public static final Identifier BOLD = Identifier.of("nocturne", "inter_bold");

    private static final Style REGULAR_STYLE = Style.EMPTY.withFont(REGULAR);
    private static final Style BOLD_STYLE = Style.EMPTY.withFont(BOLD);

    private Fonts() {}

    /** regular text in the Nocturne font */
    public static MutableText t(String s) {
        return Text.literal(s).setStyle(REGULAR_STYLE);
    }

    /** heading text in the Nocturne font (SemiBold) */
    public static MutableText b(String s) {
        return Text.literal(s).setStyle(BOLD_STYLE);
    }

    public static int w(TextRenderer tr, String s) {
        return tr.getWidth(t(s));
    }

    public static int bw(TextRenderer tr, String s) {
        return tr.getWidth(b(s));
    }

    public static String trim(TextRenderer tr, String s, int width) {
        return trim(tr, s, width, false);
    }

    /** like TextRenderer.trimToWidth, but measured in the Nocturne font; backwards keeps the end of the string */
    public static String trim(TextRenderer tr, String s, int width, boolean backwards) {
        if (w(tr, s) <= width) return s;
        int len = s.length();
        if (backwards) {
            int start = 0;
            while (start < len && w(tr, s.substring(start)) > width) start++;
            return s.substring(start);
        }
        int end = len;
        while (end > 0 && w(tr, s.substring(0, end)) > width) end--;
        return s.substring(0, end);
    }
}
