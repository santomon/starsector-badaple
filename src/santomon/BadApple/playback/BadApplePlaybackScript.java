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
import santomon.BadApple.data.BadAppleFrameData;
import santomon.BadApple.data.BadAppleMarketChange;
import santomon.BadApple.data.BadAppleSession;

import java.util.List;

/**
 * EveryFrameScript responsible for stepping through Bad Apple frames, flipping colony factions,
 * and triggering KMU political map recalculations.
 */
public class BadApplePlaybackScript implements EveryFrameScript {

    public static BadApplePlaybackScript ACTIVE_INSTANCE = null;

    private final BadAppleSession session;
    private float frameDelaySeconds;
    private int currentFrameIndex = 0;
    private float elapsedSinceLastFrame = 0.0f;
    private boolean isPlaying = true;
    private boolean isDone = false;
    private boolean loop = false;

    /**
     * @param session The preprocessed session data.
     * @param frameDelaySeconds Delay per frame step. Default is 2.0s (0.5 frames/sec).
     * @param startFrame Initial frame index.
     */
    public BadApplePlaybackScript(BadAppleSession session, float frameDelaySeconds, int startFrame) {
        this.session = session;
        this.frameDelaySeconds = Math.max(0.01f, frameDelaySeconds);
        this.currentFrameIndex = Math.max(0, Math.min(session.frameDeltas.size() - 1, startFrame));
    }

    @Override
    public boolean isDone() {
        return isDone;
    }

    @Override
    public boolean runWhilePaused() {
        // Run while game clock is paused so video can be recorded without in-game campaign days passing
        return true;
    }

    @Override
    public void advance(float amount) {
        if (!isPlaying || isDone || session == null || !session.hasData()) {
            return;
        }

        elapsedSinceLastFrame += amount;

        if (elapsedSinceLastFrame >= frameDelaySeconds) {
            elapsedSinceLastFrame = 0.0f;

            if (currentFrameIndex >= session.frameDeltas.size()) {
                if (loop) {
                    currentFrameIndex = 0;
                } else {
                    isDone = true;
                    isPlaying = false;
                    Console.showMessage("=== Bad Apple Playback Finished ===");
                    return;
                }
            }

            // --- 1. Apply Frame Market Changes ---
            BadAppleFrameData frameData = session.frameDeltas.get(currentFrameIndex);
            if (frameData != null && frameData.changes != null && !frameData.changes.isEmpty()) {
                SectorAPI sector = Global.getSector();
                for (BadAppleMarketChange change : frameData.changes) {
                    MarketAPI market = sector.getEconomy().getMarket(change.marketId);
                    if (market != null) {
                        market.setFactionId(change.newFactionId);

                        // Update physical entities attached to market
                        if (market.getPrimaryEntity() != null) {
                            market.getPrimaryEntity().setFaction(change.newFactionId);
                        }
                        if (market.getConnectedEntities() != null) {
                            for (SectorEntityToken entity : market.getConnectedEntities()) {
                                if (entity != null) {
                                    entity.setFaction(change.newFactionId);
                                }
                            }
                        }

                        notifyKMURefresh(sector, market, change.previousFactionId, change.newFactionId);
                    }
                }
            }

            // --- 2. Diagnostics & Console Status ---
            if (currentFrameIndex % 50 == 0 || currentFrameIndex == session.frameDeltas.size() - 1) {
                int total = session.frameDeltas.size();
                float progress = (float) (currentFrameIndex + 1) / total * 100.0f;
                Console.showMessage(String.format("[BadApple] Frame %d / %d (%.1f%%)", currentFrameIndex + 1, total, progress));
            }

            currentFrameIndex++;
        }
    }

    /**
     * Notifies KMU's political map layer about the market faction change to trigger border recalculation.
     */
    private void notifyKMURefresh(SectorAPI sector, MarketAPI market, String oldFaction, String newFaction) {
        try {
            MarketPoliticsRefresh.reportMarketChange(
                    sector, market, "faction_change", "badapple"
            );
        } catch (Throwable ignored) {
        }
        try {
            SectorMapMachinery machinery = SectorMapMachineryIndex.resolveMachineryFor(sector);
            if (machinery != null) {
                MapLayerRefreshBoard board = machinery.resolveRefreshBoard();
                if (board != null) {
                    board.requestRefresh(MapLayerCommonRefreshSignal.GEOMETRY);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void pause() {
        this.isPlaying = false;
    }

    public void resume() {
        this.isPlaying = true;
    }

    public void stop() {
        this.isPlaying = false;
        this.isDone = true;
        if (ACTIVE_INSTANCE == this) {
            ACTIVE_INSTANCE = null;
        }
    }

    public void setFrameDelaySeconds(float delay) {
        this.frameDelaySeconds = Math.max(0.01f, delay);
    }

    public float getFrameDelaySeconds() {
        return frameDelaySeconds;
    }

    public int getCurrentFrameIndex() {
        return currentFrameIndex;
    }

    public void setCurrentFrameIndex(int index) {
        if (session != null && session.hasData()) {
            this.currentFrameIndex = Math.max(0, Math.min(session.frameDeltas.size() - 1, index));
        }
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
