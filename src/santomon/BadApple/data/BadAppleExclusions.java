package santomon.BadApple.data;

import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Hardcoded configuration and filter utility for star systems and markets
 * that should be excluded from Bad Apple preprocessing, market initialization,
 * and political map playback (e.g. distant outlier systems such as Limbo).
 */
public class BadAppleExclusions {

    /**
     * Hardcoded set of system IDs / base names to exclude.
     * All entries should be lowercase for case-insensitive matching.
     */
    public static final Set<String> EXEMPT_SYSTEMS;

    /**
     * Hardcoded set of specific market IDs to exclude.
     */
    public static final Set<String> EXEMPT_MARKETS;

    static {
        Set<String> systems = new HashSet<>();
        // Limbo is an isolated system located far outside the Core Worlds,
        // which would skew the hyperspace bounding box if included.
        systems.add("limbo");
        systems.add("limbo_system");
        systems.add("deep space");
        systems.add("deep_space");
        EXEMPT_SYSTEMS = Collections.unmodifiableSet(systems);

        Set<String> markets = new HashSet<>();
        EXEMPT_MARKETS = Collections.unmodifiableSet(markets);
    }

    /**
     * Checks whether a star system should be excluded from Bad Apple processing.
     *
     * @param system Star system to test
     * @return true if the system should be ignored, false otherwise
     */
    public static boolean isSystemExempt(StarSystemAPI system) {
        if (system == null) {
            return true;
        }

        if (system.isHyperspace()) {
            return true;
        }

        String id = system.getId() != null ? system.getId().toLowerCase().trim() : "";
        String baseName = system.getBaseName() != null ? system.getBaseName().toLowerCase().trim() : "";
        String name = system.getName() != null ? system.getName().toLowerCase().trim() : "";

        if (EXEMPT_SYSTEMS.contains(id) || EXEMPT_SYSTEMS.contains(baseName) || EXEMPT_SYSTEMS.contains(name)) {
            return true;
        }

        if (id.contains("limbo") || baseName.contains("limbo") || name.contains("limbo")) {
            return true;
        }

        return false;
    }

    /**
     * Checks whether a market should be excluded from Bad Apple processing.
     *
     * @param market Market to test
     * @return true if the market should be ignored, false otherwise
     */
    public static boolean isMarketExempt(MarketAPI market) {
        if (market == null) {
            return true;
        }

        String id = market.getId() != null ? market.getId().toLowerCase().trim() : "";
        String name = market.getName() != null ? market.getName().toLowerCase().trim() : "";

        if (EXEMPT_MARKETS.contains(id) || EXEMPT_MARKETS.contains(name)) {
            return true;
        }

        if (market.getStarSystem() != null && isSystemExempt(market.getStarSystem())) {
            return true;
        }

        return false;
    }
}
