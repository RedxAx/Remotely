package redxax.oxy.remotely.network;

import restudio.rescreen.platform.Sha256;

import java.nio.charset.StandardCharsets;

public final class NetworkDocumentFingerprint {
    private NetworkDocumentFingerprint() {
    }

    public static String sha256(String content) {
        return Sha256.hex((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
    }
}
