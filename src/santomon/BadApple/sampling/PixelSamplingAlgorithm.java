package santomon.BadApple.sampling;

import santomon.BadApple.data.BadAppleMappedSystem;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Strategy interface for deciding which set of pixels in a frame determines a star system's market faction.
 * Can be drop-in replaced or extended to test different visual mapping algorithms.
 */
public interface PixelSamplingAlgorithm {
    /**
     * Unique identifier used in console commands (e.g., "point", "area", "adaptive").
     */
    String getId();

    /**
     * Short description of the algorithm behavior.
     */
    String getDescription();

    /**
     * Analyzes image pixels corresponding to the star system and decides whether it belongs to Sindrian Diktat (bright)
     * or Hegemony (dark).
     *
     * @param image The Bad Apple frame BufferedImage.
     * @param targetSystem The system being evaluated, including mapped pixelX and pixelY.
     * @param allSystems List of all mapped systems in the sector (for spatial/distance weighting).
     * @param brightnessThreshold Threshold between 0.0f and 1.0f (default 0.5f).
     * @return Target faction ID ("sindrian_diktat" or "hegemony").
     */
    String determineFaction(BufferedImage image, BadAppleMappedSystem targetSystem, List<BadAppleMappedSystem> allSystems, float brightnessThreshold);
}
