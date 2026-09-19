package santomon.BadApple.commands;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CustomCampaignEntityAPI;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Conditions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.maplayers.base.refresh.MapLayerCommonRefreshSignal;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.politicalmap.base.refresh.MarketPoliticsRefresh;
import org.lazywizard.console.BaseCommand;
import org.lazywizard.console.Console;
import santomon.BadApple.data.BadAppleExclusions;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Console command to initialize all star systems in the sector for Bad Apple playback:
 * 1. Spawns physical space station entities and size-3 colony markets in uninhabited star systems.
 * 2. Assigns all colonies/markets across the sector to Hegemony (or a chosen target faction).
 * 3. Excludes outlier systems (e.g. Limbo) so hyperspace coordinate bounds remain clean.
 * 4. Refreshes KMU political map layers.
 *
 * Syntax:
 *   badapple_init [optionalFactionId]
 * Examples:
 *   badapple_init             -> Populates and sets all systems to 'hegemony'
 *   badapple_init hegemony    -> Populates and sets all systems to 'hegemony'
 *   badapple_init sindrian_diktat -> Populates and sets all systems to 'sindrian_diktat'
 */
public class BadAppleInitCommand implements BaseCommand {

    public static final String DEFAULT_FACTION_ID = Factions.HEGEMONY; // "hegemony"
    public static final String STATION_ENTITY_TYPE = "station_lowtech1";

