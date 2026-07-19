package redxax.oxy.remotely.flow.sync;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowResourceMetadataContractTest {
    @Test
    void operationAvailabilitySurvivesProtocolSerialization() {
        FlowResourceMetadata metadata = new FlowResourceMetadata();
        metadata.setTypeId("trade_profile");
        metadata.setOperationAvailability(Map.of(
            "apply", "available",
            "reload", "This resource domain does not expose an explicit reload operation"
        ));

        Gson gson = new Gson();
        FlowResourceMetadata restored = gson.fromJson(gson.toJson(metadata), FlowResourceMetadata.class);

        assertEquals(2, restored.getSchemaVersion());
        assertEquals("available", restored.getOperationAvailability().get("apply"));
        assertTrue(restored.getOperationAvailability().get("reload").contains("does not expose"));
    }
}
