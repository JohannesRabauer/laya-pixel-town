package dev.rabauer.laya_pixel_town;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How the simulation talks to Laya. Three implementations exist, picked with the property
 * {@code pixeltown.laya.client}: {@code native} (one real batch call), {@code langchain4j} and {@code spring-ai}
 * (both fan the batch out into single calls, because neither library has a batch call).
 */
public interface LayaClient {
    int MAX_BATCH = 64;
    String QUESTION_ID = "next";
    String INSTRUCTIONS = "Choose what this person most needs to do right now, given their needs.";

    record Answer(String choice, Map<String, Double> probabilities, double answerConfidence) {}

    record BatchResult(List<Answer> answers, long wallMs) {}

    /** Short name of the implementation, as used in the property. */
    String name();

    String baseUrl();

    /** Returns null when reachable, otherwise a short reason. */
    String check();

    /** One decision per state, in order. The caller keeps states at or below MAX_BATCH. */
    BatchResult decide(List<String> states) throws Exception;

    /** The activities as answer options: label to description. */
    static Map<String, String> options() {
        Map<String, String> m = new LinkedHashMap<>();
        for (Person.Activity a : Person.Activity.values()) m.put(a.label, a.description);
        return m;
    }
}
