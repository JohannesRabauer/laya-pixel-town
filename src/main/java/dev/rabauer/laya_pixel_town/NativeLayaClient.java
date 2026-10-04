package dev.rabauer.laya_pixel_town;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to laya-serve over plain HTTP with the JDK HttpClient. This is the only implementation that uses
 * POST /v1/systemone/batch, which is the call this demo exists to show: one forward pass for the whole batch.
 */
public class NativeLayaClient implements LayaClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;

    public NativeLayaClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    @Override public String name() { return "native"; }

    @Override public String baseUrl() { return baseUrl; }

    @Override public String check() { return LayaHealth.check(baseUrl); }

    @Override
    public BatchResult decide(List<String> states) throws IOException, InterruptedException {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode st = body.putArray("states");
        states.forEach(st::add);
        ObjectNode q = body.putObject("questions").putObject(QUESTION_ID);
        q.put("type", "choice");
        q.put("instructions", INSTRUCTIONS);
        ObjectNode criteria = q.putObject("criteria");
        LayaClient.options().forEach(criteria::put);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/systemone/batch"))
                .timeout(Duration.ofMinutes(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> res = LayaHealth.client().send(req, HttpResponse.BodyHandlers.ofString());
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
