package com.github.sachin.lootin.listeners;

import com.github.sachin.lootin.loot.LootOverrideManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Listener for intercepting and overriding loot generation.
 * 
 * Uses two events:
 * 1. LootGenerateEvent - for inventory holders (chests, barrels, etc.)
 * 2. BlockDispenseLootEvent - for vaults and trial spawners (Paper 1.21+)
 */
public class LootOverrideListener extends BaseListener {
    
    private LootOverrideManager lootManager;
    
    public LootOverrideListener() {
        this.lootManager = plugin.getLootOverrideManager();
    }
    
    /**
     * Intercept loot generation for containers (chests, barrels, etc.)
     * NOTE: This does NOT fire for vaults or trial spawners - they use BlockDispenseLootEvent
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent e) {
        if (lootManager == null || !lootManager.isEnabled()) {
            return;
        }
        
        String tableKey = e.getLootTable().getKey().toString();
        
        plugin.debug("&e[DEBUG] LootGenerateEvent fired for: " + tableKey);
        
        // Check if we have an override for this loot table
        if (lootManager.hasOverride(tableKey)) {
            List<ItemStack> customLoot = lootManager.generateLoot(tableKey);
            
            if (customLoot != null) {
                // Replace vanilla loot with our custom loot
                e.getLoot().clear();
                e.getLoot().addAll(customLoot);
                
                plugin.debug("&e[DEBUG] Overrode loot table: " + tableKey + " with " + customLoot.size() + " items");
            }
        }
    }
    
    /**
     * Register BlockDispenseLootEvent listener for vaults and trial spawners (Paper 1.21+)
     * Uses reflection to avoid compile-time dependency on Paper API
     */
    @SuppressWarnings("unchecked")
    public void registerBlockDispenseLootListener() {
        try {
            // Check if BlockDispenseLootEvent exists (Paper 1.21+)
            Class<? extends Event> eventClass = (Class<? extends Event>) 
                Class.forName("org.bukkit.event.block.BlockDispenseLootEvent");
            
            // Get methods we'll need via reflection
            Method getBlockMethod = eventClass.getMethod("getBlock");
            Method setDispensedLootMethod = eventClass.getMethod("setDispensedLoot", List.class);
            
            // Create EventExecutor that handles the event
            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) return;
                if (lootManager == null || !lootManager.isEnabled()) return;
                
                try {
                    Block block = (Block) getBlockMethod.invoke(event);
                    Material blockType = block.getType();
                    
                    plugin.debug("&e[DEBUG] BlockDispenseLootEvent fired for block: " + blockType.name());
                    
                    if (blockType.name().equals("VAULT")) {
                        handleVaultLoot(event, block, setDispensedLootMethod);
                    } else if (blockType.name().equals("TRIAL_SPAWNER")) {
                        handleTrialSpawnerLoot(event, block, setDispensedLootMethod);
                    }
                } catch (Exception e) {
                    plugin.debug("&c[DEBUG] Error handling BlockDispenseLootEvent: " + e.getMessage());
                }
            };
            
            // Register using programmatic API
            plugin.getServer().getPluginManager().registerEvent(
                eventClass,
                this,
                EventPriority.HIGH,
                executor,
                plugin,
                false
            );
            
            plugin.getLogger().info("Registered BlockDispenseLootEvent listener for vaults and trial spawners");
            
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("BlockDispenseLootEvent not available - vault/trial spawner overrides require Paper 1.21+");
        } catch (NoSuchMethodException e) {
            plugin.getLogger().warning("BlockDispenseLootEvent API changed: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register BlockDispenseLootEvent listener: " + e.getMessage());
        }
    }
    
    /**
     * Handle vault loot override
     */
    private void handleVaultLoot(Event event, Block block, Method setDispensedLootMethod) {
        try {
            boolean isOminous = isOminousVault(block);
            String type = isOminous ? "ominous" : "normal";
            
            plugin.debug("&e[DEBUG] Vault type: " + type);
            
            if (lootManager.hasVaultOverride(type)) {
                List<ItemStack> customLoot = lootManager.generateVaultLoot(type);
                
                if (customLoot != null && !customLoot.isEmpty()) {
                    setDispensedLootMethod.invoke(event, customLoot);
                    plugin.debug("&e[DEBUG] Overrode vault (" + type + ") loot with " + customLoot.size() + " items");
                }
            } else {
                plugin.debug("&e[DEBUG] No vault override for type: " + type);
            }
        } catch (Exception ex) {
            plugin.debug("&c[DEBUG] Error handling vault loot: " + ex.getMessage());
        }
    }
    
    /**
     * Handle trial spawner loot override
     */
    private void handleTrialSpawnerLoot(Event event, Block block, Method setDispensedLootMethod) {
        try {
            boolean isOminous = isOminousTrialSpawner(block);
            String type = isOminous ? "ominous" : "normal";
            
            plugin.debug("&e[DEBUG] Trial spawner type: " + type);
            
            if (lootManager.hasTrialSpawnerOverride(type)) {
                List<ItemStack> customLoot = lootManager.generateTrialSpawnerLoot(type);
                
                if (customLoot != null && !customLoot.isEmpty()) {
                    setDispensedLootMethod.invoke(event, customLoot);
                    plugin.debug("&e[DEBUG] Overrode trial spawner (" + type + ") loot with " + customLoot.size() + " items");
                }
            } else {
                plugin.debug("&e[DEBUG] No trial spawner override for type: " + type);
            }
        } catch (Exception ex) {
            plugin.debug("&c[DEBUG] Error handling trial spawner loot: " + ex.getMessage());
        }
    }
    
    /**
     * Check if a vault block is ominous using reflection
     */
    private boolean isOminousVault(Block block) {
        try {
            org.bukkit.block.data.BlockData data = block.getBlockData();
            Class<?> vaultDataClass = Class.forName("org.bukkit.block.data.type.Vault");
            if (vaultDataClass.isInstance(data)) {
                Method isOminousMethod = vaultDataClass.getMethod("isOminous");
                return (boolean) isOminousMethod.invoke(data);
            }
        } catch (Exception e) {
            // Vault block data not available or no isOminous method
        }
        return false;
    }
    
    /**
     * Check if a trial spawner block is ominous using reflection
     */
    private boolean isOminousTrialSpawner(Block block) {
        // Try block data approach first
        try {
            org.bukkit.block.data.BlockData data = block.getBlockData();
            Class<?> trialSpawnerDataClass = Class.forName("org.bukkit.block.data.type.TrialSpawner");
            if (trialSpawnerDataClass.isInstance(data)) {
                Method isOminousMethod = trialSpawnerDataClass.getMethod("isOminous");
                return (boolean) isOminousMethod.invoke(data);
            }
        } catch (Exception e) {
            // BlockData approach not available
        }
        
        // Try block state approach as fallback
        try {
            Class<?> trialSpawnerClass = Class.forName("org.bukkit.block.TrialSpawner");
            Object state = block.getState();
            if (trialSpawnerClass.isInstance(state)) {
                Method isOminousMethod = trialSpawnerClass.getMethod("isOminous");
                return (boolean) isOminousMethod.invoke(state);
            }
        } catch (Exception e) {
            // Block state approach not available
        }
        
        return false;
    }
}
