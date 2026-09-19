package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import org.lwjgl.util.vector.Vector2f;
import santomon.BadApple.data.BadAppleFrameData;
import santomon.BadApple.data.BadAppleMappedSystem;
import santomon.BadApple.data.BadAppleMarketChange;
import santomon.BadApple.data.BadAppleSession;
import santomon.BadApple.sampling.PixelSamplingAlgorithm;
import santomon.BadApple.sampling.SamplingAlgorithms;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.*;

/**
 * Console command to preprocess Bad Apple video frames and sector star systems into market delta changes.
 *
 * Syntax:
 *   badapple_prep [maxFrames] [algorithm] [brightnessThreshold]
 * Examples:
 *   badapple_prep
 *   badapple_prep 200 area 0.5
 *   badapple_prep 6572 adaptive 0.45
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
        String algorithmName = "point";
        float brightnessThreshold = 0.5f;

        if (args != null && !args.trim().isEmpty()) {
            String[] tokens = args.trim().split("\\s+");
            if (tokens.length >= 1) {
                try {
                    maxFrames = Integer.parseInt(tokens[0]);
                    if (maxFrames <= 0) maxFrames = Integer.MAX_VALUE;
                } catch (NumberFormatException e) {
                    // Token might be algorithm name if first argument was omitted as a number
                    if (SamplingAlgorithms.getAvailableIds().contains(tokens[0].toLowerCase())) {
                        algorithmName = tokens[0].toLowerCase();
                    }
                }
            }
            if (tokens.length >= 2) {
                if (SamplingAlgorithms.getAvailableIds().contains(tokens[1].toLowerCase())) {
                    algorithmName = tokens[1].toLowerCase();
                } else {
                    try {
                        brightnessThreshold = Float.parseFloat(tokens[1]);
                    } catch (NumberFormatException ignored) {}
                }
            }
            if (tokens.length >= 3) {
                try {
                    brightnessThreshold = Float.parseFloat(tokens[2]);
                } catch (NumberFormatException ignored) {}
            }
        }

        brightnessThreshold = Math.max(0.0f, Math.min(1.0f, brightnessThreshold));
        PixelSamplingAlgorithm samplingAlgorithm = SamplingAlgorithms.get(algorithmName);

        Console.showMessage("=== Bad Apple Preprocessing Started ===");
        Console.showMessage("Algorithm: " + samplingAlgorithm.getId() + " (" + samplingAlgorithm.getDescription() + ")");
        Console.showMessage("Brightness Threshold: " + brightnessThreshold);
        Console.showMessage("Max Frames requested: " + (maxFrames == Integer.MAX_VALUE ? "ALL" : maxFrames));

        // =========================================================================
        // --- 2. System Discovery & Bounding Box Calculation ---
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

            // Find markets in system
            List<MarketAPI> markets = Global.getSector().getEconomy().getMarkets(system);
            if (markets == null || markets.isEmpty()) {
                continue;
            }

            // Select primary market (largest or first)
            MarketAPI primaryMarket = null;
            int largestSize = -1;
            for (MarketAPI market : markets) {
                if (market == null) continue;
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
        // --- 3. Frame Image Loading & Resolution Discovery ---
        // =========================================================================
        String frameFormat = "graphics/badapple/frames/output_%04d.jpg";
        int frameCountToProcess = 0;
        int frameWidth = -1;
        int frameHeight = -1;

        // Verify first frame and determine frame resolution
        try (InputStream is = Global.getSettings().openStream(String.format(frameFormat, 1))) {
            if (is != null) {
                BufferedImage firstFrame = ImageIO.read(new BufferedInputStream(is));
                if (firstFrame != null) {
                    frameWidth = firstFrame.getWidth();
                    frameHeight = firstFrame.getHeight();
                }
            }
        } catch (Exception e) {
            Console.showMessage("Warning: Could not read first frame via primary path: " + e.getMessage());
        }

        // Fallback check if frame format uses unpadded or alternative extension
        if (frameWidth <= 0 || frameHeight <= 0) {
            Console.showMessage("Error: Bad Apple frame images not accessible at graphics/badapple/frames/output_0001.jpg");
            return CommandResult.ERROR;
        }

        Console.showMessage("Frame resolution: " + frameWidth + " x " + frameHeight);

        // =========================================================================
        // --- 4. Coordinate Normalization (Hyperspace -> Pixel Space) ---
        // =========================================================================
        List<BadAppleMappedSystem> mappedSystems = new ArrayList<>();
        float rangeX = (maxX > minX) ? (maxX - minX) : 1.0f;
        float rangeY = (maxY > minY) ? (maxY - minY) : 1.0f;

        for (StarSystemAPI system : validSystems) {
            Vector2f loc = system.getLocation();
            MarketAPI primaryMarket = systemPrimaryMarkets.get(system);

            // Hyperspace coordinate: +X is right, +Y is up
            // Image pixel coordinate: +X is right, +Y is down
            float normX = (loc.x - minX) / rangeX;
            float normY = (maxY - loc.y) / rangeY; // Invert Y for image top-down indexing

            int px = Math.max(0, Math.min(frameWidth - 1, Math.round(normX * (frameWidth - 1))));
            int py = Math.max(0, Math.min(frameHeight - 1, Math.round(normY * (frameHeight - 1))));

            mappedSystems.add(new BadAppleMappedSystem(
                    system,
                    primaryMarket,
                    loc.x,
                    loc.y,
                    px,
                    py,
                    primaryMarket.getFactionId()
            ));
        }

        // =========================================================================
        // --- 5. Frame Sampling & Delta Extraction ---
        // =========================================================================
        List<BadAppleFrameData> frameDeltas = new ArrayList<>();
        Map<String, String> lastKnownFactions = new HashMap<>();

        // Initialize last known factions with the initial state
        for (BadAppleMappedSystem ms : mappedSystems) {
            lastKnownFactions.put(ms.primaryMarket.getId(), ms.primaryMarket.getFactionId());
        }

        int totalChangeCount = 0;
        int frameIndex = 0;
        int consecutiveFailures = 0;

        Console.showMessage("Sampling frames sequentially...");

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

            for (BadAppleMappedSystem ms : mappedSystems) {
                String targetFaction = samplingAlgorithm.determineFaction(image, ms, mappedSystems, brightnessThreshold);
                String previousFaction = lastKnownFactions.get(ms.primaryMarket.getId());

                if (frameIndex == 0 || !targetFaction.equals(previousFaction)) {
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

            frameDeltas.add(frameData);
            frameCountToProcess++;
            frameIndex++;
        }

        // =========================================================================
        // --- 6. Session Caching & Diagnostic Summary ---
        // =========================================================================
        BadAppleSession session = new BadAppleSession();
        session.frameDeltas = frameDeltas;
        session.mappedSystems = mappedSystems;
        session.totalFramesProcessed = frameCountToProcess;
        session.totalChanges = totalChangeCount;
        session.minHyperspaceX = minX;
        session.maxHyperspaceX = maxX;
        session.minHyperspaceY = minY;
        session.maxHyperspaceY = maxY;
        session.frameWidth = frameWidth;
        session.frameHeight = frameHeight;
        session.algorithmName = samplingAlgorithm.getId();
        session.brightnessThreshold = brightnessThreshold;
        session.preprocessDurationMs = System.currentTimeMillis() - startTimeMs;

        BadAppleSession.INSTANCE = session;

        Console.showMessage("=== Bad Apple Preprocessing Complete ===");
        Console.showMessage(String.format("Successfully preprocessed %d frames with %d total market updates (%.2f changes/frame).",
                session.totalFramesProcessed, session.totalChanges, (float) session.totalChanges / Math.max(1, session.totalFramesProcessed)));
        Console.showMessage("Elapsed Time: " + session.preprocessDurationMs + " ms.");
        Console.showMessage("Ready for playback! Run 'badapple_play' to start.");

        return CommandResult.SUCCESS;
    }
}
