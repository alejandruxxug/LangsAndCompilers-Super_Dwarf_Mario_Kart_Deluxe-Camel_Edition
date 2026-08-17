package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the eject: the boot screen's gesture run backwards.
 *
 * <p>Static for the reason the boot screen's own envelopes are. Every movement here is a fade or a
 * travel, and <strong>a still of either is a still of something at one value</strong> - a picture taken
 * at the wrong instant looks exactly like a screen that failed to draw, which is the one picture that
 * would be believed. These are what {@code previewAt} is asked for a stated instant against.
 */
@DisplayName("Shutdown screen geometry")
class ShutdownScreenGeometryTest {

    @Test
    @DisplayName("the eject starts the cartridge exactly where the boot screen left it")
    void theCartridgeStartsHalfwayOut() {
        // The two screens are one continuous object: the boot screen pushes it in this far and stops,
        // and hours later the eject picks it up from there. Starting it below the mouth instead would
        // have the machine hand back something it had swallowed whole, and would spend the first third
        // of the travel climbing to a position nobody ever moved it away from.
        double mouthY = 600;
        double height = 350;
        double seated = BootScreen.seatedY(mouthY, height);

        assertTrue(seated < mouthY, "part of it has to be inside the slot");
        assertTrue(seated > mouthY - height, "and part of it has to still be above the lip");

        // Not merely "partway" - the exact share the boot screen's own SEAT_SHARE describes, which is
        // what stops the two ends of the session disagreeing the first time that constant is moved.
        double showing = (mouthY - seated) / height;
        assertEquals(1 - BootScreen.SEAT_SHARE, showing, 1e-9,
                "the share left above the slot should be the boot screen's own");
        assertTrue(showing > 0.35 && showing < 0.65,
                "and it should read as halfway out, was " + showing);
    }

    @Test
    @DisplayName("the picture tears as the contact breaks, and settles before anything moves")
    void theTearRunsFirst() {
        assertEquals(0, ShutdownScreen.tearProgress(0), 1e-9,
                "the tear starts the instant the application is told to close");
        assertEquals(1, ShutdownScreen.tearProgress(ShutdownScreen.GLITCH_OUT), 1e-9,
                "and is over by GLITCH_OUT");

        // Cause and consequence, in that order: the contact breaks, the picture goes, and *then* the
        // machine hands the cartridge back. Overlapping them puts both on screen at once and reads as
        // one confused event rather than two.
        assertTrue(ShutdownScreen.GLITCH_OUT < ShutdownScreen.EJECT_IN,
                "the tear has to be finished before the cartridge starts moving");
        assertEquals(1, ShutdownScreen.tearProgress(ShutdownScreen.EJECT_IN), 1e-9);
        assertEquals(0, ShutdownScreen.emergence(ShutdownScreen.GLITCH_OUT), 1e-9,
                "and nothing should have moved while it was tearing");
    }

    @Test
    @DisplayName("both ends of the session tear for about the same length of time")
    void theTearMatchesTheBootScreens() {
        // One a fraction of a sequence and the other an absolute, so they are not tied together - but
        // they are the same electrical event twice and are meant to look it. Asserted rather than left
        // to whoever next moves either number.
        double ejectTear = ShutdownScreen.GLITCH_OUT * ShutdownScreen.EJECT_SECONDS;
        assertTrue(Math.abs(ejectTear - BootScreen.GLITCH_SECONDS) < 0.15,
                "the eject's tear was " + ejectTear + " s against the insert's "
                        + BootScreen.GLITCH_SECONDS + " s");
    }

