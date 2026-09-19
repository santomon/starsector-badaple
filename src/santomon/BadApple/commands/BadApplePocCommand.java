package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import santomon.BadApple.playback.BadApplePocScript;

/**
 * Console command for the proof-of-concept test:
 * Continuously flips Chicomoztoc between Hegemony and Sindrian Diktat every second
 * (or at a configurable interval) to observe dynamic KMU political map redraws.
 *
 * Syntax:
 *   badapple_poc [optionalIntervalSeconds|stop|pause|resume|status]
 * Examples:
 *   badapple_poc             -> Flips Chicomoztoc every 1.0 second
 *   badapple_poc 0.5         -> Flips Chicomoztoc every 0.5 seconds
 *   badapple_poc stop        -> Stops the POC script
 *   badapple_poc status      -> Displays current status of the POC script
 */
public class BadApplePocCommand implements BaseCommand {

    public static final float DEFAULT_POC_INTERVAL = 1.0f; // 1 flip every second
    public static final String CHICOMOZTOAC_MARKET_ID = "chicomoztoc";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: badapple_poc can only be run within campaign mode.");
            return CommandResult.WRONG_CONTEXT;
        }

        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) {
            Console.showMessage("Error: Campaign sector or economy not available.");
            return CommandResult.ERROR;
        }

        // =========================================================================
        // --- 1. Market Verification (Chicomoztoc) ---
        // =========================================================================
        MarketAPI market = sector.getEconomy().getMarket(CHICOMOZTOAC_MARKET_ID);
        if (market == null) {
            for (MarketAPI m : sector.getEconomy().getMarketsCopy()) {
                if (m.getName() != null && m.getName().equalsIgnoreCase("Chicomoztoc")) {
                    market = m;
                    break;
                }
            }
        }

        if (market == null) {
            Console.showMessage("Error: Chicomoztoc market could not be found in the current sector.");
            return CommandResult.ERROR;
        }

        // =========================================================================
        // --- 2. Control Directives (stop, pause, resume, status) ---
        // =========================================================================
        String trimmedArgs = (args != null) ? args.trim() : "";
        String[] tokens = trimmedArgs.isEmpty() ? new String[0] : trimmedArgs.split("\\s+");

        if (tokens.length > 0) {
            String firstArg = tokens[0].toLowerCase();

            if (firstArg.equals("stop")) {
                if (BadApplePocScript.ACTIVE_INSTANCE != null) {
                    BadApplePocScript.ACTIVE_INSTANCE.stop();
                    BadApplePocScript.ACTIVE_INSTANCE = null;
                    Console.showMessage("Bad Apple POC script stopped.");
                } else {
                    Console.showMessage("No active Bad Apple POC script to stop.");
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("pause")) {
                if (BadApplePocScript.ACTIVE_INSTANCE != null) {
                    BadApplePocScript.ACTIVE_INSTANCE.pause();
                    Console.showMessage("Bad Apple POC script paused.");
                } else {
                    Console.showMessage("No active Bad Apple POC script to pause.");
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("resume")) {
                if (BadApplePocScript.ACTIVE_INSTANCE != null) {
                    BadApplePocScript.ACTIVE_INSTANCE.resume();
                    Console.showMessage("Bad Apple POC script resumed.");
                } else {
                    Console.showMessage("No active POC script to resume. Starting fresh POC at 1.0s interval...");
                    startNewPoc(market.getId(), DEFAULT_POC_INTERVAL);
                }
                return CommandResult.SUCCESS;
            }

            if (firstArg.equals("status")) {
                if (BadApplePocScript.ACTIVE_INSTANCE != null) {
                    BadApplePocScript script = BadApplePocScript.ACTIVE_INSTANCE;
                    Console.showMessage("=== Bad Apple POC Status ===");
                    Console.showMessage("Target Market: " + market.getName() + " (Current Faction: " + market.getFactionId() + ")");
                    Console.showMessage(String.format("Interval: %.2fs | Total Flips: %d | Active: %s",
                            script.getIntervalSeconds(), script.getFlipCount(), script.isPlaying()));
                } else {
                    Console.showMessage("No active Bad Apple POC script running. Current Chicomoztoc faction: " + market.getFactionId());
                }
                return CommandResult.SUCCESS;
            }
        }

        // =========================================================================
        // --- 3. Parameter Parsing & Script Launch ---
        // =========================================================================
        float intervalSeconds = DEFAULT_POC_INTERVAL;
        if (tokens.length >= 1) {
            try {
                intervalSeconds = Float.parseFloat(tokens[0]);
                if (intervalSeconds <= 0f) {
                    intervalSeconds = DEFAULT_POC_INTERVAL;
                }
            } catch (NumberFormatException ignored) {}
        }

        startNewPoc(market.getId(), intervalSeconds);
        return CommandResult.SUCCESS;
    }

    private void startNewPoc(String marketId, float intervalSeconds) {
        if (BadApplePocScript.ACTIVE_INSTANCE != null) {
            BadApplePocScript.ACTIVE_INSTANCE.stop();
            BadApplePocScript.ACTIVE_INSTANCE = null;
        }

        BadApplePocScript script = new BadApplePocScript(marketId, intervalSeconds);
        BadApplePocScript.ACTIVE_INSTANCE = script;
        Global.getSector().addTransientScript(script);

        Console.showMessage("=== Bad Apple POC Started ===");
        Console.showMessage(String.format("Target: Chicomoztoc | Interval: %.2f second(s) per flip", intervalSeconds));
        Console.showMessage("Chicomoztoc will alternate between Hegemony and Sindrian Diktat.");
        Console.showMessage("Open the campaign map view (TAB) to inspect political borders.");
    }
}
