package santomon.BadApple.data;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory storage holding the latest preprocessed Bad Apple frame sequence and system mappings.
 */
public class BadAppleSession {
    public static BadAppleSession INSTANCE = null;

    public List<BadAppleFrameData> frameDeltas = new ArrayList<>();
    public List<BadAppleMappedSystem> mappedSystems = new ArrayList<>();
    public int totalFramesProcessed = 0;
    public int totalChanges = 0;
    public float minHyperspaceX = 0f;
    public float maxHyperspaceX = 0f;
    public float minHyperspaceY = 0f;
    public float maxHyperspaceY = 0f;
    public int frameWidth = 480;
    public int frameHeight = 360;
    public String algorithmName = "point";
    public float brightnessThreshold = 0.5f;
    public long preprocessDurationMs = 0;

    public boolean hasData() {
        return frameDeltas != null && !frameDeltas.isEmpty();
    }

    public void clear() {
        frameDeltas.clear();
        mappedSystems.clear();
        totalFramesProcessed = 0;
        totalChanges = 0;
    }
}
