package com.demo.bank.handler;

import com.demo.bank.model.User;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.PromoService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/** POST /api/promo/redeem  code, account */
public class PromoHandler extends BaseHandler {
    private final PromoService promos;

    public PromoHandler(AuthService auth, PromoService promos) {
        super(auth);
        this.promos = promos;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!(method.equals("POST") && seg.length == 4 && seg[3].equals("redeem"))) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        long balance = promos.redeem(caller, form.get("code"), form.get("account"));
        Http.sendJson(ex, 200, "{\"ok\":true,\"balanceCents\":" + balance + "}");
    }
}
