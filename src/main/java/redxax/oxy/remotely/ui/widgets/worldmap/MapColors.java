package redxax.oxy.remotely.ui.widgets.worldmap;

import java.util.Map;

public class MapColors {
    public static void fillDefaults(Map<String, Integer> map) {
        reg(map, 0xFF79C05A, "grass_block", "slime_block", "bamboo");
        reg(map, 0xFF866043, "dirt", "coarse_dirt", "rooted_dirt", "podzol", "farmland", "dirt_path");
        reg(map, 0xFF7D7D7D, "stone", "andesite", "cobblestone", "stone_bricks", "gravel", "clay", "dead_brain_coral_block", "infested_stone");
        reg(map, 0xFFC2B29C, "sand", "birch_planks", "birch_log", "stripped_birch_log", "sandstone", "end_stone");
        reg(map, 0xFF4040FF, "water", "bubble_column", "ice", "packed_ice", "blue_ice");
        reg(map, 0xFF667F33, "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "azalea_leaves", "vine");
        reg(map, 0xFF8F7748, "oak_planks", "oak_log", "stripped_oak_log", "jungle_planks", "barrel", "lectern", "chest", "trapped_chest", "crafting_table");
        reg(map, 0xFF815631, "spruce_planks", "spruce_log", "stripped_spruce_log", "spruce_leaves", "campfire");
        reg(map, 0xFF1D1D21, "obsidian", "blackstone", "deepslate", "cobbled_deepslate", "basalt", "polished_blackstone");
        reg(map, 0xFFCF5B00, "lava", "magma_block");
        reg(map, 0xFFFFFFFF, "snow", "snow_block", "powder_snow", "white_wool", "quartz_block", "diorite", "birch_log");
        reg(map, 0xFFA0A0FF, "clay");
        reg(map, 0xFFD87F33, "acacia_planks", "orange_wool", "terracotta", "red_sand", "pumpkin");
        reg(map, 0xFFB24CD8, "magenta_wool", "purpur_block");
        reg(map, 0xFF6699D8, "light_blue_wool", "diamond_block");
        reg(map, 0xFFE5E533, "yellow_wool", "hay_block", "glowstone", "gold_block", "sponge");
        reg(map, 0xFF7FCC19, "lime_wool", "melon");
        reg(map, 0xFFF27FA5, "pink_wool");
        reg(map, 0xFF4C4C4C, "gray_wool", "bedrock", "netherite_block", "anvil", "spawner");
        reg(map, 0xFF999999, "light_gray_wool", "structure_block", "iron_block");
        reg(map, 0xFF4C7F99, "cyan_wool", "prismarine", "prismarine_bricks");
        reg(map, 0xFF7F3FB2, "purple_wool", "mycelium", "shulker_box");
        reg(map, 0xFF334CB2, "blue_wool", "lapis_block");
        reg(map, 0xFF664C33, "brown_wool", "dark_oak_planks", "soul_sand", "soul_soil", "dark_oak_log");
        reg(map, 0xFF667F33, "green_wool", "cactus", "moss_block");
        reg(map, 0xFF993333, "red_wool", "bricks", "nether_bricks", "red_nether_bricks", "tnt");
        reg(map, 0xFF191919, "black_wool", "coal_block");
    }

    private static void reg(Map<String, Integer> map, int color, String... keys) {
        for (String k : keys) map.put("minecraft:" + k, color);
    }
}