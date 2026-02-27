package com.js.salesman.utils;

import java.util.regex.Pattern;

public class InputValidator {
    // Prevent instantiation
    private InputValidator() {}

    public enum InputType {
        TEXT,
        EMAIL,
        PHONE,
        PASSWORD
    }

    // -------------------------
    // Enterprise Regex Patterns
    // -------------------------

    // RFC 5322 simplified but robust email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
            Pattern.CASE_INSENSITIVE
    );

    // E.164 international format (supports + and 8-15 digits)
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "^\\+?[1-9]\\d{7,14}$"
    );

    // Password:
    // Minimum 8 chars
    // At least 1 uppercase
    // At least 1 lowercase
    // At least 1 digit
    // At least 1 special char
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&^#()_+=-]).{8,}$"
    );

    // ---------------------------------
    // Public Validation Entry Point
    // ---------------------------------

    public static boolean validate(
            InputType type,
            String input,
            Integer minLength,
            Integer maxLength
    ) {

        //Default null validation (enterprise standard)
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String value = input.trim();

        //Length validation (optional)
        if (minLength != null && value.length() < minLength) {
            return false;
        }

        if (maxLength != null && value.length() > maxLength) {
            return false;
        }

        //Type-based validation
        switch (type) {

            case EMAIL:
                return EMAIL_PATTERN.matcher(value).matches();

            case PHONE:
                return PHONE_PATTERN.matcher(value).matches();

            case PASSWORD:
                return PASSWORD_PATTERN.matcher(value).matches();

            case TEXT:
            default:
                // TEXT only checks null + length
                return true;
        }
    }

    // -------------------------
    // Convenience Overloads
    // -------------------------

    public static boolean validate(InputType type, String input) {
        return validate(type, input, null, null);
    }

    public static boolean validate(InputType type, String input, int minLength) {
        return validate(type, input, minLength, null);
    }
}
