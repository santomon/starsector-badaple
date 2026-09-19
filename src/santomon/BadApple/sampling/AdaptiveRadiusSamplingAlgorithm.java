package santomon.BadApple.sampling;

import santomon.BadApple.data.BadAppleMappedSystem;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Dynamically computes sampling radius based on nearest neighbor star system distance,
 * applying Gaussian distance weighting over the influenced area.
 */
public class AdaptiveRadiusSamplingAlgorithm implements PixelSamplingAlgorithm {
    public static final String FACTION_BRIGHT = "sindrian_diktat";
    public static final String FACTION_DARK = "hegemony";

    @Override
    public String getId() {
        return "adaptive";
    }

    @Override
    public String getDescription() {
        return "Distance-adaptive Gaussian weighted neighborhood sampling";
    }

    @Override
    public String determineFaction(BufferedImage image, BadAppleMappedSystem targetSystem, List<BadAppleMappedSystem> allSystems, float brightnessThreshold) {
        int width = image.getWidth();
        int height = image.getHeight();

        // 1. Calculate nearest neighbor distance in pixel coordinates
        double nearestDistSq = Double.MAX_VALUE;
        if (allSystems != null && allSystems.size() > 1) {
            for (BadAppleMappedSystem other : allSystems) {
                if (other == targetSystem) continue;
                double dx = other.pixelX - targetSystem.pixelX;
                double dy = other.pixelY - targetSystem.pixelY;
                double dSq = dx * dx + dy * dy;
                if (dSq < nearestDistSq && dSq > 0.0) {
                    nearestDistSq = dSq;
                }
            }
        }

        double nearestDist = (nearestDistSq < Double.MAX_VALUE) ? Math.sqrt(nearestDistSq) : 10.0;
        int radius = Math.max(1, (int) Math.round(nearestDist * 0.45));
        double sigma = Math.max(0.8, radius / 2.0);
        double twoSigmaSq = 2.0 * sigma * sigma;

        int minX = Math.max(0, targetSystem.pixelX - radius);
        int maxX = Math.min(width - 1, targetSystem.pixelX + radius);
        int minY = Math.max(0, targetSystem.pixelY - radius);
        int maxY = Math.min(height - 1, targetSystem.pixelY + radius);

        double weightedLuminanceSum = 0.0;
        double totalWeight = 0.0;

        for (int y = minY; y <= maxY; y++) {
            double dy = y - targetSystem.pixelY;
            for (int x = minX; x <= maxX; x++) {
                double dx = x - targetSystem.pixelX;
                double distSq = dx * dx + dy * dy;
                if (distSq > radius * radius) continue;

                double weight = Math.exp(-distSq / twoSigmaSq);

                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0;

                weightedLuminanceSum += lum * weight;
                totalWeight += weight;
            }
        }

        double finalLuminance = (totalWeight > 0.0) ? (weightedLuminanceSum / totalWeight) : 0.0;
        return (finalLuminance >= brightnessThreshold) ? FACTION_BRIGHT : FACTION_DARK;
    }
}
