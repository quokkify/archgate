package dev.quokkify.archgate.rules.archunit.testrail;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a JSON document into plain Java values: objects become an unmodifiable {@code Map<String, Object>} in source
 * order, arrays an unmodifiable {@code List<Object>}, strings {@code String}, integers that fit {@code Long},
 * other numbers {@code BigDecimal}, booleans {@code Boolean} and {@code null} Java {@code null}.
 */
final class Json {

    private static final int MAX_DEPTH = 256;

    private final String text;
    private int position;
    private int depth;

    private Json(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        Objects.requireNonNull(text, "text");
        Json reader = new Json(text);
        reader.skipWhitespace();
        Object value = reader.value();
        reader.skipWhitespace();
        if (reader.position < text.length()) {
            throw reader.malformed("unexpected content after the value");
        }
        return value;
    }

    private Object value() {
        if (position >= text.length()) {
            throw malformed("unexpected end of input");
        }
        char next = text.charAt(position);
        return switch (next) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (next == '-' || isDigit(next)) {
                    yield number();
                }
                throw malformed("unexpected character '" + next + "'");
            }
        };
    }

    private Map<String, Object> object() {
        enter();
        position++;
        Map<String, Object> members = new LinkedHashMap<>();
        skipWhitespace();
        if (consume('}')) {
            return leave(Collections.unmodifiableMap(members));
        }
        while (true) {
            skipWhitespace();
            if (position >= text.length() || text.charAt(position) != '"') {
                throw malformed("expected a member name");
            }
            String name = string();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            members.put(name, value());
            skipWhitespace();
            if (consume('}')) {
                return leave(Collections.unmodifiableMap(members));
            }
            expect(',');
        }
    }

    private List<Object> array() {
        enter();
        position++;
        List<Object> elements = new ArrayList<>();
        skipWhitespace();
        if (consume(']')) {
            return leave(Collections.unmodifiableList(elements));
        }
        while (true) {
            skipWhitespace();
            elements.add(value());
            skipWhitespace();
            if (consume(']')) {
                return leave(Collections.unmodifiableList(elements));
            }
            expect(',');
        }
    }

    private String string() {
        position++;
        StringBuilder result = new StringBuilder();
        while (position < text.length()) {
            char next = text.charAt(position++);
            if (next == '"') {
                return result.toString();
            }
            if (next < 0x20) {
                throw malformed("unescaped control character in a string");
            }
            if (next == '\\') {
                result.append(escape());
            } else {
                result.append(next);
            }
        }
        throw malformed("unterminated string");
    }

    private char escape() {
        if (position >= text.length()) {
            throw malformed("unterminated escape");
        }
        char next = text.charAt(position++);
        return switch (next) {
            case '"', '\\', '/' -> next;
            case 'b' -> '\b';
            case 'f' -> '\f';
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case 'u' -> unicode();
            default -> throw malformed("invalid escape '\\" + next + "'");
        };
    }

    private char unicode() {
        if (position + 4 > text.length()) {
            throw malformed("incomplete unicode escape");
        }
        int code = 0;
        for (int index = 0; index < 4; index++) {
            int digit = hexDigit(text.charAt(position++));
            code = code * 16 + digit;
        }
        return (char) code;
    }

    private Object number() {
        int start = position;
        consume('-');
        if (consume('0')) {
            if (position < text.length() && isDigit(text.charAt(position))) {
                throw malformed("leading zero in a number");
            }
        } else {
            digits();
        }
        boolean integral = true;
        if (consume('.')) {
            integral = false;
            digits();
        }
        if (position < text.length() && (text.charAt(position) == 'e' || text.charAt(position) == 'E')) {
            position++;
            integral = false;
            if (!consume('+')) {
                consume('-');
            }
            digits();
        }
        String literal = text.substring(start, position);
        if (integral) {
            try {
                return Long.parseLong(literal);
            } catch (NumberFormatException tooLarge) {
                return new BigDecimal(literal);
            }
        }
        return new BigDecimal(literal);
    }

    private void digits() {
        int start = position;
        while (position < text.length() && isDigit(text.charAt(position))) {
            position++;
        }
        if (position == start) {
            throw malformed("expected a digit");
        }
    }

    private Object literal(String word, Object value) {
        if (!text.startsWith(word, position)) {
            throw malformed("unexpected token");
        }
        position += word.length();
        return value;
    }

    private void enter() {
        if (++depth > MAX_DEPTH) {
            throw malformed("nesting deeper than " + MAX_DEPTH);
        }
    }

    private <T> T leave(T value) {
        depth--;
        return value;
    }

    private void expect(char expected) {
        if (!consume(expected)) {
            throw malformed("expected '" + expected + "'");
        }
    }

    private boolean consume(char expected) {
        if (position < text.length() && text.charAt(position) == expected) {
            position++;
            return true;
        }
        return false;
    }

    private void skipWhitespace() {
        while (position < text.length()) {
            char next = text.charAt(position);
            if (next != ' ' && next != '\t' && next != '\n' && next != '\r') {
                return;
            }
            position++;
        }
    }

    private int hexDigit(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        throw malformed("invalid unicode escape");
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    private IllegalArgumentException malformed(String reason) {
        return new IllegalArgumentException("Malformed JSON at offset %d: %s".formatted(position, reason));
    }
}
