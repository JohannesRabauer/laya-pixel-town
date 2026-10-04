package dev.rabauer.laya_pixel_town;

import dev.langchain4j.model.decision.request.ChoiceQuestion;
import dev.langchain4j.model.decision.request.DecisionRequest;
import dev.langchain4j.model.decision.response.ChoiceAnswer;
import dev.langchain4j.model.typesafe.TypeSafeDecisionModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Talks to laya-serve through LangChain4j's TypeSafeDecisionModel. The library has no batch call, so the batch is
 * fanned out into one request per state on a small thread pool; Laya itself then answers them one after another.
 * The library does not expose Laya's answer_confidence, so the confidence it reports is used instead.
 */
public class LangChain4jLayaClient implements LayaClient {
    private static final int CONCURRENCY = 16;

    private final String baseUrl;
    private final TypeSafeDecisionModel model;
    private final ThreadPoolExecutor pool = new ThreadPoolExecutor(CONCURRENCY, CONCURRENCY, 30, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "pixeltown-langchain4j");
                t.setDaemon(true);
                return t;
            });

    public LangChain4jLayaClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.pool.allowCoreThreadTimeOut(true);
        this.model = TypeSafeDecisionModel.builder()
                .baseUrl(this.baseUrl)
                .apiKey("none")
                .modelName("laya-rl-agent")   // required by the library; laya-serve answers with its own model
                .timeout(Duration.ofMinutes(5))
                .maxRetries(0)
                .build();
    }

    @Override public String name() { return "langchain4j"; }

    @Override public String baseUrl() { return baseUrl; }

    @Override public String check() { return LayaHealth.check(baseUrl); }

    @Override
    public BatchResult decide(List<String> states) throws Exception {
        long t0 = System.nanoTime();
        List<CompletableFuture<Answer>> pending = new ArrayList<>();
        for (String state : states) pending.add(CompletableFuture.supplyAsync(() -> decideOne(state), pool));
        List<Answer> out = new ArrayList<>();
        try {
            for (CompletableFuture<Answer> f : pending) out.add(f.join());
        } catch (CompletionException e) {
            pending.forEach(f -> f.cancel(true));
            throw e.getCause() instanceof Exception ex ? ex : e;
        }
        return new BatchResult(out, (System.nanoTime() - t0) / 1_000_000);
    }

    private Answer decideOne(String state) {
        DecisionRequest request = DecisionRequest.builder()
                .input(state)
                .question(QUESTION_ID, ChoiceQuestion.of(INSTRUCTIONS, LayaClient.options()))
                .build();
        ChoiceAnswer a = model.decide(request).choice(QUESTION_ID);
        return new Answer(a.value(), a.probabilities(), a.confidence() == null ? Double.NaN : a.confidence());
    }
}
