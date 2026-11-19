package com.akscrobble.core;

import java.util.Map;

public interface Signer {
    SignResult sign(String method, Map<String, String> params);
}
