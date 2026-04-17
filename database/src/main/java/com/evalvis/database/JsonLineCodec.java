package com.evalvis.database;

import java.util.Optional;

final class JsonLineCodec {
    private static final String PREFIX = "{\"key\":\"";
    private static final String MIDDLE = "\",\"value\":\"";
    private static final String SUFFIX = "\"}";

    private JsonLineCodec() {
    }

    static String encode(String key, String value) {
        return PREFIX + escape(key) + MIDDLE + escape(value) + SUFFIX;
    }

    static Optional<JsonLineRecord> decode(String line) {
        if (line == null || !line.startsWith(PREFIX) || !line.endsWith(SUFFIX)) {
            return Optional.empty();
        }
        String payload = line.substring(PREFIX.length(), line.length() - SUFFIX.length());
        int separator = findSeparator(payload);
        if (separator < 0) {
            return Optional.empty();
        }
        String key = payload.substring(0, separator);
        String value = payload.substring(separator + MIDDLE.length());
        return Optional.of(new JsonLineRecord(unescape(key), unescape(value)));
    }

    private static int findSeparator(String payload) {
        for (int index = 0; index <= payload.length() - MIDDLE.length(); index++) {
            if (payload.startsWith(MIDDLE, index) && !isEscaped(payload, index)) {
                return index;
            }
        }
        return -1;
    }

    private static boolean isEscaped(String value, int index) {
        int backslashes = 0;
        for (int current = index - 1; current >= 0 && value.charAt(current) == '\\'; current--) {
            backslashes++;
        }
        return backslashes % 2 != 0;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        boolean escaping = false;
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                result.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            result.append(current);
        }
        return result.toString();
    }
}
