package com.github.sachin.lootin.loot;

import com.github.sachin.lootin.Lootin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Manages loot table overrides loaded from loottables.yml
 */
public class LootOverrideManager {
    
    private final Lootin plugin;
    private boolean enabled;
    
    // Override maps
    private Map<String, LootTableOverride> containerOverrides;
    private Map<String, LootTableOverride> trialSpawnerOverrides;  // "normal", "ominous"
    private Map<String, LootTableOverride> vaultOverrides;         // "normal", "ominous"
    
    // ItemEdit integration (optional)
    private Object itemEditManager;  // From YskLib
    private boolean itemEditAvailable;
    
    public LootOverrideManager(Lootin plugin) {
        this.plugin = plugin;
        this.containerOverrides = new HashMap<>();
        this.trialSpawnerOverrides = new HashMap<>();
        this.vaultOverrides = new HashMap<>();
        this.enabled = false;
        this.itemEditAvailable = false;
        
        initializeItemEdit();
        loadConfig();
    }
    
    /**
     * Initialize ItemEdit integration via YskLib
     */
    private void initializeItemEdit() {
        try {
            // Check if YskLib is available
            if (plugin.getServer().getPluginManager().getPlugin("YskLib") != null) {
                Class<?> yskLibClass = Class.forName("org.yusaki.lib.YskLib");
                Object yskLib = plugin.getServer().getPluginManager().getPlugin("YskLib");
                
                if (yskLib != null) {
                    // Get ItemEditManager from YskLib
                    java.lang.reflect.Method getItemEditManager = yskLibClass.getMethod("getItemEditManager");
                    this.itemEditManager = getItemEditManager.invoke(yskLib);
                    
                    if (this.itemEditManager != null) {
                        // Check if initialized
                        java.lang.reflect.Method isInitialized = this.itemEditManager.getClass().getMethod("isInitialized");
                        this.itemEditAvailable = (boolean) isInitialized.invoke(this.itemEditManager);
                        
                        if (itemEditAvailable) {
                            plugin.getLogger().info("ItemEdit integration enabled via YskLib");
                        }
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().info("YskLib/ItemEdit not available - custom items will be disabled");
            this.itemEditAvailable = false;
        }
    }
    
    /**
     * Load or reload the loottables.yml configuration
     */
    public void loadConfig() {
        containerOverrides.clear();
        trialSpawnerOverrides.clear();
        vaultOverrides.clear();
        
        File configFile = new File(plugin.getDataFolder(), "loottables.yml");
        
        // Create default config if it doesn't exist
        if (!configFile.exists()) {
            generateDefaultConfig();
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = config.getBoolean("enabled", true);
        
        if (!enabled) {
            plugin.getLogger().info("Loot table overrides are disabled");
            return;
        }
        
        // Load container overrides
        ConfigurationSection containersSection = config.getConfigurationSection("containers");
        if (containersSection != null) {
            for (String key : containersSection.getKeys(false)) {
                LootTableOverride override = loadOverride(key, containersSection.getConfigurationSection(key));
                if (override != null) {
                    containerOverrides.put(key, override);
                }
            }
        }
        
        // Load trial spawner overrides
        ConfigurationSection trialSection = config.getConfigurationSection("trial-spawner");
        if (trialSection != null) {
            for (String key : trialSection.getKeys(false)) {
                LootTableOverride override = loadOverride("trial-spawner:" + key, trialSection.getConfigurationSection(key));
                if (override != null) {
                    trialSpawnerOverrides.put(key, override);
                }
            }
        }
        
        // Load vault overrides
        ConfigurationSection vaultSection = config.getConfigurationSection("vault");
        if (vaultSection != null) {
            for (String key : vaultSection.getKeys(false)) {
                LootTableOverride override = loadOverride("vault:" + key, vaultSection.getConfigurationSection(key));
                if (override != null) {
                    vaultOverrides.put(key, override);
                }
            }
        }
        
        plugin.getLogger().info("Loaded " + containerOverrides.size() + " container overrides, " +
                               trialSpawnerOverrides.size() + " trial spawner overrides, " +
                               vaultOverrides.size() + " vault overrides");
    }
    
    /**
     * Load a single override from a configuration section
     */
    private LootTableOverride loadOverride(String key, ConfigurationSection section) {
        if (section == null) return null;
        
        boolean enabled = section.getBoolean("enabled", true);
        String rollsStr = section.getString("rolls", "1");
        
        LootTableOverride override = new LootTableOverride(key);
        override.setEnabled(enabled);
        override.parseRolls(rollsStr);
        
        List<Map<?, ?>> itemsList = section.getMapList("items");
        for (Map<?, ?> itemMap : itemsList) {
            try {
                String item = String.valueOf(itemMap.get("item"));
                Object amountObj = itemMap.get("amount");
                String amount = amountObj != null ? String.valueOf(amountObj) : "1";
                Object chanceObj = itemMap.get("chance");
                double chance = chanceObj != null ? Double.parseDouble(String.valueOf(chanceObj)) : 1.0;
                Object weightObj = itemMap.get("weight");
                int weight = weightObj != null ? Integer.parseInt(String.valueOf(weightObj)) : 1;
                
                LootEntry entry = new LootEntry(item, amount, chance, weight);
                override.addItem(entry);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load loot entry in " + key + ": " + e.getMessage());
            }
        }
        
        return override;
    }
    
    /**
     * Generate default loottables.yml by copying from plugin resources
     */
    public void generateDefaultConfig() {
        File configFile = new File(plugin.getDataFolder(), "loottables.yml");
        
        // Save the default resource file from the JAR
        if (!configFile.exists()) {
            plugin.saveResource("loottables.yml", false);
            plugin.getLogger().info("Generated default loottables.yml from vanilla loot table data");
        }
    }
    
    /**
     * Regenerate the default config (reset)
     */
    public void regenerateDefaults() {
        File configFile = new File(plugin.getDataFolder(), "loottables.yml");
        if (configFile.exists()) {
            configFile.delete();
        }
        plugin.saveResource("loottables.yml", false);
        plugin.getLogger().info("Reset loottables.yml to defaults");
        loadConfig();
    }
    
    // ==================== LEGACY METHODS (kept for reference but no longer used) ====================
    
    /**
     * @deprecated No longer used - loottables.yml is now a resource file with vanilla data
     */
    @Deprecated
    private void generateDefaultConfigOld() {
        File configFile = new File(plugin.getDataFolder(), "loottables.yml");
        YamlConfiguration config = new YamlConfiguration();
        
        // Header
        config.options().setHeader(Arrays.asList(
            "============================================================",
            "LOOTIN - LOOT TABLE CONFIGURATION",
            "============================================================",
            "This file contains loot tables with vanilla-like rates.",
            "Edit items, amounts, chances, weights as desired.",
            "Add ItemEdit items with: \"itemedit:your_item_id\"",
            "",
            "Set enabled: false on a table to use vanilla behavior instead.",
            "Use /lootin generatetables --reset to regenerate defaults.",
            "============================================================"
        ));
        
        config.set("enabled", true);
        
        // Generate container overrides for common loot tables
        generateContainerDefaults(config);
        
        // Generate trial spawner overrides
        generateTrialSpawnerDefaults(config);
        
        // Generate vault overrides
        generateVaultDefaults(config);
        
        try {
            config.save(configFile);
            plugin.getLogger().info("Generated default loottables.yml");
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save loottables.yml: " + e.getMessage());
        }
    }
    
    /**
     * Generate container loot table defaults for ALL vanilla loot tables
     */
    private void generateContainerDefaults(YamlConfiguration config) {
        // ==================== ABANDONED MINESHAFT ====================
        addLootTable(config, "minecraft:chests/abandoned_mineshaft", "1-3", Arrays.asList(
            createItemMap("RAIL", "4-8", 1.0, 20),
            createItemMap("POWERED_RAIL", "1-4", 1.0, 5),
            createItemMap("DETECTOR_RAIL", "1-4", 1.0, 5),
            createItemMap("ACTIVATOR_RAIL", "1-4", 1.0, 5),
            createItemMap("TORCH", "1-16", 1.0, 15),
            createItemMap("COAL", "3-8", 1.0, 10),
            createItemMap("BREAD", "1-3", 1.0, 15),
            createItemMap("NAME_TAG", "1", 1.0, 30),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 20),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 1),
            createItemMap("IRON_PICKAXE", "1", 1.0, 5),
            createItemMap("MELON_SEEDS", "2-4", 1.0, 10),
            createItemMap("PUMPKIN_SEEDS", "2-4", 1.0, 10),
            createItemMap("BEETROOT_SEEDS", "2-4", 1.0, 10),
            createItemMap("DIAMOND", "1-2", 1.0, 3),
            createItemMap("IRON_INGOT", "1-5", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 5),
            createItemMap("REDSTONE", "4-9", 1.0, 5),
            createItemMap("LAPIS_LAZULI", "4-9", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 10)
        ));
        
        // ==================== BURIED TREASURE ====================
        addLootTable(config, "minecraft:chests/buried_treasure", "5-8", Arrays.asList(
            createItemMap("HEART_OF_THE_SEA", "1", 1.0, 1),
            createItemMap("DIAMOND", "1-4", 1.0, 14),
            createItemMap("IRON_INGOT", "1-4", 1.0, 11),
            createItemMap("GOLD_INGOT", "1-4", 1.0, 14),
            createItemMap("EMERALD", "4-8", 1.0, 14),
            createItemMap("PRISMARINE_CRYSTALS", "1-5", 1.0, 5),
            createItemMap("COOKED_COD", "2-4", 1.0, 15),
            createItemMap("COOKED_SALMON", "2-4", 1.0, 15),
            createItemMap("LEATHER_CHESTPLATE", "1", 1.0, 5),
            createItemMap("IRON_SWORD", "1", 1.0, 5),
            createItemMap("TNT", "1-2", 1.0, 5),
            createItemMap("POTION", "1", 1.0, 3)
        ));
        
        // ==================== DESERT PYRAMID ====================
        addLootTable(config, "minecraft:chests/desert_pyramid", "2-4", Arrays.asList(
            createItemMap("BONE", "4-6", 1.0, 25),
            createItemMap("ROTTEN_FLESH", "3-7", 1.0, 25),
            createItemMap("GUNPOWDER", "1-8", 1.0, 20),
            createItemMap("SAND", "1-8", 1.0, 25),
            createItemMap("STRING", "1-8", 1.0, 25),
            createItemMap("SPIDER_EYE", "1-3", 1.0, 25),
            createItemMap("GOLD_INGOT", "2-7", 1.0, 15),
            createItemMap("IRON_INGOT", "1-5", 1.0, 15),
            createItemMap("EMERALD", "1-3", 1.0, 15),
            createItemMap("DIAMOND", "1-3", 1.0, 5),
            createItemMap("SADDLE", "1", 1.0, 20),
            createItemMap("IRON_HORSE_ARMOR", "1", 1.0, 15),
            createItemMap("GOLDEN_HORSE_ARMOR", "1", 1.0, 10),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 20),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 20),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 2),
            createItemMap("DUNE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== END CITY TREASURE ====================
        addLootTable(config, "minecraft:chests/end_city_treasure", "2-6", Arrays.asList(
            createItemMap("DIAMOND", "2-7", 1.0, 15),
            createItemMap("IRON_INGOT", "4-8", 1.0, 20),
            createItemMap("GOLD_INGOT", "2-7", 1.0, 20),
            createItemMap("EMERALD", "2-6", 1.0, 15),
            createItemMap("BEETROOT_SEEDS", "1-10", 1.0, 10),
            createItemMap("SADDLE", "1", 1.0, 15),
            createItemMap("IRON_HORSE_ARMOR", "1", 1.0, 10),
            createItemMap("GOLDEN_HORSE_ARMOR", "1", 1.0, 8),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("DIAMOND_SWORD", "1", 1.0, 8),
            createItemMap("DIAMOND_BOOTS", "1", 1.0, 8),
            createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 8),
            createItemMap("DIAMOND_LEGGINGS", "1", 1.0, 8),
            createItemMap("DIAMOND_HELMET", "1", 1.0, 8),
            createItemMap("DIAMOND_PICKAXE", "1", 1.0, 8),
            createItemMap("DIAMOND_SHOVEL", "1", 1.0, 8),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 2),
            createItemMap("SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== IGLOO CHEST ====================
        addLootTable(config, "minecraft:chests/igloo_chest", "2-8", Arrays.asList(
            createItemMap("GOLDEN_APPLE", "1", 1.0, 100),
            createItemMap("COAL", "1-4", 1.0, 15),
            createItemMap("APPLE", "1-3", 1.0, 15),
            createItemMap("WHEAT", "2-3", 1.0, 10),
            createItemMap("GOLD_NUGGET", "1-3", 1.0, 10),
            createItemMap("ROTTEN_FLESH", "1", 1.0, 10),
            createItemMap("STONE_AXE", "1", 1.0, 2),
            createItemMap("EMERALD", "1", 1.0, 1)
        ));
        
        // ==================== JUNGLE TEMPLE ====================
        addLootTable(config, "minecraft:chests/jungle_temple", "2-6", Arrays.asList(
            createItemMap("DIAMOND", "1-3", 1.0, 15),
            createItemMap("IRON_INGOT", "1-5", 1.0, 25),
            createItemMap("GOLD_INGOT", "2-7", 1.0, 15),
            createItemMap("EMERALD", "1-3", 1.0, 15),
            createItemMap("BONE", "4-6", 1.0, 25),
            createItemMap("ROTTEN_FLESH", "3-7", 1.0, 25),
            createItemMap("BAMBOO", "1-3", 1.0, 15),
            createItemMap("SADDLE", "1", 1.0, 15),
            createItemMap("IRON_HORSE_ARMOR", "1", 1.0, 10),
            createItemMap("GOLDEN_HORSE_ARMOR", "1", 1.0, 8),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 10),
            createItemMap("WILD_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== JUNGLE TEMPLE DISPENSER ====================
        addLootTable(config, "minecraft:chests/jungle_temple_dispenser", "1-2", Arrays.asList(
            createItemMap("ARROW", "2-7", 1.0, 100)
        ));
        
        // ==================== NETHER BRIDGE (FORTRESS) ====================
        addLootTable(config, "minecraft:chests/nether_bridge", "2-4", Arrays.asList(
            createItemMap("DIAMOND", "1-3", 1.0, 5),
            createItemMap("IRON_INGOT", "1-5", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 15),
            createItemMap("GOLDEN_SWORD", "1", 1.0, 10),
            createItemMap("GOLDEN_CHESTPLATE", "1", 1.0, 10),
            createItemMap("FLINT_AND_STEEL", "1", 1.0, 5),
            createItemMap("NETHER_WART", "3-7", 1.0, 10),
            createItemMap("SADDLE", "1", 1.0, 10),
            createItemMap("GOLDEN_HORSE_ARMOR", "1", 1.0, 8),
            createItemMap("IRON_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 2),
            createItemMap("OBSIDIAN", "2-4", 1.0, 5),
            createItemMap("RIB_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== PILLAGER OUTPOST ====================
        addLootTable(config, "minecraft:chests/pillager_outpost", "2-3", Arrays.asList(
            createItemMap("CROSSBOW", "1", 1.0, 100),
            createItemMap("WHEAT", "3-5", 1.0, 15),
            createItemMap("CARROT", "3-5", 1.0, 15),
            createItemMap("POTATO", "2-5", 1.0, 15),
            createItemMap("DARK_OAK_LOG", "2-3", 1.0, 10),
            createItemMap("EXPERIENCE_BOTTLE", "1", 1.0, 10),
            createItemMap("ARROW", "2-7", 1.0, 15),
            createItemMap("TRIPWIRE_HOOK", "1-3", 1.0, 5),
            createItemMap("IRON_INGOT", "1-3", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 5),
            createItemMap("SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2),
            createItemMap("GOAT_HORN", "1", 1.0, 2)
        ));
        
        // ==================== SHIPWRECK MAP ====================
        addLootTable(config, "minecraft:chests/shipwreck_map", "1-3", Arrays.asList(
            createItemMap("FILLED_MAP", "1", 1.0, 100),
            createItemMap("PAPER", "1-10", 1.0, 25),
            createItemMap("FEATHER", "1-5", 1.0, 15),
            createItemMap("BOOK", "1-5", 1.0, 10),
            createItemMap("COMPASS", "1", 1.0, 10),
            createItemMap("CLOCK", "1", 1.0, 5),
            createItemMap("EMPTY_MAP", "1", 1.0, 20),
            createItemMap("COAST_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== SHIPWRECK SUPPLY ====================
        addLootTable(config, "minecraft:chests/shipwreck_supply", "3-10", Arrays.asList(
            createItemMap("PAPER", "1-12", 1.0, 15),
            createItemMap("WHEAT", "8-21", 1.0, 14),
            createItemMap("CARROT", "4-8", 1.0, 14),
            createItemMap("POTATO", "2-6", 1.0, 14),
            createItemMap("POISONOUS_POTATO", "2-6", 1.0, 7),
            createItemMap("MOSS_BLOCK", "1-4", 1.0, 5),
            createItemMap("COAL", "2-8", 1.0, 10),
            createItemMap("ROTTEN_FLESH", "5-24", 1.0, 10),
            createItemMap("PUMPKIN", "1-3", 1.0, 7),
            createItemMap("BAMBOO", "1-3", 1.0, 7),
            createItemMap("GUNPOWDER", "1-5", 1.0, 5),
            createItemMap("TNT", "1-2", 1.0, 3),
            createItemMap("LEATHER_HELMET", "1", 1.0, 3),
            createItemMap("LEATHER_CHESTPLATE", "1", 1.0, 3),
            createItemMap("LEATHER_LEGGINGS", "1", 1.0, 3),
            createItemMap("LEATHER_BOOTS", "1", 1.0, 3),
            createItemMap("COAST_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== SHIPWRECK TREASURE ====================
        addLootTable(config, "minecraft:chests/shipwreck_treasure", "3-6", Arrays.asList(
            createItemMap("IRON_INGOT", "1-5", 1.0, 20),
            createItemMap("GOLD_INGOT", "1-5", 1.0, 20),
            createItemMap("EMERALD", "1-5", 1.0, 10),
            createItemMap("DIAMOND", "1", 1.0, 5),
            createItemMap("EXPERIENCE_BOTTLE", "1", 1.0, 10),
            createItemMap("IRON_NUGGET", "1-10", 1.0, 15),
            createItemMap("GOLD_NUGGET", "1-10", 1.0, 15),
            createItemMap("LAPIS_LAZULI", "1-10", 1.0, 10),
            createItemMap("COAST_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== SIMPLE DUNGEON ====================
        addLootTable(config, "minecraft:chests/simple_dungeon", "1-3", Arrays.asList(
            createItemMap("SADDLE", "1", 1.0, 20),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 15),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 2),
            createItemMap("MUSIC_DISC_13", "1", 1.0, 4),
            createItemMap("MUSIC_DISC_CAT", "1", 1.0, 4),
            createItemMap("NAME_TAG", "1", 1.0, 20),
            createItemMap("GOLDEN_HORSE_ARMOR", "1", 1.0, 10),
            createItemMap("IRON_HORSE_ARMOR", "1", 1.0, 15),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 10),
            createItemMap("IRON_INGOT", "1-4", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-4", 1.0, 5),
            createItemMap("BREAD", "1", 1.0, 20),
            createItemMap("WHEAT", "1-4", 1.0, 20),
            createItemMap("BUCKET", "1", 1.0, 10),
            createItemMap("REDSTONE", "1-4", 1.0, 15),
            createItemMap("COAL", "1-4", 1.0, 15),
            createItemMap("BONE", "1-8", 1.0, 20),
            createItemMap("ROTTEN_FLESH", "1-8", 1.0, 20),
            createItemMap("STRING", "1-8", 1.0, 20),
            createItemMap("GUNPOWDER", "1-8", 1.0, 20)
        ));
        
        // ==================== SPAWN BONUS CHEST ====================
        addLootTable(config, "minecraft:chests/spawn_bonus_chest", "3-4", Arrays.asList(
            createItemMap("WOODEN_AXE", "1", 1.0, 25),
            createItemMap("WOODEN_PICKAXE", "1", 1.0, 25),
            createItemMap("STONE_AXE", "1", 1.0, 10),
            createItemMap("STONE_PICKAXE", "1", 1.0, 10),
            createItemMap("APPLE", "1-2", 1.0, 50),
            createItemMap("BREAD", "1-2", 1.0, 50),
            createItemMap("SALMON", "1-2", 1.0, 25),
            createItemMap("OAK_PLANKS", "1-12", 1.0, 50),
            createItemMap("OAK_LOG", "1-3", 1.0, 25),
            createItemMap("STICK", "1-12", 1.0, 50),
            createItemMap("ACACIA_LOG", "1-3", 1.0, 10),
            createItemMap("BIRCH_LOG", "1-3", 1.0, 10),
            createItemMap("DARK_OAK_LOG", "1-3", 1.0, 10),
            createItemMap("JUNGLE_LOG", "1-3", 1.0, 10),
            createItemMap("SPRUCE_LOG", "1-3", 1.0, 10)
        ));
        
        // ==================== STRONGHOLD CORRIDOR ====================
        addLootTable(config, "minecraft:chests/stronghold_corridor", "2-3", Arrays.asList(
            createItemMap("ENDER_PEARL", "1", 1.0, 10),
            createItemMap("DIAMOND", "1-3", 1.0, 3),
            createItemMap("IRON_INGOT", "1-5", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 5),
            createItemMap("REDSTONE", "4-9", 1.0, 5),
            createItemMap("BREAD", "1-3", 1.0, 15),
            createItemMap("APPLE", "1-3", 1.0, 15),
            createItemMap("IRON_PICKAXE", "1", 1.0, 5),
            createItemMap("IRON_SWORD", "1", 1.0, 5),
            createItemMap("IRON_CHESTPLATE", "1", 1.0, 5),
            createItemMap("IRON_HELMET", "1", 1.0, 5),
            createItemMap("IRON_LEGGINGS", "1", 1.0, 5),
            createItemMap("IRON_BOOTS", "1", 1.0, 5),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 5),
            createItemMap("COAL", "3-8", 1.0, 10),
            createItemMap("EYE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== STRONGHOLD CROSSING ====================
        addLootTable(config, "minecraft:chests/stronghold_crossing", "1-4", Arrays.asList(
            createItemMap("IRON_INGOT", "1-5", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 5),
            createItemMap("REDSTONE", "4-9", 1.0, 5),
            createItemMap("COAL", "3-8", 1.0, 10),
            createItemMap("BREAD", "1-3", 1.0, 15),
            createItemMap("APPLE", "1-3", 1.0, 15),
            createItemMap("IRON_PICKAXE", "1", 1.0, 1),
            createItemMap("BOOK", "1-3", 1.0, 10),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 5),
            createItemMap("EYE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== STRONGHOLD LIBRARY ====================
        addLootTable(config, "minecraft:chests/stronghold_library", "2-10", Arrays.asList(
            createItemMap("BOOK", "1-3", 1.0, 100),
            createItemMap("PAPER", "2-7", 1.0, 100),
            createItemMap("EMPTY_MAP", "1", 1.0, 5),
            createItemMap("COMPASS", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 60),
            createItemMap("EYE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== UNDERWATER RUIN BIG ====================
        addLootTable(config, "minecraft:chests/underwater_ruin_big", "2-8", Arrays.asList(
            createItemMap("COAL", "1-4", 1.0, 25),
            createItemMap("GOLD_NUGGET", "1-3", 1.0, 25),
            createItemMap("EMERALD", "1", 1.0, 15),
            createItemMap("WHEAT", "2-3", 1.0, 25),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 4),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 5),
            createItemMap("GOLDEN_HELMET", "1", 1.0, 4),
            createItemMap("FISHING_ROD", "1", 1.0, 4),
            createItemMap("FILLED_MAP", "1", 1.0, 10),
            createItemMap("DIAMOND", "1", 1.0, 1)
        ));
        
        // ==================== UNDERWATER RUIN SMALL ====================
        addLootTable(config, "minecraft:chests/underwater_ruin_small", "2-8", Arrays.asList(
            createItemMap("COAL", "1-4", 1.0, 25),
            createItemMap("STONE_AXE", "1", 1.0, 5),
            createItemMap("ROTTEN_FLESH", "1", 1.0, 25),
            createItemMap("EMERALD", "1", 1.0, 5),
            createItemMap("WHEAT", "2-3", 1.0, 25),
            createItemMap("GOLDEN_HELMET", "1", 1.0, 2),
            createItemMap("FISHING_ROD", "1", 1.0, 2),
            createItemMap("FILLED_MAP", "1", 1.0, 5),
            createItemMap("LEATHER_CHESTPLATE", "1", 1.0, 5)
        ));
        
        // ==================== WOODLAND MANSION ====================
        addLootTable(config, "minecraft:chests/woodland_mansion", "1-3", Arrays.asList(
            createItemMap("LEAD", "1", 1.0, 20),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 15),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 2),
            createItemMap("MUSIC_DISC_13", "1", 1.0, 15),
            createItemMap("MUSIC_DISC_CAT", "1", 1.0, 15),
            createItemMap("NAME_TAG", "1", 1.0, 20),
            createItemMap("CHAINMAIL_CHESTPLATE", "1", 1.0, 10),
            createItemMap("DIAMOND_HOE", "1", 1.0, 15),
            createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 10),
            createItemMap("IRON_INGOT", "1-4", 1.0, 10),
            createItemMap("GOLD_INGOT", "1-4", 1.0, 5),
            createItemMap("BREAD", "1", 1.0, 20),
            createItemMap("WHEAT", "1-4", 1.0, 20),
            createItemMap("BUCKET", "1", 1.0, 10),
            createItemMap("REDSTONE", "1-4", 1.0, 15),
            createItemMap("COAL", "1-4", 1.0, 15),
            createItemMap("BONE", "1-8", 1.0, 10),
            createItemMap("GUNPOWDER", "1-8", 1.0, 10),
            createItemMap("STRING", "1-8", 1.0, 10),
            createItemMap("VEX_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== ANCIENT CITY ====================
        addLootTable(config, "minecraft:chests/ancient_city", "5-10", Arrays.asList(
            createItemMap("ECHO_SHARD", "1-3", 1.0, 30),
            createItemMap("DISC_FRAGMENT_5", "1-3", 1.0, 20),
            createItemMap("SCULK_SENSOR", "1-3", 1.0, 15),
            createItemMap("SCULK_CATALYST", "1-2", 1.0, 10),
            createItemMap("COMPASS", "1", 1.0, 10),
            createItemMap("DIAMOND", "1-3", 1.0, 10),
            createItemMap("AMETHYST_SHARD", "1-15", 1.0, 15),
            createItemMap("EXPERIENCE_BOTTLE", "1-3", 1.0, 15),
            createItemMap("GLOW_BERRIES", "1-15", 1.0, 15),
            createItemMap("IRON_LEGGINGS", "1", 1.0, 5),
            createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 5),
            createItemMap("MUSIC_DISC_OTHERSIDE", "1", 1.0, 2),
            createItemMap("DIAMOND_HOE", "1", 1.0, 5),
            createItemMap("LEAD", "1-3", 1.0, 10),
            createItemMap("NAME_TAG", "1", 1.0, 10),
            createItemMap("SADDLE", "1", 1.0, 10),
            createItemMap("DIAMOND_HORSE_ARMOR", "1", 1.0, 5),
            createItemMap("DIAMOND_LEGGINGS", "1", 1.0, 5),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 10),
            createItemMap("WARD_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 5),
            createItemMap("SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== ANCIENT CITY ICE BOX ====================
        addLootTable(config, "minecraft:chests/ancient_city_ice_box", "4-10", Arrays.asList(
            createItemMap("SUSPICIOUS_STEW", "2-6", 1.0, 20),
            createItemMap("PACKED_ICE", "2-6", 1.0, 40),
            createItemMap("BAKED_POTATO", "1-10", 1.0, 20),
            createItemMap("GOLDEN_CARROT", "1-10", 1.0, 10),
            createItemMap("SNOWBALL", "2-6", 1.0, 10)
        ));
        
        // ==================== BASTION BRIDGE ====================
        addLootTable(config, "minecraft:chests/bastion_bridge", "1-2", Arrays.asList(
            createItemMap("LODESTONE", "1", 1.0, 1),
            createItemMap("GOLD_INGOT", "4-9", 1.0, 10),
            createItemMap("IRON_INGOT", "4-9", 1.0, 10),
            createItemMap("GOLDEN_SWORD", "1", 1.0, 10),
            createItemMap("GOLDEN_CHESTPLATE", "1", 1.0, 10),
            createItemMap("GOLDEN_HELMET", "1", 1.0, 10),
            createItemMap("GOLDEN_LEGGINGS", "1", 1.0, 10),
            createItemMap("GOLDEN_BOOTS", "1", 1.0, 10),
            createItemMap("GOLDEN_AXE", "1", 1.0, 10),
            createItemMap("SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== BASTION HOGLIN STABLE ====================
        addLootTable(config, "minecraft:chests/bastion_hoglin_stable", "3-4", Arrays.asList(
            createItemMap("GOLDEN_SHOVEL", "1", 1.0, 10),
            createItemMap("GOLDEN_AXE", "1", 1.0, 10),
            createItemMap("SADDLE", "1", 1.0, 12),
            createItemMap("GOLD_BLOCK", "1", 1.0, 5),
            createItemMap("GOLDEN_HOE", "1", 1.0, 10),
            createItemMap("CRIMSON_NYLIUM", "2-7", 1.0, 10),
            createItemMap("CRIMSON_ROOTS", "2-7", 1.0, 10),
            createItemMap("CRIMSON_FUNGUS", "2-7", 1.0, 10),
            createItemMap("GLOWSTONE", "3-6", 1.0, 15),
            createItemMap("SOUL_SAND", "2-7", 1.0, 15),
            createItemMap("ANCIENT_DEBRIS", "1", 1.0, 2),
            createItemMap("NETHERITE_SCRAP", "1", 1.0, 4),
            createItemMap("SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== BASTION OTHER ====================
        addLootTable(config, "minecraft:chests/bastion_other", "1-2", Arrays.asList(
            createItemMap("GOLD_INGOT", "2-8", 1.0, 25),
            createItemMap("IRON_INGOT", "1-6", 1.0, 25),
            createItemMap("GOLDEN_SWORD", "1", 1.0, 10),
            createItemMap("GOLDEN_AXE", "1", 1.0, 10),
            createItemMap("GOLDEN_HOE", "1", 1.0, 10),
            createItemMap("GOLDEN_SHOVEL", "1", 1.0, 10),
            createItemMap("GOLDEN_PICKAXE", "1", 1.0, 10),
            createItemMap("GOLDEN_HELMET", "1", 1.0, 10),
            createItemMap("GOLDEN_CHESTPLATE", "1", 1.0, 10),
            createItemMap("GOLDEN_LEGGINGS", "1", 1.0, 10),
            createItemMap("GOLDEN_BOOTS", "1", 1.0, 10),
            createItemMap("SPECTRAL_ARROW", "10-22", 1.0, 10),
            createItemMap("CRYING_OBSIDIAN", "1-5", 1.0, 10),
            createItemMap("GILDED_BLACKSTONE", "5-8", 1.0, 10),
            createItemMap("SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== BASTION TREASURE ====================
        addLootTable(config, "minecraft:chests/bastion_treasure", "3-4", Arrays.asList(
            createItemMap("NETHERITE_INGOT", "1", 1.0, 5),
            createItemMap("ANCIENT_DEBRIS", "1", 1.0, 8),
            createItemMap("NETHERITE_SCRAP", "1", 1.0, 12),
            createItemMap("DIAMOND", "2-6", 1.0, 10),
            createItemMap("GOLD_BLOCK", "1-2", 1.0, 15),
            createItemMap("IRON_BLOCK", "1-2", 1.0, 15),
            createItemMap("DIAMOND_SWORD", "1", 1.0, 8),
            createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 8),
            createItemMap("DIAMOND_PICKAXE", "1", 1.0, 8),
            createItemMap("CROSSBOW", "1", 1.0, 8),
            createItemMap("SPECTRAL_ARROW", "8-12", 1.0, 10),
            createItemMap("SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 2),
            createItemMap("NETHERITE_UPGRADE_SMITHING_TEMPLATE", "1", 1.0, 2)
        ));
        
        // ==================== TRIAL CHAMBERS CORRIDOR ====================
        addLootTable(config, "minecraft:chests/trial_chambers/corridor", "1-3", Arrays.asList(
            createItemMap("ARROW", "4-14", 1.0, 20),
            createItemMap("TORCH", "3-6", 1.0, 20),
            createItemMap("HONEYCOMB", "2-8", 1.0, 15),
            createItemMap("SCAFFOLDING", "2-10", 1.0, 15),
            createItemMap("TUFF", "8-20", 1.0, 20),
            createItemMap("BAMBOO_PLANKS", "3-6", 1.0, 15),
            createItemMap("BONE", "2-8", 1.0, 10)
        ));
        
        // ==================== TRIAL CHAMBERS ENTRANCE ====================
        addLootTable(config, "minecraft:chests/trial_chambers/entrance", "2-3", Arrays.asList(
            createItemMap("TRIAL_KEY", "1", 1.0, 10),
            createItemMap("ARROW", "5-10", 1.0, 20),
            createItemMap("WOODEN_AXE", "1", 1.0, 10),
            createItemMap("HONEYCOMB", "2-8", 1.0, 15),
            createItemMap("STICK", "2-5", 1.0, 15),
            createItemMap("TRIAL_SPAWNER", "1", 1.0, 5)
        ));
        
        // ==================== TRIAL CHAMBERS INTERSECTION ====================
        addLootTable(config, "minecraft:chests/trial_chambers/intersection", "1-3", Arrays.asList(
            createItemMap("DIAMOND", "1-2", 1.0, 5),
            createItemMap("EMERALD", "1-4", 1.0, 10),
            createItemMap("IRON_INGOT", "1-6", 1.0, 15),
            createItemMap("GOLD_INGOT", "1-5", 1.0, 15),
            createItemMap("AMETHYST_SHARD", "8-20", 1.0, 15),
            createItemMap("BLOCK_OF_IRON", "1-2", 1.0, 5),
            createItemMap("DIAMOND_BLOCK", "1", 1.0, 2),
            createItemMap("EMERALD_BLOCK", "1", 1.0, 3)
        ));
        
        // ==================== TRIAL CHAMBERS INTERSECTION BARREL ====================
        addLootTable(config, "minecraft:chests/trial_chambers/intersection_barrel", "3-5", Arrays.asList(
            createItemMap("DIAMOND", "1-3", 1.0, 8),
            createItemMap("EMERALD", "2-5", 1.0, 12),
            createItemMap("BAKED_POTATO", "2-4", 1.0, 20),
            createItemMap("GLOW_BERRIES", "2-10", 1.0, 20),
            createItemMap("GOLDEN_CARROT", "1-3", 1.0, 15),
            createItemMap("ENDER_PEARL", "1-2", 1.0, 5),
            createItemMap("POTION", "1-2", 1.0, 8)
        ));
        
        // ==================== TRIAL CHAMBERS REWARD ====================
        addLootTable(config, "minecraft:chests/trial_chambers/reward", "1-3", Arrays.asList(
            createItemMap("EMERALD", "2-4", 1.0, 22),
            createItemMap("WIND_CHARGE", "8-12", 1.0, 22),
            createItemMap("ARROW", "2-8", 1.0, 8),
            createItemMap("DIAMOND", "1-2", 1.0, 11),
            createItemMap("EMERALD_BLOCK", "1-2", 1.0, 11),
            createItemMap("DIAMOND_AXE", "1", 1.0, 11),
            createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 6),
            createItemMap("CROSSBOW", "1", 1.0, 6),
            createItemMap("GOLDEN_APPLE", "1", 1.0, 6),
            createItemMap("ENCHANTED_BOOK", "1", 1.0, 3),
            createItemMap("BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 3),
            createItemMap("FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 3),
            createItemMap("HEAVY_CORE", "1", 1.0, 2),
            createItemMap("OMINOUS_BOTTLE", "1", 1.0, 5)
        ));
        
        // ==================== TRIAL CHAMBERS SUPPLY ====================
        addLootTable(config, "minecraft:chests/trial_chambers/supply", "3-5", Arrays.asList(
            createItemMap("ARROW", "4-14", 1.0, 20),
            createItemMap("POTION", "2", 1.0, 15),
            createItemMap("BAKED_POTATO", "2-4", 1.0, 20),
            createItemMap("GLOW_BERRIES", "2-10", 1.0, 15),
            createItemMap("ACACIA_PLANKS", "3-6", 1.0, 15),
            createItemMap("MOSS_BLOCK", "5-15", 1.0, 10),
            createItemMap("BONE_MEAL", "2-5", 1.0, 10),
            createItemMap("TUFF", "5-10", 1.0, 15),
            createItemMap("TORCH", "3-6", 1.0, 15)
        ));
        
        // ==================== VILLAGE CHESTS ====================
        generateVillageChests(config);
    }
    
    /**
     * Generate village chest loot tables
     */
    private void generateVillageChests(YamlConfiguration config) {
        // Village Armorer
        addLootTable(config, "minecraft:chests/village/village_armorer", "1-5", Arrays.asList(
            createItemMap("IRON_INGOT", "1-3", 1.0, 20),
            createItemMap("BREAD", "1-4", 1.0, 20),
            createItemMap("IRON_HELMET", "1", 1.0, 10),
            createItemMap("IRON_CHESTPLATE", "1", 1.0, 5),
            createItemMap("IRON_LEGGINGS", "1", 1.0, 5),
            createItemMap("IRON_BOOTS", "1", 1.0, 5),
            createItemMap("EMERALD", "1", 1.0, 5)
        ));
        
        // Village Butcher
        addLootTable(config, "minecraft:chests/village/village_butcher", "1-5", Arrays.asList(
            createItemMap("EMERALD", "1", 1.0, 10),
            createItemMap("PORKCHOP", "1-3", 1.0, 25),
            createItemMap("WHEAT", "1-3", 1.0, 25),
            createItemMap("BEEF", "1-3", 1.0, 25),
            createItemMap("MUTTON", "1-3", 1.0, 25),
            createItemMap("COAL", "1-3", 1.0, 15)
        ));
        
        // Village Cartographer
        addLootTable(config, "minecraft:chests/village/village_cartographer", "1-5", Arrays.asList(
            createItemMap("FILLED_MAP", "1", 1.0, 20),
            createItemMap("PAPER", "1-5", 1.0, 30),
            createItemMap("COMPASS", "1", 1.0, 10),
            createItemMap("BREAD", "1-4", 1.0, 20),
            createItemMap("STICK", "1-2", 1.0, 10)
        ));
        
        // Village Desert House
        addLootTable(config, "minecraft:chests/village/village_desert_house", "3-8", Arrays.asList(
            createItemMap("CLAY_BALL", "1", 1.0, 25),
            createItemMap("GREEN_DYE", "1", 1.0, 10),
            createItemMap("CACTUS", "1-4", 1.0, 20),
            createItemMap("WHEAT", "1-7", 1.0, 25),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("BOOK", "1", 1.0, 5),
            createItemMap("DEAD_BUSH", "1-3", 1.0, 15),
            createItemMap("EMERALD", "1-3", 1.0, 5)
        ));
        
        // Village Fisher
        addLootTable(config, "minecraft:chests/village/village_fisher", "1-5", Arrays.asList(
            createItemMap("EMERALD", "1", 1.0, 5),
            createItemMap("COD", "1-3", 1.0, 30),
            createItemMap("SALMON", "1-3", 1.0, 30),
            createItemMap("WATER_BUCKET", "1", 1.0, 5),
            createItemMap("BARREL", "1", 1.0, 10),
            createItemMap("WHEAT_SEEDS", "1-3", 1.0, 15),
            createItemMap("COAL", "1-3", 1.0, 10)
        ));
        
        // Village Fletcher
        addLootTable(config, "minecraft:chests/village/village_fletcher", "1-5", Arrays.asList(
            createItemMap("EMERALD", "1", 1.0, 5),
            createItemMap("ARROW", "1-3", 1.0, 30),
            createItemMap("FEATHER", "1-3", 1.0, 25),
            createItemMap("EGG", "1-3", 1.0, 15),
            createItemMap("FLINT", "1-3", 1.0, 25),
            createItemMap("STICK", "1-3", 1.0, 20)
        ));
        
        // Village Mason
        addLootTable(config, "minecraft:chests/village/village_mason", "1-5", Arrays.asList(
            createItemMap("CLAY_BALL", "1", 1.0, 30),
            createItemMap("FLOWER_POT", "1", 1.0, 5),
            createItemMap("STONE", "1", 1.0, 20),
            createItemMap("STONE_BRICKS", "1", 1.0, 20),
            createItemMap("BREAD", "1-4", 1.0, 20),
            createItemMap("EMERALD", "1", 1.0, 5),
            createItemMap("YELLOW_DYE", "1", 1.0, 10)
        ));
        
        // Village Plains House
        addLootTable(config, "minecraft:chests/village/village_plains_house", "3-8", Arrays.asList(
            createItemMap("GOLD_NUGGET", "1-3", 1.0, 10),
            createItemMap("DANDELION", "1", 1.0, 10),
            createItemMap("POPPY", "1", 1.0, 10),
            createItemMap("POTATO", "1-7", 1.0, 25),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("APPLE", "1-5", 1.0, 20),
            createItemMap("OAK_SAPLING", "1-2", 1.0, 10),
            createItemMap("BOOK", "1", 1.0, 5),
            createItemMap("FEATHER", "1", 1.0, 10),
            createItemMap("EMERALD", "1-4", 1.0, 5)
        ));
        
        // Village Savanna House
        addLootTable(config, "minecraft:chests/village/village_savanna_house", "3-8", Arrays.asList(
            createItemMap("GOLD_NUGGET", "1-3", 1.0, 10),
            createItemMap("ACACIA_SAPLING", "1-2", 1.0, 15),
            createItemMap("WHEAT_SEEDS", "1-5", 1.0, 25),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("TORCH", "1-2", 1.0, 10),
            createItemMap("TALL_GRASS", "1-5", 1.0, 15),
            createItemMap("EMERALD", "1-4", 1.0, 5),
            createItemMap("SADDLE", "1", 1.0, 2)
        ));
        
        // Village Shepherd
        addLootTable(config, "minecraft:chests/village/village_shepherd", "1-5", Arrays.asList(
            createItemMap("WHITE_WOOL", "1-8", 1.0, 20),
            createItemMap("BLACK_WOOL", "1-3", 1.0, 10),
            createItemMap("GRAY_WOOL", "1-3", 1.0, 10),
            createItemMap("BROWN_WOOL", "1-3", 1.0, 10),
            createItemMap("LIGHT_GRAY_WOOL", "1-3", 1.0, 10),
            createItemMap("EMERALD", "1", 1.0, 5),
            createItemMap("SHEARS", "1", 1.0, 5),
            createItemMap("WHEAT", "1-6", 1.0, 25)
        ));
        
        // Village Snowy House
        addLootTable(config, "minecraft:chests/village/village_snowy_house", "3-8", Arrays.asList(
            createItemMap("BLUE_ICE", "1", 1.0, 5),
            createItemMap("SNOW_BLOCK", "1-4", 1.0, 10),
            createItemMap("POTATO", "1-7", 1.0, 25),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("FURNACE", "1", 1.0, 5),
            createItemMap("COAL", "1-4", 1.0, 15),
            createItemMap("EMERALD", "1-4", 1.0, 5),
            createItemMap("SNOWBALL", "1-7", 1.0, 10)
        ));
        
        // Village Taiga House
        addLootTable(config, "minecraft:chests/village/village_taiga_house", "3-8", Arrays.asList(
            createItemMap("IRON_NUGGET", "1-5", 1.0, 15),
            createItemMap("FERN", "1-7", 1.0, 15),
            createItemMap("LARGE_FERN", "1-7", 1.0, 15),
            createItemMap("PUMPKIN_SEEDS", "1-5", 1.0, 10),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("SWEET_BERRIES", "1-7", 1.0, 15),
            createItemMap("SPRUCE_SAPLING", "1-5", 1.0, 10),
            createItemMap("SPRUCE_LOG", "1-5", 1.0, 20),
            createItemMap("EMERALD", "1-4", 1.0, 5)
        ));
        
        // Village Tannery
        addLootTable(config, "minecraft:chests/village/village_tannery", "1-5", Arrays.asList(
            createItemMap("LEATHER", "1-3", 1.0, 30),
            createItemMap("LEATHER_CHESTPLATE", "1", 1.0, 10),
            createItemMap("LEATHER_BOOTS", "1", 1.0, 10),
            createItemMap("LEATHER_HELMET", "1", 1.0, 10),
            createItemMap("LEATHER_LEGGINGS", "1", 1.0, 10),
            createItemMap("BREAD", "1-4", 1.0, 20),
            createItemMap("SADDLE", "1", 1.0, 5),
            createItemMap("EMERALD", "1-4", 1.0, 5)
        ));
        
        // Village Temple
        addLootTable(config, "minecraft:chests/village/village_temple", "3-8", Arrays.asList(
            createItemMap("REDSTONE", "1-4", 1.0, 20),
            createItemMap("BREAD", "1-4", 1.0, 25),
            createItemMap("ROTTEN_FLESH", "1-4", 1.0, 25),
            createItemMap("LAPIS_LAZULI", "1-4", 1.0, 15),
            createItemMap("GOLD_INGOT", "1-4", 1.0, 5),
            createItemMap("EMERALD", "1-4", 1.0, 5)
        ));
        
        // Village Toolsmith
        addLootTable(config, "minecraft:chests/village/village_toolsmith", "3-8", Arrays.asList(
            createItemMap("DIAMOND", "1-3", 1.0, 5),
            createItemMap("IRON_INGOT", "1-5", 1.0, 20),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 10),
            createItemMap("BREAD", "1-3", 1.0, 20),
            createItemMap("IRON_PICKAXE", "1", 1.0, 10),
            createItemMap("COAL", "1-3", 1.0, 15),
            createItemMap("STICK", "1-3", 1.0, 20),
            createItemMap("IRON_SHOVEL", "1", 1.0, 10)
        ));
        
        // Village Weaponsmith
        addLootTable(config, "minecraft:chests/village/village_weaponsmith", "3-8", Arrays.asList(
            createItemMap("DIAMOND", "1-3", 1.0, 5),
            createItemMap("IRON_INGOT", "1-5", 1.0, 25),
            createItemMap("GOLD_INGOT", "1-3", 1.0, 10),
            createItemMap("BREAD", "1-3", 1.0, 20),
            createItemMap("APPLE", "1-3", 1.0, 20),
            createItemMap("IRON_PICKAXE", "1", 1.0, 5),
            createItemMap("IRON_SWORD", "1", 1.0, 10),
            createItemMap("IRON_HELMET", "1", 1.0, 5),
            createItemMap("IRON_CHESTPLATE", "1", 1.0, 5),
            createItemMap("IRON_LEGGINGS", "1", 1.0, 5),
            createItemMap("IRON_BOOTS", "1", 1.0, 5)
        ));
    }
    
    /**
     * Helper to add a loot table to config
     */
    private void addLootTable(YamlConfiguration config, String key, String rolls, List<Map<String, Object>> items) {
        config.set("containers.\"" + key + "\".enabled", true);
        config.set("containers.\"" + key + "\".rolls", rolls);
        config.set("containers.\"" + key + "\".items", items);
    }
    
    /**
     * Generate trial spawner loot table defaults
     */
    private void generateTrialSpawnerDefaults(YamlConfiguration config) {
        // Normal trial spawner
        config.set("trial-spawner.normal.enabled", true);
        config.set("trial-spawner.normal.rolls", "1");
        List<Map<String, Object>> normalItems = new ArrayList<>();
        normalItems.add(createItemMap("TRIAL_KEY", "1", 0.5, 50));
        normalItems.add(createItemMap("GLOW_BERRIES", "2-10", 0.5, 12));
        normalItems.add(createItemMap("EMERALD", "1-6", 0.5, 12));
        normalItems.add(createItemMap("BAKED_POTATO", "1-3", 0.5, 12));
        normalItems.add(createItemMap("GOLDEN_CARROT", "1-3", 0.5, 4));
        normalItems.add(createItemMap("ENDER_PEARL", "1", 0.5, 4));
        normalItems.add(createItemMap("POTION", "1", 0.5, 4));
        config.set("trial-spawner.normal.items", normalItems);
        
        // Ominous trial spawner
        config.set("trial-spawner.ominous.enabled", true);
        config.set("trial-spawner.ominous.rolls", "1-2");
        List<Map<String, Object>> ominousItems = new ArrayList<>();
        ominousItems.add(createItemMap("OMINOUS_TRIAL_KEY", "1", 0.5, 50));
        ominousItems.add(createItemMap("EMERALD", "2-8", 0.5, 20));
        ominousItems.add(createItemMap("DIAMOND", "1-2", 0.5, 10));
        ominousItems.add(createItemMap("GOLDEN_APPLE", "1", 0.5, 10));
        ominousItems.add(createItemMap("ENDER_PEARL", "1-2", 0.5, 10));
        config.set("trial-spawner.ominous.items", ominousItems);
    }
    
    /**
     * Generate vault loot table defaults
     */
    private void generateVaultDefaults(YamlConfiguration config) {
        // Normal vault
        config.set("vault.normal.enabled", true);
        config.set("vault.normal.rolls", "1-3");
        List<Map<String, Object>> normalItems = new ArrayList<>();
        normalItems.add(createItemMap("EMERALD", "2-4", 1.0, 22));
        normalItems.add(createItemMap("WIND_CHARGE", "8-12", 1.0, 22));
        normalItems.add(createItemMap("ARROW", "2-8", 1.0, 8));
        normalItems.add(createItemMap("DIAMOND", "1-2", 1.0, 11));
        normalItems.add(createItemMap("EMERALD_BLOCK", "1-2", 1.0, 11));
        normalItems.add(createItemMap("DIAMOND_AXE", "1", 1.0, 11));
        normalItems.add(createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 6));
        normalItems.add(createItemMap("CROSSBOW", "1", 1.0, 6));
        normalItems.add(createItemMap("GOLDEN_APPLE", "1", 1.0, 6));
        normalItems.add(createItemMap("ENCHANTED_BOOK", "1", 1.0, 3));
        normalItems.add(createItemMap("BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 3));
        normalItems.add(createItemMap("FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 3));
        config.set("vault.normal.items", normalItems);
        
        // Ominous vault
        config.set("vault.ominous.enabled", true);
        config.set("vault.ominous.rolls", "1-3");
        List<Map<String, Object>> ominousItems = new ArrayList<>();
        ominousItems.add(createItemMap("EMERALD_BLOCK", "1-3", 1.0, 15));
        ominousItems.add(createItemMap("DIAMOND_BLOCK", "1", 1.0, 10));
        ominousItems.add(createItemMap("NETHERITE_INGOT", "1", 1.0, 5));
        ominousItems.add(createItemMap("HEAVY_CORE", "1", 1.0, 8));
        ominousItems.add(createItemMap("ENCHANTED_GOLDEN_APPLE", "1", 1.0, 5));
        ominousItems.add(createItemMap("DIAMOND_AXE", "1", 1.0, 10));
        ominousItems.add(createItemMap("DIAMOND_CHESTPLATE", "1", 1.0, 10));
        ominousItems.add(createItemMap("DIAMOND_HELMET", "1", 1.0, 10));
        ominousItems.add(createItemMap("DIAMOND_LEGGINGS", "1", 1.0, 10));
        ominousItems.add(createItemMap("DIAMOND_BOOTS", "1", 1.0, 10));
        ominousItems.add(createItemMap("BOLT_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 5));
        ominousItems.add(createItemMap("FLOW_ARMOR_TRIM_SMITHING_TEMPLATE", "1", 1.0, 5));
        config.set("vault.ominous.items", ominousItems);
    }
    
    /**
     * Helper to create item map for config
     */
    private Map<String, Object> createItemMap(String item, String amount, double chance, int weight) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", item);
        map.put("amount", amount);
        map.put("chance", chance);
        map.put("weight", weight);
        return map;
    }
    
    /**
     * Resolve an item string to an ItemStack
     */
    public ItemStack resolveItem(LootEntry entry) {
        String itemRef = entry.getItem();
        
        // Check for ItemEdit prefix
        if (entry.isItemEditItem()) {
            return getItemEditItem(entry.getItemEditId());
        }
        
        // Regular material
        try {
            Material mat = Material.valueOf(itemRef.toUpperCase());
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + itemRef);
            return null;
        }
    }
    
    /**
     * Get an ItemEdit item by ID
     */
    private ItemStack getItemEditItem(String id) {
        if (!itemEditAvailable || itemEditManager == null) {
            plugin.getLogger().warning("ItemEdit item requested but not available: " + id);
            return null;
        }
        
        try {
            java.lang.reflect.Method getItem = itemEditManager.getClass().getMethod("getItem", String.class);
            ItemStack item = (ItemStack) getItem.invoke(itemEditManager, id);
            if (item != null) {
                return item.clone();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get ItemEdit item: " + id + " - " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get all available ItemEdit item IDs
     */
    @SuppressWarnings("unchecked")
    public Set<String> getItemEditIds() {
        if (!itemEditAvailable || itemEditManager == null) {
            return Collections.emptySet();
        }
        
        try {
            java.lang.reflect.Method getItemIds = itemEditManager.getClass().getMethod("getItemIds");
            return (Set<String>) getItemIds.invoke(itemEditManager);
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }
    
    // ==================== Override Checking ====================
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean hasContainerOverride(String lootTableKey) {
        LootTableOverride override = containerOverrides.get(lootTableKey);
        return override != null && override.isEnabled();
    }
    
    public boolean hasTrialSpawnerOverride(String type) {
        LootTableOverride override = trialSpawnerOverrides.get(type);
        return override != null && override.isEnabled();
    }
    
    public boolean hasVaultOverride(String type) {
        LootTableOverride override = vaultOverrides.get(type);
        return override != null && override.isEnabled();
    }
    
    // ==================== Loot Generation ====================
    
    public List<ItemStack> generateContainerLoot(String lootTableKey) {
        LootTableOverride override = containerOverrides.get(lootTableKey);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    public List<ItemStack> generateTrialSpawnerLoot(String type) {
        LootTableOverride override = trialSpawnerOverrides.get(type);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    public List<ItemStack> generateVaultLoot(String type) {
        LootTableOverride override = vaultOverrides.get(type);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    public boolean isItemEditAvailable() {
        return itemEditAvailable;
    }
    
    /**
     * Reload the configuration
     */
    public void reload() {
        loadConfig();
    }
}
