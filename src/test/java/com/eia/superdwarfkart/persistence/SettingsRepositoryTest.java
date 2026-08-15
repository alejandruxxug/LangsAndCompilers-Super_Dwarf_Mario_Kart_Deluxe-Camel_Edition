package com.eia.superdwarfkart.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The handful of choices that outlive a launch.
 *
 * <p>The rule that matters most here is that <em>nothing</em> in this file may stop the application
 * opening (ground rule 5), so the failure cases get as much attention as the round trip.
 */
class SettingsRepositoryTest {

    @Test
    @DisplayName("the Spotify application credentials survive a restart")
    void credentialsRoundTrip(@TempDir Path temp) {
        Path file = temp.resolve("settings.json");

        SettingsRepository first = new SettingsRepository(file);
        first.setSpotifyCredentials("an-id", "a-secret");

        SettingsRepository reopened = new SettingsRepository(file);
        assertEquals("an-id", reopened.spotifyClientId());
        assertEquals("a-secret", reopened.spotifyClientSecret());
    }

    @Test
    @DisplayName("clearing the credentials really clears both halves")
    void credentialsClear(@TempDir Path temp) {
        Path file = temp.resolve("settings.json");
        SettingsRepository settings = new SettingsRepository(file);
        settings.setSpotifyCredentials("an-id", "a-secret");

        settings.setSpotifyCredentials(null, null);

        assertNull(settings.spotifyClientId());
        assertNull(settings.spotifyClientSecret());
        assertNull(new SettingsRepository(file).spotifyClientId());
    }

    @Test
    @DisplayName("a blank credential is stored as absent rather than as an empty string")
    void blankCredentialsBecomeNull(@TempDir Path temp) {
        SettingsRepository settings = new SettingsRepository(temp.resolve("settings.json"));

        settings.setSpotifyCredentials("   ", "");

        // Otherwise the catalogue would report itself configured with a credential that cannot work.
        assertNull(settings.spotifyClientId());
        assertNull(settings.spotifyClientSecret());
    }

    /**
     * A settings file written before this feature existed.
     *
     * <p>It has no such keys at all, and must load rather than throw - the whole file is optional
     * and every value in it has a working default.
     */
    @Test
    @DisplayName("a file from an older build loads with the new values simply absent")
    void anOlderFileStillLoads(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("settings.json");
        Files.writeString(file, "{\"version\":1,\"moodId\":\"PEACH_CIRCUIT\",\"racerId\":\"MARIO\"}");

        SettingsRepository settings = new SettingsRepository(file);

        assertEquals("PEACH_CIRCUIT", settings.moodId());
        assertEquals("MARIO", settings.racerId());
        assertNull(settings.spotifyClientId());
        assertTrue(settings.spotifyAutoFetch(), "absent means the default, not false");
    }

    @Test
    @DisplayName("an unreadable file costs the stored settings and nothing else")
    void aCorruptFileIsTreatedAsMissing(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("settings.json");
        Files.writeString(file, "{ this is not json");

        SettingsRepository settings = new SettingsRepository(file);

        assertNull(settings.moodId());
        assertNull(settings.spotifyClientId());
        // And it must still be writable afterwards, so one bad launch is not permanent.
        settings.setSpotifyCredentials("an-id", "a-secret");
        assertEquals("an-id", new SettingsRepository(file).spotifyClientId());
    }

    /**
     * A key this build has never heard of, as a later version would write.
     *
     * <p>Jackson throws on an unknown property unless told not to, which would turn a profile
     * written by a newer build into a refusal to open.
     */
    @Test
    @DisplayName("a key from a newer build is ignored rather than fatal")
    void unknownKeysAreIgnored(@TempDir Path temp) throws IOException {
        Path file = temp.resolve("settings.json");
        Files.writeString(file,
                "{\"version\":9,\"moodId\":\"SKY_GARDEN\",\"somethingFromTheFuture\":true}");

        assertEquals("SKY_GARDEN", new SettingsRepository(file).moodId());
    }
}
