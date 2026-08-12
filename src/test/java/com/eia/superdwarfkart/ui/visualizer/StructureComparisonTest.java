package com.eia.superdwarfkart.ui.visualizer;

import com.eia.superdwarfkart.model.Song;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three-way search that makes the panel's argument in one action.
 */
@DisplayName("Compare Structures")
class StructureComparisonTest {

    private static List<Song> library(int size) {
        List<Song> songs = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String title = String.format("Track %04d", i);
            songs.add(new Song(title, "Koji Kondo", Path.of("/music/" + i + ".mp3")));
        }
        // Shuffled so the tree does not degenerate; a deterministic seed keeps the test stable.
        Collections.shuffle(songs, new Random(7));
        return songs;
    }

    @Test
    @DisplayName("reports one result per structure, in mode order")
    void reportsAllThree() {
        List<Song> songs = library(64);
        List<StructureComparison.Result> results = StructureComparison.run(songs, songs.get(30));

        assertEquals(3, results.size());
        assertEquals("CircularDoublyLinkedList", results.get(0).structure());
        assertEquals("SimpleQueue", results.get(1).structure());
        assertEquals("BinarySearchTree", results.get(2).structure());
        for (StructureComparison.Result result : results) {
            assertTrue(result.found(), result.structure() + " should have found the song");
            assertEquals(songs.size(), result.measurement().n());
        }
    }

    @Test
    @DisplayName("the tree wins by the margin the complexity claims")
    void treeIsDramaticallyCheaper() {
        List<Song> songs = library(500);
        // The ring and the queue hold songs in insertion order and know nothing about titles, so
        // their cost depends on where the song sits in that order and not on what it is called.
        // The last one inserted is therefore their worst case, and an entirely ordinary case for
        // the tree - which is exactly the contrast worth putting on screen.
        Song target = songs.get(songs.size() - 1);

        List<StructureComparison.Result> results = StructureComparison.run(songs, target);
        int ring = results.get(0).steps();
        int queue = results.get(1).steps();
        int tree = results.get(2).steps();

        assertTrue(tree < 60, "a tree search over 500 songs should be tens of steps, was " + tree);
        assertTrue(ring > tree * 5, "ring " + ring + " should dwarf tree " + tree);
        assertTrue(queue > tree * 5, "queue " + queue + " should dwarf tree " + tree);
    }

    @Test
    @DisplayName("the tree's cost is the same wherever the song was inserted; the others' is not")
    void treeCostDoesNotDependOnInsertionPosition() {
        List<Song> songs = library(500);

        int treeForFirst = StructureComparison.run(songs, songs.get(0)).get(2).steps();
        int treeForLast = StructureComparison.run(songs, songs.get(songs.size() - 1)).get(2).steps();
        int ringForFirst = StructureComparison.run(songs, songs.get(0)).get(0).steps();
        int ringForLast = StructureComparison.run(songs, songs.get(songs.size() - 1)).get(0).steps();

        // An ordered structure reaches anything in about log n; a linear one reaches the first
        // element immediately and the last only after walking everything in between.
        assertTrue(Math.abs(treeForFirst - treeForLast) < 60,
                "tree cost should not swing with insertion position: "
                        + treeForFirst + " vs " + treeForLast);
        assertTrue(ringForLast > ringForFirst * 10,
                "ring cost should swing hugely with insertion position: "
                        + ringForFirst + " vs " + ringForLast);
    }

    @Test
    @DisplayName("reports honestly when the song is not there")
    void missingSong() {
        List<Song> songs = library(16);
        Song absent = new Song("Not In The Library", "Nobody", Path.of("/music/absent.mp3"));

        List<StructureComparison.Result> results = StructureComparison.run(songs, absent);

        for (StructureComparison.Result result : results) {
            assertFalse(result.found(), result.structure() + " should not have found the song");
            assertTrue(result.steps() > 0, "a failed search still costs the comparisons it made");
        }
    }

    @Test
    @DisplayName("runs on an empty library without throwing")
    void emptyLibrary() {
        Song target = new Song("Anything", "Anyone", Path.of("/music/a.mp3"));
        List<StructureComparison.Result> results = StructureComparison.run(List.of(), target);

        assertEquals(3, results.size());
        for (StructureComparison.Result result : results) {
            assertFalse(result.found());
            assertEquals(0, result.steps());
        }
    }
}
