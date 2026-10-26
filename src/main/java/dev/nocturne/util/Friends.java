package dev.nocturne.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Friend list, stored separately from configs so loading a config never changes it. */
public final class Friends {
    private static final Map<String, String> MAP = new LinkedHashMap<>(); // lower-case -> display name
    private static final Gson GSON = new Gson();

    private Friends() {}

    private static Path file() {
        Path d = FabricLoader.getInstance().getConfigDir().resolve("nocturne");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d.resolve("friends.json");
    }

    public static List<String> all() { return new ArrayList<>(MAP.values()); }

    public static boolean add(String name) {
        if (name == null) return false;
        String t = name.trim();
        if (t.isEmpty() || t.length() > 16) return false;
        String k = t.toLowerCase(Locale.ROOT);
        if (MAP.containsKey(k)) return false;
        MAP.put(k, t);
        save();
        return true;
    }

    public static void remove(String name) {
        MAP.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    public static boolean isFriend(String name) {
        return name != null && MAP.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public static boolean isFriend(Entity e) {
        return e instanceof PlayerEntity && !MAP.isEmpty() && MAP.containsKey(e.getName().getString().toLowerCase(Locale.ROOT));
    }

    public static void save() {
        try { Files.writeString(file(), GSON.toJson(all())); } catch (Exception ignored) {}
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Type t = new TypeToken<List<String>>() {}.getType();
            List<String> l = GSON.fromJson(r, t);
            MAP.clear();
            if (l != null) for (String n : l) if (n != null && !n.isBlank()) MAP.put(n.toLowerCase(Locale.ROOT), n);
        } catch (Exception ignored) {}
    }
}
