package dev.rabauer.laya_pixel_town;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Fixed town layout in tile units (80 x 47) and the walkway network people use. */
public final class Town {
    public static final int WIDTH = 80;
    public static final int HEIGHT = 47;
    public static final double VERTICAL_LANE_X = 39.5;
    private static final double[] LANES = {14.5, 23.5, 32.5};

    public record Place(String type, double x, double y, double w, double h) {
        public double doorX() { return x + w / 2; }
        public double doorY() { return y + h; }
    }

    private final List<Place> homes = new ArrayList<>();
    private final List<Place> works = new ArrayList<>();
    private Place restaurant;
    private Place shop;

    public Town() {
        int[][] houses = {{17, 4, 6, 5}, {25, 4, 6, 5}, {46, 4, 6, 5}, {54, 4, 6, 5}, {62, 4, 6, 5}, {70, 4, 6, 5},
                {8, 16, 10, 5}, {20, 16, 8, 5}};
        for (int[] h : houses) homes.add(new Place("home", h[0], h[1], h[2], h[3]));
        works.add(new Place("work", 44, 27, 20, 4));
        works.add(new Place("work", 6, 27, 26, 4));
        restaurant = new Place("restaurant", 44, 16, 10, 5);
        shop = new Place("shop", 58, 16, 8, 5);
    }

    public List<Place> homes() { return homes; }
    public List<Place> works() { return works; }
    public Place restaurant() { return restaurant; }
    public Place shop() { return shop; }

    public List<Place> allBuildings() {
        List<Place> all = new ArrayList<>(homes);
        all.addAll(works);
        all.add(restaurant);
        all.add(shop);
        return all;
    }

    /** The lane (horizontal walkway) that serves a given y. */
    public static double laneFor(double y) {
        if (y < 19) return LANES[0];
        if (y < 28) return LANES[1];
        return LANES[2];
    }

    /** A random spot in one of the two parks (below the lower path). */
    public double[] parkSpot(Random rnd) {
        double x = rnd.nextBoolean() ? 46 + rnd.nextDouble() * 28 : 4 + rnd.nextDouble() * 30;
        return new double[]{x, 33.8};
    }

    /** A random spot on the bottom edge of the path ring around the pond (top left). */
    public double[] pondSpot(Random rnd) {
        return new double[]{RING[0][0] + rnd.nextDouble() * (RING[3][0] - RING[0][0]), RING[0][1]};
    }

    // The path ring around the pond, one tile outside it: bottom left, top left, top right, bottom right.
    private static final double[][] RING = {{2, 12}, {2, 2}, {15, 2}, {15, 12}};
    private static final double[] RING_START = {0, 10, 23, 33};
    private static final double RING_LENGTH = 46;

    /** Waypoints for a short walk along the pond ring, in a random direction, starting from a point on it. */
    public static List<double[]> pondStroll(double x, double y, Random rnd) {
        double s0 = ringParam(x, y);
        double s1 = s0 + (4 + rnd.nextDouble() * 8) * (rnd.nextBoolean() ? 1 : -1);
        double lo = Math.min(s0, s1), hi = Math.max(s0, s1);
        List<double[]> pts = new ArrayList<>();
        for (int lap = -1; lap <= 2; lap++) {
            for (int k = 0; k < 4; k++) {
                double c = RING_START[k] + lap * RING_LENGTH;
                if (c > lo && c < hi) pts.add(RING[k]);
            }
        }
        if (s1 < s0) java.util.Collections.reverse(pts);
        pts.add(ringPos(s1));
        return pts;
    }

    private static double[] ringPos(double s) {
        s = ((s % RING_LENGTH) + RING_LENGTH) % RING_LENGTH;
        for (int k = 3; k >= 0; k--) {
            if (s >= RING_START[k]) {
                double[] a = RING[k], b = RING[(k + 1) % 4];
                double len = Math.hypot(b[0] - a[0], b[1] - a[1]), t = (s - RING_START[k]) / len;
                return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
            }
        }
        return RING[0];
    }

    private static double ringParam(double x, double y) {
        double best = Double.MAX_VALUE, bestS = 0;
        for (int k = 0; k < 4; k++) {
            double[] a = RING[k], b = RING[(k + 1) % 4];
            double dx = b[0] - a[0], dy = b[1] - a[1], len2 = dx * dx + dy * dy;
            double t = Math.max(0, Math.min(1, ((x - a[0]) * dx + (y - a[1]) * dy) / len2));
            double d = Math.hypot(a[0] + dx * t - x, a[1] + dy * t - y);
            if (d < best) { best = d; bestS = RING_START[k] + t * Math.sqrt(len2); }
        }
        return bestS;
    }

    /** A short walk to another spot within `radius` tiles, kept inside the park that (x, y) is in. */
    public double[] parkStroll(double x, double y, double radius, Random rnd) {
        double lo = x < VERTICAL_LANE_X ? 4 : 46, hi = x < VERTICAL_LANE_X ? 34 : 74;
        double nx = Math.max(lo, Math.min(hi, x + (rnd.nextDouble() * 2 - 1) * radius));
        double ny = Math.max(34, Math.min(45, y + (rnd.nextDouble() * 2 - 1) * radius));
        return new double[]{nx, ny};
    }

    /** Where people who want company meet: a few fixed gathering points per park and at the pond, so they end up close together. */
    public double[] socialSpot(boolean pond, Random rnd) {
        if (pond) return new double[]{5 + rnd.nextDouble() * 6, RING[0][1]};
        double[] centers = {10, 22, 30, 52, 62, 70};
        return new double[]{centers[rnd.nextInt(centers.length)] + (rnd.nextDouble() * 2 - 1) * 1.5, 35 + rnd.nextDouble() * 3};
    }

    /** A random spot on one of the walkways. */
    public double[] wanderSpot(Random rnd) {
        double lane = LANES[rnd.nextInt(LANES.length)];
        return new double[]{2 + rnd.nextDouble() * 76, lane};
    }

    /** Waypoints from (ax,ay) to (bx,by) along the lane network. */
    public static List<double[]> route(double ax, double ay, double bx, double by) {
        List<double[]> pts = new ArrayList<>();
        double la = laneFor(ay), lb = laneFor(by);
        add(pts, ax, la);
        if (la != lb) {
            add(pts, VERTICAL_LANE_X, la);
            add(pts, VERTICAL_LANE_X, lb);
        }
        add(pts, bx, lb);
        add(pts, bx, by);
        return pts;
    }

    private static void add(List<double[]> pts, double x, double y) {
        if (!pts.isEmpty()) {
            double[] last = pts.get(pts.size() - 1);
            if (Math.abs(last[0] - x) < 1e-6 && Math.abs(last[1] - y) < 1e-6) return;
        }
        pts.add(new double[]{x, y});
    }
}
