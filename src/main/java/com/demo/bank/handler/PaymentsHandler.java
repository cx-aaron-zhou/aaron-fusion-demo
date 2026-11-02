package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.PaymentLinkService;
import com.demo.bank.service.StatementTokenService;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * <pre>
 *   POST /api/payments/links               account, amount, ttl (seconds)    (Bearer token)
 *   POST /api/payments/links/pay           link, from                        (Bearer token)
 *   POST /api/payments/statements          account                           (Bearer token)
 *   GET  /api/payments/statements/{token}  shareable read-only statement
 * </pre>
 */
public class PaymentsHandler extends BaseHandler {
    private final PaymentLinkService links;
    private final StatementTokenService statements;
    private final AccountService accounts;
    private final DataStore store;

    public PaymentsHandler(AuthService auth, PaymentLinkService links, StatementTokenService statements,
                           AccountService accounts, DataStore store) {
        super(auth);
        this.links = links;
        this.statements = statements;
        this.accounts = accounts;
        this.store = store;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        if (method.equals("GET") && seg.length == 5 && seg[3].equals("statements")) {
            Account account = accounts.getAccount(statements.open(seg[4]));
            if (account == null) {
                Http.sendError(ex, 404, "account not found");
                return;
            }
            Http.sendJson(ex, 200, accountJson(account, store.txnsFor(account.getId())));
            return;
        }
        if (!method.equals("POST")) {
            notFound(ex);
            return;
        }
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        if (seg.length == 4 && seg[3].equals("links")) {
            long ttl = Long.parseLong(form.getOrDefault("ttl", "3600"));
            String link = links.create(caller, form.get("account"), amount(form), ttl);
            Http.sendJson(ex, 201, "{\"link\":\"" + Http.escape(link) + "\"}");
        } else if (seg.length == 5 && seg[3].equals("links") && seg[4].equals("pay")) {
            long paid = links.pay(caller, form.get("from"), form.get("link"));
            Http.sendJson(ex, 200, "{\"ok\":true,\"paidCents\":" + paid + "}");
        } else if (seg.length == 4 && seg[3].equals("statements")) {
            Account account = accounts.getAccount(form.get("account"));
            if (account == null || !account.getOwnerUserId().equals(caller.getId())) {
                Http.sendError(ex, 403, "not your account");
                return;
            }
            Http.sendJson(ex, 201, "{\"token\":\"" + Http.escape(statements.issue(account.getId())) + "\"}");
        } else {
            notFound(ex);
        }
    }
}
