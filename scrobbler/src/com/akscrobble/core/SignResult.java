package com.akscrobble.core;

import com.akscrobble.core.update.MiniJson;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class SignResult {
    public enum Kind { OK, RETRYABLE, UNAVAILABLE, INVALID_PARAMS, REJECTED }

    public final Kind kind;
    public final int httpStatus;
    public final String apiKey;
    public final String apiSig;
    public final String errorCode;
    public final String message;
    public final List<String> invalidParams;

    private SignResult(Kind kind, int httpStatus, String apiKey, String apiSig,
            String errorCode, String message, List<String> invalidParams) {
        this.kind = kind;
        this.httpStatus = httpStatus;
        this.apiKey = apiKey;
        this.apiSig = apiSig;
        this.errorCode = errorCode;
        this.message = message;
        this.invalidParams = Collections.unmodifiableList(new ArrayList<String>(invalidParams));
    }

    public static SignResult parse(int httpStatus, String body) {
        if (httpStatus == 200) {
            try {
                Object parsed = MiniJson.parse(body);
                if (parsed instanceof Map) {
                    Map<?, ?> object = (Map<?, ?>) parsed;
                    Object key = object.get("api_key");
                    Object sig = object.get("api_sig");
                    if (key instanceof String && ((String) key).length() > 0
                            && sig instanceof String && ((String) sig).length() > 0) {
                        return result(Kind.OK, httpStatus, (String) key, (String) sig, null, null,
                                Collections.<String>emptyList());
                    }
                }
            } catch (RuntimeException ignored) {
                // Treat malformed or unexpected response bodies as a retryable service response.
            }
            return result(Kind.RETRYABLE, httpStatus, null, null, "malformed_response", null,
                    Collections.<String>emptyList());
        }

        String code = null;
        String message = null;
        List<String> invalid = new ArrayList<String>();
        try {
            Object parsed = MiniJson.parse(body);
            if (parsed instanceof Map) {
                Object errorValue = ((Map<?, ?>) parsed).get("error");
                if (errorValue instanceof Map) {
                    Map<?, ?> error = (Map<?, ?>) errorValue;
                    Object codeValue = error.get("code");
                    Object messageValue = error.get("message");
                    if (codeValue instanceof String && !((String) codeValue).isEmpty()) code = (String) codeValue;
                    if (messageValue instanceof String) message = (String) messageValue;
                    Object detailsValue = error.get("details");
                    if (detailsValue instanceof List) {
                        for (Object item : (List<?>) detailsValue) {
                            if (item instanceof Map) {
                                Object param = ((Map<?, ?>) item).get("param");
                                if (param instanceof String) invalid.add((String) param);
                            }
                        }
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // HTTP status remains useful if the response is not JSON.
        }

        Kind kind = classify(code, httpStatus);
        String effectiveCode = code == null ? "http_" + httpStatus : code;
        return result(kind, httpStatus, null, null, effectiveCode, message, invalid);
    }

    public static SignResult transportError(String message) {
        return result(Kind.RETRYABLE, 0, null, null, "transport", message,
                Collections.<String>emptyList());
    }

    public static SignResult invalidLocally(List<String> params) {
        List<String> safeParams = params == null ? Collections.<String>emptyList() : params;
        return result(Kind.INVALID_PARAMS, 0, null, null, "invalid_params", null, safeParams);
    }

    public boolean isOk() { return kind == Kind.OK; }

    public List<Integer> invalidIndices() {
        TreeSet<Integer> indices = new TreeSet<Integer>();
        for (String param : invalidParams) {
            if (param == null) continue;
            int open = param.indexOf('[');
            int close = param.indexOf(']', open + 1);
            if (open <= 0 || close != param.length() - 1 || close - open < 2
                    || close - open > 3 || !letters(param, 0, open) || !digits(param, open + 1, close)) continue;
            try { indices.add(Integer.valueOf(param.substring(open + 1, close))); }
            catch (NumberFormatException ignored) { }
        }
        return Collections.unmodifiableList(new ArrayList<Integer>(indices));
    }

    private static boolean letters(String value, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = value.charAt(i);
            if (!((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))) return false;
        }
        return true;
    }

    private static boolean digits(String value, int start, int end) {
        for (int i = start; i < end; i++) if (value.charAt(i) < '0' || value.charAt(i) > '9') return false;
        return true;
    }

    private static Kind classify(String code, int status) {
        if ("app_disabled".equals(code) || "method_forbidden".equals(code) || "unknown_app".equals(code))
            return Kind.UNAVAILABLE;
        if ("invalid_params".equals(code)) return Kind.INVALID_PARAMS;
        if ("rate_limited".equals(code) || "app_misconfigured".equals(code) || "internal".equals(code))
            return Kind.RETRYABLE;
        if ("invalid_request".equals(code) || "method_not_allowed".equals(code)
                || "payload_too_large".equals(code) || "unsupported_media_type".equals(code))
            return Kind.REJECTED;
        if (status == 429 || status >= 500) return Kind.RETRYABLE;
        if (status == 403 || status == 404) return Kind.RETRYABLE;
        if (status >= 400 && status < 500) return Kind.REJECTED;
        return Kind.RETRYABLE;
    }

    private static SignResult result(Kind kind, int status, String key, String sig,
            String code, String message, List<String> invalid) {
        return new SignResult(kind, status, key, sig, code, message, invalid);
    }

    @Override public String toString() {
        return "SignResult{kind=" + kind + ", httpStatus=" + httpStatus + ", errorCode=" + errorCode + "}";
    }
}
