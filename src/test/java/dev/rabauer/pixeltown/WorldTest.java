package dev.rabauer.pixeltown;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Behaviour of the simulation around Laya, with a test double standing in for the server. */
class WorldTest {
    /** Always answers "eat", or throws when told to. */
    static class StubLaya extends LayaClient {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicInteger states = new AtomicInteger();
        volatile boolean fail;

        StubLaya() { super("http://stub"); }

        @Override
        public BatchResult decide(List<String> batch) throws IOException {
            calls.incrementAndGet();
            states.addAndGet(batch.size());
            if (fail) throw new IOException("down");
            List<Answer> out = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                Map<String, Double> p = new LinkedHashMap<>();
                for (Person.Activity a : Person.Activity.values()) p.put(a.label, a == Person.Activity.EAT ? 0.9 : 0.1 / 6);
                out.add(new Answer("eat", p, 0.9));
            }
            return new BatchResult(out, 5);
        }
    }

    private World world;

    @AfterEach
    void stop() { if (world != null) world.stop(); }

    private static void await(java.util.function.BooleanSupplier cond, long ms) throws InterruptedException {
        long end = System.currentTimeMillis() + ms;
        while (!cond.getAsBoolean() && System.currentTimeMillis() < end) Thread.sleep(20);
    }

    @Test
    void decisionsAreBatchedAtMost64PerCall() throws Exception {
        StubLaya laya = new StubLaya();
        world = new World(100, 60, 1, laya);
        await(() -> laya.states.get() >= 100, 5000);
        assertTrue(laya.states.get() >= 100);
        assertTrue(laya.calls.get() >= 2, "100 people need at least two batches");
        assertTrue(laya.states.get() / (double) laya.calls.get() > 1.0, "never one call per person");
    }

    @Test
    void chosenActivityIsApplied() throws Exception {
        StubLaya laya = new StubLaya();
        world = new World(10, 60, 1, laya);
        await(() -> world.people.stream().allMatch(p -> p.lastDecision != null), 5000);
        assertTrue(world.people.stream().allMatch(p -> p.activity == Person.Activity.EAT));
    }

    @Test
    void pauseStopsTimeAndStepRunsOneBatch() throws Exception {
        StubLaya laya = new StubLaya();
        world = new World(10, 5, 1, laya);
        await(() -> laya.calls.get() >= 1, 5000);
        world.pause();
        Thread.sleep(300);
        double frozen = world.simMinute();
        int calls = laya.calls.get();
        Thread.sleep(400);
        assertEquals(frozen, world.simMinute(), 1e-9, "paused: no sim time passes");
        assertEquals(calls, laya.calls.get(), "paused: no decision calls");

        world.step();
        await(() -> world.simMinute() > frozen, 2000);
        assertTrue(world.simMinute() > frozen, "step advances time");
    }

    @Test
    void layaFailureKeepsActivitiesAndShowsMessage() throws Exception {
        StubLaya laya = new StubLaya();
        laya.fail = true;
        world = new World(10, 60, 1, laya);
        await(() -> !world.message().isEmpty(), 5000);
        assertTrue(world.message().startsWith("Laya did not answer"));
        assertTrue(world.people.stream().allMatch(p -> p.activity == Person.Activity.RELAX && p.lastDecision == null));

        laya.fail = false;
        await(() -> world.message().isEmpty() && world.people.stream().allMatch(p -> p.lastDecision != null), 8000);
        assertEquals("", world.message());
    }
}
