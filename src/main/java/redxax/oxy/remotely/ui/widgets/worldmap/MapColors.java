package redxax.oxy.remotely.ui.widgets.worldmap;

import java.util.Map;

public class MapColors {
    public static void fillDefaults(Map<String, Integer> map) {
        reg(map, 0xFF79C05A, "grass_block", "slime_block", "bamboo", "moss_block", "moss_carpet", "big_dripleaf", "small_dripleaf");
        reg(map, 0xFF4C7632, "fern", "large_fern", "short_grass", "tall_grass", "azalea", "flowering_azalea");
        reg(map, 0xFF59AE30, "oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "azalea_leaves", "vine", "mangrove_leaves");
        reg(map, 0xFF619961, "spruce_leaves", "birch_leaves");
        reg(map, 0xFF80A755, "lily_pad", "sugar_cane", "cactus");

        reg(map, 0xFF866043, "dirt", "coarse_dirt", "rooted_dirt", "podzol", "farmland", "dirt_path", "granite", "polished_granite");
        reg(map, 0xFF7D7D7D, "stone", "andesite", "polished_andesite", "cobblestone", "stone_bricks", "gravel", "clay", "dead_brain_coral_block", "infested_stone");
        reg(map, 0xFF646464, "tuff", "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "deepslate_tiles", "bedrock");
        reg(map, 0xFF383838, "basalt", "smooth_basalt", "polished_basalt");

        reg(map, 0xFFDDBB7E, "sand", "sandstone", "chiseled_sandstone", "cut_sandstone", "smooth_sandstone", "end_stone");
        reg(map, 0xFFD47C3C, "red_sand", "red_sandstone", "chiseled_red_sandstone", "cut_red_sandstone");

        reg(map, 0x883F76E4, "water", "bubble_column");
        reg(map, 0xFF90B5FF, "ice", "packed_ice", "blue_ice", "frosted_ice");

        reg(map, 0xFF8F7748, "oak_planks", "oak_log", "stripped_oak_log", "jungle_planks", "jungle_log", "stripped_jungle_log", "barrel", "chest", "crafting_table", "bookshelf", "lectern", "composter");
        reg(map, 0xFF815631, "spruce_planks", "spruce_log", "stripped_spruce_log", "campfire");
        reg(map, 0xFFC2B29C, "birch_planks", "birch_log", "stripped_birch_log");
        reg(map, 0xFF382513, "dark_oak_planks", "dark_oak_log", "stripped_dark_oak_log");
        reg(map, 0xFFA85A32, "acacia_planks", "acacia_log", "stripped_acacia_log");
        reg(map, 0xFF6D492D, "mangrove_planks", "mangrove_log", "stripped_mangrove_log");
        reg(map, 0xFFEBB0BA, "cherry_planks", "cherry_log", "stripped_cherry_log", "cherry_leaves");
        reg(map, 0xFF985E88, "crimson_stem", "stripped_crimson_stem", "crimson_hyphae", "crimson_planks");
        reg(map, 0xFF3A8E8C, "warped_stem", "stripped_warped_stem", "warped_hyphae", "warped_planks");

        reg(map, 0xFF723232, "netherrack", "nether_bricks", "red_nether_bricks", "cracked_nether_bricks", "chiseled_nether_bricks");
        reg(map, 0xFF1D1D21, "obsidian", "blackstone", "polished_blackstone", "polished_blackstone_bricks", "ancient_debris", "crying_obsidian", "respawn_anchor");
        reg(map, 0xFFCF5B00, "lava", "magma_block", "shroomlight");
        reg(map, 0xFF664C33, "soul_sand", "soul_soil");
        reg(map, 0xFFF4E58C, "glowstone");

        reg(map, 0xFFFFFFFF, "snow", "snow_block", "powder_snow", "white_wool", "quartz_block", "diorite", "polished_diorite", "calcite", "smooth_quartz", "white_concrete", "white_concrete_powder", "sea_lantern");

        reg(map, 0xFFD87F33, "orange_wool", "terracotta", "pumpkin", "jack_o_lantern", "orange_concrete");
        reg(map, 0xFFB24CD8, "magenta_wool", "purpur_block", "magenta_concrete");
        reg(map, 0xFF6699D8, "light_blue_wool", "diamond_block", "beacon", "light_blue_concrete");
        reg(map, 0xFFE5E533, "yellow_wool", "hay_block", "gold_block", "sponge", "wet_sponge", "yellow_concrete");
        reg(map, 0xFF7FCC19, "lime_wool", "melon", "lime_concrete");
        reg(map, 0xFFF27FA5, "pink_wool", "pink_concrete");
        reg(map, 0xFF4C4C4C, "gray_wool", "netherite_block", "anvil", "spawner", "gray_concrete");
        reg(map, 0xFF999999, "light_gray_wool", "structure_block", "iron_block", "light_gray_concrete");
        reg(map, 0xFF4C7F99, "cyan_wool", "prismarine", "prismarine_bricks", "cyan_concrete");
        reg(map, 0xFF7F3FB2, "purple_wool", "mycelium", "shulker_box", "purple_concrete", "amethyst_block", "budding_amethyst");
        reg(map, 0xFF334CB2, "blue_wool", "lapis_block", "blue_concrete");
        reg(map, 0xFF664C33, "brown_wool", "brown_concrete");
        reg(map, 0xFF667F33, "green_wool", "green_concrete");
        reg(map, 0xFF993333, "red_wool", "bricks", "tnt", "red_concrete");
        reg(map, 0xFF191919, "black_wool", "coal_block", "black_concrete");

        reg(map, 0xFFFF0000, "poppy", "rose_bush", "red_tulip");
        reg(map, 0xFFFFFF00, "dandelion", "sunflower");
        reg(map, 0xFF3366CC, "cornflower", "blue_orchid");
        reg(map, 0xFFFFFFFF, "azure_bluet", "oxeye_daisy", "lily_of_the_valley", "white_tulip");
        reg(map, 0xFFFFC0CB, "pink_tulip", "peony");
        reg(map, 0xFFA020F0, "allium", "lilac");
    }

    private static void reg(Map<String, Integer> map, int color, String... keys) {
        for (String k : keys) map.put("minecraft:" + k, color);
    }
}
