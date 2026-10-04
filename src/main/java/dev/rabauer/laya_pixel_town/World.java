package dev.rabauer.laya_pixel_town;

import dev.rabauer.laya_pixel_town.Person.Activity;
import dev.rabauer.laya_pixel_town.Person.Decision;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The simulation. One thread advances time and movement; another keeps asking Laya for the decisions that
 * are due, in batches, as fast as Laya answers. People keep doing what they were doing until an answer arrives.
 */
public class World {
    static final double SIM_MIN_PER_SECOND = 6.0;   // at 1x one simulated day lasts four real minutes
    static final double WALK_TILES_PER_MIN = 0.5;
    static final double TICK_SECONDS = 0.1;
    private static final String[] NAMES = {"Maya", "Tom", "Ann", "Bo", "Cy", "Dee", "Eli", "Fay", "Gus", "Hana", "Ivo", "Jo",
            "Kai", "Lena", "Max", "Nia", "Omar", "Pia", "Quinn", "Rosa", "Sam", "Tess", "Uma", "Vic", "Wes", "Xena", "Yan",
            "Zoe", "Abe", "Bea", "Carl", "Dora", "Ed", "Flo", "Gil", "Hugo", "Ida", "Jin", "Kit", "Lou", "Mia", "Ned",
            "Ola", "Pat", "Rex", "Sue", "Ty", "Una", "Val", "Walt", "Yara", "Zed", "Ben", "Cleo", "Dan", "Eve", "Finn",
            "Gia", "Hal", "Isa"};

    public final Town town = new Town();
    public final List<Person> people = new ArrayList<>();
    private final Random rnd;
    private final LayaClient laya;
    private final double decisionInterval;
    private final Object lock = new Object();

    private double simMinute = 7 * 60;
    private volatile boolean paused;
    private volatile int speed = 1;
    private volatile boolean running = true;
    private final AtomicInteger stepPermits = new AtomicInteger();

    // metrics
    private final Deque<long[]> completions = new ArrayDeque<>();   // {epochMs, count}
    private final long startedAt = System.currentTimeMillis();
    private volatile long lastBatchMs = -1;
    private volatile int lastBatchSize;
    private volatile String message = "";

    public World(int population, double decisionInterval, long seed, LayaClient laya) {
        this.rnd = new Random(seed);
        this.laya = laya;
        this.decisionInterval = decisionInterval;
        for (int i = 0; i < population; i++) {
            Town.Place home = town.homes().get(i % town.homes().size());
            Town.Place work = town.works().get(rnd.nextInt(town.works().size()));
            String name = i < NAMES.length ? NAMES[i] : NAMES[i % NAMES.length] + " " + (char) ('A' + i / NAMES.length);
            people.add(new Person(i, name, home, work, rnd));
        }
        Thread sim = new Thread(this::simLoop, "pixeltown-sim");
        Thread dec = new Thread(this::decisionLoop, "pixeltown-decisions");
        sim.setDaemon(true);
        dec.setDaemon(true);
        sim.start();
        dec.start();
    }

    // ---- controls ----
    public void pause() { paused = true; }
    public void resume() { paused = false; }
    public boolean paused() { return paused; }
    public void step() {
        if (!paused) return;
        stepPermits.incrementAndGet();
        synchronized (lock) { advance(TICK_SECONDS * 10); }
    }
    public void speed(int s) { speed = (s == 2 || s == 5) ? s : 1; }
    public int speed() { return speed; }
    public void stop() { running = false; }

    // ---- simulation ----
    private void simLoop() {
        while (running) {
            long t0 = System.nanoTime();
            if (!paused) {
                synchronized (lock) { advance(TICK_SECONDS); }
            }
            long sleep = (long) (TICK_SECONDS * 1000) - (System.nanoTime() - t0) / 1_000_000;
            if (sleep > 0) sleepQuietly(sleep);
        }
    }

