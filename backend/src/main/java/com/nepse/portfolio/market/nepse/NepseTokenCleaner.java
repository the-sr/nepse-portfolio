package com.nepse.portfolio.market.nepse;

import java.util.Arrays;

/**
 * NEPSE returns access tokens with a handful of junk characters injected at
 * positions derived from the salt values in the authentication response.
 * The token is usable only after those characters are removed.
 */
public final class NepseTokenCleaner {

    private NepseTokenCleaner() {
    }

    /**
     * @param token   raw token returned by {@code /api/authenticate/prove}
     * @param indexes 0-based positions of the junk characters, strictly increasing
     * @return token with the characters at {@code indexes} removed
     */
    public static String clean(String token, int[] indexes) {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("Token is empty");
        }
        int[] sorted = indexes.clone();
        Arrays.sort(sorted);

        StringBuilder out = new StringBuilder(token.length() - sorted.length);
        int previous = 0;
        for (int index : sorted) {
            if (index < previous || index >= token.length()) {
                throw new IllegalArgumentException(
                        "Token index " + index + " is out of range for a token of length " + token.length());
            }
            out.append(token, previous, index);
            previous = index + 1;
        }
        out.append(token, previous, token.length());
        return out.toString();
    }
}
