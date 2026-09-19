package santomon.BadApple.procgen;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2D Poisson Disk Sampler (Bridson's Algorithm) for generating an evenly spaced,
 * high-density constellation of star system coordinates across hyperspace.
 *
 * Enforces:
 * 1. Configured domain bounds (width x height) maintaining 4:3 Bad Apple aspect ratio.
 * 2. Guaranteed minimum distance between any two system coordinates (r_min).
 * 3. Central Core Worlds exclusion radius to preserve vanilla systems.
 * 4. Pre-populated collision grid respecting existing vanilla star system locations.
 */
public class BadApplePoissonSampler {

    // --- Configuration Constants ---
    public static final float DOMAIN_WIDTH = 48000f;          // Total hyperspace width (-24000 to +24000)
    public static final float DOMAIN_HEIGHT = 36000f;         // Total hyperspace height (-18000 to +18000, 4:3 aspect ratio)
    public static final float MIN_SYSTEM_SPACING = 1800f;     // Minimum distance between any two star systems
    public static final float CORE_EXCLUSION_RADIUS = 13000f; // Hyperspace central radius reserved for Core Worlds
    public static final int CANDIDATES_PER_POINT = 30;        // Bridson sample attempts per active point

    /**
     * Generates a list of candidate star system coordinates (X, Y) in hyperspace.
     *
     * @param existingLocations List of existing star system locations to avoid overlapping.
     * @param random            Random number generator instance.
     * @return List of generated coordinates in hyperspace.
     */
    public static List<Vector2f> generateCoordinates(List<Vector2f> existingLocations, Random random) {
        if (random == null) {
            random = new Random();
        }

        float halfWidth = DOMAIN_WIDTH / 2f;
        float halfHeight = DOMAIN_HEIGHT / 2f;
        float minX = -halfWidth;
        float maxX = halfWidth;
        float minY = -halfHeight;
        float maxY = halfHeight;

        float r = MIN_SYSTEM_SPACING;
        float rSq = r * r;
        float coreRadiusSq = CORE_EXCLUSION_RADIUS * CORE_EXCLUSION_RADIUS;

        // Grid cell size = r / sqrt(2) guarantees at most one point per cell
        float cellSize = (float) (r / Math.sqrt(2));
        int gridWidth = (int) Math.ceil(DOMAIN_WIDTH / cellSize);
        int gridHeight = (int) Math.ceil(DOMAIN_HEIGHT / cellSize);

        // Grid stores indices into the allPoints list (-1 = empty)
        int[][] grid = new int[gridWidth][gridHeight];
        for (int x = 0; x < gridWidth; x++) {
            for (int y = 0; y < gridHeight; y++) {
                grid[x][y] = -1;
            }
        }

        List<Vector2f> allPoints = new ArrayList<>();
        List<Vector2f> activeList = new ArrayList<>();
        List<Vector2f> resultPoints = new ArrayList<>();

        // =========================================================================
        // --- 1. Populate Grid with Existing Sector Star Systems ---
        // =========================================================================
        if (existingLocations != null) {
            for (Vector2f existing : existingLocations) {
                if (existing == null) continue;
                int gx = (int) ((existing.x - minX) / cellSize);
                int gy = (int) ((existing.y - minY) / cellSize);
                if (gx >= 0 && gx < gridWidth && gy >= 0 && gy < gridHeight) {
                    int pointIndex = allPoints.size();
                    allPoints.add(new Vector2f(existing.x, existing.y));
                    grid[gx][gy] = pointIndex;
                }
            }
        }

        // =========================================================================
        // --- 2. Seed Initial Active Points in Outer Quadrants ---
        // =========================================================================
        // Seed starting points in the four quadrants outside the core exclusion zone
        float[] seedXOffsets = {-0.75f, 0.75f, -0.75f, 0.75f, 0f, 0f};
        float[] seedYOffsets = {-0.75f, -0.75f, 0.75f, 0.75f, -0.8f, 0.8f};

        for (int i = 0; i < seedXOffsets.length; i++) {
            float seedX = seedXOffsets[i] * halfWidth + (random.nextFloat() - 0.5f) * 2000f;
            float seedY = seedYOffsets[i] * halfHeight + (random.nextFloat() - 0.5f) * 2000f;
            Vector2f seedPoint = new Vector2f(seedX, seedY);

            if (isValidCandidate(seedPoint, minX, maxX, minY, maxY, coreRadiusSq, rSq, cellSize, gridWidth, gridHeight, grid, allPoints)) {
                addPoint(seedPoint, minX, minY, cellSize, grid, allPoints, activeList, resultPoints);
            }
        }

        // If no seeds placed yet, attempt random seeds across the canvas
        int seedAttempts = 0;
        while (activeList.isEmpty() && seedAttempts < 200) {
            seedAttempts++;
            float rx = minX + random.nextFloat() * DOMAIN_WIDTH;
            float ry = minY + random.nextFloat() * DOMAIN_HEIGHT;
            Vector2f candidate = new Vector2f(rx, ry);
            if (isValidCandidate(candidate, minX, maxX, minY, maxY, coreRadiusSq, rSq, cellSize, gridWidth, gridHeight, grid, allPoints)) {
                addPoint(candidate, minX, minY, cellSize, grid, allPoints, activeList, resultPoints);
            }
        }

        // =========================================================================
        // --- 3. Bridson Poisson Disk Expansion Loop ---
        // =========================================================================
        while (!activeList.isEmpty()) {
            int activeIndex = random.nextInt(activeList.size());
            Vector2f basePoint = activeList.get(activeIndex);
            boolean foundCandidate = false;

            for (int attempt = 0; attempt < CANDIDATES_PER_POINT; attempt++) {
                // Generate random point in spherical annulus between r and 2r
                double angle = random.nextDouble() * 2.0 * Math.PI;
                float distance = r + random.nextFloat() * r; // [r, 2r)
                float candX = (float) (basePoint.x + distance * Math.cos(angle));
                float candY = (float) (basePoint.y + distance * Math.sin(angle));
                Vector2f candidate = new Vector2f(candX, candY);

                if (isValidCandidate(candidate, minX, maxX, minY, maxY, coreRadiusSq, rSq, cellSize, gridWidth, gridHeight, grid, allPoints)) {
                    addPoint(candidate, minX, minY, cellSize, grid, allPoints, activeList, resultPoints);
                    foundCandidate = true;
                    break;
                }
            }

            if (!foundCandidate) {
                // Remove base point from active list when all candidates fail
                int lastIdx = activeList.size() - 1;
                activeList.set(activeIndex, activeList.get(lastIdx));
                activeList.remove(lastIdx);
            }
        }

        return resultPoints;
    }

    /**
     * Checks whether a candidate coordinate is within domain bounds, outside the core exclusion zone,
     * and at least minDistance away from all neighbor points in the spatial grid.
     */
    private static boolean isValidCandidate(Vector2f candidate,
                                            float minX, float maxX,
                                            float minY, float maxY,
                                            float coreRadiusSq,
                                            float rSq,
                                            float cellSize,
                                            int gridWidth, int gridHeight,
                                            int[][] grid,
                                            List<Vector2f> allPoints) {
        // Bounds check
        if (candidate.x < minX || candidate.x > maxX || candidate.y < minY || candidate.y > maxY) {
            return false;
        }

        // Core Worlds exclusion zone check
        float distFromCenterSq = candidate.x * candidate.x + candidate.y * candidate.y;
        if (distFromCenterSq < coreRadiusSq) {
            return false;
        }

        // Spatial grid coordinate
        int gx = (int) ((candidate.x - minX) / cellSize);
        int gy = (int) ((candidate.y - minY) / cellSize);

        if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) {
            return false;
        }

        // Check 5x5 neighboring grid cells
        int startX = Math.max(0, gx - 2);
        int endX = Math.min(gridWidth - 1, gx + 2);
        int startY = Math.max(0, gy - 2);
        int endY = Math.min(gridHeight - 1, gy + 2);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                int neighborIndex = grid[x][y];
                if (neighborIndex != -1) {
                    Vector2f neighbor = allPoints.get(neighborIndex);
                    float dx = candidate.x - neighbor.x;
                    float dy = candidate.y - neighbor.y;
                    if (dx * dx + dy * dy < rSq) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * Registers a valid point into the spatial grid, point list, active list, and result collection.
     */
    private static void addPoint(Vector2f point,
                                 float minX, float minY,
                                 float cellSize,
                                 int[][] grid,
                                 List<Vector2f> allPoints,
                                 List<Vector2f> activeList,
                                 List<Vector2f> resultPoints) {
        int gx = (int) ((point.x - minX) / cellSize);
        int gy = (int) ((point.y - minY) / cellSize);

        int pointIndex = allPoints.size();
        allPoints.add(point);
        grid[gx][gy] = pointIndex;
        activeList.add(point);
        resultPoints.add(point);
    }
}
