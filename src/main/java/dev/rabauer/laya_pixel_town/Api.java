package dev.rabauer.laya_pixel_town;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class Api {
    public record StartRequest(Integer population, Double decisionInterval, Long seed, String layaUrl) {}

    private final ObjectMapper mapper = new ObjectMapper();
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    private final ScheduledExecutorService pusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "pixeltown-sse");
        t.setDaemon(true);
        return t;
    });
    private final String defaultUrl;
    private volatile World world;

    public Api(@Value("${pixeltown.laya.default-url}") String defaultUrl) {
        this.defaultUrl = defaultUrl;
        pusher.scheduleAtFixedRate(this::push, 200, 100, TimeUnit.MILLISECONDS);
    }

    @GetMapping("/defaults")
    public Map<String, Object> defaults() {
        return Map.of("layaUrl", defaultUrl, "population", 100, "decisionInterval", 60, "seed", 0);
    }

    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam String url) {
        String problem = new LayaClient(url).check();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", problem == null);
        m.put("problem", problem);
        return m;
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody StartRequest req) {
        String url = req.layaUrl() == null || req.layaUrl().isBlank() ? defaultUrl : req.layaUrl().trim();
        LayaClient client = new LayaClient(url);
        String problem = client.check();
        if (problem != null) {
            return ResponseEntity.status(503).body(Map.of("ok", false, "problem", "Laya is not reachable at " + url + "."));
        }
        int pop = Math.max(10, Math.min(300, req.population() == null ? 100 : req.population()));
        double interval = req.decisionInterval() == null ? 60 : Math.max(5, req.decisionInterval());
        long seed = req.seed() == null || req.seed() == 0 ? System.nanoTime() : req.seed();
        World old = world;
        if (old != null) old.stop();
        world = new World(pop, interval, seed, client);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/stop")
    public Map<String, Object> stop() {
        World w = world;
        if (w != null) w.stop();
        world = null;
        return Map.of("ok", true);
    }

    @PostMapping("/pause")
    public Map<String, Object> pause() { if (world != null) world.pause(); return Map.of("ok", true); }

    @PostMapping("/resume")
    public Map<String, Object> resume() { if (world != null) world.resume(); return Map.of("ok", true); }

    @PostMapping("/step")
    public Map<String, Object> step() { if (world != null) world.step(); return Map.of("ok", true); }

    @PostMapping("/speed/{n}")
    public Map<String, Object> speed(@PathVariable int n) { if (world != null) world.speed(n); return Map.of("ok", true); }

    @GetMapping("/map")
    public Map<String, Object> map() {
        Town town = new Town();
        List<Map<String, Object>> buildings = new ArrayList<>();
        for (Town.Place p : town.allBuildings()) {
            buildings.add(Map.of("type", p.type(), "x", p.x(), "y", p.y(), "w", p.w(), "h", p.h()));
        }
        return Map.of("w", Town.WIDTH, "h", Town.HEIGHT, "buildings", buildings);
    }

    @GetMapping("/person/{id}")
    public ResponseEntity<Map<String, Object>> person(@PathVariable int id) {
        World w = world;
        if (w == null) return ResponseEntity.notFound().build();
        synchronized (w.lock()) {
            if (id < 0 || id >= w.people.size()) return ResponseEntity.notFound().build();
            Person p = w.people.get(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.id);
            m.put("name", p.name);
            m.put("activity", p.activity.label);
            m.put("place", p.arrived ? p.placeLabel : "On the way");
            m.put("needs", p.needs());
            m.put("last", decisionView(p.lastDecision, w));
            List<Object> hist = new ArrayList<>();
            for (Person.Decision d : p.historyList()) hist.add(decisionView(d, w));
            m.put("history", hist);
            return ResponseEntity.ok(m);
        }
    }

    private static Map<String, Object> decisionView(Person.Decision d, World w) {
        if (d == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("time", clock(d.simMinute()));
        m.put("state", d.stateText());
        List<Map<String, Object>> opts = new ArrayList<>();
        d.probabilities().entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(e -> opts.add(Map.of("label", e.getKey(), "p", e.getValue())));
        m.put("options", opts);
        m.put("chosen", d.chosen());
        m.put("confidence", d.answerConfidence());
        m.put("batchMs", d.batchMs());
        m.put("batchSize", d.batchSize());
        return m;
    }

    private static String clock(double minute) {
        int total = (int) minute;
        return String.format("%02d:%02d", (total / 60) % 24, total % 60);
    }

    // ---- live stream ----
    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        SseEmitter em = new SseEmitter(0L);
        emitters.add(em);
        em.onCompletion(() -> emitters.remove(em));
        em.onTimeout(() -> emitters.remove(em));
        em.onError(e -> emitters.remove(em));
        return em;
    }

    private void push() {
        if (emitters.isEmpty()) return;
        World w = world;
        String json;
        try {
            json = w == null ? "{\"running\":false}" : snapshot(w);
        } catch (Exception e) {
            return;
        }
        for (SseEmitter em : emitters) {
            try {
                em.send(SseEmitter.event().data(json));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(em);
            }
        }
    }

    private String snapshot(World w) throws IOException {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("running", true);
        m.put("paused", w.paused());
        m.put("speed", w.speed());
        m.put("clock", w.clock());
        m.put("minute", w.simMinute());
        m.put("n", w.people.size());
        m.put("dps", Math.round(w.decisionsPerSecond() * 100) / 100.0);
        m.put("batch", w.lastBatchSize());
        m.put("ms", w.lastBatchMs());
        m.put("msg", w.message());
        List<double[]> ps = new ArrayList<>();
        synchronized (w.lock()) {
            for (Person p : w.people) {
                ps.add(new double[]{p.id, Math.round(p.x * 100) / 100.0, Math.round(p.y * 100) / 100.0, p.activity.ordinal(), p.inside ? 1 : 0});
            }
        }
        m.put("p", ps);
        return mapper.writeValueAsString(m);
    }
}
