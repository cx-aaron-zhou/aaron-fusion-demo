package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.FxService;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/** POST /api/fx/transfer  from, to, amount (cents of the source currency). */
public class FxHandler extends BaseHandler {
    private final FxService fx;
    private final AccountService accounts;

    public FxHandler(AuthService auth, FxService fx, AccountService accounts) {
        super(auth);
        this.fx = fx;
        this.accounts = accounts;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (!(method.equals("POST") && seg.length == 4 && seg[3].equals("transfer"))) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        Account from = accounts.getAccount(form.get("from"));
        if (from == null || !from.getOwnerUserId().equals(caller.getId())) {
            Http.sendError(ex, 403, "not your account");
            return;
        }
        long credited = fx.transfer(from.getId(), form.get("to"), amount(form));
        Http.sendJson(ex, 200, "{\"ok\":true,\"creditedCents\":" + credited
                + ",\"balanceCents\":" + from.getBalanceCents() + "}");
    }
}
