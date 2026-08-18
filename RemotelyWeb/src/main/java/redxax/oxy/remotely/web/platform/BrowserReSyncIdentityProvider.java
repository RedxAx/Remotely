package redxax.oxy.remotely.web.platform;

import redxax.oxy.remotely.collaboration.CollaborationService;
import redxax.oxy.remotely.data.flow.ReSyncIdentityProvider;
import redxax.oxy.remotely.util.NameUuid;

public final class BrowserReSyncIdentityProvider implements ReSyncIdentityProvider {
    public BrowserReSyncIdentityProvider(BrowserLaunchSession.Metadata metadata) {
    }

    @Override
    public String clientId(String serverId) {
        String server = serverId == null || serverId.isBlank() ? "default" : serverId.trim();
        return "remotely-web-" + NameUuid.from(identitySeed() + ':' + server);
    }

    @Override
    public CollaborationService.Identity collaborationIdentity(String fallbackClientId) {
        String subject = metadata().grantId();
        if (subject == null || subject.isBlank()) subject = fallbackClientId;
        if (subject == null || subject.isBlank()) subject = "remotely-web";
        subject = sanitize(subject);
        return new CollaborationService.Identity(subject, "Browser Collaborator", "", "restudio-web");
    }

    private String identitySeed() {
        BrowserLaunchSession.Metadata metadata = metadata();
        if (metadata.grantId() != null && !metadata.grantId().isBlank()) return metadata.grantId().trim();
        if (metadata.audience() != null && !metadata.audience().isBlank()) return metadata.audience().trim();
        return "remotely-web";
    }

    private BrowserLaunchSession.Metadata metadata() {
        return BrowserLaunchSession.metadata();
    }

    private String sanitize(String value) {
        String result = value.length() > 128 ? value.substring(0, 128) : value;
        return result.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
