package com.demo.bank.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Random identifier helper shared by the services.
 */
public final class Ids {

    private static final SecureRandom RNG = new SecureRandom();

    private Ids() {
    }

    public static String random(int bytes) {
        byte[] buf = new byte[bytes];
        RNG.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }
}
