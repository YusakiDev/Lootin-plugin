package com.github.sachin.lootin.loot;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Represents a complete loot table override definition
 */
public class LootTableOverride {
    
    private String key;           // Loot table key (e.g., "minecraft:chests/desert_pyramid")
    private boolean enabled;
    private int minRolls;
    private int maxRolls;
    private List<LootEntry> items;
    
    public LootTableOverride(String key) {
        this.key = key;
        this.enabled = true;
        this.minRolls = 1;
        this.maxRolls = 1;
        this.items = new ArrayList<>();
    }
    
    public LootTableOverride(String key, boolean enabled, int minRolls, int maxRolls, List<LootEntry> items) {
        this.key = key;
        this.enabled = enabled;
        this.minRolls = minRolls;
        this.maxRolls = maxRolls;
        this.items = items != null ? items : new ArrayList<>();
    }
    
    /**
     * Parse rolls from a string like "2-4" or "3"
     */
    public void parseRolls(String rollsStr) {
        if (rollsStr.contains("-")) {
            String[] parts = rollsStr.split("-");
            this.minRolls = Integer.parseInt(parts[0].trim());
            this.maxRolls = Integer.parseInt(parts[1].trim());
        } else {
            this.minRolls = Integer.parseInt(rollsStr.trim());
            this.maxRolls = this.minRolls;
        }
    }
    
    /**
     * Get random number of rolls
     */
    public int getRandomRolls(Random random) {
        if (minRolls == maxRolls) return minRolls;
        return random.nextInt(maxRolls - minRolls + 1) + minRolls;
    }
    
    /**
     * Select a random item based on weight
     */
    public LootEntry selectByWeight(Random random) {
        if (items.isEmpty()) return null;
        
        int totalWeight = items.stream().mapToInt(LootEntry::getWeight).sum();
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (LootEntry entry : items) {
            cumulative += entry.getWeight();
            if (roll < cumulative) {
                return entry;
            }
        }
        
        // Fallback (shouldn't happen)
        return items.get(items.size() - 1);
    }
    
    /**
     * Generate loot items for this table
     * @param itemResolver Function to resolve item strings to ItemStacks
     * @param random Random instance
     * @return List of generated ItemStacks
     */
    public List<ItemStack> generateLoot(ItemResolver itemResolver, Random random) {
        List<ItemStack> result = new ArrayList<>();
        
        if (!enabled || items.isEmpty()) {
            return result;
        }
        
        int rolls = getRandomRolls(random);
        
        for (int i = 0; i < rolls; i++) {
            LootEntry selected = selectByWeight(random);
            if (selected != null) {
                // First roll always drops (ignore chance), subsequent rolls check chance
                boolean shouldDrop = (i == 0) || selected.shouldDrop(random);
                
                if (shouldDrop) {
                    ItemStack item = itemResolver.resolve(selected);
                    if (item != null) {
                        item.setAmount(selected.getRandomAmount(random));
                        result.add(item);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Functional interface for resolving item strings to ItemStacks
     */
    @FunctionalInterface
    public interface ItemResolver {
        ItemStack resolve(LootEntry entry);
    }
    
    // Getters and setters
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getMinRolls() {
        return minRolls;
    }
    
    public void setMinRolls(int minRolls) {
        this.minRolls = minRolls;
    }
    
    public int getMaxRolls() {
        return maxRolls;
    }
    
    public void setMaxRolls(int maxRolls) {
        this.maxRolls = maxRolls;
    }
    
    public List<LootEntry> getItems() {
        return items;
    }
    
    public void setItems(List<LootEntry> items) {
        this.items = items;
    }
    
    public void addItem(LootEntry entry) {
        this.items.add(entry);
    }
    
    /**
     * Get rolls range as string (e.g., "2-4" or "3")
     */
    public String getRollsRange() {
        if (minRolls == maxRolls) {
            return String.valueOf(minRolls);
        }
        return minRolls + "-" + maxRolls;
    }
}
