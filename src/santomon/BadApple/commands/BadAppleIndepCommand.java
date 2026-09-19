package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Console command to bulk-assign all existing colonies and markets across the sector
 * to the independent faction (or an optionally specified faction).
 *
 * Syntax:
 *   badapple_indep [optionalFactionId]
 * Examples:
 *   badapple_indep             -> Sets all markets to 'independent'
 *   badapple_indep neutral     -> Sets all markets to 'neutral'
 *   badapple_indep hegemony    -> Sets all markets to 'hegemony'
 */
public class BadAppleIndepCommand implements BaseCommand {

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: badapple_indep can only be run within campaign mode.");
            return CommandResult.WRONG_CONTEXT;
        }

        SectorAPI sector = Global.getSector();
        if (sector == null || sector.getEconomy() == null) {
            Console.showMessage("Error: Campaign sector or economy not available.");
            return CommandResult.ERROR;
        }

        // =========================================================================
        // --- 1. Target Faction Resolution ---
        // =========================================================================
        String targetFactionId = Factions.INDEPENDENT; // "independent"
        String trimmedArgs = (args != null) ? args.trim() : "";
        if (!trimmedArgs.isEmpty()) {
            String requestedFaction = trimmedArgs.split("\\s+")[0];
            if (sector.getFaction(requestedFaction) != null) {
                targetFactionId = requestedFaction;
            } else {
                Console.showMessage("Warning: Faction ID '" + requestedFaction + "' not found in sector. Defaulting to 'independent'.");
            }
        }

        Console.showMessage("=== Bulk Setting All Markets to '" + targetFactionId + "' ===");

        // =========================================================================
        // --- 2. Sector Market Iteration & Faction Assignment ---
        // =========================================================================
        List<MarketAPI> allMarkets = sector.getEconomy().getMarketsCopy();
        int updatedMarketsCount = 0;
        Set<String> affectedSystemNames = new HashSet<>();

        for (MarketAPI market : allMarkets) {
            if (market == null) continue;

            String oldFactionId = market.getFactionId();

            // Apply faction to market data model
            market.setFactionId(targetFactionId);

            // Apply faction to primary physical entity (planet/station)
            SectorEntityToken primaryEntity = market.getPrimaryEntity();
            if (primaryEntity != null) {
                primaryEntity.setFaction(targetFactionId);
            }

            // Apply faction to all connected orbital structures and sub-entities
            if (market.getConnectedEntities() != null) {
                for (SectorEntityToken connected : market.getConnectedEntities()) {
                    if (connected != null) {
                        connected.setFaction(targetFactionId);
                    }
                }
            }

            // Record affected star system
            if (market.getStarSystem() != null) {
                affectedSystemNames.add(market.getStarSystem().getNameWithLowercaseType());
            }

            // Notify KMU for each updated market
            try {
                MarketPoliticsRefresh.reportMarketChange(
                        sector, market, "faction_change", "badapple_indep"
                );
            } catch (Throwable ignored) {}

            updatedMarketsCount++;
        }

        // =========================================================================
        // --- 3. KMU Political Map Layer Cache Invalidation ---
        // =========================================================================
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
        // --- 4. Console Diagnostics & Summary ---
        // =========================================================================
        Console.showMessage(String.format("Successfully updated %d markets across %d star systems to '%s'.",
                updatedMarketsCount, affectedSystemNames.size(), targetFactionId));
        Console.showMessage("KMU political spheres refreshed. Open campaign map (TAB) to inspect.");

        return CommandResult.SUCCESS;
    }
}
