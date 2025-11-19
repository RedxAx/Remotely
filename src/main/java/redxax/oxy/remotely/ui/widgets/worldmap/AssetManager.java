package redxax.oxy.remotely.ui.widgets.worldmap;

import restudio.rebase.util.RebaseLogger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.jar.JarFile;

public class AssetManager {

    private final Map<String, Integer> colorMap = new HashMap<>();
    private volatile boolean highQualityLoaded = false;

    private static final Map<String, Integer> BIOME_GRASS = new HashMap<>();
    private static final Map<String, Integer> BIOME_WATER = new HashMap<>();
    private static final Map<String, Integer> BIOME_FOLIAGE = new HashMap<>();
    private static final int NEUTRAL_FOLIAGE_BASE = 0xFF808080;

    static {
        BIOME_GRASS.put("default", 0xFF79C05A);
        BIOME_WATER.put("default", 0xFF3F76E4);
        BIOME_FOLIAGE.put("default", 0xFF59AE30);
        BIOME_FOLIAGE.put("leaf_litter", 0xFF644B32);

        regBiome("minecraft:plains", 0xFF91BD59, 0xFF3F76E4, 0xFF77AB2F);
        regBiome("minecraft:sunflower_plains", 0xFF91BD59, 0xFF3F76E4, 0xFF77AB2F);

        regBiome("minecraft:meadow", 0xFF83BB6D, 0xFF3F76E4, 0xFF6BA941);
        regBiome("minecraft:cherry_grove", 0xFFB6DB61, 0xFF5DB7EF, 0xFFB6DB61);

        regBiome("minecraft:forest", 0xFF79C05A, 0xFF3F76E4, 0xFF59AE30);
        regBiome("minecraft:flower_forest", 0xFF79C05A, 0xFF3F76E4, 0xFF59AE30);
        regBiome("minecraft:birch_forest", 0xFF88BB67, 0xFF3F76E4, 0xFF6BA941);
        regBiome("minecraft:dark_forest", 0xFF507A32, 0xFF3F76E4, 0xFF59AE30);
        regBiome("minecraft:old_growth_birch_forest", 0xFF88BB67, 0xFF3F76E4, 0xFF6BA941);

        regBiome("minecraft:swamp", 0xFF6A7039, 0xFF617B64, 0xFF6A7039);
        regBiome("minecraft:mangrove_swamp", 0xFF6A7039, 0xFF3A7A6A, 0xFF6A7039);

        regBiome("minecraft:jungle", 0xFF59C93C, 0xFF3F76E4, 0xFF30BB0B);
        regBiome("minecraft:sparse_jungle", 0xFF64C93C, 0xFF3F76E4, 0xFF30BB0B);
        regBiome("minecraft:bamboo_jungle", 0xFF76C600, 0xFF3F76E4, 0xFF76C600);

        regBiome("minecraft:snowy_plains", 0xFF80B497, 0xFF3D57D6, 0xFF68B474);
        regBiome("minecraft:ice_spikes", 0xFF80B497, 0xFF3D57D6, 0xFF68B474);
        regBiome("minecraft:taiga", 0xFF81C281, 0xFF4076E4, 0xFF68B474);
        regBiome("minecraft:snowy_taiga", 0xFF81C281, 0xFF4076E4, 0xFF68B474);
        regBiome("minecraft:old_growth_pine_taiga", 0xFF81C281, 0xFF4076E4, 0xFF68B474);

        regBiome("minecraft:desert", 0xFFBFB755, 0xFF32A0DE, 0xFFAEA42A);
        regBiome("minecraft:savanna", 0xFFBFB755, 0xFF32A0DE, 0xFFAEA42A);
        regBiome("minecraft:savanna_plateau", 0xFFBFB755, 0xFF32A0DE, 0xFFAEA42A);
        regBiome("minecraft:badlands", 0xFF90814D, 0xFF32A0DE, 0xFF9E814D);
        regBiome("minecraft:wooded_badlands", 0xFF90814D, 0xFF32A0DE, 0xFF9E814D);

        regBiome("minecraft:ocean", 0xFF8EB971, 0xFF1787D4, 0xFF71A74D);
        regBiome("minecraft:deep_ocean", 0xFF8EB971, 0xFF1787D4, 0xFF71A74D);
        regBiome("minecraft:warm_ocean", 0xFF8EB971, 0xFF43D5EE, 0xFF71A74D);
        regBiome("minecraft:lukewarm_ocean", 0xFF8EB971, 0xFF45ADF2, 0xFF71A74D);
        regBiome("minecraft:cold_ocean", 0xFF8EB971, 0xFF3D57D6, 0xFF71A74D);
        regBiome("minecraft:frozen_ocean", 0xFF80B497, 0xFF3D57D6, 0xFF68B474);

        regBiome("minecraft:river", 0xFF8EB971, 0xFF3F76E4, 0xFF71A74D);
        regBiome("minecraft:frozen_river", 0xFF80B497, 0xFF3D57D6, 0xFF68B474);
        regBiome("minecraft:beach", 0xFF8EB971, 0xFF3F76E4, 0xFF71A74D);
        regBiome("minecraft:snowy_beach", 0xFF80B497, 0xFF3D57D6, 0xFF68B474);
        regBiome("minecraft:stony_shore", 0xFF8EB971, 0xFF3F76E4, 0xFF71A74D);
    }

