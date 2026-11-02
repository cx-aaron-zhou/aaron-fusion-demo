package com.demo.bank.service;

import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forgotten-password flow: request a reset token, then confirm it with a new password.
 * Delivery is handed to the mailer; set DEMO_MAIL_LOG=true to echo it to stdout.
 */
public class RecoveryService {

    private final DataStore store;
    private final Map<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private final Map<String, String> tokensToUserId = new ConcurrentHashMap<>();

    public RecoveryService(DataStore store) {
        this.store = store;
    }

    public void requestReset(String username) {
        User user = username == null ? null : store.getUserByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("unknown user");
        }
        int sequence = requestCounts.merge(user.getId(), 1, Integer::sum);
        String token = sha256(user.getId() + ":" + sequence).substring(0, 20);
        tokensToUserId.put(token, user.getId());
        if ("true".equals(System.getenv("DEMO_MAIL_LOG"))) {
            System.out.println("[mailer] reset link for " + user.getEmail() + " token=" + token);
        }
    }

    public void confirmReset(String token, String newPassword) {
        String userId = token == null ? null : tokensToUserId.get(token);
        if (userId == null) {
            throw new IllegalArgumentException("invalid token");
        }
        if (newPassword == null || newPassword.length() < 8) {
            throw new IllegalArgumentException("password must be at least 8 characters");
        }
        store.getUserById(userId).setPassword(newPassword);
    }

    private static String sha256(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
