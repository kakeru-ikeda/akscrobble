package com.akscrobble.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class LastfmCall {
    private final Signer signer;
    private final LastfmTransport transport;
    private final String sessionKey;
    private final String sessionApiKey;
    private String cachedToken;
    private SignResult cachedSignature;

    public LastfmCall(Signer signer, LastfmTransport transport, String sessionKey,
            String sessionApiKey) {
        this.signer = signer;
        this.transport = transport;
        this.sessionKey = sessionKey == null ? "" : sessionKey;
        this.sessionApiKey = sessionApiKey == null ? "" : sessionApiKey;
    }

    public LastfmResponse getToken() {
        return execute(LastfmRequest.getToken(), false, null);
    }

    public LastfmResponse getSession(String token) {
        TreeMap<String, String> params = LastfmRequest.getSession(token);
        LastfmResponse localFailure = localFailure(params);
        if (localFailure != null) return localFailure;
        SignResult signature = null;
        if (token != null && token.equals(cachedToken) && cachedSignature != null
                && cachedSignature.isOk()) {
            signature = cachedSignature;
        } else {
            signature = sign(params);
            if (signature.isOk()) {
                cachedToken = token;
                cachedSignature = signature;
            } else {
                cachedToken = null;
                cachedSignature = null;
            }
        }
        return execute(params, false, signature);
    }

    public LastfmResponse updateNowPlaying(TrackInfo track) {
        if (sessionKey.length() == 0) return missingSession();
        return execute(LastfmRequest.nowPlaying(track, sessionKey), true, null);
    }

    public LastfmResponse scrobble(List<PendingScrobble> batch) {
        if (sessionKey.length() == 0) return missingSession();
        return execute(LastfmRequest.scrobbleBatch(batch, sessionKey), true, null);
    }

    private LastfmResponse execute(TreeMap<String, String> params, boolean allowResign,
            SignResult suppliedSignature) {
        LastfmResponse localFailure = localFailure(params);
        if (localFailure != null) return localFailure;

        SignResult signature = suppliedSignature == null ? sign(params) : suppliedSignature;
        if (!signature.isOk()) return LastfmResponse.signerFailure(signature);

        if (params.containsKey("sk") && (sessionApiKey.length() == 0
                || !sessionApiKey.equals(signature.apiKey))) {
            return LastfmResponse.sessionMismatch().withApiKey(signature.apiKey);
        }

        TreeMap<String, String> signedParams = new TreeMap<String, String>(params);
        signedParams.put("api_key", signature.apiKey);
        signedParams.put("api_sig", signature.apiSig);
        LastfmResponse response = transport.post(LastfmRequest.formBody(signedParams))
                .withApiKey(signature.apiKey);
        if (response.errorCode == 13 && allowResign) {
            return execute(params, false, null);
        }
        return response;
    }

    private static LastfmResponse localFailure(TreeMap<String, String> params) {
        List<String> invalid = new ArrayList<String>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length() == 0) invalid.add(entry.getKey());
        }
        if (!invalid.isEmpty()) {
            return LastfmResponse.signerFailure(SignResult.invalidLocally(invalid));
        }
        return null;
    }

    private SignResult sign(TreeMap<String, String> params) {
        return signer.sign(params.get("method"), Scrobsign.signableParams(params));
    }

    private static LastfmResponse missingSession() {
        return LastfmResponse.parse("<lfm status=\"failed\"><error code=\"9\">"
                + "Missing session key</error></lfm>");
    }
}
