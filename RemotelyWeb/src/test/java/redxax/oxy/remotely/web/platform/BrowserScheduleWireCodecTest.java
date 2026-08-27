package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.schedule.ServerScheduleWireCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BrowserScheduleWireCodecTest {
    private static final String VALID = "{\"id\":\"s\",\"name\":\"Restart\",\"enabled\":true,"
            + "\"timing\":{\"type\":\"CRON\",\"runAt\":\"\",\"cron\":\"0 4 * * *\",\"zoneId\":\"UTC\"},"
            + "\"onlyWhenOnline\":false,\"tasks\":[{\"id\":\"t\",\"sequence\":1,\"action\":\"RESTART\","
            + "\"payload\":\"\",\"delaySeconds\":0,\"continueOnFailure\":false}],\"nextRunAt\":\"\","
            + "\"lastRunAt\":\"\",\"lastResult\":\"\",\"revision\":\"1\",\"processing\":false}";

    @Test
    void sharedCodecParsesBrowserCreateAndListResponses() {
        assertEquals("s", ServerScheduleWireCodec.schedule(VALID).id());
        assertEquals("s", ServerScheduleWireCodec.schedules("[" + VALID + "]").getFirst().id());
    }

    @Test
    void sharedCodecDoesNotLinkTheJvmGsonParser() throws IOException {
        String resource = "/" + ServerScheduleWireCodec.class.getName().replace('.', '/') + ".class";
        var source = ServerScheduleWireCodec.class.getResourceAsStream(resource);
        assertNotNull(source);
        try (var stream = source) {
            byte[] bytecode = stream.readAllBytes();
            assertFalse(new String(bytecode, StandardCharsets.ISO_8859_1).contains("com/google/gson/JsonParser"));
        }
    }

    @Test
    void sharedCodecPreservesMissingFieldsForStrictRejection() {
        String missingAction = "{\"id\":\"s\",\"name\":\"Bad\",\"enabled\":true,"
                + "\"timing\":{\"type\":\"CRON\",\"runAt\":\"\",\"cron\":\"0 4 * * *\",\"zoneId\":\"UTC\"},"
                + "\"onlyWhenOnline\":false,\"tasks\":[{\"id\":\"t\",\"sequence\":1,\"payload\":\"\","
                + "\"delaySeconds\":0,\"continueOnFailure\":false}],\"nextRunAt\":\"\",\"lastRunAt\":\"\","
                + "\"lastResult\":\"\",\"revision\":\"1\",\"processing\":false}";
        assertThrows(IllegalArgumentException.class, () -> ServerScheduleWireCodec.schedule(missingAction));
    }

    @Test
    void browserNeverSynthesizesScheduleIdempotencyKeys() {
        assertNull(BrowserRemotelyServerApi.requestIdempotencyKey("POST", "/server-schedules/servers/server", "{}"));
    }
}
