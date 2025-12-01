package com.github.sachin.lootin.listeners;

import com.github.sachin.lootin.loot.LootOverrideManager;
import com.github.sachin.lootin.loot.VaultResetManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.block.data.type.TrialSpawner;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;

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
    private VaultResetManager vaultResetManager;
    
    public LootOverrideListener() {
        this.lootManager = plugin.getLootOverrideManager();
        this.vaultResetManager = plugin.getVaultResetManager();
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
     * Intercept vault and trial spawner loot ejection (Paper 1.21+)
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockDispenseLoot(BlockDispenseLootEvent e) {
        if (lootManager == null || !lootManager.isEnabled()) {
            return;
        }
        
        Block block = e.getBlock();
        Material blockType = block.getType();
        
        plugin.debug("&e[DEBUG] BlockDispenseLootEvent fired for block: " + blockType.name());
        
        if (blockType == Material.VAULT) {
            handleVaultLoot(e, block);
        } else if (blockType == Material.TRIAL_SPAWNER) {
            handleTrialSpawnerLoot(e, block);
        }
    }
    
    /**
     * Handle vault loot override and record for reset tracking
     */
    private void handleVaultLoot(BlockDispenseLootEvent e, Block block) {
        boolean isOminous = isOminousVault(block);
        String type = isOminous ? "ominous" : "normal";
        
        plugin.debug("&e[DEBUG] Vault type: " + type);
        
        // Record vault open for reset tracking
        Player player = e.getPlayer();
        if (player != null && vaultResetManager != null && vaultResetManager.isEnabled()) {
            vaultResetManager.recordVaultOpen(block, player.getUniqueId());
        }
        
        // Override loot if configured
        if (lootManager.hasVaultOverride(type)) {
            List<ItemStack> customLoot = lootManager.generateVaultLoot(type);
            
            if (customLoot != null && !customLoot.isEmpty()) {
                e.setDispensedLoot(customLoot);
                plugin.debug("&e[DEBUG] Overrode vault (" + type + ") loot with " + customLoot.size() + " items");
            }
        } else {
            plugin.debug("&e[DEBUG] No vault override for type: " + type);
        }
    }
    
    /**
     * Handle trial spawner loot override
     */
    private void handleTrialSpawnerLoot(BlockDispenseLootEvent e, Block block) {
        boolean isOminous = isOminousTrialSpawner(block);
        String type = isOminous ? "ominous" : "normal";
        
        plugin.debug("&e[DEBUG] Trial spawner type: " + type);
        
        if (lootManager.hasTrialSpawnerOverride(type)) {
            List<ItemStack> customLoot = lootManager.generateTrialSpawnerLoot(type);
            
            if (customLoot != null && !customLoot.isEmpty()) {
                e.setDispensedLoot(customLoot);
                plugin.debug("&e[DEBUG] Overrode trial spawner (" + type + ") loot with " + customLoot.size() + " items");
            }
        } else {
            plugin.debug("&e[DEBUG] No trial spawner override for type: " + type);
        }
    }
    
    /**
     * Check if a vault block is ominous
     */
    private boolean isOminousVault(Block block) {
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Vault vaultData) {
            return vaultData.isOminous();
        }
        return false;
    }
    
    /**
     * Check if a trial spawner block is ominous
     */
    private boolean isOminousTrialSpawner(Block block) {
        // Try block data first
        if (block.getBlockData() instanceof TrialSpawner spawnerData) {
            return spawnerData.isOminous();
        }
        
        // Try block state as fallback
        if (block.getState() instanceof org.bukkit.block.TrialSpawner spawner) {
            return spawner.isOminous();
        }
        
        return false;
    }
    
    /**
     * Auto-reset vaults when player moves near them (if reset time has passed)
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent e) {
        if (vaultResetManager == null || !vaultResetManager.isEnabled()) {
            return;
        }
        
        // Only check when player moves to a new block (not just head rotation)
        Location from = e.getFrom();
        Location to = e.getTo();
        if (to == null || (from.getBlockX() == to.getBlockX() 
                && from.getBlockY() == to.getBlockY() 
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        
        Player player = e.getPlayer();
        
        // Check nearby blocks for vaults (4 block radius - vault activation range)
        int radius = 4;
        Location playerLoc = player.getLocation();
        
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = playerLoc.clone().add(x, y, z).getBlock();
                    
                    if (block.getType() == Material.VAULT) {
                        // Check and auto-reset if time has passed
                        if (vaultResetManager.checkAndResetForPlayer(block, player.getUniqueId())) {
                            plugin.debug("&e[DEBUG] Auto-reset vault at " + block.getLocation() + " for player " + player.getName());
                        }
                    }
                }
            }
        }
    }
}
