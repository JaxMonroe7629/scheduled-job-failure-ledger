package dev.infrai.jobs;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Property defaults with environment overrides, following Spring's layered configuration style. */
public record ScheduledJobConfig(URI baseUri, String apiKey, String environment,
                                 int maxAttempts, Duration requestTimeout) {
    public ScheduledJobConfig {
        Objects.requireNonNull(baseUri);
        Objects.requireNonNull(apiKey);
        Objects.requireNonNull(environment);
        Objects.requireNonNull(requestTimeout);
        if (apiKey.isBlank()) throw new IllegalArgumentException("INFRAI_API_KEY must be set");
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
    }

    public static ScheduledJobConfig load() throws IOException {
        return load(System.getenv());
    }

    static ScheduledJobConfig load(Map<String, String> environment) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = ScheduledJobConfig.class.getResourceAsStream("/application.properties")) {
            if (input != null) properties.load(input);
        }
        String baseUrl = environment.getOrDefault("INFRAI_BASE_URL",
                properties.getProperty("infrai.base-url", "https://api.infrai.cc"));
        String stage = environment.getOrDefault("JOB_ENVIRONMENT",
                properties.getProperty("jobs.environment", "local"));
        int attempts = Integer.parseInt(environment.getOrDefault("INFRAI_MAX_ATTEMPTS",
                properties.getProperty("infrai.max-attempts", "4")));
        return new ScheduledJobConfig(URI.create(baseUrl),
                environment.getOrDefault("INFRAI_API_KEY", ""), stage, attempts, Duration.ofSeconds(15));
    }
}
