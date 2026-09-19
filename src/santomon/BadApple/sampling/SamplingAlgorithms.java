package santomon.BadApple.sampling;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Registry of available sampling algorithms.
 * Allows easy drop-in registration and retrieval of pixel-to-faction decision strategies.
 */
public class SamplingAlgorithms {
    private static final Map<String, PixelSamplingAlgorithm> REGISTRY = new LinkedHashMap<>();

    static {
        register(new PointSamplingAlgorithm());
        register(new BoxAreaSamplingAlgorithm());
        register(new AdaptiveRadiusSamplingAlgorithm());
    }

    public static void register(PixelSamplingAlgorithm algorithm) {
        if (algorithm != null && algorithm.getId() != null) {
            REGISTRY.put(algorithm.getId().toLowerCase().trim(), algorithm);
        }
    }

    public static PixelSamplingAlgorithm get(String name) {
        if (name == null || name.trim().isEmpty()) {
            return REGISTRY.get("point");
        }
        PixelSamplingAlgorithm algo = REGISTRY.get(name.toLowerCase().trim());
        if (algo == null) {
            return REGISTRY.get("point");
        }
        return algo;
    }

    public static Set<String> getAvailableIds() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }

    public static Map<String, PixelSamplingAlgorithm> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
