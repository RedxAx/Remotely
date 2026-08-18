package redxax.oxy.remotely.data.flow;

import redxax.oxy.remotely.collaboration.CollaborationService;

public interface ReSyncIdentityProvider {
    String clientId(String serverId);

    CollaborationService.Identity collaborationIdentity(String fallbackClientId);

    default String clientVersion() {
        return ReSyncClientVersion.CURRENT;
    }
}
