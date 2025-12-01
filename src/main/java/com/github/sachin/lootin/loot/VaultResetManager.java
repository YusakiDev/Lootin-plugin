package com.github.sachin.lootin.loot;

import com.github.sachin.lootin.Lootin;
import com.github.sachin.lootin.utils.ChestUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Vault;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

/**
 * Manages vault reset functionality - allows vaults to be reused after a configured time.
 * Requires Paper 1.21.4+ for Vault block state API.
 */
public class VaultResetManager {
    
    private final Lootin plugin;
    private final NamespacedKey VAULT_OPEN_PREFIX;
    
    private boolean enabled;
    private long resetTimeMillis;
    
    public VaultResetManager(Lootin plugin) {
        this.plugin = plugin;
        this.VAULT_OPEN_PREFIX = new NamespacedKey(plugin, "vault-opened");
        loadConfig();
    }
    
    /**
     * Load configuration from config.yml
     */
    public void loadConfig() {
        this.enabled = plugin.getConfig().getBoolean("vault-reset.enabled", false);
        String resetTimeStr = plugin.getConfig().getString("vault-reset.reset-time", "7d");
        this.resetTimeMillis = ChestUtils.parseTimeString(resetTimeStr);
        
        if (enabled) {
            plugin.getLogger().info("Vault reset enabled with reset time: " + resetTimeStr);
        }
    }
    
    /**
     * Check if vault reset feature is enabled
     */
    public boolean isEnabled() {
        return enabled;
    }
    
    /**
     * Record that a player has opened a vault (store timestamp)
     */
    public void recordVaultOpen(Block block, UUID playerUUID) {
        if (!enabled) return;
        
        if (block.getState() instanceof Vault vault) {
            PersistentDataContainer pdc = vault.getPersistentDataContainer();
            NamespacedKey key = getPlayerKey(playerUUID);
            pdc.set(key, PersistentDataType.LONG, System.currentTimeMillis());
            vault.update();
            
            plugin.debug("&e[DEBUG] Recorded vault open for player " + playerUUID + " at " + block.getLocation());
        }
    }
    
    /**
     * Check if a player's vault access should be reset (time has passed)
     */
    public boolean shouldResetForPlayer(Vault vault, UUID playerUUID) {
        if (!enabled) return false;
        
        PersistentDataContainer pdc = vault.getPersistentDataContainer();
        NamespacedKey key = getPlayerKey(playerUUID);
        
        if (!pdc.has(key, PersistentDataType.LONG)) {
            return false; // No record of opening
        }
        
        long openTime = pdc.get(key, PersistentDataType.LONG);
        long elapsed = System.currentTimeMillis() - openTime;
        
        return elapsed >= resetTimeMillis;
    }
    
    /**
     * Check and auto-reset vault for a player if reset time has passed
     * @return true if vault was reset
     */
    public boolean checkAndResetForPlayer(Block block, UUID playerUUID) {
        if (!enabled) return false;
        
        if (block.getState() instanceof Vault vault) {
            // Only reset if player has been rewarded AND reset time has passed
            if (vault.hasRewardedPlayer(playerUUID) && shouldResetForPlayer(vault, playerUUID)) {
                vault.removeRewardedPlayer(playerUUID);
                
                // Clear the timestamp
                PersistentDataContainer pdc = vault.getPersistentDataContainer();
                pdc.remove(getPlayerKey(playerUUID));
                
                vault.update();
                
                plugin.debug("&e[DEBUG] Auto-reset vault for player " + playerUUID + " at " + block.getLocation());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Reset vault for a specific player (manual reset via command)
     * @return true if player was in rewarded list and removed
     */
    public boolean resetVaultForPlayer(Block block, UUID playerUUID) {
        if (block.getState() instanceof Vault vault) {
            if (vault.hasRewardedPlayer(playerUUID)) {
                vault.removeRewardedPlayer(playerUUID);
                
                // Clear the timestamp
                PersistentDataContainer pdc = vault.getPersistentDataContainer();
                pdc.remove(getPlayerKey(playerUUID));
                
                vault.update();
                
                plugin.debug("&e[DEBUG] Manually reset vault for player " + playerUUID + " at " + block.getLocation());
                return true;
            }
        }
        return false;
    }
    
    /**
     * Reset vault for all players (manual reset via command)
     * @return number of players reset
     */
    public int resetVaultForAllPlayers(Block block) {
        if (block.getState() instanceof Vault vault) {
            Collection<UUID> rewardedPlayers = new ArrayList<>(vault.getRewardedPlayers());
            int count = rewardedPlayers.size();
            
            for (UUID uuid : rewardedPlayers) {
                vault.removeRewardedPlayer(uuid);
            }
            
            // Clear all timestamps
            PersistentDataContainer pdc = vault.getPersistentDataContainer();
            for (UUID uuid : rewardedPlayers) {
                pdc.remove(getPlayerKey(uuid));
            }
            
            vault.update();
            
            plugin.debug("&e[DEBUG] Reset vault for " + count + " players at " + block.getLocation());
            return count;
        }
        return 0;
    }
    
    /**
     * Get list of players who have been rewarded by this vault
     */
    public Collection<UUID> getRewardedPlayers(Block block) {
        if (block.getState() instanceof Vault vault) {
            return vault.getRewardedPlayers();
        }
        return new ArrayList<>();
    }
    
    /**
     * Check if a block is a vault
     */
    public boolean isVault(Block block) {
        return block.getState() instanceof Vault;
    }
    
    /**
     * Get PDC key for a player's vault open timestamp
     */
    private NamespacedKey getPlayerKey(UUID playerUUID) {
        return new NamespacedKey(plugin, "vault-opened-" + playerUUID.toString());
    }
    
    /**
     * Reload configuration
     */
    public void reload() {
        loadConfig();
    }
}
