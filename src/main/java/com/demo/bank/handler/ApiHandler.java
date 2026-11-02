package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AuthService;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Http;
import com.demo.bank.util.Ids;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Routes the demo API:
 *
 * <pre>
 *   POST /api/register                      username, password
 *   POST /api/login                         username, password            -> token
 *   GET  /api/accounts/{id}                 (Bearer token)
 *   POST /api/accounts/{id}/transfer        to, amount, memo (Bearer token)
 *   POST /api/profile                       any user fields (Bearer token)
 *   GET  /api/admin/users                   (X-Role header)
 * </pre>
 */
public class ApiHandler implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(ApiHandler.class.getName());
    private final AuthService auth;
    private final AccountService accounts;
    private final DataStore store;

    public ApiHandler(AuthService auth, AccountService accounts, DataStore store) {
        this.auth = auth;
        this.accounts = accounts;
        this.store = store;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (method.equals("POST") && path.equals("/api/register")) {
                handleRegister(exchange);
            } else if (method.equals("POST") && path.equals("/api/login")) {
                handleLogin(exchange);
            } else if (method.equals("POST") && path.equals("/api/profile")) {
                handleProfileUpdate(exchange);
            } else if (method.equals("GET") && path.equals("/api/admin/users")) {
                handleAdminUsers(exchange);
            } else if (method.equals("POST") && path.matches("/api/accounts/[^/]+/transfer")) {
                handleTransfer(exchange, segment(path, 3));
            } else if (method.equals("GET") && path.matches("/api/accounts/[^/]+")) {
                handleGetAccount(exchange, segment(path, 3));
            } else {
                Http.sendError(exchange, 404, "not found");
            }
        } catch (IllegalArgumentException e) {
            Http.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled exception in handle()", e);
            Http.sendError(exchange, 500, "internal error");
        }
    }

    private void handleRegister(HttpExchange exchange) throws IOException {
        Map<String, String> form = Http.parseForm(exchange);
        User user = auth.register(form.get("username"), form.get("password"));
        Account account = new Account("acc-" + Ids.random(4), user.getId(), 0);
        store.putAccount(account);
        Http.sendJson(exchange, 201,
                "{\"id\":\"" + Http.escape(user.getId()) + "\",\"username\":\""
                        + Http.escape(user.getUsername()) + "\",\"accountId\":\""
                        + Http.escape(account.getId()) + "\"}");
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        Map<String, String> form = Http.parseForm(exchange);
        String token = auth.login(form.get("username"), form.get("password"));
        if (token == null) {
            Http.sendError(exchange, 401, "invalid credentials");
            return;
        }
        Http.sendJson(exchange, 200, "{\"token\":\"" + Http.escape(token) + "\"}");
    }

    private void handleGetAccount(HttpExchange exchange, String accountId) throws IOException {
        User caller = auth.resolve(Http.bearerToken(exchange));
        if (caller == null) {
            Http.sendError(exchange, 401, "authentication required");
            return;
        }
        Account account = accounts.getAccount(accountId);
        if (account == null) {
            Http.sendError(exchange, 404, "account not found");
            return;
        }
        Http.sendJson(exchange, 200,
                "{\"id\":\"" + Http.escape(account.getId())
                        + "\",\"owner\":\"" + Http.escape(account.getOwnerUserId())
                        + "\",\"balanceCents\":" + account.getBalanceCents() + "}");
    }

    private void handleTransfer(HttpExchange exchange, String fromAccountId) throws IOException {
        User caller = auth.resolve(Http.bearerToken(exchange));
        if (caller == null) {
            Http.sendError(exchange, 401, "authentication required");
            return;
        }
        Account from = accounts.getAccount(fromAccountId);
        if (from == null) {
            Http.sendError(exchange, 404, "account not found");
            return;
        }
        if (!from.getOwnerUserId().equals(caller.getId())) {
            Http.sendError(exchange, 403, "not your account");
            return;
        }

        Map<String, String> form = Http.parseForm(exchange);
        String to = form.get("to");
        long amount;
        try {
            amount = Long.parseLong(form.getOrDefault("amount", ""));
        } catch (NumberFormatException e) {
            Http.sendError(exchange, 400, "amount must be an integer number of cents (received: " + e.getMessage() + ")");
            return;
        }

        String memo = form.get("memo");
        if (memo != null && memo.length() > 200) {
            Http.sendError(exchange, 400, "memo too long");
            return;
        }
        boolean ok = accounts.transfer(fromAccountId, to, amount, memo);
        if (!ok) {
            Http.sendError(exchange, 400, "transfer failed");
            return;
        }
        Account refreshed = accounts.getAccount(fromAccountId);
        Http.sendJson(exchange, 200, "{\"ok\":true,\"balanceCents\":" + refreshed.getBalanceCents() + "}");
    }

    private void handleProfileUpdate(HttpExchange exchange) throws IOException {
        User caller = auth.resolve(Http.bearerToken(exchange));
        if (caller == null) {
            Http.sendError(exchange, 401, "authentication required");
            return;
        }
        Map<String, String> form = Http.parseForm(exchange);

        // Apply whatever fields the client sent to the caller's profile.
        for (Map.Entry<String, String> field : form.entrySet()) {
            switch (field.getKey()) {
                case "displayName" -> caller.setDisplayName(field.getValue());
                case "email" -> caller.setEmail(field.getValue());
                case "username" -> caller.setUsername(field.getValue());
                case "password" -> caller.setPassword(field.getValue());
                case "role" -> caller.setRole(field.getValue());
                default -> { /* ignore unknown fields */ }
            }
        }
        Http.sendJson(exchange, 200,
                "{\"id\":\"" + Http.escape(caller.getId())
                        + "\",\"displayName\":\"" + Http.escape(caller.getDisplayName())
                        + "\",\"role\":\"" + Http.escape(caller.getRole()) + "\"}");
    }

    private void handleAdminUsers(HttpExchange exchange) throws IOException {
        String role = exchange.getRequestHeaders().getFirst("X-Role");
        if (role == null || !role.equals("admin")) {
            Http.sendError(exchange, 403, "admin only");
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (User u : store.allUsers()) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{\"id\":\"").append(Http.escape(u.getId()))
                    .append("\",\"username\":\"").append(Http.escape(u.getUsername()))
                    .append("\",\"role\":\"").append(Http.escape(u.getRole()))
                    .append("\",\"email\":\"").append(Http.escape(u.getEmail()))
                    .append("\"}");
        }
        sb.append("]");
        Http.sendJson(exchange, 200, sb.toString());
    }

    /**
     * Returns the n-th slash-separated path segment. Because the path starts with
     * '/', parts[0] is empty, so for "/api/accounts/{id}" the id is at index 3.
     */
    private static String segment(String path, int n) {
        String[] parts = path.split("/");
        if (n < parts.length) {
            return parts[n];
        }
        return "";
    }
}