    private void advance(double realSeconds) {
        double dt = realSeconds * SIM_MIN_PER_SECOND * speed;
        simMinute += dt;
        for (Person p : people) update(p, dt);
    }

    private void update(Person p, double dt) {
        p.hunger += 0.12 * dt;
        p.energy -= 0.07 * dt;
        p.social -= 0.08 * dt;
        p.fun -= 0.08 * dt;
        p.money -= 0.02 * dt;

        if (!p.route.isEmpty()) {
            double budget = WALK_TILES_PER_MIN * dt;
            while (budget > 0 && !p.route.isEmpty()) {
                double[] wp = p.route.peekFirst();
                double dx = wp[0] - p.x, dy = wp[1] - p.y;
                double dist = Math.hypot(dx, dy);
                if (dist <= budget) {
                    p.x = wp[0];
                    p.y = wp[1];
                    budget -= dist;
                    p.route.pollFirst();
                } else {
                    p.x += dx / dist * budget;
                    p.y += dy / dist * budget;
                    budget = 0;
                }
            }
            if (p.route.isEmpty()) {
                p.arrived = true;
                p.inside = p.destInside;
            }
        } else if (p.arrived && p.area != null && simMinute >= p.nextStrollAt) {
            // People who are out relaxing or chatting walk around the pond or the park instead of standing still.
            boolean chat = p.activity == Activity.SOCIALIZE;   // chatters only shuffle around their meeting point
            if (p.area.equals("pond")) {
                if (chat) p.route.add(new double[]{Math.max(2, Math.min(15, p.x + (rnd.nextDouble() * 2 - 1) * 1.5)), p.y});
                else p.route.addAll(Town.pondStroll(p.x, p.y, rnd));
            } else {
                p.route.add(town.parkStroll(p.x, p.y, chat ? 1.5 : 8, rnd));
            }
            p.nextStrollAt = simMinute + 8 + rnd.nextDouble() * 20;
        }
        if (p.arrived) {
            switch (p.activity) {
                case SLEEP -> { p.energy += 0.3 * dt; p.hunger -= 0.06 * dt; }
                case EAT -> { p.hunger -= 1.5 * dt; p.money -= 0.1 * dt; }
                case WORK -> { p.money += 0.25 * dt; p.energy -= 0.05 * dt; p.fun -= 0.05 * dt; }
                case SOCIALIZE -> { p.social += 0.8 * dt; p.fun += 0.1 * dt; }
                case RELAX -> { p.fun += 0.7 * dt; p.energy += 0.02 * dt; }
                case SHOP -> { p.money -= 0.4 * dt; p.fun += 0.3 * dt; }
                case WANDER -> { p.fun += 0.2 * dt; p.energy -= 0.03 * dt; }
            }
        }
        p.hunger = clamp(p.hunger);
        p.energy = clamp(p.energy);
        p.social = clamp(p.social);
        p.fun = clamp(p.fun);
        p.money = clamp(p.money);
    }

    private static double clamp(double v) { return Math.max(0, Math.min(100, v)); }

