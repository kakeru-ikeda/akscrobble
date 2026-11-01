package com.akscrobble.core.update;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MiniJson {
    private final String input;
    private int index;

    private MiniJson(String input) {
        if (input == null) throw new IllegalArgumentException("json is null");
        this.input = input;
    }

    public static Object parse(String json) {
        MiniJson parser = new MiniJson(json);
        parser.space();
        Object value = parser.value(0);
        parser.space();
        if (parser.index != json.length()) throw parser.error("trailing data");
        return value;
    }

    private Object value(int depth) {
        if (depth > 32) throw error("nesting too deep");
        if (index >= input.length()) throw error("expected value");
        char c = input.charAt(index);
        if (c == '{') return object(depth + 1);
        if (c == '[') return array(depth + 1);
        if (c == '"') return string();
        if (c == 't') return literal("true", Boolean.TRUE);
        if (c == 'f') return literal("false", Boolean.FALSE);
        if (c == 'n') return literal("null", null);
        if (c == '-' || (c >= '0' && c <= '9')) return number();
        throw error("unexpected character");
    }

    private Map<String, Object> object(int depth) {
        index++;
        LinkedHashMap<String, Object> result = new LinkedHashMap<String, Object>();
        space();
        if (take('}')) return result;
        while (true) {
            if (index >= input.length() || input.charAt(index) != '"') throw error("expected key");
            String key = string();
            space();
            require(':');
            space();
            result.put(key, value(depth));
            space();
            if (take('}')) return result;
            require(',');
            space();
        }
    }

    private List<Object> array(int depth) {
        index++;
        ArrayList<Object> result = new ArrayList<Object>();
        space();
        if (take(']')) return result;
        while (true) {
            result.add(value(depth));
            space();
            if (take(']')) return result;
            require(',');
            space();
        }
    }

    private String string() {
        require('"');
        StringBuilder out = new StringBuilder();
        while (index < input.length()) {
            char c = input.charAt(index++);
            if (c == '"') return out.toString();
            if (c < 0x20) throw error("control character in string");
            if (c != '\\') { out.append(c); continue; }
            if (index >= input.length()) throw error("unfinished escape");
            char e = input.charAt(index++);
            switch (e) {
                case '"': out.append('"'); break;
                case '\\': out.append('\\'); break;
                case '/': out.append('/'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'n': out.append('\n'); break;
                case 'r': out.append('\r'); break;
                case 't': out.append('\t'); break;
                case 'u':
                    if (index + 4 > input.length()) throw error("short unicode escape");
                    int code = 0;
                    for (int i = 0; i < 4; i++) {
                        int digit = Character.digit(input.charAt(index++), 16);
                        if (digit < 0) throw error("invalid unicode escape");
                        code = (code << 4) | digit;
                    }
                    out.append((char) code);
                    break;
                default: throw error("invalid escape");
            }
        }
        throw error("unterminated string");
    }

    private Object number() {
        int start = index;
        if (take('-') && index == input.length()) throw error("invalid number");
        if (take('0')) {
            if (index < input.length() && Character.isDigit(input.charAt(index))) throw error("leading zero");
        } else {
            digits();
        }
        boolean decimal = false;
        if (take('.')) { decimal = true; digits(); }
        if (take('e') || take('E')) {
            decimal = true;
            if (!take('+')) take('-');
            digits();
        }
        String token = input.substring(start, index);
        try {
            if (decimal) return Double.valueOf(token);
            return Long.valueOf(token);
        } catch (NumberFormatException ex) {
            throw error("number out of range");
        }
    }

    private void digits() {
        int start = index;
        while (index < input.length() && input.charAt(index) >= '0' && input.charAt(index) <= '9') index++;
        if (start == index) throw error("expected digit");
    }

    private Object literal(String text, Object value) {
        if (!input.startsWith(text, index)) throw error("invalid literal");
        index += text.length();
        return value;
    }

    private void space() {
        while (index < input.length()) {
            char c = input.charAt(index);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n') index++;
            else break;
        }
    }

    private boolean take(char c) {
        if (index < input.length() && input.charAt(index) == c) { index++; return true; }
        return false;
    }

    private void require(char c) { if (!take(c)) throw error("expected " + c); }
    private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at " + index); }
    private MiniJson() { throw new AssertionError(); }
}
