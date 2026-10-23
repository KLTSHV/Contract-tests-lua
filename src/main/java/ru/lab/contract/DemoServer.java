package ru.lab.contract;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Локальный провайдер: корректный ответ и два нарушения контракта. */
final class DemoServer {
    private DemoServer() {}

    static HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            int status;
            String body;
            if (!"GET".equals(exchange.getRequestMethod())) {
                status = 405; body = "{\"error\":\"method not allowed\"}";
            } else {
                switch (path) {
                    case "/users/42" -> { status = 200; body = "{\"id\":42,\"name\":\"Анна\",\"age\":20}"; }
                    case "/users/broken" -> { status = 200; body = "{\"id\":\"42\",\"name\":\"Анна\",\"age\":20}"; }
                    case "/users/unavailable" -> { status = 503; body = "{\"error\":\"temporarily unavailable\"}"; }
                    default -> { status = 404; body = "{\"error\":\"not found\"}"; }
                }
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (var output = exchange.getResponseBody()) { output.write(bytes); }
            finally { exchange.close(); }
        });
        server.start();
        return server;
    }
}
