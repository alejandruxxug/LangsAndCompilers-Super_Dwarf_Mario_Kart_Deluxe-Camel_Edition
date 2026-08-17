package com.eia.superdwarfkart.ui;

import com.eia.superdwarfkart.mood.Palette;
import com.eia.superdwarfkart.mood.PaletteRole;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;

/**
 * The glass in front of the two screens that are the machine rather than the software.
 *
 * <p>{@link BootScreen} and {@link ShutdownScreen} are a console powering up and powering down, and
 * they are the only two places in this application that draw no interface at all - which is exactly
 * what makes them the only two where a picture of a picture is the right idea. Everywhere else the
 * 8-bit look comes from hard edges and a sixteen colour palette; here it comes from the display those
 * colours would have arrived on. So this is a <em>curved tube</em>: rounded corners, bowed scanlines, a
 * vignette that hugs the glass, a lit rim, a sheen and a slow sync roll. It is deliberately fenced to
 * those two views: the same treatment over the library would be a filter somebody chose rather than the
 * hardware, and over the runner it would be a full-canvas alpha composite competing with the game for a
 * frame budget that has no GPU behind it (§7).
 *
 * <h2>The curvature is the shape of the mask, not a warp of the picture</h2>
 *
 * <p>A television of this age is a bulging glass bottle, and the two things that say so are the
 * <strong>rounded corners</strong> and the fact that the raster <strong>bows</strong> with the glass.
 * {@link #curveX} and {@link #curveY} take a point on the window and answer where it sits on the tube,
 * which is what makes the grille and the vignette sag; {@link #edgeDistance} then takes that curved
 * point and measures it against a rounded rectangle, which is what shapes the corners and tells the
 * case from the screen.
 *
 * <p><strong>Those are two knobs rather than one, and it was one to begin with.</strong> Reading the
 * silhouette straight off the warp gives a region that meets the window at exactly four points - the
 * middles of its edges - and falls away from all four: a lens, not a television, with its sides bowed
 * in over their whole height and its corners closed to points. That is what the first screenshot
 * showed. Split, {@link #CURVATURE} can be strong enough to see in the raster while
 * {@link #CORNER_SHARE} keeps the outline a rectangle with the corners taken off - and the warp still
 * feeds the outline, so the sides bulge gently the way the front of a tube actually does.
 *
 * <p><strong>What is deliberately not done is warping the picture itself.</strong> A barrel distortion
 * of the drawn frame is a full-canvas pixel remap, per frame, on a machine whose Prism pipeline is the
 * software rasteriser (§7) - it is the single most expensive thing this application could ask for, in
 * front of a screen whose whole job is to appear instantly. So the glass is curved and the picture
 * behind it is flat. On two screens that are mostly black with hard-edged text on them, the cues the eye
 * actually reads are the corner shape and the bow of the scanlines, and both of those are here.
 *
 * <p><strong>The mask is rasterised once and blitted, never redrawn row by row.</strong> Scanlines are
 * the obvious thing to draw as a loop of one-pixel {@code fillRect}s, and at this window's size that is
 * around four hundred calls a frame on a software rasteriser. Every part of the glass is static for a
 * given size and palette, so all of it - curvature, grille, vignette, glass edge, rim, case and sheen -
 * is baked into one {@link WritableImage} on the first frame after a resize and costs exactly one
 * {@code drawImage} afterwards. That is the same lesson {@code MoodOverlayRenderer} learned pre-tiling
 * its layers, applied before it had a chance to cost anything.
 *
 * <p><strong>Two colours in one blit, and the arithmetic is exact.</strong> The glass both darkens
 * (towards {@link PaletteRole#SHADOW}) and lifts (towards {@link PaletteRole#TEXT_PRIMARY}, for the rim
 * and the sheen), and source-over of a shade {@code s} followed by a lift {@code t} collapses to one
 * fill: {@code alpha = 1 - (1-s)(1-t)} carrying {@code s(1-t)} of the shadow and {@code t} of the light.
 * See {@link #mask}. Two blits would have been the obvious way and would have cost twice the fill rate
 * on the one machine that cannot afford it.
 *
 * <p><strong>No colour is named here.</strong> Everything darkens towards {@code SHADOW} and every lift
 * goes towards {@code TEXT_PRIMARY}, both taken from whatever palette the caller passes - which for both
 * screens is {@link Palette#hardware()}, the console's own black and white. Ground rule 7 is untouched:
 * this names roles and asks a palette, exactly as the screens it sits over do.
 *
 * <p>Every quantity is a static pure function of a point, a distance or a clock - see
 * {@link #curveX}, {@link #insideTube}, {@link #tubeHalfWidth}, {@link #scanlineShade},
 * {@link #vignetteShade}, {@link #edgeDistance}, {@link #edgeShade}, {@link #bezelLift},
 * {@link #glareLift} and {@link #rollCentre}. None of this can be photographed while it is moving and
 * none of it can be waited for in a test run with no toolkit, which is the same reason
 * {@code BootScreen}'s envelopes are written that way.
 */
