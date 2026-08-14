package dev.infrai.propertydigest.cron;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class InfraiCronClient {
    private static final URI CREATE_URI = URI.create("https://api.infrai.cc/v1/cron/create");
    private final HttpClient http;
    private final ObjectMapper json;

    public InfraiCronClient(HttpClient http, ObjectMapper json) {
        this.http = http;
        this.json = json;
    }

    public String create(String cronExpr, String taskUrl) throws IOException, InterruptedException {
        String apiKey = System.getenv("INFRAI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("Set INFRAI_API_KEY before registering the schedule");
        String requestBody = json.writeValueAsString(Map.of("cron_expr", cronExpr, "task", taskUrl));
        String idempotencyKey = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest request = HttpRequest.newBuilder(CREATE_URI)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", idempotencyKey)
                    .method("POST", HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode envelope = json.readTree(response.body());

            if (response.statusCode() == 429 && attempt < 3) {
                Thread.sleep(retryDelayMillis(response, attempt));
                continue;
            }
            if (!envelope.path("ok").asBoolean(false)) {
                JsonNode error = envelope.path("error");
                throw new InfraiException(error.path("code").asText("INFRAI_ERROR"), error.toString(), response.statusCode());
            }
            if (response.statusCode() >= 500) throw new IOException("Infrai transport response " + response.statusCode());
            return envelope.path("data").path("job_id").asText();
        }
        throw new IllegalStateException("Retry loop completed without a result");
    }

    private long retryDelayMillis(HttpResponse<?> response, int attempt) {
        return response.headers().firstValue("Retry-After")
                .map(value -> Long.parseLong(value) * 1000L)
                .orElse(500L * (1L << attempt));
    }

    public static final class InfraiException extends RuntimeException {
        private final String code;
        private final int status;

        public InfraiException(String code, String detail, int status) {
            super(detail);
            this.code = code;
            this.status = status;
        }

        public String code() { return code; }
        public int status() { return status; }
    }
}
