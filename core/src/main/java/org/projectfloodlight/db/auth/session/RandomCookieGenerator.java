package org.projectfloodlight.db.auth.session;

import java.security.SecureRandom;

/**
 * returns a random cookie string from the base64 url-safe alphabet.
 * <b>Threadsafe</b> - Can be safely shared between threads.
 *
 * @author Andreas Wundsam <andreas.wundsam@bigswitch.com>
 */
public class RandomCookieGenerator {
    private final char[] cookieSource =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
                    .toCharArray();

    // SecureRandom is threadsafe in all known implementations
    // (and guaranteed in the API contract of JDK 7. Lock contention /might/ be
    // an issue, but we're not handling that many session creations
    private final SecureRandom secureRandom;

    private final int cookieLength;

    public RandomCookieGenerator() {
        this(32);
    }

    public RandomCookieGenerator(int cookieLength) {
        this.secureRandom = new SecureRandom();
        this.cookieLength = cookieLength;
    }

    public String createCookie() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < cookieLength; i++)
            builder.append(cookieSource[secureRandom.nextInt(cookieSource.length)]);
        return builder.toString();
    }

}
