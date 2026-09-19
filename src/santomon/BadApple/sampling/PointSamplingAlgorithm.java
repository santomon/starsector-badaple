package santomon.BadApple.sampling;

import santomon.BadApple.data.BadAppleMappedSystem;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Samples the exact single pixel at the star system's mapped coordinates.
 */
public class PointSamplingAlgorithm implements PixelSamplingAlgorithm {
    public static final String FACTION_BRIGHT = "sindrian_diktat";
    public static final String FACTION_DARK = "hegemony";

    @Override
    public String getId() {
        return "point";
    }

    @Override
    public String getDescription() {
        return "Direct single-pixel center sampling";
    }

    @Override
    public String determineFaction(BufferedImage image, BadAppleMappedSystem targetSystem, List<BadAppleMappedSystem> allSystems, float brightnessThreshold) {
        int x = Math.max(0, Math.min(image.getWidth() - 1, targetSystem.pixelX));
        int y = Math.max(0, Math.min(image.getHeight() - 1, targetSystem.pixelY));

        int rgb = image.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;

        // Relative perceptual luminance
        float luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;

        return (luminance >= brightnessThreshold) ? FACTION_BRIGHT : FACTION_DARK;
    }
}