final class CrtEffect {

    /**
     * How much the picture bulges, as the share a point at the far edge of one axis pushes the other
     * axis out by.
     *
     * <p>This is the <em>raster's</em> curve and not the shape of the screen - see
     * {@link #CORNER_SHARE} for that. It is what makes the scanlines sag away from the middle of the
     * tube, which is most of what says the glass is curved rather than merely rounded off: a rounded
     * corner on its own could have been drawn on a flat panel, where a raster that bows could not.
     *
     * <p><strong>The two were one number to begin with and that was wrong, visibly.</strong> Taking
     * the outline from this same warp makes the glass a region that touches the window at exactly four
     * points - the middles of its edges - and falls away from all four, which is a lens rather than a
     * television. The screenshot showed it immediately: the sides bowed in over their whole height and
     * the corners closed to points. Splitting them lets the curve be strong enough to see while the
     * silhouette stays a rectangle with its corners taken off.
     */
    static final double CURVATURE = 0.12;

    /**
     * How round the corners of the tube are, as a share of the shorter side of the window.
     *
     * <p>An old set is a rectangle with the corners taken off, not an oval: the sides and the top run
     * straight for most of their length and turn over an arc a good deal smaller than the picture. At
     * 0.13 of the shorter side that arc is about 104 px on this window, which is the proportion a
     * four-by-three television actually has.
     *
     * <p>It is deliberately not larger. The cut is real screen area, and everything the two bracket
     * screens draw is centred and sits in the middle band - {@link #tubeHalfWidth} is what anything
     * near an edge should be measured against rather than the window's own width.
     */
    static final double CORNER_SHARE = 0.13;

    /**
     * How many rows one scanline cycle spans.
     *
     * <p>Three rather than two: at two, half of every screen is darkened and the effect stops reading
     * as a grille and starts reading as the whole picture being dimmed, which is what a fifty percent
     * duty cycle actually is. At three, one dark row in three leaves the picture bright and the texture
     * visible - and the pixel font's own strokes are two and three pixels wide, so a two-row cycle
     * beats against them and puts a moire pattern through the title.
     *
     * <p><strong>The rows are counted on the tube rather than on the window</strong>, so the grille
     * bows with the glass - see {@link #mask}. That is most of what makes the curvature legible: a
     * rounded corner on its own could be a rounded corner drawn on a flat screen, where a raster that
     * sags at the sides could not.
     */
    static final int SCANLINE_PERIOD = 3;

    /**
     * How dark the dark row of a cycle goes, as a share of the way to {@code SHADOW}.
     *
     * <p>Deepened from 0.30 when the tube was curved, and only that far: the grille is what the title
     * is read through, and every step deeper is a step towards a picture that is dim rather than
     * scanned. Measured on the real shot - see {@code CrtEffectTest}, which pins the average the
     * grille takes off the screen well under a fifth.
     */
    static final double SCANLINE_SHADE = 0.38;

    /** How dark the row after it goes, so the line has an edge rather than a step. */
    static final double SCANLINE_SOFT = 0.12;

    /**
     * How dark the corners go, as a share of the way to {@code SHADOW}.
     *
     * <p>A tube is brightest in the middle and falls away at the edges, and on a screen whose ground is
     * already the darkest role there is, this is visible only where something is drawn on top - which
     * is the whole point. It is what stops a full-screen title from looking like it was pasted onto a
     * flat rectangle. Measured <strong>in tube space</strong>, so the falloff follows the curve rather
     * than sitting in a circle behind it.
     */
    static final double VIGNETTE_SHADE = 0.72;

