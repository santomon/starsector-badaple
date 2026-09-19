package santomon.BadApple.procgen;

import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2D Poisson Disk Sampler (Bridson's Algorithm) for generating an evenly spaced,
 * high-density constellation of star system coordinates across hyperspace.
 *
 * Supports multi-density sampling:
 * 1. Full sector hyperspace bounds matching the 4:3 video aspect ratio.
 * 2. Outer hyperspace density with minimum distance r_outer (3200 units).
 * 3. Core Worlds region density with half-density spacing r_core (~4525 units) to fill
 *    empty gaps around vanilla core systems without causing overcrowding.
 * 4. Pre-populated collision grid respecting existing vanilla star system locations.
 */
public class BadApplePoissonSampler {

    // --- Configuration Constants ---
    public static final float DOMAIN_WIDTH = 140000f;         // Total hyperspace width (-70000 to +70000)
    public static final float DOMAIN_HEIGHT = 105000f;        // Total hyperspace height (-52500 to +52500, 4:3 aspect ratio)
    public static final float MIN_SYSTEM_SPACING = 3200f;     // Minimum distance between star systems in outer hyperspace
    public static final float CORE_RADIUS = 14000f;           // Hyperspace central radius corresponding to Core Worlds
    public static final float CORE_SYSTEM_SPACING = (float) MIN_SYSTEM_SPACING;
    public static final int CANDIDATES_PER_POINT = 30;        // Bridson sample attempts per active point

    /**
     * Determines the required local spacing at hyperspace coordinate (X, Y).
     */
    public static float getSpacingAt(float x, float y) {
        float distSq = x * x + y * y;
        if (distSq < CORE_RADIUS * CORE_RADIUS) {
            return CORE_SYSTEM_SPACING;
        }
        return MIN_SYSTEM_SPACING;
    }

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

        // Grid cell size = MIN_SYSTEM_SPACING / sqrt(2) ensures fine-grained spatial indexing
        float cellSize = (float) (MIN_SYSTEM_SPACING / Math.sqrt(2.0));
        int gridWidth = (int) Math.ceil(DOMAIN_WIDTH / cellSize);
        int gridHeight = (int) Math.ceil(DOMAIN_HEIGHT / cellSize);

        // Grid stores lists of point indices per cell to safely handle multi-point cells
        @SuppressWarnings("unchecked")
        List<Integer>[][] grid = new ArrayList[gridWidth][gridHeight];

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
                    if (grid[gx][gy] == null) {
                        grid[gx][gy] = new ArrayList<>(2);
                    }
                    grid[gx][gy].add(pointIndex);
                }
            }
        }

        // =========================================================================
        // --- 2. Seed Initial Active Points Across Hyperspace & Core ---
        // =========================================================================
        float[] seedXOffsets = {-0.75f, 0.75f, -0.75f, 0.75f, 0f, 0f, 0f, -0.2f, 0.2f};
        float[] seedYOffsets = {-0.75f, -0.75f, 0.75f, 0.75f, -0.8f, 0.8f, 0f, 0f, 0f};

        for (int i = 0; i < seedXOffsets.length; i++) {
            float seedX = seedXOffsets[i] * halfWidth + (random.nextFloat() - 0.5f) * 2000f;
            float seedY = seedYOffsets[i] * halfHeight + (random.nextFloat() - 0.5f) * 2000f;
            Vector2f seedPoint = new Vector2f(seedX, seedY);

            if (isValidCandidate(seedPoint, minX, maxX, minY, maxY, cellSize, gridWidth, gridHeight, grid, allPoints)) {
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
            if (isValidCandidate(candidate, minX, maxX, minY, maxY, cellSize, gridWidth, gridHeight, grid, allPoints)) {
                addPoint(candidate, minX, minY, cellSize, grid, allPoints, activeList, resultPoints);
            }
        }

        // =========================================================================
        // --- 3. Bridson Poisson Disk Expansion Loop ---
        // =========================================================================
        while (!activeList.isEmpty()) {
            int activeIndex = random.nextInt(activeList.size());
            Vector2f basePoint = activeList.get(activeIndex);
            float rBase = getSpacingAt(basePoint.x, basePoint.y);
            boolean foundCandidate = false;

            for (int attempt = 0; attempt < CANDIDATES_PER_POINT; attempt++) {
                // Generate random candidate in annulus [rBase, 2*rBase)
                double angle = random.nextDouble() * 2.0 * Math.PI;
                float distance = rBase + random.nextFloat() * rBase;
                float candX = (float) (basePoint.x + distance * Math.cos(angle));
                float candY = (float) (basePoint.y + distance * Math.sin(angle));
                Vector2f candidate = new Vector2f(candX, candY);

                if (isValidCandidate(candidate, minX, maxX, minY, maxY, cellSize, gridWidth, gridHeight, grid, allPoints)) {
                    addPoint(candidate, minX, minY, cellSize, grid, allPoints, activeList, resultPoints);
                    foundCandidate = true;
                    break;
                }
            }

            if (!foundCandidate) {
                int lastIdx = activeList.size() - 1;
                activeList.set(activeIndex, activeList.get(lastIdx));
                activeList.remove(lastIdx);
            }
        }

        return resultPoints;
    }

    /**
     * Checks whether a candidate coordinate is within domain bounds and respects
     * local density spacing against all neighboring points.
     */
    private static boolean isValidCandidate(Vector2f candidate,
                                            float minX, float maxX,
                                            float minY, float maxY,
                                            float cellSize,
                                            int gridWidth, int gridHeight,
                                            List<Integer>[][] grid,
                                            List<Vector2f> allPoints) {
        // Domain bounds check
        if (candidate.x < minX || candidate.x > maxX || candidate.y < minY || candidate.y > maxY) {
            return false;
        }

        float rCandidate = getSpacingAt(candidate.x, candidate.y);

        // Spatial grid coordinate
        int gx = (int) ((candidate.x - minX) / cellSize);
        int gy = (int) ((candidate.y - minY) / cellSize);

        if (gx < 0 || gx >= gridWidth || gy < 0 || gy >= gridHeight) {
            return false;
        }

        // Search radius in cells covers maximum possible interaction distance (CORE_SYSTEM_SPACING)
        int searchRadiusCells = (int) Math.ceil(CORE_SYSTEM_SPACING / cellSize) + 1;
        int startX = Math.max(0, gx - searchRadiusCells);
        int endX = Math.min(gridWidth - 1, gx + searchRadiusCells);
        int startY = Math.max(0, gy - searchRadiusCells);
        int endY = Math.min(gridHeight - 1, gy + searchRadiusCells);

        for (int x = startX; x <= endX; x++) {
            for (int y = startY; y <= endY; y++) {
                List<Integer> cellIndices = grid[x][y];
                if (cellIndices != null) {
                    for (int neighborIndex : cellIndices) {
                        Vector2f neighbor = allPoints.get(neighborIndex);
                        float rNeighbor = getSpacingAt(neighbor.x, neighbor.y);
                        float reqDist = (rCandidate + rNeighbor) / 2.0f;
                        float reqDistSq = reqDist * reqDist;

                        float dx = candidate.x - neighbor.x;
                        float dy = candidate.y - neighbor.y;
                        if (dx * dx + dy * dy < reqDistSq) {
                            return false;
                        }
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
                                 List<Integer>[][] grid,
                                 List<Vector2f> allPoints,
                                 List<Vector2f> activeList,
                                 List<Vector2f> resultPoints) {
        int gx = (int) ((point.x - minX) / cellSize);
        int gy = (int) ((point.y - minY) / cellSize);

        int pointIndex = allPoints.size();
        allPoints.add(point);
        if (grid[gx][gy] == null) {
            grid[gx][gy] = new ArrayList<>(2);
        }
        grid[gx][gy].add(pointIndex);
        activeList.add(point);
        resultPoints.add(point);
    }
}
