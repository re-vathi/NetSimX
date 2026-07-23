package com.netsimx.persistence;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecentProjectsTest {

    @AfterAll
    static void cleanUp() {
        // These tests write to the real ~/.netsimx/recent-projects.json (RecentProjects
        // has no path injection point for tests) - reset it afterward so running the
        // suite doesn't leave test fixtures in a real user's recent-projects list.
        RecentProjects.save(List.of());
    }

    @Test
    void mostRecentlyAddedPathAppearsFirst() {
        RecentProjects.save(List.of());
        RecentProjects.addAndSave("/a/one.json");
        RecentProjects.addAndSave("/a/two.json");
        RecentProjects.addAndSave("/a/three.json");

        List<String> loaded = RecentProjects.load();
        assertEquals(3, loaded.size());
        assertEquals("/a/three.json", loaded.get(0));
    }

    @Test
    void reAddingAnExistingPathMovesItToFrontWithoutDuplicating() {
        RecentProjects.save(List.of());
        RecentProjects.addAndSave("/a/one.json");
        RecentProjects.addAndSave("/a/two.json");
        RecentProjects.addAndSave("/a/one.json"); // re-add

        List<String> loaded = RecentProjects.load();
        assertEquals(2, loaded.size(), "Re-adding an existing path should not duplicate it");
        assertEquals("/a/one.json", loaded.get(0));
    }

    @Test
    void listIsTrimmedToMaxEntries() {
        RecentProjects.save(List.of());
        for (int i = 0; i < 15; i++) {
            RecentProjects.addAndSave("/a/file" + i + ".json");
        }
        List<String> loaded = RecentProjects.load();
        assertTrue(loaded.size() <= 10, "Recent list should be capped");
        assertEquals("/a/file14.json", loaded.get(0), "Most recent should still be first after trimming");
    }
}