    /**
     * How far out the vignette starts, as a share of the half-diagonal of the tube.
     *
     * <p>Everything inside this is untouched. Start it at the centre and the middle of the screen -
     * where the title, the bar and the cartridge all are - is already being darkened, which reads as a
     * dirty screen rather than as a curved one.
     */
    static final double VIGNETTE_INNER = 0.38;

    /**
     * How dark the picture goes where it meets the glass, as a share of the way to {@code SHADOW}.
     *
     * <p>Separate from the vignette and much sharper, because it is a different thing: the vignette is
     * the beam falling off towards the corners, and this is the thickness of the glass at the rim. It
     * is what keeps the picture from ending at a hard line against the case.
     */
    static final double EDGE_SHADE = 0.85;

    /** Over how many pixels the picture darkens into the glass edge. */
    static final double EDGE_PIXELS = 40;

    /**
     * How brightly the very edge of the glass catches the light.
     *
     * <p><strong>This is the one part of the curvature that can be seen on a screen with nothing drawn
     * on it, and without it the rounded corners are invisible.</strong> The case is
     * {@code SHADOW} and so is the room, so black corners on a black screen change not one pixel - the
     * same trap that ate one attempt at the boot glitch. A lit rim has somewhere to go, and it is also
     * the truer picture: the curve of the glass is exactly where a tube picks up the light in the room.
     */
    static final double RIM_LIFT = 0.18;

    /**
     * How light the case around the tube is, as a share of the way to {@code TEXT_PRIMARY}.
     *
     * <p>A shade off the room's own black, which is what the moulded surround of a television is next
     * to a screen showing nothing. Faint on purpose: it exists to give the corners a shape, not to put
     * a grey frame around the application.
     */
    static final double BEZEL_LIFT = 0.05;

    /** Over how many pixels the rim's light eases away into the flat case. */
    static final double BEZEL_PIXELS = 16;

    /**
     * How brightly the glass reflects the room, at the middle of the sheen.
     *
     * <p>A broad diagonal band across the upper left, which is where a screen catches a window. It is
     * the one soft-edged thing here besides the vignette, and it is allowed for the same reason: this
     * is a picture of a display rather than a control, so the hard-edge rule is about what is drawn
     * <em>on</em> the tube and not about the tube.
     */
    static final double GLARE_LIFT = 0.07;

    /** Where the middle of the sheen sits along the diagonal, from -1 at the top left to 1 at the bottom right. */
    static final double GLARE_CENTRE = -0.40;

    /** How far the sheen reaches either side of its middle, along the same diagonal. */
    static final double GLARE_WIDTH = 0.75;

    /**
     * How long the sync roll takes to travel the screen once, in seconds.
     *
     * <p>A band of slightly brighter picture drifting down, which is what a tube does when its vertical
     * hold is a little out. <strong>Slow on purpose:</strong> §8b caps anything full-screen and rhythmic
     * at 3 Hz and this is 0.11 Hz, two orders of magnitude under it. It is also the one thing on either
     * of these screens that moves without being asked to, which is what stops a held frame from reading
     * as an application that has frozen - the same job the boot screen's starfield and the companion
     * window's spinning record do.
     */
    static final double ROLL_SECONDS = 9.0;

    /** How tall the roll band is, as a share of the screen's height. */
    static final double ROLL_HEIGHT_SHARE = 0.16;

    /** How much the roll lifts the picture at its brightest. */
    static final double ROLL_ALPHA = 0.06;

    /**
     * How many bands the roll is drawn in, so its edges fade rather than step.
     *
     * <p>Sixteen rather than six, and that came off a screenshot. The room is pure black, so the band
     * is the only thing on it at that end of the screen and every step in its ramp is against nothing
     * at all - at six the roll read as three or four horizontal bars rather than as a swell, which
     * looks like a drawing fault rather than a tube. Sixteen fills are still nothing next to the one
     * full-canvas blit above them, and each is now cut to the tube's own width at its row, so the roll
     * stops at the glass instead of running out across the case.
     */
    static final int ROLL_BANDS = 16;