    @Override
    public CommandResult runCommand(String args, CommandContext context) {
        if (!context.isInCampaign()) {
            Console.showMessage("Error: badapple_init can only be run within campaign mode.");
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
        String targetFactionId = DEFAULT_FACTION_ID;
        String trimmedArgs = (args != null) ? args.trim() : "";
        if (!trimmedArgs.isEmpty()) {
            String requestedFaction = trimmedArgs.split("\\s+")[0];
            if (sector.getFaction(requestedFaction) != null) {
                targetFactionId = requestedFaction;
            } else {
                Console.showMessage("Warning: Faction ID '" + requestedFaction + "' not found in sector. Defaulting to '" + DEFAULT_FACTION_ID + "'.");
            }
        }

        Console.showMessage("=== Initializing Sector for Bad Apple (Target Faction: " + targetFactionId + ") ===");

        // =========================================================================
        // --- 2. System Traversal, Station/Market Spawning & Faction Assignment ---
        // =========================================================================
        List<StarSystemAPI> allStarSystems = sector.getStarSystems();
        if (allStarSystems == null || allStarSystems.isEmpty()) {
            Console.showMessage("Error: No star systems found in the sector.");
            return CommandResult.ERROR;
        }

        int totalSystemsVisited = 0;
        int exemptSystemsSkipped = 0;
        int existingMarketsUpdated = 0;
        int newStationsSpawned = 0;
        Set<String> processedSystemNames = new HashSet<>();

        for (StarSystemAPI system : allStarSystems) {
            if (system == null) continue;
            totalSystemsVisited++;

            // Skip exempt systems (e.g. Limbo)
            if (BadAppleExclusions.isSystemExempt(system)) {
                exemptSystemsSkipped++;
                continue;
            }

            List<MarketAPI> existingMarkets = sector.getEconomy().getMarkets(system);

            if (existingMarkets != null && !existingMarkets.isEmpty()) {
                // System already has active markets: ensure all non-exempt markets belong to target faction
                for (MarketAPI market : existingMarkets) {
                    if (market == null || BadAppleExclusions.isMarketExempt(market)) {
                        continue;
                    }

                    market.setFactionId(targetFactionId);

                    SectorEntityToken primaryEntity = market.getPrimaryEntity();
                    if (primaryEntity != null) {
                        primaryEntity.setFaction(targetFactionId);
                    }

                    if (market.getConnectedEntities() != null) {
                        for (SectorEntityToken connected : market.getConnectedEntities()) {
                            if (connected != null) {
                                connected.setFaction(targetFactionId);
                            }
                        }
                    }

                    try {
                        MarketPoliticsRefresh.reportMarketChange(
                                sector, market, "faction_change", "badapple_init"
                        );
                    } catch (Throwable ignored) {}

                    existingMarketsUpdated++;
                }
            } else {
                // System is uninhabited: spawn a dedicated space station and colony market
                SectorEntityToken station = spawnStationEntity(system, targetFactionId);

                String systemBaseName = (system.getBaseName() != null && !system.getBaseName().trim().isEmpty())
                        ? system.getBaseName()
                        : system.getName();
                String marketId = "badapple_mkt_" + system.getId();

                MarketAPI newMarket = Global.getFactory().createMarket(marketId, systemBaseName + " Station", 3);
                newMarket.setFactionId(targetFactionId);
                newMarket.setPrimaryEntity(station);
                newMarket.getStability().modifyFlat("base", 10.0f);
                newMarket.setSurveyLevel(MarketAPI.SurveyLevel.FULL);
                newMarket.setPlanetConditionMarketOnly(false);
                newMarket.setHidden(false);
                newMarket.addCondition(Conditions.POPULATION_3);
                newMarket.addCondition(Conditions.SPACEPORT);

                station.setMarket(newMarket);
                station.setFaction(targetFactionId);

                sector.getEconomy().addMarket(newMarket, false);

                try {
                    MarketPoliticsRefresh.reportMarketChange(
                            sector, newMarket, "market_spawned", "badapple_init"
                    );
                } catch (Throwable ignored) {}

                newStationsSpawned++;
            }

            processedSystemNames.add(system.getBaseName() != null ? system.getBaseName() : system.getName());
        }

        // =========================================================================
        // --- 3. Global Economy Faction Alignment Sweep ---
        // =========================================================================
        // Ensure any remaining non-exempt standalone markets across the sector are aligned
        for (MarketAPI market : sector.getEconomy().getMarketsCopy()) {
            if (market == null || BadAppleExclusions.isMarketExempt(market)) {
                continue;
            }

            if (!targetFactionId.equals(market.getFactionId())) {
                market.setFactionId(targetFactionId);
                if (market.getPrimaryEntity() != null) {
                    market.getPrimaryEntity().setFaction(targetFactionId);
                }
                if (market.getConnectedEntities() != null) {
                    for (SectorEntityToken entity : market.getConnectedEntities()) {
                        if (entity != null) entity.setFaction(targetFactionId);
                    }
                }
                try {
                    MarketPoliticsRefresh.reportMarketChange(
                            sector, market, "faction_change", "badapple_init"
                    );
                } catch (Throwable ignored) {}
            }
        }

        // =========================================================================
        // --- 4. KMU Political Map Layer Cache Invalidation ---
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
        // --- 5. Summary & Diagnostics ---
        // =========================================================================
        Console.showMessage(String.format("Initialization complete: %d systems initialized across the sector.", processedSystemNames.size()));
        Console.showMessage(String.format("  - Existing markets updated: %d", existingMarketsUpdated));
        Console.showMessage(String.format("  - Space stations spawned:   %d", newStationsSpawned));
        Console.showMessage(String.format("  - Outlier systems exempt:   %d", exemptSystemsSkipped));
        Console.showMessage("  - Target faction:           " + targetFactionId);
        Console.showMessage("KMU political spheres refreshed. Open campaign map (TAB) to inspect.");

        return CommandResult.SUCCESS;
    }

    /**
     * Creates and orbits a dedicated space station custom entity in the star system to host the new colony market.
     */
    private SectorEntityToken spawnStationEntity(StarSystemAPI system, String targetFactionId) {
        String systemBaseName = (system.getBaseName() != null && !system.getBaseName().trim().isEmpty())
                ? system.getBaseName()
                : system.getName();
        String stationId = "badapple_station_" + system.getId();
        String stationName = systemBaseName + " Station";

        // If a station entity with this ID was already spawned previously, reuse it
        SectorEntityToken existing = system.getEntityById(stationId);
        if (existing != null) {
            existing.setFaction(targetFactionId);
            return existing;
        }

        // Spawn a space station custom entity
        CustomCampaignEntityAPI station = system.addCustomEntity(
                stationId,
                stationName,
                STATION_ENTITY_TYPE,
                targetFactionId
        );

        // Find best celestial body to orbit
        PlanetAPI orbitPlanet = null;
        if (system.getPlanets() != null) {
            for (PlanetAPI planet : system.getPlanets()) {
                if (planet != null && !planet.isStar()) {
                    orbitPlanet = planet;
                    break;
                }
            }
        }

        if (orbitPlanet != null) {
            station.setCircularOrbitPointingDown(orbitPlanet, 45f, orbitPlanet.getRadius() + 150f, 30f);
        } else if (system.getStar() != null) {
            station.setCircularOrbitWithSpin(system.getStar(), 45f, system.getStar().getRadius() + 1200f, 150f, 5f, 10f);
        } else if (system.getCenter() != null) {
            station.setCircularOrbitWithSpin(system.getCenter(), 45f, 1000f, 150f, 5f, 10f);
        } else {
            station.setFixedLocation(0f, 0f);
        }

        return station;
    }
}
