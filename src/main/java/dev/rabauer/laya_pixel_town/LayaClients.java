package dev.rabauer.laya_pixel_town;

/** Creates the Laya client chosen by {@code pixeltown.laya.client}. */
public final class LayaClients {
    private LayaClients() {}

    public static LayaClient create(String type, String baseUrl) {
        return switch (type == null ? "native" : type.trim().toLowerCase()) {
            case "native" -> new NativeLayaClient(baseUrl);
            case "langchain4j" -> new LangChain4jLayaClient(baseUrl);
            case "spring-ai" -> new SpringAiLayaClient(baseUrl);
            default -> throw new IllegalArgumentException(
                    "Unknown pixeltown.laya.client '" + type + "'. Use native, langchain4j or spring-ai.");
        };
    }
}
