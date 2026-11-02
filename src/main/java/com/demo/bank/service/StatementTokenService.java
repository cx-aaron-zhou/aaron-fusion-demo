package com.demo.bank.service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Opaque, expiring tokens that let a customer share a read-only statement link without
 * logging in. The token carries the account id and expiry, encrypted so it cannot be read
 * or edited by the recipient.
 */
public class StatementTokenService {

    private static final long TTL_MILLIS = 3_600_000L;
    private static final int PLAIN_LENGTH = 16 + 1 + 13;

    private final SecretKeySpec key;
    private final IvParameterSpec iv;

    public StatementTokenService() {
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        this.key = new SecretKeySpec(secret, "AES");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest("statement-token-v1".getBytes(StandardCharsets.UTF_8));
            this.iv = new IvParameterSpec(Arrays.copyOf(digest, 16));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String issue(String accountId) {
        String plain = String.format("%-16s|%013d", accountId, System.currentTimeMillis() + TTL_MILLIS);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(crypt(Cipher.ENCRYPT_MODE, plain.getBytes(StandardCharsets.UTF_8)));
    }

    /** @return the account id the token was issued for. */
    public String open(String token) {
        byte[] raw = Base64.getUrlDecoder().decode(token);
        String plain = new String(crypt(Cipher.DECRYPT_MODE, raw), StandardCharsets.UTF_8);
        if (plain.length() != PLAIN_LENGTH || plain.charAt(16) != '|') {
            throw new IllegalArgumentException("invalid token");
        }
        long expires = Long.parseLong(plain.substring(17));
        if (expires < System.currentTimeMillis()) {
            throw new IllegalArgumentException("token expired");
        }
        return plain.substring(0, 16).trim();
    }

    private byte[] crypt(int mode, byte[] input) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding");
            cipher.init(mode, key, iv);
            return cipher.doFinal(input);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
