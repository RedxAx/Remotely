package redxax.oxy.remotely.flow.ui;

import org.junit.jupiter.api.Test;
import redxax.oxy.remotely.flow.data.FlowDataType;
import redxax.oxy.remotely.flow.registry.NodeDefinition;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionCallPinModelTest {
    @Test
    void literalSelectionsExposeTypedPinsWithoutReplacingReservedPins() {
        List<NodeDefinition.PinDefinition> baseInputs = List.of(
            pin("function", true, FlowDataType.FUNCTION),
            pin("arguments", true, FlowDataType.ANY)
        );
        List<NodeDefinition.PinDefinition> baseOutputs = List.of(pin("result", false, FlowDataType.RESULT));
        NodeDefinition signature = new NodeDefinition.Builder("custom_function:reward", "Reward", NodeDefinition.NodeCategory.FUNCTION)
            .input(pin("amount", true, FlowDataType.NUMBER))
            .output(pin("granted", false, FlowDataType.BOOLEAN))
            .output(pin("result", false, FlowDataType.STRING))
            .build();

        FunctionCallPinModel.ResolvedPins resolved = FunctionCallPinModel.resolve(baseInputs, baseOutputs, signature, false);

        assertTrue(resolved.inputs().stream().anyMatch(pin -> "amount".equals(pin.getName())));
        assertFalse(resolved.inputs().stream().anyMatch(pin -> "arguments".equals(pin.getName())));
        assertTrue(resolved.outputs().stream().anyMatch(pin -> "granted".equals(pin.getName())));
        assertEquals(1, resolved.outputs().stream().filter(pin -> "result".equals(pin.getName())).count());
        assertEquals(Map.of("amount", "number"), resolved.signatureInputs());
        assertEquals(Map.of("granted", "boolean"), resolved.signatureOutputs());
    }

    @Test
    void wiredSelectionsKeepOnlyTheDynamicContract() {
        List<NodeDefinition.PinDefinition> baseInputs = List.of(pin("function", true, FlowDataType.FUNCTION));
        NodeDefinition signature = new NodeDefinition.Builder("custom_function:reward", "Reward", NodeDefinition.NodeCategory.FUNCTION)
            .input(pin("amount", true, FlowDataType.NUMBER))
            .build();

        FunctionCallPinModel.ResolvedPins resolved = FunctionCallPinModel.resolve(baseInputs, List.of(), signature, true);

        assertFalse(resolved.inputs().stream().anyMatch(pin -> "amount".equals(pin.getName())));
        assertTrue(resolved.signatureInputs().isEmpty());
    }

    @Test
    void declaredArgumentsReplaceTheGenericInputForDynamicSelections() {
        List<NodeDefinition.PinDefinition> baseInputs = List.of(
            pin("function", true, FlowDataType.FUNCTION),
            pin("arguments", true, FlowDataType.ANY)
        );
        List<NodeDefinition.PinDefinition> declared = List.of(
            pin("player", true, FlowDataType.PLAYER),
            pin("item", true, FlowDataType.ITEM)
        );

        FunctionCallPinModel.ResolvedPins resolved = FunctionCallPinModel.resolve(baseInputs, List.of(), null, true, declared);

        assertFalse(resolved.inputs().stream().anyMatch(pin -> "arguments".equals(pin.getName())));
        assertTrue(resolved.inputs().stream().anyMatch(pin -> "player".equals(pin.getName())));
        assertTrue(resolved.inputs().stream().anyMatch(pin -> "item".equals(pin.getName())));
        assertEquals(Map.of("player", "player", "item", "item"), resolved.signatureInputs());
    }

    @Test
    void signatureChangesMigratePinsWithoutReconnectWarnings() {
        assertEquals(Map.of("arguments", "player"),
            FunctionCallPinModel.pinMigrations(Map.of(), Map.of("player", "player"), true, false));
        assertEquals(Map.of("amount", "quantity"),
            FunctionCallPinModel.pinMigrations(Map.of("amount", "number"), Map.of("quantity", "number"), true, false));
        assertEquals(Map.of(),
            FunctionCallPinModel.pinMigrations(Map.of("player", "player"), Map.of("player", "player", "item", "item"), true, false));
    }

    private NodeDefinition.PinDefinition pin(String name, boolean input, FlowDataType type) {
        return new NodeDefinition.PinBuilder(
            name,
            NodeDefinition.PinType.DATA,
            input ? NodeDefinition.PinDirection.INPUT : NodeDefinition.PinDirection.OUTPUT,
            type
        ).build();
    }
}
