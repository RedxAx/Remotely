package redxax.oxy.remotely.worldgen.registry;

import redxax.oxy.remotely.flow.data.FlowDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WorldGenNodeDefinition {
    private final String id;
    private final String displayName;
    private final String category;
    private final List<PinDefinition> inputs;
    private final List<PinDefinition> outputs;
    private final int color;
    private final int priority;
    private final String description;
    private final boolean hidden;

    private WorldGenNodeDefinition(Builder builder) {
        this.id = builder.id;
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.inputs = List.copyOf(builder.inputs);
        this.outputs = List.copyOf(builder.outputs);
        this.color = builder.color;
        this.priority = builder.priority;
        this.description = builder.description;
        this.hidden = builder.hidden;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public List<PinDefinition> getInputs() {
        return inputs;
    }

    public List<PinDefinition> getOutputs() {
        return outputs;
    }

    public int getColor() {
        return color;
    }

    public int getPriority() {
        return priority;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHidden() {
        return hidden;
    }

    public static Builder builder(String id, String displayName) {
        return new Builder(id, displayName);
    }

    public static class Builder {
        private final String id;
        private final String displayName;
        private String category = "World Gen";
        private final List<PinDefinition> inputs = new ArrayList<>();
        private final List<PinDefinition> outputs = new ArrayList<>();
        private int color = 0xFF228B22;
        private int priority = 2000;
        private String description = "";
        private boolean hidden;

        private Builder(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public Builder category(String category) {
            this.category = category;
            return this;
        }

        public Builder input(String name, FlowDataType dataType, Object defaultValue, String widgetType) {
            inputs.add(new PinDefinition(name, dataType, PinDirection.INPUT, defaultValue, widgetType, Map.of(), "", null, List.of()));
            return this;
        }

        public Builder input(String name, FlowDataType dataType, Object defaultValue, String widgetType, List<String> options) {
            inputs.add(new PinDefinition(name, dataType, PinDirection.INPUT, defaultValue, widgetType, Map.of(), "", null, options != null ? options : List.of()));
            return this;
        }

        public Builder input(String name, FlowDataType dataType, Object defaultValue, String widgetType, List<String> options, String description) {
            inputs.add(new PinDefinition(name, dataType, PinDirection.INPUT, defaultValue, widgetType, Map.of(), description, null, options != null ? options : List.of()));
            return this;
        }

        public Builder output(String name, FlowDataType dataType) {
            outputs.add(new PinDefinition(name, dataType, PinDirection.OUTPUT, null, null, Map.of(), "", null, List.of()));
            return this;
        }

        public Builder color(int color) {
            this.color = color;
            return this;
        }

        public Builder priority(int priority) {
            this.priority = priority;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public WorldGenNodeDefinition build() {
            if (description == null || description.isBlank()) {
                description = inferDescription(id);
            }
            return new WorldGenNodeDefinition(this);
        }

        private String inferDescription(String id) {
            if (id == null) return "Builds Part Of The World Generation Pipeline";
            return switch (id) {
                case "simplex", "perlin", "value", "cellular", "white" -> "Creates A Deterministic Noise Field For Terrain, Climate, Masks, Or Density";
                case "fbm", "ridged", "ping_pong" -> "Layers A Source Field Into Larger And Smaller Terrain Detail";
                case "add", "multiply", "min", "max", "abs", "clamp", "remap" -> "Combines Or Reshapes Numeric World Generation Values";
                case "domain_warp_gradient", "domain_warp_simplex" -> "Distorts A Source Field To Create Natural Curves And Irregular Borders";
                case "terrace" -> "Turns Smooth Values Into Stepped Plateaus";
                case "continental_shelf", "mountain_range", "river_network", "eroded_peaks", "badlands_plateau", "volcanic_field" -> "Creates A Ready-To-Use Terrain Shape That Can Be Combined With Other Shapes";
                case "terrain_density", "density_from_height" -> "Converts Terrain Inputs Into Solid And Empty World Density";
                case "output_height", "output_density", "output_continentalness", "output_erosion", "output_weirdness", "output_depth", "output_temperature", "output_humidity" -> "Defines The Final Value Used By This World Generation Stage";
                case "output_biome" -> "Defines The Final Biome And Its Vanilla Content Policy";
                case "biome_constant", "biome_profile", "biome_select", "biome_blend", "climate_map", "biome_climate_router" -> "Selects A Biome From Climate, Masks, Or Connected Biome Values";
                case "surface_rule", "material_layer", "beach_rule", "underwater_rule", "snow_rule" -> "Builds A Surface Material Rule For Generated Terrain";
                case "height_band", "slope_mask", "biome_filter", "height_filter", "chance_filter" -> "Creates A Reusable Placement Filter";
                case "cave_noise", "worm_cave", "cheese_cave", "ravine", "cave_system" -> "Creates Cave Density That Can Be Combined And Carved";
                case "carve_if", "density_combine" -> "Combines Cave Density And Controls Where Terrain Is Carved";
                case "ore_vein", "tree_feature", "vegetation_patch", "liquid_lake", "disk", "boulder", "placed_feature" -> "Defines A Feature That Can Be Passed Into A Placement Chain";
                case "scatter", "poisson_scatter" -> "Adds A Feature To The Connected Placement Chain With Its Own Filters And Frequency";
                case "structure_placement" -> "Adds A Structure To The Connected Placement Chain With Surface, Spacing, Biome, And Override Controls";
                case "spawn_rule" -> "Adds An Entity Rule To The Connected Spawn Chain With Category, Biome, Height, Light, Time, And Weather Controls";
                case "output_features", "output_structures", "output_spawns" -> "Applies The Connected Chain As This Stage's Final Result";
                default -> "Builds Part Of The World Generation Pipeline";
            };
        }
    }

    public enum PinDirection {
        INPUT,
        OUTPUT
    }

    public record PinDefinition(String name, FlowDataType dataType, PinDirection direction, Object defaultValue, String widgetType,
                                Map<String, Object> constraints, String description, String visibleWhen, List<String> options) {
    }
}
