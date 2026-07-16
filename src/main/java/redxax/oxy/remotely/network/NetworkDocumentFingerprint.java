package redxax.oxy.remotely.network;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class NetworkDocumentFingerprint {
    private NetworkDocumentFingerprint() {
    }

    public static String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest((content == null ? "" : content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
