package santomon.BadApple.sampling;

import santomon.BadApple.data.BadAppleMappedSystem;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Samples and averages the pixel luminance in a box neighborhood around the mapped star system coordinate.
 */
public class BoxAreaSamplingAlgorithm implements PixelSamplingAlgorithm {
    public static final String FACTION_BRIGHT = "sindrian_diktat";
    public static final String FACTION_DARK = "hegemony";

    private final int radius;

    public BoxAreaSamplingAlgorithm() {
        this(3);
    }

    public BoxAreaSamplingAlgorithm(int radius) {
        this.radius = Math.max(1, radius);
    }

    @Override
    public String getId() {
        return "area";
    }

    @Override
    public String getDescription() {
        return "Box area average sampling (radius " + radius + " px)";
    }

    @Override
    public String determineFaction(BufferedImage image, BadAppleMappedSystem targetSystem, List<BadAppleMappedSystem> allSystems, float brightnessThreshold) {
        int width = image.getWidth();
        int height = image.getHeight();

        int minX = Math.max(0, targetSystem.pixelX - radius);
        int maxX = Math.min(width - 1, targetSystem.pixelX + radius);
        int minY = Math.max(0, targetSystem.pixelY - radius);
        int maxY = Math.min(height - 1, targetSystem.pixelY + radius);

        double totalLuminance = 0.0;
        int count = 0;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;
                count++;
            }
        }

        double avgLuminance = (count > 0) ? (totalLuminance / count) : 0.0;
        return (avgLuminance >= brightnessThreshold) ? FACTION_BRIGHT : FACTION_DARK;
    }
}
