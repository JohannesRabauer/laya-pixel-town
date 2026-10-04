package dev.rabauer.laya_pixel_town;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to laya-serve over plain HTTP. langchain4j-typesafe has no call for POST /v1/systemone/batch,
 * which is the call this demo exists to show, so the batch path is a thin JDK HttpClient wrapper.
 */
public class LayaClient {
    public static final int MAX_BATCH = 64;

    public record Answer(String choice, Map<String, Double> probabilities, double answerConfidence) {}

    public record BatchResult(List<Answer> answers, long wallMs) {}

    private static final String QUESTION_ID = "next";
    private static final String INSTRUCTIONS = "Choose what this person most needs to do right now, given their needs.";

    private final ObjectMapper mapper = new ObjectMapper();
    // HTTP/1.1 on purpose: the default HTTP/2 client adds "Upgrade: h2c" to cleartext requests, and uvicorn
    // answers every one of them with "Unsupported upgrade request" plus a missing-WebSocket-library warning.
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(3)).build();
    private final String baseUrl;

    public LayaClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String baseUrl() { return baseUrl; }

    /** Returns null when reachable, otherwise a short reason. */
    public String check() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? null : "HTTP " + res.statusCode();
        } catch (IOException | IllegalArgumentException e) {
            return e.getClass().getSimpleName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    /** One batch call; the caller keeps states at or below MAX_BATCH. */
    public BatchResult decide(List<String> states) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode st = body.putArray("states");
        states.forEach(st::add);
        ObjectNode q = body.putObject("questions").putObject(QUESTION_ID);
        q.put("type", "choice");
        q.put("instructions", INSTRUCTIONS);
        ObjectNode criteria = q.putObject("criteria");
        for (Person.Activity a : Person.Activity.values()) criteria.put(a.label, a.description);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/systemone/batch"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (res.statusCode() != 200) throw new IOException("Laya answered HTTP " + res.statusCode() + ": " + res.body());

        JsonNode results = mapper.readTree(res.body()).path("results");
        if (results.size() != states.size()) throw new IOException("Laya returned " + results.size() + " results for " + states.size() + " states");
        List<Answer> out = new ArrayList<>();
        for (JsonNode r : results) {
            JsonNode a = r.path("answers").path(QUESTION_ID);
            Map<String, Double> probs = new LinkedHashMap<>();
            a.path("probabilities").fields().forEachRemaining(e -> probs.put(e.getKey(), e.getValue().asDouble()));
            out.add(new Answer(a.path("choice").asText(), probs, a.path("answer_confidence").asDouble(Double.NaN)));
        }
        return new BatchResult(out, ms);
    }
}