    /** The baked glass, or {@code null} until the first frame at a given size. */
    private WritableImage mask;

    private double maskWidth;
    private double maskHeight;

    /**
     * The palette the mask was baked with.
     *
     * <p>Compared by identity, exactly as the level meters' colour ramp is: a {@link Palette} is
     * immutable, so a different look is always a different object and the cache invalidates itself.
     */
    private Palette maskPalette;

    // ------------------------------------------------------------------
    // The tube: where a point on the window lands on the glass
    // ------------------------------------------------------------------

    /**
     * Where a point on the window sits across the tube.
     *
     * <p>The standard curvature of every picture of a cathode ray tube there is: a point is pushed
     * outwards along one axis in proportion to how far it already is along the other, so the middle of
     * the picture is untouched and the corners are pushed furthest. Past 1 is not screen at all.
     *
     * @param nx how far across the window, from -1 at the left to 1 at the right
     * @param ny how far down it, from -1 at the top to 1 at the bottom
     * @return the same point in tube coordinates; outside {@code [-1, 1]} means it is on the case
     */
    static double curveX(double nx, double ny) {
        return nx * (1 + CURVATURE * ny * ny);
    }

    /**
     * Where a point on the window sits down the tube. The mirror of {@link #curveX}.
     *
     * @param nx how far across the window, from -1 to 1
     * @param ny how far down it, from -1 to 1
     * @return the same point in tube coordinates
     */
    static double curveY(double nx, double ny) {
        return ny * (1 + CURVATURE * nx * nx);
    }

    /**
     * How large the corner arc is, in pixels.
     *
     * <p>Off the shorter side, so a window of any shape gets the same <em>looking</em> corner rather
     * than one that stretches with it, and capped at half of either side so a very small window
     * degenerates into an oval instead of into nonsense.
     *
     * @param width  the window's width, in pixels
     * @param height its height
     * @return the radius, in pixels
     */
    static double cornerRadius(double width, double height) {
        return Math.min(Math.min(width, height) * CORNER_SHARE, Math.min(width, height) / 2);
    }

    /**
     * How far a point on the tube is from the edge of the glass, in pixels.
     *
     * <p>The exact signed distance to a rounded rectangle: step in from each side by the corner
     * radius, and what is left is a plain box whose distance is trivial and whose corners are a
     * quarter circle. Positive inside the picture, negative out on the case, and <strong>in real
     * pixels on both counts</strong> - so the rim, the glass edge and the case all come out the same
     * thickness across the top as down the sides, which a distance measured in normalised units would
     * not on a window that is nearly twice as wide as it is tall.
     *
     * <p>Takes the point on the <em>tube</em>, not on the window, so the caller's own warp decides how
     * much the silhouette bulges - see {@link #mask}, which feeds it the curved position.
     *
     * @param x      how far across the tube, in pixels
     * @param y      how far down it, in pixels
     * @param width  the window's width, in pixels
     * @param height its height
     * @return pixels to the nearest edge of the glass; negative on the case
     */
    static double edgeDistance(double x, double y, double width, double height) {
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        double radius = cornerRadius(width, height);
        double acrossPast = Math.abs(x - halfWidth) - (halfWidth - radius);
        double downPast = Math.abs(y - halfHeight) - (halfHeight - radius);
        double outAcross = Math.max(acrossPast, 0);
        double outDown = Math.max(downPast, 0);
        double beyond = outAcross > 0 && outDown > 0
                // Only the corner needs the arc, and it is the only place both are past at once.
                ? Math.sqrt(outAcross * outAcross + outDown * outDown)
                : outAcross + outDown;
        return radius - (beyond + Math.min(Math.max(acrossPast, downPast), 0));
    }

    /**
     * @param x      how far across the tube, in pixels
     * @param y      how far down it, in pixels
     * @param width  the window's width, in pixels
     * @param height its height
     * @return whether that point is glass rather than case
     */
    static boolean insideTube(double x, double y, double width, double height) {
        return edgeDistance(x, y, width, height) >= 0;
    }