    @Test
    @DisplayName("the name hands over rather than appearing twice")
    void theNameIsNeverInTwoPlacesAtOnce() {
        // The cartridge is on screen from the first frame now, and its label is most of what is above
        // the lip - so the label's own copy of the name has to arrive exactly as the splash leaves,
        // or the title is printed twice at once. Written as a complement rather than as a second ramp
        // precisely so this holds by construction.
        for (double t = 0; t <= 1.0001; t += 0.01) {
            assertEquals(1, ShutdownScreen.splashAlpha(t) + ShutdownScreen.plateAlpha(t), 1e-9,
                    "the name's two homes should always share exactly one copy of it, at " + t);
        }
        assertEquals(0, ShutdownScreen.plateAlpha(0), 1e-9,
                "the label should be blank while the name is across the screen");
        assertEquals(1, ShutdownScreen.plateAlpha(ShutdownScreen.EJECT_OUT), 1e-9,
                "and carrying it by the time the cartridge is out");
    }

    @Test
    @DisplayName("every moment worth photographing shows something the others do not")
    void theMomentsAreDistinct() {
        // Three shots because no two of the three things on this screen are ever there together. A
        // moment that photographed the same state as another would be a screenshot nobody could read
        // anything off, and the run would still pass.
        double glitch = ShutdownScreen.Moment.GLITCH.instant();
        double splash = ShutdownScreen.Moment.SPLASH.instant();
        double eject = ShutdownScreen.Moment.EJECT.instant();

        assertTrue(ShutdownScreen.tearProgress(glitch) < 1, "the glitch shot must catch a live tear");
        assertEquals(0, ShutdownScreen.emergence(glitch), 1e-9,
                "and the cartridge parked, or it is photographing two things at once");

        assertEquals(1, ShutdownScreen.tearProgress(splash), 1e-9,
                "the splash shot must be taken after the picture has settled");
        assertEquals(1, ShutdownScreen.splashAlpha(splash), 1e-9,
                "with the name still across the screen");

        double out = ShutdownScreen.emergence(eject);
        assertTrue(out > 0.2 && out < 0.9,
                "the eject shot must catch the cartridge moving rather than parked, was " + out);
        assertEquals(0, ShutdownScreen.blackout(eject), 1e-9,
                "and before the picture starts going away");
    }

    @Test
    @DisplayName("the cartridge is fully in before it starts, and fully out well before the fade")
    void theEjectIsComplete() {
        assertEquals(0, ShutdownScreen.emergence(0), 1e-9,
                "it should start seated, which is where theCartridgeStartsHalfwayOut says that is");
        assertEquals(0, ShutdownScreen.emergence(ShutdownScreen.EJECT_IN), 1e-9,
                "it should not have moved before it starts moving");
        assertEquals(1, ShutdownScreen.emergence(ShutdownScreen.EJECT_OUT), 1e-9,
                "it should be all the way out at EJECT_OUT");

        // And it stays there. A cartridge that finished its travel and then crept is a mechanism that
        // has not let go, which is the opposite of what this screen is saying.
        assertEquals(1, ShutdownScreen.emergence(ShutdownScreen.FADE_OUT), 1e-9);
        assertEquals(1, ShutdownScreen.emergence(1), 1e-9);
    }

    @Test
    @DisplayName("it comes out and never goes back in")
    void theEjectIsMonotonic() {
        double previous = -1;
        for (double t = 0; t <= 1.0001; t += 0.01) {
            double out = ShutdownScreen.emergence(t);
            assertTrue(out >= previous - 1e-9,
                    "the cartridge must never sink back into the slot, at " + t);
            previous = out;
        }
    }

