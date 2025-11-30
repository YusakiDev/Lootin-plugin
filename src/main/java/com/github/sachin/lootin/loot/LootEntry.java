package com.github.sachin.lootin.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Random;

/**
 * Represents a single item entry in a loot table
 */
public class LootEntry {
    
    private String item;        // Material name or "itemedit:custom_id"
    private int minAmount;
    private int maxAmount;
    private double chance;      // 0.0 - 1.0
    private int weight;         // Relative weight for selection
    
    public LootEntry(String item, int minAmount, int maxAmount, double chance, int weight) {
        this.item = item;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.chance = chance;
        this.weight = weight;
    }
    
    public LootEntry(String item, String amountRange, double chance, int weight) {
        this.item = item;
        this.chance = chance;
        this.weight = weight;
        parseAmount(amountRange);
    }
    
    private void parseAmount(String amountRange) {
        if (amountRange.contains("-")) {
            String[] parts = amountRange.split("-");
            this.minAmount = Integer.parseInt(parts[0].trim());
            this.maxAmount = Integer.parseInt(parts[1].trim());
        } else {
            this.minAmount = Integer.parseInt(amountRange.trim());
            this.maxAmount = this.minAmount;
        }
    }
    
    /**
     * Check if this entry uses an ItemEdit item
     */
    public boolean isItemEditItem() {
        return item != null && item.toLowerCase().startsWith("itemedit:");
    }
    
    /**
     * Get the ItemEdit item ID (without prefix)
     */
    public String getItemEditId() {
        if (!isItemEditItem()) return null;
        return item.substring("itemedit:".length());
    }
    
    /**
     * Get the material name (for vanilla items)
     */
    public String getMaterialName() {
        return item;
    }
    
    /**
     * Get a random amount within the min-max range
     */
    public int getRandomAmount(Random random) {
        if (minAmount == maxAmount) return minAmount;
        return random.nextInt(maxAmount - minAmount + 1) + minAmount;
    }
    
    /**
     * Check if this entry should drop based on chance
     */
    public boolean shouldDrop(Random random) {
        return random.nextDouble() <= chance;
    }
    
    // Getters and setters
    
    public String getItem() {
        return item;
    }
    
    public void setItem(String item) {
        this.item = item;
    }
    
    public int getMinAmount() {
        return minAmount;
    }
    
    public void setMinAmount(int minAmount) {
        this.minAmount = minAmount;
    }
    
    public int getMaxAmount() {
        return maxAmount;
    }
    
    public void setMaxAmount(int maxAmount) {
        this.maxAmount = maxAmount;
    }
    
    public double getChance() {
        return chance;
    }
    
    public void setChance(double chance) {
        this.chance = chance;
    }
    
    public int getWeight() {
        return weight;
    }
    
    public void setWeight(int weight) {
        this.weight = weight;
    }
    
    /**
     * Get amount range as string (e.g., "1-3" or "1")
     */
    public String getAmountRange() {
        if (minAmount == maxAmount) {
            return String.valueOf(minAmount);
        }
        return minAmount + "-" + maxAmount;
    }
}
