package redxax.oxy.remotely.flow.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import restudio.resync.flow.workspace.WorkspacePatch;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowWorkspaceDocumentTest {
    @Test
    void diffsIndependentNodeChangesWithoutReplacingTheGraph() {
        JsonObject before = object("""
            {
              "nodes": {
                "first": {"type": "log", "x": 10, "y": 20, "inputValues": {"message": "Before"}}
              },
              "connections": []
            }
            """);
        JsonObject after = object("""
            {
              "nodes": {
                "first": {"type": "log", "x": 80, "y": 20, "inputValues": {"message": "After"}},
                "second": {"type": "delay", "x": 140, "y": 20, "inputValues": {}}
              },
              "connections": []
            }
            """);

        List<WorkspacePatch<JsonElement>> patches = FlowWorkspaceDocument.diff(before, after);

        assertTrue(patches.stream().anyMatch(patch -> "/nodes/first/x".equals(patch.path())));
        assertTrue(patches.stream().anyMatch(patch -> "/nodes/first/inputValues/message".equals(patch.path())));
        assertTrue(patches.stream().anyMatch(patch -> "/nodes/second".equals(patch.path())));
        JsonObject applied = before.deepCopy();
        FlowWorkspaceDocument.apply(applied, patches);
        assertEquals(after, applied);
    }

    @Test
    void connectionChangesAreSetOperationsThatCompose() {
        JsonObject base = object("""
            {"nodes": {}, "connections": []}
            """);
        JsonObject first = object("""
            {"nodes": {}, "connections": [{"sourceNodeId":"a","sourcePin":"out","targetNodeId":"b","targetPin":"in"}]}
            """);
        JsonObject second = object("""
            {"nodes": {}, "connections": [{"sourceNodeId":"c","sourcePin":"out","targetNodeId":"d","targetPin":"in"}]}
            """);

        List<WorkspacePatch<JsonElement>> firstPatches = FlowWorkspaceDocument.diff(base, first);
        List<WorkspacePatch<JsonElement>> secondPatches = FlowWorkspaceDocument.diff(base, second);
        JsonObject merged = base.deepCopy();
        FlowWorkspaceDocument.apply(merged, firstPatches);
        FlowWorkspaceDocument.apply(merged, firstPatches);
        FlowWorkspaceDocument.apply(merged, secondPatches);

        assertEquals(2, merged.getAsJsonArray("connections").size());
        assertTrue(firstPatches.stream().allMatch(patch -> "array_add".equals(patch.op())));
        assertTrue(secondPatches.stream().allMatch(patch -> "array_add".equals(patch.op())));
    }

    private JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
