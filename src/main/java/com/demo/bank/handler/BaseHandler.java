package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.Txn;
import com.demo.bank.model.User;
import com.demo.bank.service.AuthService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Shared plumbing for the feature handlers: path splitting, error mapping and
 * bearer-token lookup.
 */
public abstract class BaseHandler implements HttpHandler {
    private static final Logger LOG = Logger.getLogger(BaseHandler.class.getName());

    protected final AuthService auth;

    protected BaseHandler(AuthService auth) {
        this.auth = auth;
    }

    /** @param seg the path split on '/', so seg[0] is empty and seg[1] is "api" */
    protected abstract void route(HttpExchange exchange, String method, String[] seg) throws IOException;

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        try {
            String path = exchange.getRequestURI().getPath();
            route(exchange, exchange.getRequestMethod(), path.split("/"));
        } catch (IllegalArgumentException e) {
            Http.sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Unhandled exception in handle()", e);
            Http.sendError(exchange, 500, "internal error");
        }
    }

    /** Resolves the caller, or sends 401 and returns null. */
    protected User caller(HttpExchange exchange) throws IOException {
        User caller = auth.resolve(Http.bearerToken(exchange));
        if (caller == null) {
            Http.sendError(exchange, 401, "authentication required");
        }
        return caller;
    }

    protected static void notFound(HttpExchange exchange) throws IOException {
        Http.sendError(exchange, 404, "not found");
    }

    protected static long amount(Map<String, String> form) {
        try {
            return Long.parseLong(form.getOrDefault("amount", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("amount must be an integer number of cents");
        }
    }

    protected static String accountJson(Account a, List<Txn> txns) {
        StringBuilder sb = new StringBuilder("{\"id\":\"").append(Http.escape(a.getId()))
                .append("\",\"balanceCents\":").append(a.getBalanceCents())
                .append(",\"transactions\":[");
        boolean first = true;
        for (Txn t : txns) {
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{\"from\":\"").append(Http.escape(t.getFromAccountId()))
                    .append("\",\"to\":\"").append(Http.escape(t.getToAccountId()))
                    .append("\",\"amountCents\":").append(t.getAmountCents())
                    .append(",\"memo\":\"").append(Http.escape(t.getMemo())).append("\"}");
        }
        return sb.append("]}").toString();
    }
}
