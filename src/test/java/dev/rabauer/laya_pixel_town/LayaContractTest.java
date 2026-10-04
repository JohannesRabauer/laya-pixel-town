package dev.rabauer.laya_pixel_town;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs against a real laya-serve; skipped when none is reachable. Set LAYA_URL to choose the server. */
class LayaContractTest {
    private static final String URL = System.getenv().getOrDefault("LAYA_URL", "http://localhost:8003");

    @Test
    void batchReturnsOneAnswerPerStateInOrder() throws Exception {
        LayaClient laya = new LayaClient(URL);
        assumeTrue(laya.check() == null, "laya-serve not reachable at " + URL);

        List<String> states = List.of(
                "Tom is exhausted and very sleepy. It is midnight. Right now Tom is relaxing.",
                "Dee is bored and wants to have fun. It is afternoon. Right now Dee is working.",
                "Ann is starving. It is midday. Right now Ann is working.");
        LayaClient.BatchResult res = laya.decide(states);

        assertEquals(states.size(), res.answers().size());
        for (LayaClient.Answer a : res.answers()) {
            assertEquals(Person.Activity.values().length, a.probabilities().size());
            assertTrue(a.probabilities().containsKey(a.choice()));
            double sum = a.probabilities().values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(1.0, sum, 0.05);
        }
        assertTrue(res.wallMs() > 0);
    }
}
