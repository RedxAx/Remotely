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
            assertNotNull(ReSyncResourceDropCapabilities.forResource(payload), type);
            assertEquals(payload.isLiteralAssignable(), ReSyncResourceDropCapabilities.forResource(payload) != null, type);
        }
        assertNull(ReSyncResourceDropCapabilities.forResource(new ReSyncResourceDragPayload(ReSyncResourceDragPayload.FOLDER, "example", "Example", "")));
    }

    @Test
    void runnableResourcesDropAsActions() {
        assertEquals("flow.run", dropSpec(ReSyncResourceDragPayload.FLOW).nodeType());
        assertEquals("command.run", dropSpec(ReSyncResourceDragPayload.COMMAND).nodeType());
        assertEquals("gui.open", dropSpec(ReSyncResourceDragPayload.GUI).nodeType());
        assertEquals("scoreboard.show.template", dropSpec(ReSyncResourceDragPayload.SCOREBOARD).nodeType());
        assertEquals("tab.apply", dropSpec(ReSyncResourceDragPayload.TAB).nodeType());
        assertEquals("trade.get.profile", dropSpec(ReSyncResourceDragPayload.TRADE_PROFILE).nodeType());
        assertEquals("npc.get", dropSpec(ReSyncResourceDragPayload.NPC_DEFINITION).nodeType());
        assertEquals("worldgen.get", dropSpec(ReSyncResourceDragPayload.WORLDGEN).nodeType());
    }

    @Test
    void functionDropsAsItsSignatureAwareNode() {
        ReSyncResourceDropCapabilities.DropSpec spec = dropSpec(ReSyncResourceDragPayload.FUNCTION);

        assertEquals("custom_function:example", spec.nodeType());
        assertEquals(0, spec.inputValues("example").size());
    }

    private ReSyncResourceDropCapabilities.DropSpec dropSpec(String type) {
        return ReSyncResourceDropCapabilities.forResource(new ReSyncResourceDragPayload(type, "example", "Example", ""));
    }
}
