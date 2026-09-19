package santomon.BadApple.data;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a StarSystem mapped to Bad Apple image pixel coordinates.
 */
public class BadAppleMappedSystem {
    public StarSystemAPI system;
    public MarketAPI primaryMarket;
    public List<MarketAPI> markets = new ArrayList<>();
    public float hyperspaceX;
    public float hyperspaceY;
    public int pixelX;
    public int pixelY;
    public String currentFactionId;

    public BadAppleMappedSystem(StarSystemAPI system, MarketAPI primaryMarket, List<MarketAPI> markets, float hyperspaceX, float hyperspaceY, int pixelX, int pixelY, String currentFactionId) {
        this.system = system;
        this.primaryMarket = primaryMarket;
        if (markets != null) {
            this.markets.addAll(markets);
        } else if (primaryMarket != null) {
            this.markets.add(primaryMarket);
        }
        this.hyperspaceX = hyperspaceX;
        this.hyperspaceY = hyperspaceY;
        this.pixelX = pixelX;
        this.pixelY = pixelY;
        this.currentFactionId = currentFactionId;
    }

    public BadAppleMappedSystem(StarSystemAPI system, MarketAPI primaryMarket, float hyperspaceX, float hyperspaceY, int pixelX, int pixelY, String currentFactionId) {
        this(system, primaryMarket, null, hyperspaceX, hyperspaceY, pixelX, pixelY, currentFactionId);
    }
}
