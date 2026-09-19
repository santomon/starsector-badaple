package santomon.BadApple.data;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

/**
 * Represents a StarSystem mapped to Bad Apple image pixel coordinates.
 */
public class BadAppleMappedSystem {
    public StarSystemAPI system;
    public MarketAPI primaryMarket;
    public float hyperspaceX;
    public float hyperspaceY;
    public int pixelX;
    public int pixelY;
    public String currentFactionId;

    public BadAppleMappedSystem(StarSystemAPI system, MarketAPI primaryMarket, float hyperspaceX, float hyperspaceY, int pixelX, int pixelY, String currentFactionId) {
        this.system = system;
        this.primaryMarket = primaryMarket;
        this.hyperspaceX = hyperspaceX;
        this.hyperspaceY = hyperspaceY;
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.currentFactionId = currentFactionId;
    }
}
