package com.eia.superdwarfkart.model;

import java.time.Duration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Totals over a library, computed on demand.
 *
 * <p><strong>Derived on read, never stored</strong> - the same decision the runner's rank is made
 * with, for the same reason. Every number here is a function of the songs, so a stored copy is a
 * second source of truth that can disagree with them, and the way it would disagree is by being
 * quietly stale after an edit rather than by failing.
 *
 * <p>The cost is one pass over the library per view, which on a library of any plausible size is
 * nothing next to the repaint that follows it.
 *
 * @param songCount      how many songs are in the library
 * @param favoriteCount  how many are marked as favourites
 * @param ratedCount     how many carry a rating above zero
 * @param totalPlays     the sum of every song's play count
 * @param playedCount    how many have been played at least once
 * @param totalDuration  the combined length of every song
 * @param listenedTime   length times play count, summed: roughly how long has been spent listening
 * @param averageRating  mean rating over the rated songs, or 0 when none are rated
 * @param topPlayed      the most played songs, most first
 * @param topRated       the highest rated songs, best first
 * @param playsByArtist  play counts summed per artist, busiest first
 */
public record LibraryStatistics(
        int songCount,
        int favoriteCount,
        int ratedCount,
        int totalPlays,
        int playedCount,
        Duration totalDuration,
        Duration listenedTime,
        double averageRating,
        List<Song> topPlayed,
        List<Song> topRated,
        Map<String, Integer> playsByArtist) {

    /** How many entries each of the leader lists holds. */
    public static final int TOP_COUNT = 5;

    /**
     * Computes the totals over a collection of songs.
     *
     * @param songs the songs to summarise; must not be {@code null}
     * @return the statistics
     */
    public static LibraryStatistics of(List<Song> songs) {
        Objects.requireNonNull(songs, "songs must not be null");

        int favorites = 0;
        int rated = 0;
        int plays = 0;
        int played = 0;
        long ratingSum = 0;
        Duration total = Duration.ZERO;
        Duration listened = Duration.ZERO;
        Map<String, Integer> byArtist = new LinkedHashMap<>();

        for (Song song : songs) {
            if (song.isFavorite()) {
                favorites++;
            }
            if (song.getRating() > 0) {
                rated++;
                ratingSum += song.getRating();
            }
            int count = song.getPlayCount();
            plays += count;
            if (count > 0) {
                played++;
            }

            Duration length = song.getDuration();
            if (length != null) {
                total = total.plus(length);
                listened = listened.plus(length.multipliedBy(count));
            }

            if (count > 0) {
                String artist = song.getArtist() == null || song.getArtist().isBlank()
                        ? "Unknown" : song.getArtist();
                byArtist.merge(artist, count, Integer::sum);
            }
        }

        return new LibraryStatistics(
                songs.size(),
                favorites,
                rated,
                plays,
                played,
                total,
                listened,
                rated == 0 ? 0 : (double) ratingSum / rated,
                top(songs, Comparator.comparingInt(Song::getPlayCount).reversed(),
                        song -> song.getPlayCount() > 0),
                top(songs, Comparator.comparingInt(Song::getRating).reversed(),
                        song -> song.getRating() > 0),
                sortedByValue(byArtist));
    }

    private static List<Song> top(List<Song> songs, Comparator<Song> order,
                                  java.util.function.Predicate<Song> included) {
        return songs.stream().filter(included).sorted(order).limit(TOP_COUNT).toList();
    }

    private static Map<String, Integer> sortedByValue(Map<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(TOP_COUNT)
                .collect(LinkedHashMap::new, (map, e) -> map.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll);
    }
}
