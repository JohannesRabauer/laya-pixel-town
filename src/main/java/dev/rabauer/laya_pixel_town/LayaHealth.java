package dev.rabauer.laya_pixel_town;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** GET /health on laya-serve. None of the client libraries wraps it, so every implementation shares this. */
final class LayaHealth {
    // HTTP/1.1 on purpose: the default HTTP/2 client adds "Upgrade: h2c" to cleartext requests, and uvicorn
    // answers every one of them with "Unsupported upgrade request" plus a missing-WebSocket-library warning.
    private static final HttpClient HTTP = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(3)).build();

    private LayaHealth() {}

    /** Returns null when reachable, otherwise a short reason. */
    static String check(String baseUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/health")).timeout(Duration.ofSeconds(4)).GET().build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            return res.statusCode() == 200 ? null : "HTTP " + res.statusCode();
        } catch (IOException | IllegalArgumentException e) {
            return e.getClass().getSimpleName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted";
        }
    }

    static HttpClient client() { return HTTP; }
}
