package redxax.oxy.remotely.ui.collaboration;

import redxax.oxy.remotely.collaboration.CollaborationService;
import restudio.rebase.account.Account;
import restudio.rebase.restudio.ReStudio;
import restudio.rescreen.util.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CollaborationAvatarResolver implements CollaborationOverlay.AvatarProvider {
    private final Map<String, Identifier> avatars = new ConcurrentHashMap<>();
    private final Map<String, Long> failures = new ConcurrentHashMap<>();
    private final Set<String> requests = ConcurrentHashMap.newKeySet();

    public Identifier resolve(CollaborationService.Presence presence) {
        return resolve(presence != null ? presence.identity() : null);
    }

    @Override
    public Identifier resolve(CollaborationService.Identity identity) {
        if (identity == null) {
            return null;
        }
        String key = identity.source() + ':' + identity.subjectId() + ':' + identity.avatar();
        Identifier avatar = avatars.get(key);
        Long failedAt = failures.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt < 60_000L) {
            return null;
        }
        failures.remove(key);
        if (avatar != null || !requests.add(key)) {
            return avatar;
        }
        if ("minecraft".equalsIgnoreCase(identity.source())) {
            new Account(identity.displayName(), identity.avatar(), null, 0).getFaceIdAsync()
                .whenComplete((id, error) -> complete(key, id, error));
        } else {
            ReStudio.loadAvatarId(identity.subjectId(), identity.avatar())
                .whenComplete((id, error) -> complete(key, id, error));
        }
        return null;
    }

    private void complete(String key, Identifier avatar, Throwable error) {
        requests.remove(key);
        if (error == null && avatar != null) {
            avatars.put(key, avatar);
            failures.remove(key);
        } else {
            failures.put(key, System.currentTimeMillis());
        }
    }
}
