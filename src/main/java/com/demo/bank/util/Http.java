package com.demo.bank.util;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Small helpers for reading requests and writing responses with the JDK HttpServer.
 * Request bodies are {@code application/x-www-form-urlencoded}.
 */
public final class Http {

    private Http() {
    }

    public static Map<String, String> parseForm(HttpExchange exchange) throws IOException {
        byte[] raw;
        try (InputStream in = exchange.getRequestBody()) {
            raw = in.readAllBytes();
        }
        return parsePairs(new String(raw, StandardCharsets.UTF_8));
    }

    public static Map<String, String> parseQuery(HttpExchange exchange) {
        return parsePairs(exchange.getRequestURI().getRawQuery());
    }

    private static Map<String, String> parsePairs(String body) {
        Map<String, String> result = new HashMap<>();
        if (body == null || body.isBlank()) {
            return result;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                result.put(decode(pair), "");
            } else {
                result.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
        }
        return result;
    }

    private static String decode(String s) {
        return URLDecoder.decode(s, StandardCharsets.UTF_8);
    }

    public static String bearerToken(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        return header.substring("Bearer ".length()).trim();
    }

    public static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] out = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, out.length);
        exchange.getResponseBody().write(out);
        exchange.getResponseBody().close();
    }

    public static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":\"" + escape(message) + "\"}");
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
