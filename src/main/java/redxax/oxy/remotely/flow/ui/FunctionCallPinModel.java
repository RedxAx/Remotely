package redxax.oxy.remotely.flow.ui;

import redxax.oxy.remotely.flow.registry.NodeDefinition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class FunctionCallPinModel {
    private FunctionCallPinModel() {
    }

    static ResolvedPins resolve(List<NodeDefinition.PinDefinition> baseInputs, List<NodeDefinition.PinDefinition> baseOutputs,
                                NodeDefinition signature, boolean dynamic) {
        return resolve(baseInputs, baseOutputs, signature, dynamic, List.of());
    }

    static ResolvedPins resolve(List<NodeDefinition.PinDefinition> baseInputs, List<NodeDefinition.PinDefinition> baseOutputs,
                                NodeDefinition signature, boolean dynamic, List<NodeDefinition.PinDefinition> declaredInputs) {
        List<NodeDefinition.PinDefinition> inputs = new ArrayList<>(baseInputs != null ? baseInputs : List.of());
        List<NodeDefinition.PinDefinition> outputs = new ArrayList<>(baseOutputs != null ? baseOutputs : List.of());
        if (declaredInputs != null && !declaredInputs.isEmpty()) {
            inputs.removeIf(pin -> "arguments".equals(pin.getName()));
            append(inputs, declaredInputs);
        } else if (signature != null && !dynamic) {
            inputs.removeIf(pin -> "arguments".equals(pin.getName()));
            append(inputs, signature.getInputs());
        }
        if (signature != null && !dynamic) {
            append(outputs, signature.getOutputs());
        }
        return new ResolvedPins(inputs, outputs, types(inputs, baseInputs), types(outputs, baseOutputs));
    }

    static Map<String, String> pinMigrations(Map<String, String> previous, Map<String, String> current,
                                             boolean input, boolean genericInputAvailable) {
        Map<String, String> before = previous != null ? previous : Map.of();
        Map<String, String> after = current != null ? current : Map.of();
        Map<String, String> migrations = new LinkedHashMap<>();
        if (input && before.isEmpty() && after.size() == 1) {
            migrations.put("arguments", after.keySet().iterator().next());
        } else if (input && after.isEmpty() && genericInputAvailable && before.size() == 1) {
            migrations.put(before.keySet().iterator().next(), "arguments");
        }

        List<String> removed = before.keySet().stream().filter(name -> !after.containsKey(name)).toList();
        List<String> added = new ArrayList<>(after.keySet().stream().filter(name -> !before.containsKey(name)).toList());
        for (String oldName : removed) {
            List<String> compatible = added.stream().filter(name -> Objects.equals(before.get(oldName), after.get(name))).toList();
            if (compatible.size() == 1) {
                String newName = compatible.getFirst();
                migrations.put(oldName, newName);
                added.remove(newName);
            }
        }

        List<String> unmatched = removed.stream().filter(name -> !migrations.containsKey(name)).toList();
        if (unmatched.size() == added.size()) {
            for (int index = 0; index < unmatched.size(); index++) {
                migrations.put(unmatched.get(index), added.get(index));
            }
        }
        return migrations;
    }

    private static void append(List<NodeDefinition.PinDefinition> target, List<NodeDefinition.PinDefinition> additions) {
        Set<String> names = new LinkedHashSet<>();
        for (NodeDefinition.PinDefinition pin : target) {
            names.add(pin.getName());
        }
        for (NodeDefinition.PinDefinition pin : additions) {
            if (pin != null && !"flow".equals(pin.getName()) && names.add(pin.getName())) {
                target.add(pin);
            }
        }
    }

    private static Map<String, String> types(List<NodeDefinition.PinDefinition> resolved, List<NodeDefinition.PinDefinition> base) {
        Set<String> baseNames = new LinkedHashSet<>();
        if (base != null) {
            for (NodeDefinition.PinDefinition pin : base) {
                baseNames.add(pin.getName());
            }
        }
        Map<String, String> types = new LinkedHashMap<>();
        for (NodeDefinition.PinDefinition pin : resolved) {
            if (!baseNames.contains(pin.getName())) {
                types.put(pin.getName(), pin.getTypeRef().toString());
            }
        }
        return types;
    }

    record ResolvedPins(List<NodeDefinition.PinDefinition> inputs, List<NodeDefinition.PinDefinition> outputs,
                        Map<String, String> signatureInputs, Map<String, String> signatureOutputs) {
    }
}
