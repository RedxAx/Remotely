package redxax.oxy.remotely.data.flow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReSyncProtocolContractTest {
    @Test
    void flowPacketFixtureMatchesTheSharedContract() {
        assertEquals(0x01, ReSyncProtocolContract.FLOW_PACKET_REQUEST);
        assertEquals(0x03, ReSyncProtocolContract.FLOW_PACKET_SAVE);
        assertEquals(0x04, ReSyncProtocolContract.FLOW_PACKET_GUI_STATE);
        assertEquals(0x05, ReSyncProtocolContract.FLOW_PACKET_ERROR);
        assertEquals(0x06, ReSyncProtocolContract.FLOW_PACKET_TRIGGER_UPDATE);
        assertEquals(0x08, ReSyncProtocolContract.FLOW_PACKET_DELETE);
        assertEquals(0x09, ReSyncProtocolContract.FLOW_PACKET_LIST_REQUEST);
        assertEquals(0x27, ReSyncProtocolContract.FLOW_PACKET_PLACEHOLDER_PREVIEW_REQUEST);
        assertEquals(0x28, ReSyncProtocolContract.FLOW_PACKET_PLACEHOLDER_PREVIEW);
        assertEquals(0x37, ReSyncProtocolContract.FLOW_PACKET_OPTION_CATALOG_REQUEST);
        assertEquals(0x38, ReSyncProtocolContract.FLOW_PACKET_OPTION_CATALOG);
        assertEquals(0x44, ReSyncProtocolContract.FLOW_PACKET_JOB);
        assertEquals(0x48, ReSyncProtocolContract.FLOW_PACKET_FUNCTION_TEST_REQUEST);
        assertEquals(0x49, ReSyncProtocolContract.FLOW_PACKET_FUNCTION_TEST_RESULT);
        assertEquals(0x5A, ReSyncProtocolContract.FLOW_PACKET_EDIT_TARGET_STATE);
        assertEquals(0x60, ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_OPEN);
        assertEquals(0x61, ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_APPLY);
        assertEquals(0x62, ReSyncProtocolContract.FLOW_PACKET_QUICK_EDIT_RESULT);
        assertEquals(0x63, ReSyncProtocolContract.FLOW_PACKET_OPEN_CUSTOM_CONTENT);
    }

    @Test
    void resourceFixtureUsesTheSameGeneratedContract() {
        assertEquals(ReSyncProtocolContract.FLOW_PACKET_REQUEST, ReSyncResourceType.FLOW.requestByte());
        assertEquals(ReSyncProtocolContract.FLOW_PACKET_SAVE, ReSyncResourceType.FLOW.saveByte());
        assertEquals(ReSyncProtocolContract.FLOW_PACKET_DELETE, ReSyncResourceType.FLOW.deleteByte());
        assertEquals(ReSyncProtocolContract.FLOW_PACKET_LIST_REQUEST, ReSyncResourceType.FLOW.listRequestByte());
        assertEquals(ReSyncProtocolContract.CUSTOM_CONTENT_PACKET_DATA, ReSyncResourceType.CUSTOM_CONTENT.dataResponseByte());
        assertEquals(ReSyncProtocolContract.CUSTOM_CONTENT_PACKET_SAVE_ACK, ReSyncResourceType.CUSTOM_CONTENT.saveAckByte());
        assertEquals(ReSyncProtocolContract.DIALOG_PACKET_DATA, ReSyncResourceType.DIALOG.dataResponseByte());
        assertEquals(ReSyncProtocolContract.DIALOG_PACKET_SAVE_ACK, ReSyncResourceType.DIALOG.saveAckByte());
        assertEquals(ReSyncProtocolContract.LOOT_TABLE_PACKET_DATA, ReSyncResourceType.LOOT_TABLE.dataResponseByte());
        assertEquals(ReSyncProtocolContract.LOOT_TABLE_PACKET_SAVE_ACK, ReSyncResourceType.LOOT_TABLE.saveAckByte());
    }
}
