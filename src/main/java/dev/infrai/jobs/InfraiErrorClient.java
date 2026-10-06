package dev.infrai.jobs;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small client for infrai.errors.capture; no SDK is required. */
public final class InfraiErrorClient implements FailureSink {
    private static final String CAPTURE_PATH = "/v1/errors/capture";
    private static final Pattern OK = Pattern.compile("\\\"ok\\\"\\s*:\\s*(true|false)");
    private static final Pattern ERROR = Pattern.compile("\\\"error\\\"\\s*:\\s*(null|\\{.*?})", Pattern.DOTALL);

    private final HttpClient http;
    private final ScheduledJobConfig config;
    private final Sleeper sleeper;

    public InfraiErrorClient(ScheduledJobConfig config) {
        this(HttpClient.newBuilder().connectTimeout(config.requestTimeout()).build(), config, Thread::sleep);
    }

    InfraiErrorClient(HttpClient http, ScheduledJobConfig config, Sleeper sleeper) {
        this.http = http;
        this.config = config;
        this.sleeper = sleeper;
    }

    @Override
    public void capture(String exception, String idempotencyKey) throws IOException, InterruptedException {
        String requestBody = "{\"exception\":" + quote(exception) + "}";
        for (int attempt = 1; attempt <= config.maxAttempts(); attempt++) {
            HttpRequest request = HttpRequest.newBuilder(config.baseUri().resolve(CAPTURE_PATH))
                    .timeout(config.requestTimeout())
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            Envelope envelope = decodeEnvelope(response.body());

            if (response.statusCode() == 429 && attempt < config.maxAttempts()) {
                sleeper.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (response.statusCode() >= 500) {
                throw new IOException("Infrai transport response " + response.statusCode());
            }
            if (!envelope.ok()) {
                throw new InfraiException(response.statusCode(), envelope.error());
            }
            return;
        }
        throw new IOException("Retry budget exhausted");
    }

    private static Envelope decodeEnvelope(String body) throws IOException {
        Matcher ok = OK.matcher(body);
        if (!ok.find()) throw new IOException("Response is not an Infrai envelope");
        Matcher error = ERROR.matcher(body);
        String detail = error.find() ? error.group(1) : "null";
        return new Envelope(Boolean.parseBoolean(ok.group(1)), detail);
    }

    private static long retryDelayMillis(HttpResponse<?> response, int attempt) {
        String value = response.headers().firstValue("Retry-After").orElse("").trim();
        if (!value.isEmpty()) {
            try { return Math.max(0L, Long.parseLong(value) * 1_000L); }
            catch (NumberFormatException ignored) { /* use bounded exponential delay */ }
        }
        return Math.min(8_000L, 250L << (attempt - 1));
    }

    static String quote(String value) {
        StringBuilder result = new StringBuilder(value.length() + 16).append('"');
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> {
                    if (c < 0x20) result.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    else result.append(c);
                }
            }
        }
        return result.append('"').toString();
    }

    private record Envelope(boolean ok, String error) {}

    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }

    public static final class InfraiException extends IOException {
        private final int statusCode;
        InfraiException(int statusCode, String detail) {
            super("Infrai rejected the capture (HTTP " + statusCode + "): " + detail);
            this.statusCode = statusCode;
        }
        public int statusCode() { return statusCode; }
    }
}

@FunctionalInterface
interface FailureSink {
    void capture(String exception, String idempotencyKey) throws IOException, InterruptedException;
}
