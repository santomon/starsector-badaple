package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import santomon.BadApple.data.BadAppleExclusions;
import santomon.BadApple.data.BadAppleFrameData;
import santomon.BadApple.data.BadAppleMappedSystem;
import santomon.BadApple.data.BadAppleMarketChange;
import santomon.BadApple.data.BadAppleSession;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Console command to preprocess Bad Apple video frames and sector star systems into market delta changes.
 *
 * Direct Lookup Table Workflow:
 * 1. Discover frame dimensions from the first video frame (output_0001.jpg).
 * 2. Fetch sector star systems with active markets (excluding outlier systems like Limbo) and compute the hyperspace bounding box.
 * 3. Map each system to a static integer pixel coordinate (pixelX, pixelY) in the lookup table.
 * 4. Sequentially iterate through frame images, sample pixels directly at lookup coordinates, and record market faction deltas.
 * 5. Cache preprocessed delta sequences in memory for instant playback via badapple_play.
 *
 * Syntax:
 *   badapple_prep [maxFrames] [brightnessThreshold]
 * Examples:
 *   badapple_prep
 *   badapple_prep 200
 *   badapple_prep 6572 0.5
 */
public class BadApplePreprocessCommand implements BaseCommand {

    public static final String FACTION_HEGEMONY = "hegemony";
    public static final String FACTION_DIKTAT = "sindrian_diktat";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: badapple_prep can only be run within campaign mode.");
            return CommandResult.WRONG_CONTEXT;
        }

        long startTimeMs = System.currentTimeMillis();

        // =========================================================================
        // --- 1. Argument Parsing & Validation ---
        // =========================================================================
        int maxFrames = Integer.MAX_VALUE;
        float brightnessThreshold = 0.5f;

        if (args != null && !args.trim().isEmpty()) {
            String[] tokens = args.trim().split("\\s+");
            if (tokens.length >= 1) {
                try {
                    maxFrames = Integer.parseInt(tokens[0]);
                    if (maxFrames <= 0) maxFrames = Integer.MAX_VALUE;
                } catch (NumberFormatException ignored) {}
            }
            if (tokens.length >= 2) {
                try {
                    brightnessThreshold = Float.parseFloat(tokens[1]);
                } catch (NumberFormatException ignored) {}
            }
        }

        brightnessThreshold = Math.max(0.0f, Math.min(1.0f, brightnessThreshold));

        Console.showMessage("=== Bad Apple Preprocessing Started ===");
        Console.showMessage("Brightness Threshold: " + brightnessThreshold);
        Console.showMessage("Max Frames requested: " + (maxFrames == Integer.MAX_VALUE ? "ALL" : maxFrames));

        // =========================================================================
        // --- 2. Frame Dimension Discovery (Fetch First Frame) ---
        // =========================================================================
        String frameFormat = "graphics/badapple/frames/output_%04d.jpg";
        int frameWidth = -1;
        int frameHeight = -1;

        try (InputStream is = Global.getSettings().openStream(String.format(frameFormat, 1))) {
            if (is != null) {
                BufferedImage firstFrame = ImageIO.read(new BufferedInputStream(is));
                if (firstFrame != null) {
                    frameWidth = firstFrame.getWidth();
                    frameHeight = firstFrame.getHeight();
                }
            }
        } catch (Exception e) {
            Console.showMessage("Warning: Could not read first frame: " + e.getMessage());
        }

        if (frameWidth <= 0 || frameHeight <= 0) {
            Console.showMessage("Error: Bad Apple frame images not accessible at graphics/badapple/frames/output_0001.jpg");
            return CommandResult.ERROR;
        }

        Console.showMessage("Frame Resolution: " + frameWidth + " x " + frameHeight);

        // =========================================================================
        // --- 3. System Discovery & Bounding Box Calculation ---
        // =========================================================================
        List<StarSystemAPI> allStarSystems = Global.getSector().getStarSystems();
        if (allStarSystems == null || allStarSystems.isEmpty()) {
            Console.showMessage("Error: No star systems found in the sector.");
            return CommandResult.ERROR;
        }

        List<StarSystemAPI> validSystems = new ArrayList<>();
        Map<StarSystemAPI, MarketAPI> systemPrimaryMarkets = new HashMap<>();

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;

        for (StarSystemAPI system : allStarSystems) {
            if (system == null) continue;

            // Skip exempt systems (e.g. Limbo)
            if (BadAppleExclusions.isSystemExempt(system)) {
                continue;
            }

            // Find markets in system
            List<MarketAPI> markets = Global.getSector().getEconomy().getMarkets(system);
            if (markets == null || markets.isEmpty()) {
                continue;
            }

            // Select primary market (largest or first)
            MarketAPI primaryMarket = null;
            int largestSize = -1;
            for (MarketAPI market : markets) {
                if (market == null || BadAppleExclusions.isMarketExempt(market)) continue;
                if (market.getSize() > largestSize) {
                    largestSize = market.getSize();
                    primaryMarket = market;
                }
            }

            if (primaryMarket == null) continue;

            Vector2f loc = system.getLocation();
            if (loc == null) continue;

            validSystems.add(system);
            systemPrimaryMarkets.put(system, primaryMarket);

            if (loc.x < minX) minX = loc.x;
            if (loc.x > maxX) maxX = loc.x;
            if (loc.y < minY) minY = loc.y;
            if (loc.y > maxY) maxY = loc.y;
        }

        if (validSystems.isEmpty()) {
            Console.showMessage("Error: No star systems with active markets found in the sector.");
            return CommandResult.ERROR;
        }

        Console.showMessage("Discovered " + validSystems.size() + " systems with markets.");
        Console.showMessage(String.format("Hyperspace Bounding Box: X:[%.1f to %.1f], Y:[%.1f to %.1f]", minX, maxX, minY, maxY));

        // =========================================================================
        // --- 4. Build System-to-Pixel Lookup Table ---
        // =========================================================================
        List<BadAppleMappedSystem> lookupTable = new ArrayList<>(validSystems.size());
        float rangeX = (maxX > minX) ? (maxX - minX) : 1.0f;
        float rangeY = (maxY > minY) ? (maxY - minY) : 1.0f;

        for (StarSystemAPI system : validSystems) {
            Vector2f loc = system.getLocation();
            MarketAPI primaryMarket = systemPrimaryMarkets.get(system);

            // Hyperspace: +X right, +Y up
            // Image pixel: +X right, +Y down (top-left origin)
            float normX = (loc.x - minX) / rangeX;
            float normY = (maxY - loc.y) / rangeY;

            int px = Math.max(0, Math.min(frameWidth - 1, Math.round(normX * (frameWidth - 1))));
            int py = Math.max(0, Math.min(frameHeight - 1, Math.round(normY * (frameHeight - 1))));

            lookupTable.add(new BadAppleMappedSystem(
                    system,
                    primaryMarket,
                    loc.x,
                    loc.y,
                    px,
                    py,
                    primaryMarket.getFactionId()
            ));
        }

        Console.showMessage("Lookup table constructed for " + lookupTable.size() + " star systems.");

        // =========================================================================
        // --- 5. Sequential Frame Processing & Delta Extraction ---
        // =========================================================================
        List<BadAppleFrameData> frameDeltas = new ArrayList<>();
        Map<String, String> lastKnownFactions = new HashMap<>();

        // Initialize last known factions from the current in-game market state
        for (BadAppleMappedSystem ms : lookupTable) {
            lastKnownFactions.put(ms.primaryMarket.getId(), ms.primaryMarket.getFactionId());
        }

        int totalChangeCount = 0;
        int activeFramesCount = 0;
        int frameCountToProcess = 0;
        int frameIndex = 0;
        int consecutiveFailures = 0;

        Console.showMessage("Sampling video frames via lookup table...");

        while (frameIndex < maxFrames && consecutiveFailures < 5) {
            int frameNum = frameIndex + 1;
            String framePath = String.format(frameFormat, frameNum);

            BufferedImage image = null;
            try (InputStream is = Global.getSettings().openStream(framePath)) {
                if (is != null) {
                    image = ImageIO.read(new BufferedInputStream(is));
                }
            } catch (Exception ignored) {}

            if (image == null) {
                consecutiveFailures++;
                frameIndex++;
                continue;
            }

            consecutiveFailures = 0;
            String frameFileName = String.format("output_%04d.jpg", frameNum);
            BadAppleFrameData frameData = new BadAppleFrameData(frameIndex, frameFileName);

            for (BadAppleMappedSystem ms : lookupTable) {
                // Direct lookup pixel sampling
                int rgb = image.getRGB(ms.pixelX, ms.pixelY);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Perceived luminance (ITU-R BT.601)
                float luminance = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f;
                String targetFaction = (luminance >= brightnessThreshold) ? FACTION_DIKTAT : FACTION_HEGEMONY;
                String previousFaction = lastKnownFactions.get(ms.primaryMarket.getId());

                if (!targetFaction.equalsIgnoreCase(previousFaction)) {
                    frameData.changes.add(new BadAppleMarketChange(
                            ms.primaryMarket.getId(),
                            ms.primaryMarket.getName(),
                            previousFaction,
                            targetFaction
                    ));
                    lastKnownFactions.put(ms.primaryMarket.getId(), targetFaction);
                    totalChangeCount++;
                }
            }

            if (!frameData.changes.isEmpty()) {
                activeFramesCount++;
            }

            frameDeltas.add(frameData);
            frameCountToProcess++;
            frameIndex++;
        }

        // =========================================================================
        // --- 6. Session Caching & Diagnostic Summary ---
        // =========================================================================
        BadAppleSession session = new BadAppleSession();
        session.frameDeltas = frameDeltas;
        session.mappedSystems = lookupTable;
        session.totalFramesProcessed = frameCountToProcess;
        session.totalChanges = totalChangeCount;
        session.minHyperspaceX = minX;
        session.maxHyperspaceX = maxX;
        session.minHyperspaceY = minY;
        session.maxHyperspaceY = maxY;
        session.frameWidth = frameWidth;
        session.frameHeight = frameHeight;
        session.algorithmName = "direct_lookup";
        session.brightnessThreshold = brightnessThreshold;
        session.preprocessDurationMs = System.currentTimeMillis() - startTimeMs;

        BadAppleSession.INSTANCE = session;

        Console.showMessage("=== Bad Apple Preprocessing Complete ===");
        Console.showMessage(String.format("Processed %d frames in %d ms (Avg %.2f ms/frame).",
                session.totalFramesProcessed, session.preprocessDurationMs,
                (float) session.preprocessDurationMs / Math.max(1, session.totalFramesProcessed)));
        Console.showMessage(String.format("Total Market Flips: %d across %d active delta frames.",
                session.totalChanges, activeFramesCount));
        Console.showMessage("Ready for playback! Run 'badapple_play [delaySeconds] [startFrame]' to start.");

        return CommandResult.SUCCESS;
    }
}
