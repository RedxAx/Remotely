package redxax.oxy.remotely.data.flow;

import restudio.rebase.restudio.ReStudio;
import redxax.oxy.remotely.collaboration.CollaborationService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ReSyncDesktopIdentityProvider implements ReSyncIdentityProvider {
    @Override
    public String clientId(String serverId) {
        ReStudio studio = ReStudio.getInstance();
        String installationId = studio.getClientId();
        String seed = (serverId == null || serverId.isBlank() ? "default" : serverId) + ':' +
            (installationId == null || installationId.isBlank() ? "remotely" : installationId);
        return "remotely-" + UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public CollaborationService.Identity collaborationIdentity(String fallbackClientId) {
        ReStudio studio = ReStudio.getInstance();
        String subjectId = studio.getUserId();
        String displayName = studio.getDisplayName();
        String resolvedSubject = subjectId != null && !subjectId.isBlank() ? subjectId : fallbackClientId;
        resolvedSubject = resolvedSubject == null ? "remotely" : resolvedSubject.trim();
        if (resolvedSubject.length() > 128) {
            resolvedSubject = resolvedSubject.substring(0, 128);
        }
        resolvedSubject = resolvedSubject.replaceAll("[^A-Za-z0-9._-]", "_");
        return new CollaborationService.Identity(resolvedSubject,
            displayName != null && !displayName.isBlank() ? displayName : "Collaborator",
            studio.getAvatarUrl() != null ? studio.getAvatarUrl() : "", "restudio");
    }
}
