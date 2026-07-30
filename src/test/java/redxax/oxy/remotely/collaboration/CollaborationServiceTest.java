package redxax.oxy.remotely.collaboration;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollaborationServiceTest {
    @Test
    void routesPresenceAndMessagesThroughAnyChannel() {
        RecordingChannel channel = new RecordingChannel();
        CollaborationService service = new CollaborationService("client");
        CollaborationService.Target target = new CollaborationService.Target("text", "notes.txt", "title");
        service.bind(channel);

        assertTrue(service.publishPresence(target, 0.25, 0.75, true, true));
        assertTrue(service.publishMessage("Hello"));

        assertEquals(target, channel.presence.getFirst().target());
        assertEquals(target, channel.messages.getFirst().target());
        assertEquals("Hello", channel.messages.getFirst().message());
    }

    @Test
    void separatesCursorMotionFromMeaningfulActivity() {
        CollaborationService service = new CollaborationService("client");
        CollaborationService.Identity identity = new CollaborationService.Identity(
            "user", "Alex", "", "restudio");

        assertTrue(service.acceptSnapshot("", List.of(new CollaborationService.Presence(
            "session", "remote", identity, "text", "notes.txt", "body",
            0.1, 0.2, true, false, -1, false, 1))));
        assertFalse(service.acceptSnapshot("", List.of(new CollaborationService.Presence(
            "session", "remote", identity, "text", "notes.txt", "body",
            0.8, 0.9, true, false, -1, false, 2))));
        assertTrue(service.acceptSnapshot("", List.of(new CollaborationService.Presence(
            "session", "remote", identity, "text", "notes.txt", "title",
            0.8, 0.9, true, true, -1, false, 3))));
    }

    @Test
    void reconnectClearsRemoteStateAndRepublishesLocalPresence() {
        RecordingChannel channel = new RecordingChannel();
        CollaborationService service = new CollaborationService("client");
        CollaborationService.Target target = new CollaborationService.Target("flow", "main", "graph");
        service.bind(channel);
        service.publishPresence(target, 0.4, 0.6, true, false);
        service.acceptSnapshot("self", List.of(new CollaborationService.Presence(
            "other", "remote", new CollaborationService.Identity("user", "Alex", "", "restudio"),
            "flow", "main", "graph", 0.2, 0.3, true, false, -1, false, 1L)));

        service.connectionLost();
        service.connectionReady();

        assertTrue(service.snapshot().isEmpty());
        assertEquals(2, channel.presence.size());
        assertEquals(target, channel.presence.getLast().target());
    }

    private static final class RecordingChannel implements CollaborationService.Channel {
        private final List<CollaborationService.PresenceUpdate> presence = new ArrayList<>();
        private final List<CollaborationService.MessageDraft> messages = new ArrayList<>();

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void publishPresence(CollaborationService.PresenceUpdate update) {
            presence.add(update);
        }

        @Override
        public void publishMessage(CollaborationService.MessageDraft message) {
            messages.add(message);
        }
    }
}
