package com.demo.bank.service;

import com.demo.bank.model.User;
import com.demo.bank.store.DataStore;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles registration, login, and bearer-token session lookup.
 */
public class AuthService {

    private static final SecureRandom RNG = new SecureRandom();

    private final DataStore store;
    private final Map<String, String> tokensToUserId = new ConcurrentHashMap<>();

    public AuthService(DataStore store) {
        this.store = store;
    }

    /** Registers a new customer and returns the created user. */
    public User register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalArgumentException("username and password are required");
        }
        if (store.getUserByUsername(username) != null) {
            throw new IllegalArgumentException("username already taken");
        }
        String id = "u-" + nextToken(6);
        User user = new User(id, username, password, "customer");
        store.putUser(user);
        return user;
    }

    /** Validates credentials and issues a session token, or returns null on failure. */
    public String login(String username, String password) {
        User user = store.getUserByUsername(username);
        if (user == null) {
            return null;
        }
        if (!user.getPassword().equals(password)) {
            return null;
        }
        String token = nextToken(24);
        tokensToUserId.put(token, user.getId());
        return token;
    }

    /** Resolves a bearer token to the authenticated user, or null if unknown. */
    public User resolve(String token) {
        if (token == null) {
            return null;
        }
        String userId = tokensToUserId.get(token);
        if (userId == null) {
            return null;
        }
        return store.getUserById(userId);
    }

    private static String nextToken(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