    /**
     * How much of a window row is actually screen.
     *
     * <p>Solved rather than searched for, because the roll asks this once per band and anything laid
     * out near the top or bottom of either screen should be able to ask it too. Straight down the
     * sides of the box it is the whole row; inside the corner arc it is the chord across the circle.
     *
     * <p>The window's own row rather than the curved one, so this answers what a caller drawing at a
     * given {@code y} may actually use. The bulge only ever pulls the glass further in, so it is the
     * safe side to be on by a few pixels.
     *
     * @param y      how far down the window, in pixels
     * @param width  its width, in pixels
     * @param height its height
     * @return the share of that row that is glass, from 0 for none of it to 1 for all of it
     */
    static double tubeHalfWidth(double y, double width, double height) {
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        if (!(halfWidth > 0) || !(halfHeight > 0)) {
            return 0;
        }
        double radius = cornerRadius(width, height);
        double past = Math.abs(y - halfHeight) - (halfHeight - radius);
        if (past <= 0) {
            return 1;
        }
        // Strictly past the radius, not at it. At exactly the radius the row is the outermost one of
        // the window, where the arc has closed to nothing and what is left is the straight span
        // between the two corners - which is most of the row rather than none of it.
        if (past > radius) {
            return 0;
        }
        return ((halfWidth - radius) + Math.sqrt(radius * radius - past * past)) / halfWidth;
    }

    // ------------------------------------------------------------------
    // The shades and the lifts, as pure functions
    // ------------------------------------------------------------------

    /**
     * How much a row of the raster is darkened by the grille.
     *
     * @param row which row of the tube, from 0
     * @return a share of the way to {@code SHADOW}, from 0 for untouched
     */
    static double scanlineShade(int row) {
        int position = Math.floorMod(row, SCANLINE_PERIOD);
        if (position == 0) {
            return SCANLINE_SHADE;
        }
        // The row after the dark one, so the line has a soft trailing edge in one direction only -
        // which is what a beam actually leaves behind it, and it stops the grille reading as a
        // symmetric pattern of stripes.
        return position == 1 ? SCANLINE_SOFT : 0;
    }

    /**
     * How much a point is darkened by the tube's falloff.
     *
     * <p>Squared past {@link #VIGNETTE_INNER}, so it eases away from the clear middle rather than
     * starting at a visible ring. Taken in <strong>tube</strong> coordinates, so the falloff bulges
     * with the glass instead of being a circle sitting behind a curved picture.
     *
     * @param x how far across the tube, from -1 to 1
     * @param y how far down it, from -1 to 1
     * @return a share of the way to {@code SHADOW}, from 0 for untouched
     */
    static double vignetteShade(double x, double y) {
        // Normalised to the half-diagonal, so the corners reach 1 whatever shape the window is and a
        // wide window is not vignetted harder down its short axis than its long one.
        double radius = Math.sqrt(x * x + y * y) / Math.sqrt(2);
        if (radius <= VIGNETTE_INNER) {
            return 0;
        }
        double past = (radius - VIGNETTE_INNER) / (1 - VIGNETTE_INNER);
        return VIGNETTE_SHADE * Math.min(past * past, 1);
    }

    /**
     * How much the picture darkens as it runs into the thickness of the glass.
     *
     * @param pixels how far inside the glass, from {@link #edgeDistance}
     * @return a share of the way to {@code SHADOW}, from 0 well inside the picture
     */
    static double edgeShade(double pixels) {
        if (pixels >= EDGE_PIXELS) {
            return 0;
        }
        double past = 1 - Math.max(pixels, 0) / EDGE_PIXELS;
        return EDGE_SHADE * past * past;
    }

    /**
     * How brightly the case around the tube is lit.
     *
     * <p>Brightest right against the glass, where the curve catches the light, easing back to the flat
     * {@link #BEZEL_LIFT} of moulded plastic. That gradient <em>is</em> the rounded corner as far as the
     * eye is concerned - see {@link #RIM_LIFT}.
     *
     * @param pixels how far outside the glass, from {@link #edgeDistance}; zero or negative
     * @return a share of the way to {@code TEXT_PRIMARY}
     */
    static double bezelLift(double pixels) {
        double depth = Math.clamp(-pixels / BEZEL_PIXELS, 0, 1);
        double near = (1 - depth) * (1 - depth);
        return BEZEL_LIFT + (RIM_LIFT - BEZEL_LIFT) * near;
    }

