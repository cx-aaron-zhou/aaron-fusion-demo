package com.demo.bank.handler;

import com.demo.bank.model.Account;
import com.demo.bank.model.Invite;
import com.demo.bank.model.Org;
import com.demo.bank.model.User;
import com.demo.bank.service.AccountService;
import com.demo.bank.service.AuthService;
import com.demo.bank.service.OrgService;
import com.demo.bank.store.DataStore;
import com.demo.bank.util.Http;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * Business-customer API:
 *
 * <pre>
 *   POST /api/orgs                                   name
 *   POST /api/orgs/accept-invite                     token
 *   POST /api/orgs/{id}/invites                      role
 *   POST /api/orgs/{id}/members/{userId}/role        role
 *   GET  /api/orgs/{id}/accounts
 *   POST /api/orgs/{id}/accounts/link                accountId
 *   POST /api/orgs/{id}/accounts/{acc}/transfer      to, amount, memo
 *   GET  /api/orgs/{id}/export?account={acc}
 * </pre>
 */
public class OrgHandler extends BaseHandler {
    private final OrgService orgs;
    private final AccountService accounts;
    private final DataStore store;

    public OrgHandler(AuthService auth, OrgService orgs, AccountService accounts, DataStore store) {
        super(auth);
        this.orgs = orgs;
        this.accounts = accounts;
        this.store = store;
    }

    @Override
    protected void route(HttpExchange ex, String method, String[] seg) throws IOException {
        boolean post = method.equals("POST");
        boolean get = method.equals("GET");
        if (post && seg.length == 3) {
            create(ex);
        } else if (post && seg.length == 4 && seg[3].equals("accept-invite")) {
            acceptInvite(ex);
        } else if (post && seg.length == 5 && seg[4].equals("invites")) {
            invite(ex, seg[3]);
        } else if (get && seg.length == 5 && seg[4].equals("accounts")) {
            listAccounts(ex, seg[3]);
        } else if (get && seg.length == 5 && seg[4].equals("export")) {
            export(ex, seg[3]);
        } else if (post && seg.length == 6 && seg[4].equals("accounts") && seg[5].equals("link")) {
            linkAccount(ex, seg[3]);
        } else if (post && seg.length == 7 && seg[4].equals("members") && seg[6].equals("role")) {
            setRole(ex, seg[3], seg[5]);
        } else if (post && seg.length == 7 && seg[4].equals("accounts") && seg[6].equals("transfer")) {
            transfer(ex, seg[3], seg[5]);
        } else {
            notFound(ex);
        }
    }

    private void create(HttpExchange ex) throws IOException {
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Org org = orgs.create(caller, Http.parseForm(ex).get("name"));
        Http.sendJson(ex, 201, "{\"id\":\"" + Http.escape(org.getId()) + "\"}");
    }

    private void acceptInvite(HttpExchange ex) throws IOException {
        User caller = caller(ex);
        if (caller == null) {
            return;
        }
        Org org = orgs.accept(Http.parseForm(ex).get("token"), caller);
        Http.sendJson(ex, 200, "{\"org\":\"" + Http.escape(org.getId())
                + "\",\"role\":\"" + Http.escape(orgs.roleOf(org, caller)) + "\"}");
    }

    private void invite(HttpExchange ex, String orgId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null) {
            return;
        }
        Invite invite = orgs.invite(org, caller, Http.parseForm(ex).get("role"));
        Http.sendJson(ex, 201, "{\"token\":\"" + Http.escape(invite.getToken())
                + "\",\"role\":\"" + Http.escape(invite.getRole()) + "\"}");
    }

    private void setRole(HttpExchange ex, String orgId, String targetUserId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null) {
            return;
        }
        orgs.setRole(org, caller, targetUserId, Http.parseForm(ex).get("role"));
        Http.sendJson(ex, 200, "{\"ok\":true}");
    }

    private void listAccounts(HttpExchange ex, String orgId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null || !requireMember(ex, org, caller)) {
            return;
        }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String accountId : org.getAccountIds()) {
            Account a = accounts.getAccount(accountId);
            if (a == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            first = false;
            sb.append("{\"id\":\"").append(Http.escape(a.getId()))
                    .append("\",\"balanceCents\":").append(a.getBalanceCents()).append("}");
        }
        Http.sendJson(ex, 200, sb.append("]").toString());
    }

    private void linkAccount(HttpExchange ex, String orgId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null) {
            return;
        }
        orgs.linkAccount(org, caller, Http.parseForm(ex).get("accountId"));
        Http.sendJson(ex, 200, "{\"ok\":true}");
    }

    private void transfer(HttpExchange ex, String orgId, String fromAccountId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null) {
            return;
        }
        if (!orgs.isAdmin(org, caller)) {
            Http.sendError(ex, 403, "admin only");
            return;
        }
        if (!org.getAccountIds().contains(fromAccountId)) {
            Http.sendError(ex, 404, "account not in organisation");
            return;
        }
        Map<String, String> form = Http.parseForm(ex);
        long amount = amount(form);
        if (amount <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        if (!accounts.transfer(fromAccountId, form.get("to"), amount, form.get("memo"))) {
            Http.sendError(ex, 400, "transfer failed");
            return;
        }
        Http.sendJson(ex, 200, "{\"ok\":true,\"balanceCents\":"
                + accounts.getAccount(fromAccountId).getBalanceCents() + "}");
    }

    private void export(HttpExchange ex, String orgId) throws IOException {
        User caller = caller(ex);
        Org org = caller == null ? null : lookup(ex, orgId);
        if (org == null || !requireMember(ex, org, caller)) {
            return;
        }
        Account account = accounts.getAccount(Http.parseQuery(ex).get("account"));
        if (account == null) {
            Http.sendError(ex, 404, "account not found");
            return;
        }
        Http.sendJson(ex, 200, accountJson(account, store.txnsFor(account.getId())));
    }

    private Org lookup(HttpExchange ex, String orgId) throws IOException {
        Org org = orgs.get(orgId);
        if (org == null) {
            Http.sendError(ex, 404, "organisation not found");
        }
        return org;
    }

    private boolean requireMember(HttpExchange ex, Org org, User caller) throws IOException {
        if (orgs.roleOf(org, caller) == null) {
            Http.sendError(ex, 403, "not a member");
            return false;
        }
        return true;
    }
}
