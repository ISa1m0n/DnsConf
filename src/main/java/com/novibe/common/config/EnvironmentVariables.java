package com.novibe.common.config;

import com.novibe.common.exception.UserInputException;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;

public class EnvironmentVariables {

    public static final String DNS = extractMandatoryVariable("DNS");

    public static final String CLIENT_ID = extractMandatoryVariable("CLIENT_ID");

    public static final String AUTH_SECRET = extractMandatoryVariable("AUTH_SECRET");

    public static final String BLOCK = System.getenv("BLOCK");

    public static final String REDIRECT = System.getenv("REDIRECT");

    public static final String EXCLUDE_REDIRECT = System.getenv("EXCLUDE_REDIRECT");

    public static final String DONOR_DNS = System.getenv("DONOR_DNS");

    public static final boolean COMPRESS = ofNullable(System.getenv("COMPRESS"))
            .map(Boolean::parseBoolean)
            .orElse(false);

    // Форк: страховки от случайного стирания профиля. По умолчанию выключены,
    // поведение апстрима сохраняется, если выставить их в 1.
    public static final boolean WIPE = isEnabled("WIPE");

    public static final boolean ALLOW_SHRINK = isEnabled("ALLOW_SHRINK");

    private static boolean isEnabled(String key) {
        String env = System.getenv(key);
        return !isNull(env) && (env.equals("1") || env.equalsIgnoreCase("true"));
    }

    private static String extractMandatoryVariable(String key) {
        String env = System.getenv(key);
        if (isNull(env) || env.isBlank()) {
            throw UserInputException.noStackTrace("Mandatory environment variable is not provided: " + key);
        }
        return env;
    }

}
