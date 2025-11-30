package com.github.sachin.lootin.loot;

import com.github.sachin.lootin.Lootin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.*;

/**
 * Manages loot table overrides loaded from loottables.yml
 * 
 * Supports three types of overrides:
 * 1. Container overrides (LootGenerateEvent) - for chests, barrels, etc.
 * 2. Vault overrides (BlockDispenseLootEvent) - for vault blocks
 * 3. Trial spawner overrides (BlockDispenseLootEvent) - for trial spawner blocks
 */
public class LootOverrideManager {
    
    private final Lootin plugin;
    private boolean enabled;
    
    // Container loot table overrides (for LootGenerateEvent)
    private Map<String, LootTableOverride> containerOverrides;
    
    // Vault overrides (for BlockDispenseLootEvent) - keyed by "normal" or "ominous"
    private Map<String, LootTableOverride> vaultOverrides;
    
    // Trial spawner overrides (for BlockDispenseLootEvent) - keyed by "normal" or "ominous"
    private Map<String, LootTableOverride> trialSpawnerOverrides;
    
    // ItemEdit integration (optional)
    private Object itemEditManager;
    private boolean itemEditAvailable;
    
    public LootOverrideManager(Lootin plugin) {
        this.plugin = plugin;
        this.containerOverrides = new HashMap<>();
        this.vaultOverrides = new HashMap<>();
        this.trialSpawnerOverrides = new HashMap<>();
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
            if (plugin.getServer().getPluginManager().getPlugin("YskLib") != null) {
                Class<?> yskLibClass = Class.forName("org.yusaki.lib.YskLib");
                Object yskLib = plugin.getServer().getPluginManager().getPlugin("YskLib");
                
                if (yskLib != null) {
                    java.lang.reflect.Method getItemEditManager = yskLibClass.getMethod("getItemEditManager");
                    this.itemEditManager = getItemEditManager.invoke(yskLib);
                    
                    if (this.itemEditManager != null) {
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
        vaultOverrides.clear();
        trialSpawnerOverrides.clear();
        
        File configFile = new File(plugin.getDataFolder(), "loottables.yml");
        
        if (!configFile.exists()) {
            generateDefaultConfig();
        }
        
        YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
        this.enabled = config.getBoolean("enabled", true);
        
        if (!enabled) {
            plugin.getLogger().info("Loot table overrides are disabled");
            return;
        }
        
        // Load container overrides (for LootGenerateEvent)
        ConfigurationSection containersSection = config.getConfigurationSection("containers");
        if (containersSection != null) {
            for (String key : containersSection.getKeys(false)) {
                LootTableOverride override = loadOverride(key, containersSection.getConfigurationSection(key));
                if (override != null) {
                    containerOverrides.put(key, override);
                }
            }
        }
        
        // Load spawner loot table overrides (also for LootGenerateEvent, different from trial-spawner block overrides)
        ConfigurationSection spawnersSection = config.getConfigurationSection("spawners");
        if (spawnersSection != null) {
            for (String key : spawnersSection.getKeys(false)) {
                LootTableOverride override = loadOverride(key, spawnersSection.getConfigurationSection(key));
                if (override != null) {
                    containerOverrides.put(key, override);
                }
            }
        }
        
        // Load vault overrides (for BlockDispenseLootEvent)
        ConfigurationSection vaultSection = config.getConfigurationSection("vault");
        if (vaultSection != null) {
            for (String key : vaultSection.getKeys(false)) {
                LootTableOverride override = loadOverride("vault:" + key, vaultSection.getConfigurationSection(key));
                if (override != null) {
                    vaultOverrides.put(key, override);
                }
            }
        }
        
        // Load trial spawner overrides (for BlockDispenseLootEvent)
        ConfigurationSection trialSection = config.getConfigurationSection("trial-spawner");
        if (trialSection != null) {
            for (String key : trialSection.getKeys(false)) {
                LootTableOverride override = loadOverride("trial-spawner:" + key, trialSection.getConfigurationSection(key));
                if (override != null) {
                    trialSpawnerOverrides.put(key, override);
                }
            }
        }
        
        int total = containerOverrides.size() + vaultOverrides.size() + trialSpawnerOverrides.size();
        plugin.getLogger().info("Loaded " + containerOverrides.size() + " container overrides, " +
                               vaultOverrides.size() + " vault overrides, " +
                               trialSpawnerOverrides.size() + " trial spawner overrides");
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
    
    /**
     * Resolve an item string to an ItemStack
     */
    public ItemStack resolveItem(LootEntry entry) {
        String itemRef = entry.getItem();
        
        if (entry.isItemEditItem()) {
            return getItemEditItem(entry.getItemEditId());
        }
        
        try {
            Material mat = Material.valueOf(itemRef.toUpperCase());
            return new ItemStack(mat);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Unknown material: " + itemRef);
            return null;
        }
    }
    
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
    
    // ==================== Status ====================
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isItemEditAvailable() {
        return itemEditAvailable;
    }
    
    // ==================== Container Override Methods (LootGenerateEvent) ====================
    
    public boolean hasOverride(String lootTableKey) {
        LootTableOverride override = containerOverrides.get(lootTableKey);
        return override != null && override.isEnabled();
    }
    
    public boolean hasContainerOverride(String lootTableKey) {
        return hasOverride(lootTableKey);
    }
    
    public List<ItemStack> generateLoot(String lootTableKey) {
        LootTableOverride override = containerOverrides.get(lootTableKey);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    public List<ItemStack> generateContainerLoot(String lootTableKey) {
        return generateLoot(lootTableKey);
    }
    
    // ==================== Vault Override Methods (BlockDispenseLootEvent) ====================
    
    public boolean hasVaultOverride(String type) {
        LootTableOverride override = vaultOverrides.get(type);
        return override != null && override.isEnabled();
    }
    
    public List<ItemStack> generateVaultLoot(String type) {
        LootTableOverride override = vaultOverrides.get(type);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    // ==================== Trial Spawner Override Methods (BlockDispenseLootEvent) ====================
    
    public boolean hasTrialSpawnerOverride(String type) {
        LootTableOverride override = trialSpawnerOverrides.get(type);
        return override != null && override.isEnabled();
    }
    
    public List<ItemStack> generateTrialSpawnerLoot(String type) {
        LootTableOverride override = trialSpawnerOverrides.get(type);
        if (override == null || !override.isEnabled()) {
            return null;
        }
        return override.generateLoot(this::resolveItem, new Random());
    }
    
    /**
     * Reload the configuration
     */
    public void reload() {
        loadConfig();
    }
}
