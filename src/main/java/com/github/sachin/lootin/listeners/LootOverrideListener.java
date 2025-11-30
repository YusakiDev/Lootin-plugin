package com.github.sachin.lootin.listeners;

import com.github.sachin.lootin.Lootin;
import com.github.sachin.lootin.loot.LootOverrideManager;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Listener for intercepting and overriding loot generation
 */
public class LootOverrideListener extends BaseListener {
    
    private LootOverrideManager lootManager;
    
    public LootOverrideListener() {
        this.lootManager = plugin.getLootOverrideManager();
    }
    
    /**
     * Intercept container loot generation (chests, barrels, trial chamber chests, etc.)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onLootGenerate(LootGenerateEvent e) {
        if (lootManager == null || !lootManager.isEnabled()) {
            return;
        }
        
        String tableKey = e.getLootTable().getKey().toString();
        
        // Check if we have an override for this loot table
        if (lootManager.hasContainerOverride(tableKey)) {
            List<ItemStack> customLoot = lootManager.generateContainerLoot(tableKey);
            
            if (customLoot != null) {
                // Replace vanilla loot with our custom loot
                e.getLoot().clear();
                e.getLoot().addAll(customLoot);
                
                plugin.debug("&e[DEBUG] Overrode loot table: " + tableKey + " with " + customLoot.size() + " items");
            }
        }
    }
    
    /**
     * Intercept trial spawner and vault loot ejection (Paper 1.21+)
     * Note: This uses BlockDispenseLootEvent which may not be available on all versions
     * Uses reflection to avoid compile-time dependency on Paper API
     */
    public void registerBlockDispenseLootListener() {
        try {
            // Check if BlockDispenseLootEvent exists (Paper 1.21+)
            Class<?> eventClass = Class.forName("org.bukkit.event.block.BlockDispenseLootEvent");
            
            // Create and register a dynamic listener using reflection
            Object listener = createBlockDispenseLootListener(eventClass);
            if (listener != null) {
                plugin.getServer().getPluginManager().registerEvents(
                    (org.bukkit.event.Listener) listener, plugin
                );
                plugin.getLogger().info("Registered BlockDispenseLootEvent listener for trial spawners and vaults");
            }
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("BlockDispenseLootEvent not available - trial spawner/vault overrides disabled");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to register BlockDispenseLootEvent listener: " + e.getMessage());
        }
    }
    
    /**
     * Create a BlockDispenseLootEvent listener using reflection
     */
    private Object createBlockDispenseLootListener(Class<?> eventClass) {
        try {
            // Create an anonymous listener class at runtime that handles the event
            return new BlockDispenseLootHandlerReflective(lootManager, plugin, eventClass);
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create BlockDispenseLootEvent handler: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Inner class to handle BlockDispenseLootEvent using reflection
     * This avoids compile-time dependency on Paper 1.21+ API
     */
    private static class BlockDispenseLootHandlerReflective implements org.bukkit.event.Listener {
        
        private final LootOverrideManager lootManager;
        private final Lootin plugin;
        private final Class<?> eventClass;
        private Method getBlockMethod;
        private Method setDispensedLootMethod;
        
        public BlockDispenseLootHandlerReflective(LootOverrideManager lootManager, Lootin plugin, Class<?> eventClass) {
            this.lootManager = lootManager;
            this.plugin = plugin;
            this.eventClass = eventClass;
            
            try {
                this.getBlockMethod = eventClass.getMethod("getBlock");
                this.setDispensedLootMethod = eventClass.getMethod("setDispensedLoot", List.class);
            } catch (NoSuchMethodException e) {
                plugin.getLogger().warning("Failed to find BlockDispenseLootEvent methods: " + e.getMessage());
            }
        }
        
        @EventHandler(priority = EventPriority.HIGH)
        public void onBlockDispenseLoot(org.bukkit.event.Event event) {
            if (!eventClass.isInstance(event)) {
                return;
            }
            
            if (lootManager == null || !lootManager.isEnabled()) {
                return;
            }
            
            try {
                Block block = (Block) getBlockMethod.invoke(event);
                Material blockType = block.getType();
                
                // Check for trial spawner
                if (blockType.name().equals("TRIAL_SPAWNER")) {
                    handleTrialSpawnerLoot(event, block);
                }
                // Check for vault
                else if (blockType.name().equals("VAULT")) {
                    handleVaultLoot(event, block);
                }
            } catch (Exception e) {
                plugin.debug("&c[DEBUG] Error in BlockDispenseLoot handler: " + e.getMessage());
            }
        }
        
        private void handleTrialSpawnerLoot(org.bukkit.event.Event event, Block block) {
            try {
                boolean isOminous = isOminousTrialSpawner(block);
                String type = isOminous ? "ominous" : "normal";
                
                if (lootManager.hasTrialSpawnerOverride(type)) {
                    List<ItemStack> customLoot = lootManager.generateTrialSpawnerLoot(type);
                    
                    if (customLoot != null) {
                        setDispensedLootMethod.invoke(event, customLoot);
                        plugin.debug("&e[DEBUG] Overrode trial spawner (" + type + ") loot with " + customLoot.size() + " items");
                    }
                }
            } catch (Exception ex) {
                plugin.debug("&c[DEBUG] Error handling trial spawner loot: " + ex.getMessage());
            }
        }
        
        private void handleVaultLoot(org.bukkit.event.Event event, Block block) {
            try {
                boolean isOminous = isOminousVault(block);
                String type = isOminous ? "ominous" : "normal";
                
                if (lootManager.hasVaultOverride(type)) {
                    List<ItemStack> customLoot = lootManager.generateVaultLoot(type);
                    
                    if (customLoot != null) {
                        setDispensedLootMethod.invoke(event, customLoot);
                        plugin.debug("&e[DEBUG] Overrode vault (" + type + ") loot with " + customLoot.size() + " items");
                    }
                }
            } catch (Exception ex) {
                plugin.debug("&c[DEBUG] Error handling vault loot: " + ex.getMessage());
            }
        }
        
        /**
         * Check if a trial spawner block is ominous using reflection
         */
        private boolean isOminousTrialSpawner(Block block) {
            try {
                // Try block data approach first
                org.bukkit.block.data.BlockData data = block.getBlockData();
                Class<?> trialSpawnerDataClass = Class.forName("org.bukkit.block.data.type.TrialSpawner");
                if (trialSpawnerDataClass.isInstance(data)) {
                    Method isOminousMethod = trialSpawnerDataClass.getMethod("isOminous");
                    return (boolean) isOminousMethod.invoke(data);
                }
            } catch (Exception e) {
                // Fallback - not available
            }
            
            try {
                // Try block state approach
                Class<?> trialSpawnerClass = Class.forName("org.bukkit.block.TrialSpawner");
                Object state = block.getState();
                if (trialSpawnerClass.isInstance(state)) {
                    Method isOminousMethod = trialSpawnerClass.getMethod("isOminous");
                    return (boolean) isOminousMethod.invoke(state);
                }
            } catch (Exception e) {
                // Not available
            }
            
            return false;
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
                // Not available
            }
            
            return false;
        }
    }
}
