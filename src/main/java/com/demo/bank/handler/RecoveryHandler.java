package com.demo.bank.handler;

import com.demo.bank.service.AuthService;
import com.demo.bank.service.RecoveryService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * <pre>
 *   POST /api/recovery/request     username
 *   POST /api/recovery/confirm     token, password
 * </pre>
 */
public class RecoveryHandler extends BaseHandler {
    private final RecoveryService recovery;

    public RecoveryHandler(AuthService auth, RecoveryService recovery) {
        super(auth);
        this.recovery = recovery;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!method.equals("POST") || seg.length != 4) {
            notFound(ex);
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        switch (seg[3]) {
            case "request" -> {
                recovery.requestReset(form.get("username"));
                Http.sendJson(ex, 202, "{\"ok\":true}");
            }
            case "confirm" -> {
                recovery.confirmReset(form.get("token"), form.get("password"));
                Http.sendJson(ex, 200, "{\"ok\":true}");
            }
            default -> notFound(ex);
        }
    }
}
