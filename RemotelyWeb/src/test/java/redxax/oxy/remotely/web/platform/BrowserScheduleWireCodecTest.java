package redxax.oxy.remotely.web.platform;

import org.junit.jupiter.api.Test;
import restudio.rebase.schedule.ServerScheduleWireCodec;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;

class BrowserScheduleWireCodecTest {
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