    /**
     * How brightly the glass reflects the room at a point.
     *
     * <p>A band along the diagonal rather than a spot, because a sheen on a screen is the shape of
     * whatever is behind the viewer stretched across a curve, and a soft blob reads as a smudge.
     *
     * @param nx how far across the window, from -1 to 1
     * @param ny how far down it, from -1 to 1
     * @return a share of the way to {@code TEXT_PRIMARY}
     */
    static double glareLift(double nx, double ny) {
        double along = (nx + ny) / 2;
        double off = Math.abs(along - GLARE_CENTRE) / GLARE_WIDTH;
        if (off >= 1) {
            return 0;
        }
        double strength = 1 - off;
        return GLARE_LIFT * strength * strength;
    }

    /**
     * Where the middle of the sync roll is at a given moment.
     *
     * <p>Wrapped rather than bounced, and it starts entirely off the top and leaves entirely off the
     * bottom, so it travels in one direction forever: a band that reversed would read as the picture
     * being scrubbed rather than as a hold that is a little out.
     *
     * @param seconds the wall clock, in seconds
     * @param height  the screen's height
     * @return the band's centre, in pixels, which may sit outside the screen at either end
     */
    static double rollCentre(double seconds, double height) {
        double band = height * ROLL_HEIGHT_SHARE;
        double span = height + band;
        double phase = ((seconds / ROLL_SECONDS) % 1 + 1) % 1;
        return -band / 2 + phase * span;
    }

    // ------------------------------------------------------------------
    // Drawing
    // ------------------------------------------------------------------

    /**
     * Puts the glass over whatever has just been drawn.
     *
     * <p>Call this <em>last</em>, over the finished frame: it is a picture of a display, so anything
     * drawn after it is a thing sitting in front of the tube rather than on it.
     *
     * @param gc       where to draw; must be the frontmost canvas
     * @param width    the screen's width, in pixels
     * @param height   its height
     * @param seconds  the wall clock, in seconds, for the roll
     * @param palette  the palette to take {@code SHADOW} and {@code TEXT_PRIMARY} from
     */
    void draw(GraphicsContext gc, double width, double height, double seconds, Palette palette) {
        Image baked = mask(width, height, palette);
        if (baked != null) {
            gc.drawImage(baked, 0, 0);
        }
        drawRoll(gc, width, height, seconds, palette);
    }

    /**
     * The band of slightly brighter picture drifting down the screen.
     *
     * <p>Drawn live rather than baked, because it is the only part of this that moves - and drawn in a
     * handful of bands rather than as a gradient so its edges fade without a {@code LinearGradient},
     * which would be the one soft-edged paint on a screen made entirely of hard ones.
     *
     * <p>Each band is cut to the tube's own width at its row, so the roll stops where the glass does.
     * Left spanning the window it would run out over the case, which is the one thing that would say
     * out loud that the rounded corners are painted on rather than the shape of the screen.
     */
    private void drawRoll(GraphicsContext gc, double width, double height, double seconds,
            Palette palette) {
        double band = height * ROLL_HEIGHT_SHARE;
        if (!(band > 0) || !(width > 0)) {
            return;
        }
        double centre = rollCentre(seconds, height);
        double step = band / ROLL_BANDS;
        for (int i = 0; i < ROLL_BANDS; i++) {
            // Strongest in the middle of the band and nothing at its edges, so it has no boundary.
            double from = -0.5 + i / (double) ROLL_BANDS;
            double to = -0.5 + (i + 1) / (double) ROLL_BANDS;
            double middle = (from + to) / 2;
            double strength = 1 - Math.abs(middle) * 2;
            if (strength <= 0) {
                continue;
            }
            double top = Math.round(centre + from * band);
            double half = tubeHalfWidth(top + step / 2, width, height);
            if (half <= 0) {
                continue;
            }
            double inset = Math.round(width * (1 - half) / 2);
            gc.setFill(palette.color(PaletteRole.TEXT_PRIMARY, ROLL_ALPHA * strength));
            gc.fillRect(inset, top, width - 2 * inset, Math.ceil(step));
        }
    }

