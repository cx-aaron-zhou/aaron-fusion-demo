package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AuthService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Batch endpoint used by the mobile app to save round trips.
 *
 * <pre>
 *   POST /api/bulk     ops=balance:{acc};transfer:{from}:{to}:{cents};...   (Bearer token, max 20 ops)
 * </pre>
 */
public class BulkHandler extends BaseHandler {
    private static final int MAX_OPS = 20;
    private final AccountService accounts;

    public BulkHandler(AuthService auth, AccountService accounts) {
        super(auth);
        this.accounts = accounts;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!method.equals("POST") || seg.length != 3) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        String[] ops = form.getOrDefault("ops", "").split(";");
        if (ops.length > MAX_OPS) {
            throw new IllegalArgumentException("too many operations");
        }
        List<String> results = new ArrayList<>();
        for (String op : ops) {
            String[] p = op.split(":");
            switch (p[0]) {
                case "balance" -> {
                    Account a = accounts.getAccount(p[1]);
                    results.add(a == null ? "not found" : String.valueOf(a.getBalanceCents()));
                }
                case "transfer" -> results.add(
                        accounts.transfer(p[1], p[2], Long.parseLong(p[3]), "bulk") ? "ok" : "failed");
                default -> results.add("unknown op");
            }
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < results.size(); i++) {
            sb.append(i == 0 ? "" : ",").append('"').append(Http.escape(results.get(i))).append('"');
        }
        Http.sendJson(ex, 200, sb.append("]").toString());
    }
}
