package dev.rabauer.laya_pixel_town;

import org.springaicommunity.typesafe.JevBatchOptions;
import org.springaicommunity.typesafe.JevBatchResult;
import org.springaicommunity.typesafe.RetryPolicy;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Choice;
import org.springaicommunity.typesafe.question.SystemOneRequest;
import org.springaicommunity.typesafe.response.ChoiceAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to laya-serve through the Spring AI community TypeSafe SDK. Its systemOneAll fans the batch out into one
 * request per state; Laya itself then answers them one after another. The SDK does not expose Laya's
 * answer_confidence, so the confidence it reports is used instead.
 */
public class SpringAiLayaClient implements LayaClient {
    private static final int CONCURRENCY = 16;

    private final String baseUrl;
    private final TypeSafeClient client;

    public SpringAiLayaClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.client = TypeSafeClient.builder()
                .baseUrl(this.baseUrl)
                .apiKey("none")
                .timeout(Duration.ofMinutes(5))
                .retryPolicy(RetryPolicy.noRetry())
                .build();
    }

    @Override public String name() { return "spring-ai"; }

    @Override public String baseUrl() { return baseUrl; }

    @Override public String check() { return LayaHealth.check(baseUrl); }

    @Override
    public BatchResult decide(List<String> states) {
        Choice.Builder question = Choice.builder().instructions(INSTRUCTIONS);
        LayaClient.options().forEach(question::option);
        Choice choice = question.build();
        List<SystemOneRequest> requests = states.stream()
                .map(s -> SystemOneRequest.builder().state(s).question(QUESTION_ID, choice).build())
                .toList();

        long t0 = System.nanoTime();
        List<JevBatchResult<SystemOneResponse>> results = client.systemOneAll(requests, JevBatchOptions.ofConcurrency(CONCURRENCY));
        long ms = (System.nanoTime() - t0) / 1_000_000;

        List<Answer> out = new ArrayList<>();
        for (JevBatchResult<SystemOneResponse> r : results) {
            ChoiceAnswer a = r.orThrow().choice(QUESTION_ID);
            out.add(new Answer(a.value(), a.probabilities(), a.confidence()));
        }
        return new BatchResult(out, ms);
    }
}