    private static void regBiome(String id, int grass, int water, int foliage) {
        BIOME_GRASS.put(id, grass);
        BIOME_WATER.put(id, water);
        BIOME_FOLIAGE.put(id, foliage);
    }

    public AssetManager() {
        MapColors.fillDefaults(colorMap);
    }

    public CompletableFuture<Void> loadAssetsFromVersion() {
        return CompletableFuture.runAsync(() -> {
            try {
                File jarFile = new File("C:/Users/redxa/.relaunched/versions/1.21.10/1.21.10.jar");
                if (!jarFile.exists()) throw new RuntimeException("JAR not found");

                Map<String, Integer> priorityMap = new HashMap<>();

                try (JarFile jar = new JarFile(jarFile)) {
                    jar.stream().forEach(entry -> {
                        String name = entry.getName();
                        if (name.startsWith("assets/minecraft/textures/block/") && name.endsWith(".png") && !name.contains("mcmeta")) {
                            String filename = name.substring(name.lastIndexOf('/') + 1).replace(".png", "");

                            String blockId;
                            int priority = 1;

                            if (filename.endsWith("_top") || filename.endsWith("_up")) {
                                blockId = filename.replace("_top", "").replace("_up", "");
                                priority = 3;
                            } else if (filename.contains("_stage")) {
                                blockId = filename.substring(0, filename.lastIndexOf("_stage"));
                                priority = 10;
                            } else if (filename.endsWith("_side") || filename.endsWith("_bottom") || filename.endsWith("_front")) {
                                blockId = filename.replace("_side", "").replace("_bottom", "").replace("_front", "");
                            } else {
                                blockId = filename;
                                priority = 2;
                            }

                            if (blockId.endsWith("_moist")) blockId = blockId.replace("_moist", "");
                            blockId = "minecraft:" + blockId;

                            synchronized (colorMap) {
                                if (priorityMap.getOrDefault(blockId, -1) < priority) {
                                    try {
                                        BufferedImage tex = ImageIO.read(jar.getInputStream(entry));
                                        if (tex != null) {
                                            int avgColor = calculateAverageColor(tex);
                                            if ((avgColor >>> 24) > 10) {
                                                colorMap.put(blockId, avgColor);
                                                priorityMap.put(blockId, priority);
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }
                    });
                }
                highQualityLoaded = true;
                RebaseLogger.log("Assets loaded. Colors: " + colorMap.size());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private int calculateAverageColor(BufferedImage image) {
        long rSum = 0, gSum = 0, bSum = 0, aSum = 0;
        int count = 0;
        int w = image.getWidth();
        int h = image.getHeight();

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = image.getRGB(x, y);
                int alpha = (rgb >> 24) & 0xFF;
                if (alpha > 10) {
                    rSum += (rgb >> 16) & 0xFF;
                    gSum += (rgb >> 8) & 0xFF;
                    bSum += (rgb) & 0xFF;
                    aSum += alpha;
                    count++;
                }
            }
        }
        if (count == 0) return 0;

        return ((int)(aSum/count) << 24) | ((int)(rSum/count) << 16) | ((int)(gSum/count) << 8) | (int)(bSum/count);
    }

    public int getBlockColor(String blockId, String biomeId) {
        int base = resolveBaseColor(blockId);

        if (highQualityLoaded) {
            if (shouldTintFoliage(blockId)) base = NEUTRAL_FOLIAGE_BASE;
            else if (shouldTintGrass(blockId)) base = NEUTRAL_FOLIAGE_BASE;
        }

        if (shouldTintGrass(blockId)) {
            int tint = BIOME_GRASS.getOrDefault(biomeId, BIOME_GRASS.get("default"));
            return applyTint(base, tint);
        }
        if (shouldTintFoliage(blockId)) {
            int tint = !blockId.contains("leaf_litter") ? BIOME_FOLIAGE.getOrDefault(biomeId, BIOME_FOLIAGE.get("default")) : BIOME_FOLIAGE.get("leaf_litter");
            return applyTint(base, tint);
        }
        if (shouldTintWater(blockId)) {
            int tint = BIOME_WATER.getOrDefault(biomeId, BIOME_WATER.get("default"));
            return applyTint(base, tint);
        }
        return base;
    }

    private int resolveBaseColor(String blockId) {
        if (blockId == null) return 0xFF000000;
        Integer color = colorMap.get(blockId);
        if (color != null) return color;
        String stripped = blockId;
        if (stripped.endsWith("_stairs")) stripped = stripped.replace("_stairs", "");
        else if (stripped.endsWith("_slab")) stripped = stripped.replace("_slab", "");
        else if (stripped.endsWith("_wall")) stripped = stripped.replace("_wall", "");
        else if (stripped.endsWith("_pane")) stripped = stripped.replace("_pane", "");
        else if (stripped.endsWith("_button")) stripped = stripped.replace("_button", "");
        else if (stripped.endsWith("_pressure_plate")) stripped = stripped.replace("_pressure_plate", "");
        else if (stripped.endsWith("_fence_gate")) stripped = stripped.replace("_fence_gate", "");
        else if (stripped.endsWith("_fence")) stripped = stripped.replace("_fence", "");
        else if (stripped.endsWith("_carpet")) stripped = stripped.replace("_carpet", "");

        color = colorMap.get(stripped);
        if (color != null) return color;

        if (!stripped.contains("_planks") && isWood(stripped)) color = colorMap.get(stripped + "_planks");
        if (color == null && stripped.contains("_wood")) color = colorMap.get(stripped.replace("_wood", "_log"));
        if (color == null && blockId.contains("path")) color = colorMap.get("minecraft:dirt");
        if (color == null && stripped.contains("pointed_dripstone")) color = colorMap.get("minecraft:dripstone_block");

        if (color == null) return getFallbackHashColor(blockId);
        return color;
    }

    private boolean isWood(String id) {
        return id.contains("oak") || id.contains("spruce") || id.contains("birch") ||
            id.contains("jungle") || id.contains("acacia") || id.contains("cherry") ||
            id.contains("mangrove") || id.contains("dark_oak");
    }

    private int getFallbackHashColor(String id) {
        int hash = id.hashCode();
        int r = (hash & 0xFF0000) >> 16;
        int g = (hash & 0x00FF00) >> 8;
        int b = (hash & 0x0000FF);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private boolean shouldTintGrass(String id) {
        return id.contains("grass_block") || id.equals("minecraft:grass") || id.contains("short_grass") || id.contains("tall_grass");
    }
    private boolean shouldTintFoliage(String id) {
        return id.contains("leaves") || id.contains("vine") || id.contains("fern") || id.contains("leaf_litter") || id.contains("fallen_leaves") || id.contains("azalea");
    }
    private boolean shouldTintWater(String id) {
        return id.contains("water") || id.contains("bubble_column") || id.contains("ice");
    }

    private int applyTint(int color, int tint) {
        int alpha = (color >> 24) & 0xFF;
        int r = ((color >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((color >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (color & 0xFF) * (tint & 0xFF) / 255;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    public boolean isHighQualityLoaded() {
        return highQualityLoaded;
    }
}
