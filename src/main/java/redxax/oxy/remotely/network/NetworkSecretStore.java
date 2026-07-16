package redxax.oxy.remotely.network;

import restudio.rebase.util.CredentialsManager;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

public class NetworkSecretStore {
    private static final String FORWARDING_SERVICE = "remotely_network_forwarding";
    private static final String ENROLLMENT_SERVICE = "remotely_network_enrollment";
    private static final String RUNTIME_CREDENTIAL_SERVICE = "remotely_network_runtime_credential";
    private static final SecureRandom RANDOM = new SecureRandom();

    public Secret createForwardingSecret() {
        String reference = UUID.randomUUID().toString();
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        CredentialsManager.setPassword(FORWARDING_SERVICE, reference, encoded);
        return new Secret(reference, encoded);
    }

    public String resolveForwardingSecret(String reference) {
        if (reference == null || reference.isBlank()) {
            return "";
        }
        String value = CredentialsManager.getPassword(FORWARDING_SERVICE, reference.trim());
        return value == null ? "" : value;
    }

    public Secret importForwardingSecret(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Forwarding secret is required");
        }
        String reference = UUID.randomUUID().toString();
        CredentialsManager.setPassword(FORWARDING_SERVICE, reference, normalized);
        return new Secret(reference, normalized);
    }

    public void deleteForwardingSecret(String reference) {
        if (reference != null && !reference.isBlank()) {
            CredentialsManager.deletePassword(FORWARDING_SERVICE, reference.trim());
        }
    }

    public String getOrCreateEnrollmentToken(String networkId, String nodeId) {
        String account = enrollmentAccount(networkId, nodeId);
        String existing = CredentialsManager.getPassword(ENROLLMENT_SERVICE, account);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }
        byte[] value = new byte[32];
        RANDOM.nextBytes(value);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(value);
        CredentialsManager.setPassword(ENROLLMENT_SERVICE, account, encoded);
        return encoded;
    }

    public String resolveEnrollmentToken(String networkId, String nodeId) {
        String value = CredentialsManager.getPassword(ENROLLMENT_SERVICE, enrollmentAccount(networkId, nodeId));
        return value == null ? "" : value;
    }

    public void deleteEnrollmentToken(String networkId, String nodeId) {
        CredentialsManager.deletePassword(ENROLLMENT_SERVICE, enrollmentAccount(networkId, nodeId));
    }

    public void deleteEnrollmentTokens(NetworkDefinition network) {
        if (network == null) {
            return;
        }
        network.members().stream().filter(member -> !member.isProxy()).forEach(member -> deleteEnrollmentToken(network.networkId(), member.nodeId()));
        deleteEnrollmentToken(network.networkId(), NetworkRuntimeIdentity.operatorNodeId(network.networkId()));
    }

    public String resolveRuntimeCredential(String networkId, String nodeId) {
        String value = CredentialsManager.getPassword(RUNTIME_CREDENTIAL_SERVICE, enrollmentAccount(networkId, nodeId));
        return value == null ? "" : value;
    }

    public void saveRuntimeCredential(String networkId, String nodeId, String credential) {
        String normalized = credential == null ? "" : credential.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Network Runtime Credential Is Required");
        }
        CredentialsManager.setPassword(RUNTIME_CREDENTIAL_SERVICE, enrollmentAccount(networkId, nodeId), normalized);
    }

    public void deleteRuntimeCredential(String networkId, String nodeId) {
        CredentialsManager.deletePassword(RUNTIME_CREDENTIAL_SERVICE, enrollmentAccount(networkId, nodeId));
    }

    private String enrollmentAccount(String networkId, String nodeId) {
        String network = networkId == null ? "" : networkId.trim();
        String node = nodeId == null ? "" : nodeId.trim();
        if (network.isBlank() || node.isBlank()) {
            throw new IllegalArgumentException("Network ID And Node ID Are Required");
        }
        return network + ":" + node;
    }

    public record Secret(String reference, String value) {
    }
}
