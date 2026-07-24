package redxax.oxy.remotely.flow.ui;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.ReSyncResourceDragPayload;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReSyncResourceDropCapabilitiesTest {
    @Test
    void everyAssignableResourceHasAnIntentionalDropNode() {
        List<String> resourceTypes = List.of(
            ReSyncResourceDragPayload.FLOW,
            ReSyncResourceDragPayload.FUNCTION,
            ReSyncResourceDragPayload.COMMAND,
            ReSyncResourceDragPayload.CUSTOM_CONTENT,
            ReSyncResourceDragPayload.GUI,
            ReSyncResourceDragPayload.SCOREBOARD,
            ReSyncResourceDragPayload.TAB,
            ReSyncResourceDragPayload.CHAT,
            ReSyncResourceDragPayload.MOTD_PROFILE,
            ReSyncResourceDragPayload.MESSAGE_RULE,
            ReSyncResourceDragPayload.RECIPE_DEFINITION,
            ReSyncResourceDragPayload.TEXT_TEMPLATE,
            ReSyncResourceDragPayload.ADVANCEMENT_TREE,
            ReSyncResourceDragPayload.DIALOG,
            ReSyncResourceDragPayload.TRADE_PROFILE,
            ReSyncResourceDragPayload.NPC_DEFINITION,
            ReSyncResourceDragPayload.LOOT_TABLE,
            ReSyncResourceDragPayload.WORLDGEN,
            ReSyncResourceDragPayload.WORLD
        );

        for (String type : resourceTypes) {
            ReSyncResourceDragPayload payload = new ReSyncResourceDragPayload(type, "example", "Example", "");
            assertNotNull(ReSyncResourceDropCapabilities.forType(type), type);
            assertEquals(payload.isLiteralAssignable(), ReSyncResourceDropCapabilities.forType(type) != null, type);
        }
        assertNull(ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.FOLDER));
    }

    @Test
    void runnableResourcesDropAsActions() {
        assertEquals("flow.run", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.FLOW).nodeType());
        assertEquals("command.run", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.COMMAND).nodeType());
        assertEquals("gui.open", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.GUI).nodeType());
        assertEquals("scoreboard.show.template", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.SCOREBOARD).nodeType());
        assertEquals("tab.apply", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.TAB).nodeType());
        assertEquals("trade.get.profile", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.TRADE_PROFILE).nodeType());
        assertEquals("npc.get", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.NPC_DEFINITION).nodeType());
        assertEquals("worldgen.get", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.WORLDGEN).nodeType());
        assertEquals("call.function", ReSyncResourceDropCapabilities.forType(ReSyncResourceDragPayload.FUNCTION).nodeType());
    }
}