    private void sendTo(Person p, Activity a) {
        double[] dest;
        boolean building = true;
        Town.Place pl;
        switch (a) {
            case SLEEP -> pl = p.home;
            case EAT -> pl = town.restaurant();
            case WORK -> pl = p.workplace;
            case SHOP -> pl = town.shop();
            case RELAX -> pl = rnd.nextBoolean() ? p.home : null;
            default -> pl = null;
        }
        boolean pond = false;
        if (pl != null) {
            dest = new double[]{pl.doorX(), pl.doorY()};
        } else {
            building = false;
            pond = (a == Activity.RELAX || a == Activity.SOCIALIZE) && rnd.nextInt(3) == 0;
            dest = a == Activity.WANDER ? town.wanderSpot(rnd)
                    : a == Activity.SOCIALIZE ? town.socialSpot(pond, rnd)
                    : pond ? town.pondSpot(rnd) : town.parkSpot(rnd);
        }
        p.area = building || a == Activity.WANDER ? null : pond ? "pond" : "park";
        p.nextStrollAt = simMinute + rnd.nextDouble() * 10;
        String outdoors = pond ? "At the pond" : "At the park";
        p.placeLabel = switch (a) {
            case SLEEP -> "At home";
            case EAT -> "At the restaurant";
            case WORK -> "At work";
            case SHOP -> "At the shop";
            case RELAX -> building ? "At home" : outdoors;
            case SOCIALIZE -> outdoors;
            case WANDER -> "Out walking";
        };
        p.destInside = building;
        p.route.clear();
        if (Math.hypot(dest[0] - p.x, dest[1] - p.y) < 0.01) {
            p.arrived = true;
            p.inside = building;
        } else {
            p.arrived = false;
            p.inside = false;
            p.route.addAll(Town.route(p.x, p.y, dest[0], dest[1]));
        }
    }

    // ---- decisions ----
    private void decisionLoop() {
        while (running) {
            if (paused && stepPermits.get() == 0) {
                sleepQuietly(50);
                continue;
            }
            List<Person> batch = new ArrayList<>();
            List<String> states = new ArrayList<>();
            double now;
            synchronized (lock) {
                now = simMinute;
                people.stream()
                        .filter(p -> !p.inFlight && now >= p.nextDecisionAt)
                        .sorted(Comparator.comparingDouble(p -> p.nextDecisionAt))
                        .limit(LayaClient.MAX_BATCH)
                        .forEach(p -> {
                            p.inFlight = true;
                            batch.add(p);
                            states.add(stateText(p, now));
                        });
            }
            if (batch.isEmpty()) {
                if (paused) stepPermits.updateAndGet(v -> Math.max(0, v - 1));
                sleepQuietly(30);
                continue;
            }
            try {
                LayaClient.BatchResult res = laya.decide(states);
                synchronized (lock) {
                    for (int i = 0; i < batch.size(); i++) apply(batch.get(i), states.get(i), res.answers().get(i), res, batch.size());
                }
                lastBatchMs = res.wallMs();
                lastBatchSize = batch.size();
                synchronized (completions) {
                    long nowMs = System.currentTimeMillis();
                    completions.addLast(new long[]{nowMs, batch.size()});
                    while (!completions.isEmpty() && nowMs - completions.peekFirst()[0] > 30_000) completions.pollFirst();
                }
                message = "";
            } catch (Exception e) {
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                synchronized (lock) { batch.forEach(p -> p.inFlight = false); }
                message = "Laya did not answer. People keep doing what they were doing.";
                sleepQuietly(1500);
            }
            if (paused) stepPermits.updateAndGet(v -> Math.max(0, v - 1));
        }
    }

    private void apply(Person p, String state, LayaClient.Answer a, LayaClient.BatchResult res, int size) {
        p.inFlight = false;
        Activity chosen = Activity.of(a.choice());
        String choice = a.choice();
        if (chosen == p.activity && satisfied(p, chosen)) {
            // Laya answers the same state the same way, so someone who has got all they can out of their current
            // activity (fun at 100 while relaxing) would stay there forever. Take Laya's next-best answer instead.
            String current = chosen.label;
            String alt = a.probabilities().entrySet().stream()
                    .filter(e -> !e.getKey().equals(current))
                    .max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
            if (alt != null) { choice = alt; chosen = Activity.of(alt); }
        }
        Decision d = new Decision(simMinute, state, a.probabilities(), choice, a.answerConfidence(), res.wallMs(), size);
        p.record(d);
        if (chosen != p.activity) {
            p.activity = chosen;
            p.activityStartedAt = simMinute;
            sendTo(p, chosen);
        }
        p.nextDecisionAt = simMinute + decisionInterval * (0.7 + 0.6 * rnd.nextDouble());
    }

