package santomon.BadApple.procgen;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.PlanetAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.StarTypes;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedural Star System Generator for Bad Apple.
 *
 * Generates a high-density, evenly spaced field of celestial star systems during New Game creation
 * (invoked via BadAppleModPlugin.onNewGameAfterProcGen()).
 *
 * Strict Separation of Concerns:
 * - Instantiates StarSystemAPI instances and assigns hyperspace positions.
 * - Initializes varied primary stars and space backgrounds.
 * - Autogenerates hyperspace jump points.
 * - STRICTLY ZERO markets, colonies, or faction alignments are created here.
 *   (Economy setup and space station spawning are handled on-demand via the badapple_init console command).
 */
public class BadAppleSectorGenerator {

    public static final String TAG_BADAPPLE_PROCGEN = "badapple_procgen";
    public static final String SECTOR_PERSISTENT_KEY = "$badapple_procgen_generated";

    // Star type definitions paired with appropriate visual radius and corona dimensions
    private static final StarTypeConfig[] STAR_TYPES = {
            new StarTypeConfig(StarTypes.YELLOW, 500f, 350f, 30),
            new StarTypeConfig(StarTypes.ORANGE, 550f, 400f, 25),
            new StarTypeConfig(StarTypes.RED_DWARF, 300f, 200f, 20),
            new StarTypeConfig(StarTypes.WHITE_DWARF, 200f, 150f, 15),
            new StarTypeConfig(StarTypes.BLUE_GIANT, 750f, 550f, 10),
            new StarTypeConfig(StarTypes.ORANGE_GIANT, 650f, 450f, 10),
            new StarTypeConfig(StarTypes.RED_GIANT, 700f, 500f, 10),
            new StarTypeConfig(StarTypes.BROWN_DWARF, 250f, 150f, 10),
            new StarTypeConfig(StarTypes.BLUE_SUPERGIANT, 900f, 650f, 5)
    };

    /**
     * Generates a dense field of star systems across hyperspace during new game creation.
     *
     * @param sector The SectorAPI instance.
     */
    public static void generateDenseSector(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        // Prevent duplicate generation if already run in this sector
        if (sector.getPersistentData().containsKey(SECTOR_PERSISTENT_KEY)) {
            System.out.println("[BadApple] Sector procgen already generated for this save. Skipping.");
            return;
        }

        long startTime = System.currentTimeMillis();
        System.out.println("[BadApple] Generating high-density star system canvas for Bad Apple...");

        Random random = new Random(133742L);

        // =========================================================================
        // --- 1. Gather Existing Sector Star System Hyperspace Locations ---
        // =========================================================================
        List<Vector2f> existingLocations = new ArrayList<>();
        List<StarSystemAPI> existingSystems = sector.getStarSystems();
        if (existingSystems != null) {
            for (StarSystemAPI existing : existingSystems) {
                if (existing != null && existing.getLocation() != null) {
                    existingLocations.add(new Vector2f(existing.getLocation().x, existing.getLocation().y));
                }
            }
        }

        System.out.println(String.format("[BadApple] Found %d existing star systems (preserving Core Worlds).", existingLocations.size()));

        // =========================================================================
        // --- 2. 2D Poisson Disk Sampling ---
        // =========================================================================
        List<Vector2f> generatedCoords = BadApplePoissonSampler.generateCoordinates(existingLocations, random);
        System.out.println(String.format("[BadApple] Poisson sampler generated %d candidate coordinates.", generatedCoords.size()));

        // =========================================================================
        // --- 3. Pure Celestial Star System Instantiation ---
        // =========================================================================
        int systemsCreated = 0;
        int totalWeight = 0;
        for (StarTypeConfig st : STAR_TYPES) {
            totalWeight += st.weight;
        }

        for (int i = 0; i < generatedCoords.size(); i++) {
            Vector2f pos = generatedCoords.get(i);
            String systemId = String.format("badapple_sys_%04d", i + 1);
            String systemName = String.format("BA-%04d", i + 1);

            // Create StarSystemAPI instance
            StarSystemAPI system = sector.createStarSystem(systemName);
            if (system == null) {
                continue;
            }

            system.getLocation().set(pos.x, pos.y);
            system.setBaseName(systemName);
            system.setProcgen(true);
            system.addTag(TAG_BADAPPLE_PROCGEN);

            // Pick weighted star type
            StarTypeConfig starConfig = pickStarType(random, totalWeight);

            // Initialize primary star
            String starId = systemId + "_star";
            PlanetAPI star = system.initStar(
                    starId,
                    starConfig.typeId,
                    starConfig.radius,
                    starConfig.coronaRadius
            );

            // Set space background texture (background1.jpg to background6.jpg)
            int bgIndex = 1 + random.nextInt(6);
            system.setBackgroundTextureFilename("graphics/backgrounds/background" + bgIndex + ".jpg");

            // Autogenerate hyperspace jump points and gravity well anchors
            system.autogenerateHyperspaceJumpPoints(true, true);

            systemsCreated++;
        }

        sector.getPersistentData().put(SECTOR_PERSISTENT_KEY, true);

        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println(String.format("[BadApple] Successfully generated %d dense star systems in %d ms.", systemsCreated, elapsed));
        System.out.println("[BadApple] Ready for market population via 'badapple_init'.");
    }

    /**
     * Picks a star type configuration based on configured relative weights.
     */
    private static StarTypeConfig pickStarType(Random random, int totalWeight) {
        int roll = random.nextInt(totalWeight);
        int running = 0;
        for (StarTypeConfig config : STAR_TYPES) {
            running += config.weight;
            if (roll < running) {
                return config;
            }
        }
        return STAR_TYPES[0];
    }

    /**
     * Data holder for star type visual attributes.
     */
    private static class StarTypeConfig {
        final String typeId;
        final float radius;
        final float coronaRadius;
        final int weight;

        StarTypeConfig(String typeId, float radius, float coronaRadius, int weight) {
            this.typeId = typeId;
            this.radius = radius;
            this.coronaRadius = coronaRadius;
            this.weight = weight;
        }
    }
}
