package com.demo.bank.service;

import com.demo.bank.model.Account;
import com.demo.bank.model.User;
import com.demo.bank.util.Ids;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shareable payment requests. A merchant creates a link for an amount; anyone holding
 * the link can pay it from one of their own accounts. Links are tamper-evident: the
 * body is base64url and is followed by an HMAC-SHA256 signature.
 */
public class PaymentLinkService {

    private static final long MAX_TTL_SECONDS = 86_400;

    private final AccountService accounts;
    private final SecretKeySpec key;
    private final Set<String> paid = ConcurrentHashMap.newKeySet();

    public PaymentLinkService(AccountService accounts) {
        this.accounts = accounts;
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, "HmacSHA256");
    }

    public String create(User caller, String payeeAccountId, long amountCents, long ttlSeconds) {
        Account payee = accounts.getAccount(payeeAccountId);
        if (payee == null || !payee.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("not your account");
        }
        if (amountCents <= 0 || ttlSeconds <= 0 || ttlSeconds > MAX_TTL_SECONDS) {
            throw new IllegalArgumentException("amount or ttl out of range");
        }
        String invoice = Ids.random(6);
        long expires = System.currentTimeMillis() + ttlSeconds * 1000;
        String body = "payee=" + payeeAccountId + "&amount=" + amountCents
                + "&inv=" + invoice + "&exp=" + expires;
        String signature = signature(amountCents + "|" + invoice + "|" + expires);
        return b64(body.getBytes(StandardCharsets.UTF_8)) + "." + signature;
    }

    /** @return the amount paid, in cents. */
    public long pay(User caller, String fromAccountId, String link) {
        String[] parts = link == null ? new String[0] : link.split("\\.");
        if (parts.length != 2) {
            throw new IllegalArgumentException("malformed link");
        }
        Map<String, String> fields = parse(new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8));
        String payee = fields.get("payee");
        String invoice = fields.get("inv");
        if (payee == null || invoice == null || fields.get("amount") == null || fields.get("exp") == null) {
            throw new IllegalArgumentException("malformed link");
        }
        long amount = Long.parseLong(fields.get("amount"));
        long expires = Long.parseLong(fields.get("exp"));

        byte[] expected = signature(amount + "|" + invoice + "|" + expires).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, parts[1].getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("bad signature");
        }
        if (expires < System.currentTimeMillis()) {
            throw new IllegalArgumentException("link expired");
        }
        Account from = accounts.getAccount(fromAccountId);
        if (from == null || !from.getOwnerUserId().equals(caller.getId())) {
            throw new IllegalArgumentException("not your account");
        }
        if (!paid.add(invoice)) {
            throw new IllegalArgumentException("already paid");
        }
        if (!accounts.transfer(fromAccountId, payee, amount, "invoice " + invoice)) {
            paid.remove(invoice);
            throw new IllegalArgumentException("payment failed");
        }
        return amount;
    }

    private String signature(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return b64(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, String> parse(String body) {
        Map<String, String> out = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                out.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return out;
    }

    private static String b64(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
