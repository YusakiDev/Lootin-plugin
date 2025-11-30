package com.github.sachin.lootin.loot;

import com.google.gson.*;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Utility to generate loottables.yml from vanilla Minecraft loot table JSON files.
 * This reads the actual vanilla loot tables and converts them to our YAML format.
 */
public class LootTableGenerator {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Generate loottables.yml from vanilla JSON files
     * @param vanillaLootTablesDir Directory containing vanilla loot table JSON files (e.g., data/minecraft/loot_table/chests/)
     * @param outputFile Output YAML file
     */
    public static void generateFromVanilla(File vanillaLootTablesDir, File outputFile) throws IOException {
        YamlConfiguration config = new YamlConfiguration();

        // Header
        config.options().setHeader(Arrays.asList(
            "============================================================",
            "LOOTIN - LOOT TABLE CONFIGURATION",
            "============================================================",
            "This file contains loot tables generated from vanilla Minecraft data.",
            "Edit items, amounts, chances, weights as desired.",
            "Add ItemEdit items with: \"itemedit:your_item_id\"",
            "",
            "Set enabled: false on a table to use vanilla behavior instead.",
            "Use /lootin generatetables --reset to regenerate defaults.",
            "============================================================"
        ));

        config.set("enabled", true);

        // Process chest loot tables
        File chestsDir = new File(vanillaLootTablesDir, "chests");
        if (chestsDir.exists()) {
            processDirectory(chestsDir, "minecraft:chests/", config, "containers");
        }

        // Process spawner loot tables (trial chambers)
        File spawnersDir = new File(vanillaLootTablesDir, "spawners");
        if (spawnersDir.exists()) {
            processDirectory(spawnersDir, "minecraft:spawners/", config, "spawners");
        }

        config.save(outputFile);
        System.out.println("Generated loot tables config: " + outputFile.getAbsolutePath());
    }

    private static void processDirectory(File dir, String prefix, YamlConfiguration config, String section) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                // Recurse into subdirectories
                processDirectory(file, prefix + file.getName() + "/", config, section);
            } else if (file.getName().endsWith(".json")) {
                String tableName = prefix + file.getName().replace(".json", "");
                processLootTable(file, tableName, config, section);
            }
        }
    }

    private static void processLootTable(File jsonFile, String tableName, YamlConfiguration config, String section) {
        try {
            String json = new String(Files.readAllBytes(jsonFile.toPath()));
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (!root.has("pools")) {
                return;
            }

            JsonArray pools = root.getAsJsonArray("pools");
            
            // Merge all pools into one for simplicity
            List<Map<String, Object>> allItems = new ArrayList<>();
            int totalMinRolls = 0;
            int totalMaxRolls = 0;

            for (JsonElement poolElement : pools) {
                JsonObject pool = poolElement.getAsJsonObject();
                
                // Parse rolls
                int[] rolls = parseRolls(pool.get("rolls"));
                totalMinRolls += rolls[0];
                totalMaxRolls += rolls[1];

                // Parse entries
                if (pool.has("entries")) {
                    JsonArray entries = pool.getAsJsonArray("entries");
                    for (JsonElement entryElement : entries) {
                        JsonObject entry = entryElement.getAsJsonObject();
                        Map<String, Object> item = parseEntry(entry);
                        if (item != null) {
                            allItems.add(item);
                        }
                    }
                }
            }

            if (allItems.isEmpty()) {
                return;
            }

            // Write to config
            String path = section + ".\"" + tableName + "\"";
            config.set(path + ".enabled", true);
            
            String rollsStr = totalMinRolls == totalMaxRolls 
                ? String.valueOf(totalMinRolls) 
                : totalMinRolls + "-" + totalMaxRolls;
            config.set(path + ".rolls", rollsStr);
            config.set(path + ".items", allItems);

            System.out.println("Processed: " + tableName + " (" + allItems.size() + " items, " + rollsStr + " rolls)");

        } catch (Exception e) {
            System.err.println("Error processing " + jsonFile.getName() + ": " + e.getMessage());
        }
    }

    private static int[] parseRolls(JsonElement rollsElement) {
        if (rollsElement == null) {
            return new int[]{1, 1};
        }

        if (rollsElement.isJsonPrimitive()) {
            int rolls = rollsElement.getAsInt();
            return new int[]{rolls, rolls};
        }

        if (rollsElement.isJsonObject()) {
            JsonObject rollsObj = rollsElement.getAsJsonObject();
            String type = rollsObj.has("type") ? rollsObj.get("type").getAsString() : "";
            
            if (type.equals("minecraft:uniform")) {
                int min = rollsObj.get("min").getAsInt();
                int max = rollsObj.get("max").getAsInt();
                return new int[]{min, max};
            }
        }

        return new int[]{1, 1};
    }

    private static Map<String, Object> parseEntry(JsonObject entry) {
        String type = entry.has("type") ? entry.get("type").getAsString() : "";
        
        // Skip empty entries
        if (type.equals("minecraft:empty")) {
            return null;
        }

        // Skip non-item entries (like nested loot tables)
        if (!type.equals("minecraft:item")) {
            return null;
        }

        if (!entry.has("name")) {
            return null;
        }

        String itemName = entry.get("name").getAsString();
        // Convert minecraft:diamond to DIAMOND
        String materialName = itemName.replace("minecraft:", "").toUpperCase();

        int weight = entry.has("weight") ? entry.get("weight").getAsInt() : 1;
        
        // Parse count from functions
        int minCount = 1;
        int maxCount = 1;
        
        if (entry.has("functions")) {
            JsonArray functions = entry.getAsJsonArray("functions");
            for (JsonElement funcElement : functions) {
                JsonObject func = funcElement.getAsJsonObject();
                String funcType = func.has("function") ? func.get("function").getAsString() : "";
                
                if (funcType.equals("minecraft:set_count")) {
                    if (func.has("count")) {
                        JsonElement countEl = func.get("count");
                        if (countEl.isJsonPrimitive()) {
                            minCount = maxCount = countEl.getAsInt();
                        } else if (countEl.isJsonObject()) {
                            JsonObject countObj = countEl.getAsJsonObject();
                            if (countObj.has("min") && countObj.has("max")) {
                                minCount = (int) countObj.get("min").getAsFloat();
                                maxCount = (int) countObj.get("max").getAsFloat();
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("item", materialName);
        
        String amountStr = minCount == maxCount 
            ? String.valueOf(minCount) 
            : minCount + "-" + maxCount;
        item.put("amount", amountStr);
        item.put("chance", 1.0);
        item.put("weight", weight);

        return item;
    }

    /**
     * Main method for standalone execution
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: LootTableGenerator <vanilla_loot_tables_dir> <output_yml>");
            System.out.println("Example: LootTableGenerator /path/to/data/minecraft/loot_table /path/to/loottables.yml");
            return;
        }

        File inputDir = new File(args[0]);
        File outputFile = new File(args[1]);

        if (!inputDir.exists()) {
            System.err.println("Input directory does not exist: " + inputDir.getAbsolutePath());
            return;
        }

        try {
            generateFromVanilla(inputDir, outputFile);
        } catch (IOException e) {
            System.err.println("Error generating loot tables: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
