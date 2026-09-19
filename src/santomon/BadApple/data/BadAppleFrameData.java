package santomon.BadApple.data;

import java.util.ArrayList;
import java.util.List;

/**
 * Data container representing all market delta changes for a single video frame.
 */
public class BadAppleFrameData {
    public int frameIndex;
    public String frameFileName;
    public List<BadAppleMarketChange> changes = new ArrayList<>();

    public BadAppleFrameData(int frameIndex, String frameFileName) {
        this.frameIndex = frameIndex;
        this.frameFileName = frameFileName;
    }
}
