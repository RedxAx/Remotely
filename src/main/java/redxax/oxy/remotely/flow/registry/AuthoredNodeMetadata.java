package redxax.oxy.remotely.flow.registry;

import java.util.Map;

public record AuthoredNodeMetadata(String id, String domain, String family, String lifecycle, String description,
                                   String handlerCapability, String selectorIntent, String inspectorIntent,
                                   Map<String, Object> sourceProvenance) {
    public AuthoredNodeMetadata(String id, String domain, String family, String lifecycle, String description,
                                String handlerCapability, String selectorIntent, String inspectorIntent) {
        this(id, domain, family, lifecycle, description, handlerCapability, selectorIntent, inspectorIntent, Map.of());
    }

    public AuthoredNodeMetadata {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Authored node ID is required");
        }
        sourceProvenance = sourceProvenance == null ? Map.of() : Map.copyOf(sourceProvenance);
    }
}
