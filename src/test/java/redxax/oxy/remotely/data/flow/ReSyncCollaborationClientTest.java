package redxax.oxy.remotely.data.flow;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReSyncCollaborationClientTest {
    @Test
    void distinguishesCursorMotionFromResourceActivityChanges() {
        ReSyncCollaborationClient client = new ReSyncCollaborationClient(new Gson(), "remotely-device");

        assertTrue(client.applySnapshot(snapshot("session", "remote-device", "flow", "welcome", 0.1, 0.2)));
        assertFalse(client.applySnapshot(snapshot("session", "remote-device", "flow", "welcome", 0.8, 0.7)));
        assertTrue(client.applySnapshot(snapshot("session", "remote-device", "gui", "menu", 0.8, 0.7)));
    }

    @Test
    void usesTheServerSessionIdentityWhenClientIdsMatch() {
        ReSyncCollaborationClient client = new ReSyncCollaborationClient(new Gson(), "remotely-device");
        client.applySnapshot(snapshot("mine", "theirs", "remotely-device", "flow", "welcome", 0.1, 0.2));

        assertFalse(client.isSelf(client.snapshot().getFirst()));

        client.applySnapshot(snapshot("theirs", "theirs", "remotely-device", "flow", "welcome", 0.1, 0.2));
        assertTrue(client.isSelf(client.snapshot().getFirst()));
    }

    @Test
    void dispatchesValidChatMessagesOnly() {
        ReSyncCollaborationClient client = new ReSyncCollaborationClient(new Gson(), "remotely-device");
        List<ReSyncCollaborationClient.Message> messages = new ArrayList<>();
        client.addMessageListener(messages::add);

        assertTrue(client.applyMessage("""
            {"id":"message","authorSessionId":"remote","author":{"subjectId":"user","displayName":"Alex","avatar":"","source":"restudio"},"resourceType":"flow","resourceId":"welcome","color":-1,"message":"Hello","sentAt":1}
            """));
        assertFalse(client.applyMessage("""
            {"id":"","authorSessionId":"remote","message":"Hello"}
            """));
        assertEquals(1, messages.size());
        assertEquals("Hello", messages.getFirst().message());
    }

    @Test
    void replaysRecentMessagesWhenTheStudioListenerAttachesLate() {
        ReSyncCollaborationClient client = new ReSyncCollaborationClient(new Gson(), "remotely-device");
        assertTrue(client.applyMessage("""
            {"id":"message","authorSessionId":"remote","author":{"subjectId":"user","displayName":"Alex","avatar":"","source":"restudio"},"resourceType":"flow","resourceId":"welcome","color":-1,"message":"Hello","sentAt":1}
            """));

        List<ReSyncCollaborationClient.Message> messages = new ArrayList<>();
        client.addMessageListener(messages::add);

        assertEquals(1, messages.size());
        assertEquals("Hello", messages.getFirst().message());
    }

    private String snapshot(String sessionId, String clientId, String type, String resourceId, double x, double y) {
        return snapshot("", sessionId, clientId, type, resourceId, x, y);
    }

    private String snapshot(String selfSessionId, String sessionId, String clientId, String type, String resourceId, double x, double y) {
        return """
            {"selfSessionId":"%s","collaborators":[{"sessionId":"%s","clientId":"%s","identity":{"subjectId":"user","displayName":"Alex","avatar":"","source":"restudio"},"resourceType":"%s","resourceId":"%s","viewId":"Studio","x":%s,"y":%s,"active":true,"color":-1,"updatedAt":1}]}
            """.formatted(selfSessionId, sessionId, clientId, type, resourceId, x, y);
    }
}
