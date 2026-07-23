package com.netsimx.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks recently opened/saved topology JSON paths for the Home
 * Dashboard's "Recent Projects" list (Module 12 wireframe). Persisted as
 * a small JSON file under the user's home directory so the list survives
 * across app restarts - deliberately just a flat list of paths, no
 * external database, consistent with the rest of the project's
 * dependency-free persistence approach (see {@link MiniJson}).
 */
public final class RecentProjects {

    private static final int MAX_ENTRIES = 10;
    private static final Path STORE_PATH = Path.of(System.getProperty("user.home"), ".netsimx", "recent-projects.json");

    private RecentProjects() {}

    @SuppressWarnings("unchecked")
    public static List<String> load() {
        try {
            if (!Files.exists(STORE_PATH)) return new ArrayList<>();
            String text = Files.readString(STORE_PATH, StandardCharsets.UTF_8);
            Object parsed = MiniJson.parse(text);
            if (!(parsed instanceof Map)) return new ArrayList<>();
            Map<String, Object> root = (Map<String, Object>) parsed;
            Object list = root.get("paths");
            if (!(list instanceof List)) return new ArrayList<>();
            List<String> result = new ArrayList<>();
            for (Object o : (List<Object>) list) {
                if (o instanceof String s) result.add(s);
            }
            return result;
        } catch (Exception e) {
            return new ArrayList<>(); // corrupt/unreadable recents file should never block the app from starting
        }
    }

    /** Adds (or moves to the front of) the recent list, trimming to {@value #MAX_ENTRIES} entries, and persists it. */
    public static List<String> addAndSave(String path) {
        List<String> current = load();
        current.remove(path);
        current.add(0, path);
        while (current.size() > MAX_ENTRIES) current.remove(current.size() - 1);
        save(current);
        return current;
    }

    public static void save(List<String> paths) {
        try {
            Files.createDirectories(STORE_PATH.getParent());
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("paths", paths);
            Files.writeString(STORE_PATH, MiniJson.write(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            // Best-effort persistence - a failure here shouldn't crash the app, just means recents won't be remembered.
        }
    }
}
