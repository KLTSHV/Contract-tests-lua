package ru.lab.contract;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Получение данных отделено от правил контракта. */
interface ResponseSource {
    Response read() throws IOException, InterruptedException;

    record Response(String body, int status, Map<String, List<String>> headers) {}

    static ResponseSource file(Path path) {
        return () -> new Response(Files.readString(path, StandardCharsets.UTF_8), 200,
                Map.of("content-type", List.of("application/json")));
    }

    static ResponseSource http(URI uri) {
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("URL должен начинаться с http:// или https://");
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json").GET().build();
        return () -> {
            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Response(response.body(), response.statusCode(), response.headers().map());
        };
    }
}
