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

    /** A random spot on the grass just below the pond (top left). */
    public double[] pondSpot(Random rnd) {
        return new double[]{3.5 + rnd.nextDouble() * 10, 11.6};
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