    /**
     * The baked glass at a given size, rebuilding it if anything has changed.
     *
     * <p><strong>Everything that darkens is invisible over bare room, and that is correct rather than a
     * bug.</strong> It all shades towards {@code SHADOW}, which on both of these screens is also the
     * ground - so the grille, the vignette and the glass edge appear only where something is drawn,
     * which is what a tube does. It is also a trap worth writing down twice: tearing this image sideways
     * to break the picture up during {@code BootScreen}'s glitch changes not one pixel, since black torn
     * over black is still black, and the same reasoning is why the rounded corners need
     * {@link #RIM_LIFT} to exist at all rather than being a black shape on a black screen.
     *
     * <p>The two directions are composited into one fill per pixel rather than into two images. A shade
     * {@code s} towards {@code SHADOW} followed by a lift {@code t} towards {@code TEXT_PRIMARY} leaves
     * {@code content (1-s)(1-t) + S s (1-t) + W t}, which is exactly one source-over of
     * {@code alpha = 1 - (1-s)(1-t)} carrying those two weights - so the blit is single and the answer
     * is not an approximation of two.
     *
     * @param width   the screen's width, in pixels
     * @param height  its height
     * @param palette the palette to take {@code SHADOW} and {@code TEXT_PRIMARY} from
     * @return the mask, or {@code null} at a size nothing can be drawn at
     */
    private Image mask(double width, double height, Palette palette) {
        int w = (int) Math.round(width);
        int h = (int) Math.round(height);
        if (w <= 0 || h <= 0) {
            return null;
        }
        if (mask != null && maskWidth == w && maskHeight == h && maskPalette == palette) {
            return mask;
        }

        Color shadow = palette.color(PaletteRole.SHADOW);
        Color light = palette.color(PaletteRole.TEXT_PRIMARY);
        double shadowRed = shadow.getRed() * 255;
        double shadowGreen = shadow.getGreen() * 255;
        double shadowBlue = shadow.getBlue() * 255;
        double lightRed = light.getRed() * 255;
        double lightGreen = light.getGreen() * 255;
        double lightBlue = light.getBlue() * 255;

        int[] pixels = new int[w * h];
        for (int y = 0; y < h; y++) {
            double ny = (y + 0.5) / h * 2 - 1;
            double acrossStretch = 1 + CURVATURE * ny * ny;
            int row = y * w;
            for (int x = 0; x < w; x++) {
                double nx = (x + 0.5) / w * 2 - 1;
                double tubeX = nx * acrossStretch;
                double tubeY = ny * (1 + CURVATURE * nx * nx);
                // The silhouette is taken at the *curved* position, so the sides of the box bulge
                // outwards with the picture and the corner arc is not a shape stuck on a flat panel.
                double distance = edgeDistance((tubeX + 1) / 2 * w, (tubeY + 1) / 2 * h, w, h);

                double shade;
                double lift;
                if (distance < 0) {
                    // The case around the tube. Opaque, so anything drawn out here is covered rather
                    // than merely dimmed - which is what makes the corners a shape instead of a tint.
                    shade = 1;
                    lift = bezelLift(distance);
                } else {
                    // The grille is counted down the *tube*, so it bows with the glass.
                    double scan = scanlineShade((int) ((tubeY + 1) / 2 * h));
                    double edges = edgeShade(distance);
                    shade = 1 - (1 - scan) * (1 - vignetteShade(tubeX, tubeY)) * (1 - edges);
                    lift = glareLift(nx, ny);
                }

                double alpha = 1 - (1 - shade) * (1 - lift);
                if (alpha <= 0) {
                    pixels[row + x] = 0;
                    continue;
                }
                double toShadow = shade * (1 - lift) / alpha;
                double toLight = lift / alpha;
                int red = (int) Math.round(shadowRed * toShadow + lightRed * toLight);
                int green = (int) Math.round(shadowGreen * toShadow + lightGreen * toLight);
                int blue = (int) Math.round(shadowBlue * toShadow + lightBlue * toLight);
                pixels[row + x] = ((int) Math.round(alpha * 255) << 24)
                        | (red << 16) | (green << 8) | blue;
            }
        }

        WritableImage baked = new WritableImage(w, h);
        baked.getPixelWriter().setPixels(0, 0, w, h,
                PixelFormat.getIntArgbInstance(), pixels, 0, w);
        mask = baked;
        maskWidth = w;
        maskHeight = h;
        maskPalette = palette;
        return mask;
    }
}
