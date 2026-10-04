package dev.rabauer.laya_pixel_town;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Person {
    public enum Activity {
        SLEEP("sleep", "go to bed and sleep because they are tired, and always when it is night"),
        EAT("eat", "eat a meal because they are hungry"),
        WORK("work", "go to work to earn money"),
        SOCIALIZE("socialize", "meet friends in the park or at the pond because they are lonely"),
        RELAX("relax", "relax and have fun because they are bored"),
        SHOP("shop", "go shopping and spend some of their money on something nice"),
        WANDER("wander", "stroll around aimlessly");

        public final String label;
        public final String description;

        Activity(String label, String description) {
            this.label = label;
            this.description = description;
        }

        public static Activity of(String label) {
            for (Activity a : values()) if (a.label.equals(label)) return a;
            return WANDER;
        }
    }

    /** One decision as Laya answered it. Immutable once created. */
    public record Decision(double simMinute, String stateText, Map<String, Double> probabilities, String chosen,
                           double answerConfidence, long batchMs, int batchSize) {}

    public final int id;
    public final String name;
    public final Town.Place home;
    public final Town.Place workplace;

    public double x, y;
    public boolean inside;
    public Activity activity = Activity.RELAX;
    public String placeLabel = "At home";
    public double hunger, energy, social, fun, money;

    final Deque<double[]> route = new ArrayDeque<>();
    boolean arrived = true;
    boolean destInside = true;
    double activityStartedAt;
    double nextDecisionAt;
    boolean inFlight;

    Decision lastDecision;
    final Deque<Decision> history = new ArrayDeque<>();

    public Person(int id, String name, Town.Place home, Town.Place workplace, java.util.Random rnd) {
        this.id = id;
        this.name = name;
        this.home = home;
        this.workplace = workplace;
        this.x = home.doorX();
        this.y = home.doorY();
        this.inside = true;
        hunger = 20 + rnd.nextInt(50);
        energy = 40 + rnd.nextInt(55);
        social = 20 + rnd.nextInt(70);
        fun = 20 + rnd.nextInt(70);
        money = 10 + rnd.nextInt(80);
    }

    void record(Decision d) {
        lastDecision = d;
        history.addFirst(d);
        while (history.size() > 10) history.removeLast();
    }

    public List<Decision> historyList() {
        return new ArrayList<>(history);
    }

    public Map<String, Integer> needs() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("hunger", (int) Math.round(hunger));
        m.put("energy", (int) Math.round(energy));
        m.put("social", (int) Math.round(social));
        m.put("fun", (int) Math.round(fun));
        m.put("money", (int) Math.round(money));
        return m;
    }
}
