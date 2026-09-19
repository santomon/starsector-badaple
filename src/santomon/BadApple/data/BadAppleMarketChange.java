package santomon.BadApple.data;

/**
 * Data container representing a single market's faction change for a specific frame.
 */
public class BadAppleMarketChange {
    public String marketId;
    public String marketName;
    public String previousFactionId;
    public String newFactionId;

    public BadAppleMarketChange(String marketId, String marketName, String previousFactionId, String newFactionId) {
        this.marketId = marketId;
        this.marketName = marketName;
        this.previousFactionId = previousFactionId;
        this.newFactionId = newFactionId;
    }
}
