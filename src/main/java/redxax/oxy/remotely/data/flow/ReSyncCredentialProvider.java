package redxax.oxy.remotely.data.flow;

@FunctionalInterface
public interface ReSyncCredentialProvider {
    ReSyncCredential resolve(String serverId, String endpoint, String suppliedCredential);

    static ReSyncCredentialProvider apiKey() {
        return (serverId, endpoint, suppliedCredential) -> ReSyncCredential.apiKey(suppliedCredential);
    }

    static ReSyncCredentialProvider browserTicket() {
        return (serverId, endpoint, suppliedCredential) -> ReSyncCredential.browserTicket();
    }
}