    /** True when the need this activity serves is already full, so carrying on would be pointless. */
    private boolean satisfied(Person p, Activity a) {
        return switch (a) {
            case SLEEP -> p.energy >= 95 && !timePhrase(simMinute).contains("night");
            case EAT -> p.hunger <= 5;
            case WORK -> p.money >= 95;
            case SOCIALIZE -> p.social >= 95;
            case RELAX -> p.fun >= 95;
            case SHOP -> p.money <= 5 || p.fun >= 95;
            case WANDER -> false;
        };
    }

    // ---- state text ----
    String stateText(Person p, double now) {
        record Need(double severity, String phrase) {}
        boolean night = timePhrase(now).contains("night");
        // At night people feel sleepier than their energy says, so tiredness leads their needs.
        double tired = Math.min(100, 100 - p.energy + (night ? 40 : 0));
        List<Need> needs = new ArrayList<>(List.of(
                new Need(p.hunger, p.hunger >= 80 ? "is starving" : p.hunger >= 60 ? "is very hungry" : "is a little hungry"),
                new Need(tired, tired >= 80 ? "is exhausted and very sleepy" : tired >= 60 ? "is tired" : "is a bit tired"),
                new Need(100 - p.social, 100 - p.social >= 80 ? "feels very lonely and wants company" : 100 - p.social >= 60 ? "feels lonely" : "would like some company"),
                new Need(100 - p.fun, 100 - p.fun >= 80 ? "is bored to death" : 100 - p.fun >= 60 ? "is bored and wants some fun" : "could use some fun"),
                new Need(100 - p.money, 100 - p.money >= 80 ? "has no money left and needs to earn some" : 100 - p.money >= 60 ? "is short of money" : "has little money")));
        needs.sort(Comparator.comparingDouble(Need::severity).reversed());
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Need n : needs) {
            if (n.severity() < 40 || shown == 3) break;
            sb.append(p.name).append(' ').append(n.phrase()).append(". ");
            shown++;
        }
        if (shown == 0) sb.append(p.name).append(" feels fine: fed, rested and content. ");
        else sb.append("Other needs are fine. ");
        // Nothing in the needs above points to the shop, so Laya never picked it. Spare money does.
        if (p.money >= 65) sb.append(p.name).append(" has money to spend. ");
        // Laya anchors on the current activity and on daytime phrases (it answered "relax" for everyone),
        // so the state names needs only, and the time of day only when it is night.
        String time = timePhrase(now);
        if (night) sb.append("It is ").append(time).append(", dark outside and time for bed.");
        return sb.toString();
    }

    static String timePhrase(double minute) {
        double h = (minute / 60.0) % 24;
        if (h < 5) return "the middle of the night";
        if (h < 9) return "early morning";
        if (h < 12) return "morning";
        if (h < 14) return "midday";
        if (h < 18) return "afternoon";
        if (h < 22) return "evening";
        return "late at night";
    }

    // ---- views ----
    public String clock() {
        synchronized (lock) {
            int total = (int) simMinute;
            int day = total / 1440 + 1;
            int h = (total / 60) % 24, m = total % 60;
            return String.format(Locale.ROOT, "Day %d · %02d:%02d", day, h, m);
        }
    }

    public double simMinute() { synchronized (lock) { return simMinute; } }

    public double decisionsPerSecond() {
        synchronized (completions) {
            long nowMs = System.currentTimeMillis();
            while (!completions.isEmpty() && nowMs - completions.peekFirst()[0] > 30_000) completions.pollFirst();
            long count = completions.stream().mapToLong(c -> c[1]).sum();
            double window = Math.min(30_000, nowMs - startedAt) / 1000.0;
            return window < 1 ? 0 : count / window;
        }
    }

    public long lastBatchMs() { return lastBatchMs; }
    public int lastBatchSize() { return lastBatchSize; }
    public String message() { return message; }
    public String layaUrl() { return laya.baseUrl(); }
    public Object lock() { return lock; }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