    @Test
    @DisplayName("the name is never on the screen and on the label at once")
    void theSplashHandsOverToTheLabel() {
        // The boot screen's rule, run backwards: the title is on the screen while the cartridge is
        // not, and on the cartridge's label once it is. The two overlapping would be the name printed
        // twice at the same moment, which is precisely why the boot splash did not exist until the
        // cartridge went into the machine.
        assertEquals(1, ShutdownScreen.splashAlpha(0), 1e-9,
                "the splash should be up before anything moves");

        double handedOver = ShutdownScreen.EJECT_IN + ShutdownScreen.SPLASH_HANDOVER;
        assertEquals(0, ShutdownScreen.splashAlpha(handedOver), 1e-9,
                "and gone once the handover is done");
        assertTrue(handedOver < ShutdownScreen.EJECT_OUT,
                "the splash has to be gone before the cartridge finishes arriving");

        // The handover finishes early in the travel, which is the overlap being *used* rather than
        // tolerated: the name is back on the label while the cartridge is still barely out of the
        // slot, so what the eye follows is the title travelling with the object that carries it.
        assertTrue(ShutdownScreen.emergence(handedOver) < 0.5,
                "the cartridge should barely have moved when the splash finishes leaving");
    }

    @Test
    @DisplayName("the splash only ever fades away")
    void theSplashIsMonotonic() {
        double previous = 2;
        for (double t = 0; t <= 1.0001; t += 0.01) {
            double alpha = ShutdownScreen.splashAlpha(t);
            assertTrue(alpha <= previous + 1e-9, "the splash must never come back, at " + t);
            previous = alpha;
        }
    }

    @Test
    @DisplayName("the screen is black by the end, and not before")
    void theBlackoutFinishesTheSequence() {
        assertEquals(0, ShutdownScreen.blackout(0), 1e-9);
        assertEquals(0, ShutdownScreen.blackout(ShutdownScreen.FADE_OUT), 1e-9);
        assertEquals(1, ShutdownScreen.blackout(1), 1e-9,
                "the application must not go while there is still a picture on screen");
    }

    @Test
    @DisplayName("the sound has room to fade before the application goes")
    void thereIsRoomForTheFade() {
        // The eject sound is 7.71 s against a three second animation, so it is always still playing
        // when the blackout starts and SoundEffect.stop() fades over a quarter of a second. Firing
        // that at FADE_OUT rather than at the end is what gives the fade somewhere to happen; doing
        // both at once would fade into a process that has already exited, and the tick that leaves is
        // the exact thing FADE_SECONDS exists to prevent.
        double roomSeconds = (1 - ShutdownScreen.FADE_OUT) * ShutdownScreen.EJECT_SECONDS;
        assertTrue(roomSeconds > 0.3,
                "there should be more than the fade's own length left after the blackout starts, was "
                        + roomSeconds + " s");
    }

    @Test
    @DisplayName("quitting stays quick enough not to be the hang this screen replaced")
    void theEjectIsShorterThanTheHangItReplaced() {
        // The go-librespot teardown gives the child a five second grace period, and a frozen dock for
        // that long is what this screen exists to stop looking like. An animation that took longer
        // than the wait it was covering would have replaced the problem with itself.
        assertTrue(ShutdownScreen.EJECT_SECONDS < 5,
                "the eject must be shorter than the teardown it hides");
        assertTrue(ShutdownScreen.EJECT_SECONDS >= 2,
                "and long enough for the cartridge's travel to be followed by eye");
    }

    @Test
    @DisplayName("both ends of the application break the name in the same places")
    void bothScreensWrapTheNameTheSameWay() {
        // It goes through BootScreen.wrapName rather than having an idea of its own, so a title
        // hyphenated one way on the way in and another on the way out is not possible. The sizes
        // differ deliberately - this is a goodbye rather than an arrival.
        double width = AppConfig.MAIN_WIDTH;
        double bootSize = BootScreen.splashAt(width).size();
        double shutdownSize = ShutdownScreen.splashFontSize(width);
        assertTrue(shutdownSize < bootSize,
                "the goodbye should be smaller than the arrival, was " + shutdownSize
                        + " against " + bootSize);

        List<String> lines = BootScreen.wrapName(AppConfig.APP_NAME,
                (int) Math.floor(width * 0.7 / shutdownSize));
        assertEquals(AppConfig.APP_NAME, String.join("", lines),
                "wrapping must lose nothing");
        assertTrue(lines.size() <= 3, "and must still read as a logo rather than a column");
    }
}
