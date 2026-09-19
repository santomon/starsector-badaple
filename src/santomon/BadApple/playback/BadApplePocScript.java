package santomon.BadApple.playback;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import org.lazywizard.console.Console;

/**
 * Proof of concept script that alternates the faction of Chicomoztoc between
 * Hegemony and Sindrian Diktat at regular intervals.
 */
public class BadApplePocScript implements EveryFrameScript {

    public static BadApplePocScript ACTIVE_INSTANCE = null;

    private final String targetMarketId;
    private final String factionA = "hegemony";
    private final String factionB = "sindrian_diktat";

    private float intervalSeconds;
    private float elapsed = 0.0f;
    private int flipCount = 0;
    private boolean isPlaying = true;
    private boolean isDone = false;

    public BadApplePocScript(String targetMarketId, float intervalSeconds) {
        this.targetMarketId = targetMarketId;
        this.intervalSeconds = Math.max(0.1f, intervalSeconds);
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public boolean runWhilePaused() {
        // Run while paused so changes are visible immediately on the campaign map screen
        return true;
    }

    @Override
    public void advance(float amount) {
        if (!isPlaying || isDone) {
            return;
        }

        elapsed += amount;

        if (elapsed >= intervalSeconds) {
            elapsed = 0.0f;

            SectorAPI sector = Global.getSector();
            if (sector == null || sector.getEconomy() == null) {
                return;
            }

            // =========================================================================
            // --- 1. Target Market Resolution ---
            // =========================================================================
            MarketAPI market = sector.getEconomy().getMarket(targetMarketId);
            if (market == null) {
                // Fallback search by display name
                for (MarketAPI m : sector.getEconomy().getMarketsCopy()) {
                    if (m.getName() != null && m.getName().equalsIgnoreCase("Chicomoztoc")) {
                        market = m;
                        break;
                    }
                }
            }

            if (market == null) {
                Console.showMessage("[BadApple POC] Error: Target market '" + targetMarketId + "' not found.");
                stop();
                return;
            }

            // =========================================================================
            // --- 2. Determine Next Faction & Apply Faction Flip ---
            // =========================================================================
            String currentFaction = market.getFactionId();
            String nextFaction = factionA.equalsIgnoreCase(currentFaction) ? factionB : factionA;

            market.setFactionId(nextFaction);

            // Update primary entity (planet/station)
            SectorEntityToken primaryEntity = market.getPrimaryEntity();
            if (primaryEntity != null) {
                primaryEntity.setFaction(nextFaction);
            }

            // Update all connected orbital entities
            if (market.getConnectedEntities() != null) {
                for (SectorEntityToken connected : market.getConnectedEntities()) {
                    if (connected != null) {
                        connected.setFaction(nextFaction);
                    }
                }
            }

            flipCount++;

            // =========================================================================
            // --- 3. Trigger KMU Political Map Refresh ---
            // =========================================================================
            try {
                MarketPoliticsRefresh.reportMarketChange(
                        sector, market, "faction_change", "badapple_poc"
                );
            } catch (Throwable ignored) {}

            try {
                SectorMapMachinery machinery = SectorMapMachineryIndex.resolveMachineryFor(sector);
                if (machinery != null) {
                    MapLayerRefreshBoard board = machinery.resolveRefreshBoard();
                    if (board != null) {
                        board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
                    }
                }
            } catch (Throwable ignored) {}

            // =========================================================================
            // --- 4. Diagnostics & Status Feedback ---
            // =========================================================================
            Console.showMessage(String.format("[BadApple POC #%d] %s flipped from '%s' to '%s' (Interval: %.1fs)",
                    flipCount, market.getName(), currentFaction, nextFaction, intervalSeconds));
        }
    }

    public void stop() {
        this.isPlaying = false;
        this.isDone = true;
        if (ACTIVE_INSTANCE == this) {
            ACTIVE_INSTANCE = null;
        }
    }

    public void pause() {
        this.isPlaying = false;
    }

    public void resume() {
        this.isPlaying = true;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public float getIntervalSeconds() {
        return intervalSeconds;
    }

    public void setIntervalSeconds(float intervalSeconds) {
        this.intervalSeconds = Math.max(0.1f, intervalSeconds);
    }

    public int getFlipCount() {
        return flipCount;
    }

    public String getTargetMarketId() {
        return targetMarketId;
    }
}
