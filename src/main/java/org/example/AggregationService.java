package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.api.StatefulRedisConnection;
import org.example.model.AppResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AggregationService {

    private static final String WEATHER_URL = "https://api.open-meteo.com/v1/forecast?latitude=51.107883&longitude=17.038538&current_weather=true";
    private static final String FACT_URL = "https://uselessfacts.jsph.pl/api/v2/facts/random";
    private static final String IP_URL = "https://api.ipify.org/?format=json";

    private final HttpClient httpClient;
    private final StatefulRedisConnection<String, String> redisConnection;
    private final ObjectMapper mapper;

    public AggregationService(StatefulRedisConnection<String, String> redisConnection) {
        this.redisConnection = redisConnection;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.mapper = new ObjectMapper();
    }

    public CompletableFuture<AppResponse> getAggregatedData() {
        var weatherFuture = fetchData("weather", WEATHER_URL);
        var factFuture = fetchData("fact", FACT_URL);
        var ipFuture = fetchData("ip", IP_URL);

        return CompletableFuture.allOf(weatherFuture, factFuture, ipFuture)
                .thenApply(v -> {
                    JsonNode weather = weatherFuture.join();
                    JsonNode fact = factFuture.join();
                    JsonNode ip = ipFuture.join();
                    return new AppResponse(weather, fact, ip);
                });
    }

    private CompletableFuture<JsonNode> fetchData(String key, String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 200) {
                        String body = response.body();
                        redisConnection.async().setex(key, 60, body);
                        return parseJson(body);
                    } else {
                        throw new RuntimeException("API Error: " + response.statusCode());
                    }
                })
                .exceptionallyCompose(ex -> {
                    System.err.println("API failed for " + key + ", checking Redis. Error: " + ex.getMessage());
                    return redisConnection.async().get(key)
                            .thenApply(cachedValue -> {
                                if (cachedValue != null) {
                                    return parseJson(cachedValue);
                                } else {
                                    return mapper.createObjectNode().put("error", "Service unavailable");
                                }
                            });
                });
    }

    private JsonNode parseJson(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode().put("error", "Invalid JSON");
        }
    }
}