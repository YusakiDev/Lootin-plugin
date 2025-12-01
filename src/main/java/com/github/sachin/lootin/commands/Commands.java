package com.github.sachin.lootin.commands;

import com.github.sachin.lootin.Lootin;
import com.github.sachin.lootin.compat.PaperCompat;
import com.github.sachin.lootin.loot.LootOverrideManager;
import com.github.sachin.lootin.loot.VaultResetManager;
import com.github.sachin.lootin.utils.*;

import com.github.sachin.lootin.utils.storage.LootinContainer;
import com.github.sachin.lootin.utils.storage.PlayerLootData;
import com.github.sachin.lootin.utils.storage.StorageConverterUtility;
import com.jeff_media.morepersistentdatatypes.DataType;

import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Subcommand;

import java.util.*;

@CommandAlias("lootin")
public class Commands extends BaseCommand{

    private Lootin plugin;

    public Commands(Lootin plugin){
        this.plugin = plugin;
    }


    @Subcommand("reload")
    public void onCommand(CommandSender sender){
        if(sender.hasPermission("lootin.command.reload")){
            plugin.reloadConfigs();
            sender.sendMessage(plugin.getMessage(LConstants.RELOADED,null));
        }
        else{
            sender.sendMessage(plugin.getMessage(LConstants.NO_PERMISSION,null));
        }
    }

    @Subcommand("set")
    @CommandCompletion("CHEST|BARREL|MINECART")
    public void onSet(Player player, String[] args) {
        if (args.length < 1) return;

        if (!player.hasPermission("lootin.command.set")) {
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION, player);
            return;
        }

        String type = args[0];
        RayTraceResult ray = player.rayTraceBlocks(4);

        switch (type.toUpperCase()) {
            case "CHEST":
            case "TRAPPED_CHEST":
                handleBlockContainer(player, ray, Material.CHEST, Material.TRAPPED_CHEST, ContainerType.CHEST);
                break;
            case "BARREL":
                handleBlockContainer(player, ray, Material.BARREL, null, ContainerType.BARREL);
                break;
            case "MINECART":
                handleMinecartContainer(player);
                break;
            default:
                plugin.sendPlayerMessage("&cInvalid container type!", player);
                break;
        }
    }

    private void handleBlockContainer(Player player, RayTraceResult ray, Material mainType, Material altType, ContainerType containerType) {
        if (ray != null && (ray.getHitBlock().getType() == mainType || (altType != null && ray.getHitBlock().getType() == altType))) {
            BlockState blockState = ray.getHitBlock().getState();
            Inventory inventory = (blockState instanceof Chest) ? ((Chest) blockState).getInventory() : ((Barrel) blockState).getInventory();

            if (!inventory.isEmpty()) {
                ChestUtils.setLootinContainer(null, blockState, containerType);
                PersistentDataContainer pdc = ((PersistentDataHolder)blockState).getPersistentDataContainer();
                pdc.set(LConstants.CUSTOM_CONTAINER_KEY, PersistentDataType.INTEGER, 1);

                // Save initial items as template for refill (both per-player and shared mode)
                pdc.set(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY, inventory.getContents());

                blockState.update();
                plugin.sendPlayerMessage("&a" + containerType.name() + " set as lootin container successfully, the contents are per player now!", player);
            } else {
                plugin.sendPlayerMessage("&c" + containerType.name() + " is empty!", player);
            }
        } else {
            plugin.sendPlayerMessage(LConstants.LOOK_AT_CONTAINER, player);
        }
    }

    private void handleMinecartContainer(Player player) {
        RayTraceResult raytrace = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 4, (en) -> en.getType() == EntityType.CHEST_MINECART);
        if (raytrace != null && raytrace.getHitEntity() != null) {
            StorageMinecart minecart = (StorageMinecart) raytrace.getHitEntity();
            if (!minecart.getInventory().isEmpty()) {
                ChestUtils.setLootinContainer(minecart, null, ContainerType.MINECART);
                PersistentDataContainer pdc = minecart.getPersistentDataContainer();
                pdc.set(LConstants.CUSTOM_CONTAINER_KEY, PersistentDataType.INTEGER, 1);

                // Save initial items as template for refill (both per-player and shared mode)
                pdc.set(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY, minecart.getInventory().getContents());

                plugin.sendPlayerMessage("&aMinecart set as lootin container successfully, the contents are per player now!", player);
            } else {
                plugin.sendPlayerMessage("&cMinecart is empty!", player);
            }
        } else {
            plugin.sendPlayerMessage(LConstants.LOOK_AT_CONTAINER, player);
        }
    }

    @Subcommand("clear")
    @CommandCompletion("all|player @players @nothing")
    public void onClearCommand(Player player,String[] args){
        if(!player.hasPermission("lootin.command.clear")){
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION,player);
            return;
        }
        if(args.length<1) return;
        LootinContainer lootinContainer = getTargetContainer(player);
        if(lootinContainer != null){
            if(args[0].equalsIgnoreCase("all")){
                if(!lootinContainer.getPlayerDataMap().isEmpty()){
                    plugin.sendPlayerMessage("&aCleared data of &e"+lootinContainer.getPlayerDataMap() .size()+"&a players from the container",player);
                    lootinContainer.getPlayerDataMap().clear();
                    plugin.cachedContainers.put(lootinContainer.getContainerID(),lootinContainer);
                }
                else{
                    plugin.sendPlayerMessage("&cNo data found of any player in the container.",player);
                }
            }
            else if(args[0].equalsIgnoreCase("player") && args.length>=2){
                Iterator<Map.Entry<UUID, PlayerLootData>> iterator = lootinContainer.getPlayerDataMap().entrySet().iterator();
                while (iterator.hasNext() ){
                    Map.Entry<UUID, PlayerLootData> entry = iterator.next();
                    if(args[1].equals(Bukkit.getOfflinePlayer(entry.getKey()).getName())){
                        iterator.remove();
                        plugin.sendPlayerMessage("&aCleared data of &e"+args[1]+"&a from the container.",player);
                        plugin.cachedContainers.put(lootinContainer.getContainerID(),lootinContainer);
                        return;
                    }
                }
                plugin.sendPlayerMessage("&cNo data found for the player &6"+args[1]+" &cin the container.",player);
            }
        }else{
            plugin.sendPlayerMessage(LConstants.LOOK_AT_CONTAINER, player);
        }
    }

    @Subcommand("info")
    public void onInfoCommand(Player player){
        if(!player.hasPermission("lootin.command.info")){
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION,player);
            return;
        }
        LootinContainer lootinContainer = getTargetContainer(player);

        if(lootinContainer != null){
            if(plugin.isRunningPaper){
                PaperCompat.sendPlayerMessage(lootinContainer,player);
            }
        }
        else{
            plugin.sendPlayerMessage(LConstants.LOOK_AT_CONTAINER, player);
        }
    }

    @Subcommand("test")
    @CommandCompletion("@loottables")
    public void onTestCommand(Player player,String[] args){
        if(!player.hasPermission("lootin.command.test")){
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION,player);
            return;
        }
        if(args.length < 1){
            plugin.sendPlayerMessage("Specify the type of loot table",player);
            return;
        }
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            plugin.sendPlayerMessage("&cYou're not looking at a block.",player);
            return;
        }
        Location chestLocation = targetBlock.getRelative(BlockFace.UP).getLocation();
        chestLocation.getBlock().setType(Material.CHEST);
        Block block = chestLocation.getBlock();
        if (block.getState() instanceof Chest) {
            Chest chest = (Chest) block.getState();
            LootTable lootTable = LootTables.DESERT_PYRAMID.getLootTable();
            if(args[0].split(":").length==2){
                NamespacedKey key = NamespacedKey.fromString(args[0],null);
                if(key != null) {
                    LootTable l = Bukkit.getLootTable(key);
                    if(l != null) {
                        lootTable = l;
                    }
                }
            }
            chest.setLootTable(lootTable);
            chest.update();
            plugin.sendPlayerMessage("&eSpawned desert pyramid chest!",player);
        } else {
            plugin.sendPlayerMessage("&cFailed to create chest.",player);
        }
    }

    @Subcommand("refill")
    public void onRefillCommand(Player player) {
        if(!player.hasPermission("lootin.command.refill")){
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION,player);
            return;
        }

        plugin.debug("&e[DEBUG] Refill command executed by " + player.getName());

        PersistentDataHolder holder = null;
        RayTraceResult blockRay = player.rayTraceBlocks(4);
        RayTraceResult entityRay = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 4,(en) -> en.getType()==EntityType.CHEST_MINECART);

        if(blockRay != null && blockRay.getHitBlock().getState() instanceof PersistentDataHolder){
            holder = (PersistentDataHolder) blockRay.getHitBlock().getState();
            plugin.debug("&e[DEBUG] Found block holder: " + blockRay.getHitBlock().getType());
        }
        else if(entityRay != null && entityRay.getHitEntity() != null){
            holder = entityRay.getHitEntity();
            plugin.debug("&e[DEBUG] Found entity holder: " + entityRay.getHitEntity().getType());
        }

        if(holder == null) {
            plugin.debug("&e[DEBUG] No holder found!");
            plugin.sendPlayerMessage(LConstants.LOOK_AT_CONTAINER, player);
            return;
        }

        PersistentDataContainer pdc = holder.getPersistentDataContainer();

        // Check if it's a lootin container
        boolean hasDataKey = pdc.has(LConstants.DATA_KEY);
        boolean hasStorageKey = pdc.has(LConstants.STORAGE_DATA_KEY);
        boolean hasIdentityKey = pdc.has(LConstants.IDENTITY_KEY);
        boolean hasLootTableKey = pdc.has(LConstants.LOOTTABLE_KEY, PersistentDataType.STRING);
        plugin.debug("&e[DEBUG] Has DATA_KEY: " + hasDataKey + ", Has STORAGE_DATA_KEY: " + hasStorageKey + ", Has IDENTITY_KEY: " + hasIdentityKey + ", Has LOOTTABLE_KEY: " + hasLootTableKey);

        if(!hasDataKey && !hasStorageKey && !hasIdentityKey && !hasLootTableKey) {
            plugin.sendPlayerMessage("&cThis is not a lootin container!", player);
            return;
        }

        // In shared mode, immediately refill the container
        if(plugin.getConfig().getBoolean(LConstants.SHARED_MODE, false)) {
            plugin.debug("&e[DEBUG] Shared mode - performing immediate refill");
            boolean success = refillContainerNow(holder, player);
            if(success) {
                plugin.sendPlayerMessage("&aContainer refilled successfully!", player);
            } else {
                plugin.sendPlayerMessage("&cFailed to refill container!", player);
            }
            return;
        }

        // In per-player mode, reset the refill timer to force refill on next open
        boolean hadRefillKey = pdc.has(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG);
        plugin.debug("&e[DEBUG] Had SHARED_LAST_REFILL_KEY: " + hadRefillKey);
        pdc.remove(LConstants.SHARED_LAST_REFILL_KEY);

        // Update the block/entity state
        if(holder instanceof BlockState) {
            ((BlockState) holder).update();
            plugin.debug("&e[DEBUG] BlockState updated");
        }

        plugin.sendPlayerMessage("&aRefill timer reset! Container will refill on next open.", player);
    }

    private boolean refillContainerNow(PersistentDataHolder holder, Player player) {
        if(holder instanceof StorageMinecart) {
            StorageMinecart minecart = (StorageMinecart) holder;
            return ChestUtils.refillSharedContainer(minecart, player);
        } else if(holder instanceof BlockState) {
            BlockState state = (BlockState) holder;
            return ChestUtils.refillSharedContainer(state, player);
        }
        return false;
    }

    private LootinContainer getTargetContainer(Player player){
        LootinContainer lootinContainer = null;
        PersistentDataHolder holder = null;
        RayTraceResult blockRay = player.rayTraceBlocks(4);
        RayTraceResult entiryRay = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 4,(en) -> en.getType()==EntityType.CHEST_MINECART);
        if(blockRay != null && blockRay.getHitBlock().getState() instanceof PersistentDataHolder){
            holder = (PersistentDataHolder) blockRay.getHitBlock().getState();

        }
        else if(entiryRay != null && entiryRay.getHitEntity() != null){
            holder = entiryRay.getHitEntity();
        }

        if(holder == null) return null;
        if(holder.getPersistentDataContainer().has(LConstants.STORAGE_DATA_KEY)){
            lootinContainer = StorageConverterUtility.getContainerData(holder.getPersistentDataContainer().get(LConstants.STORAGE_DATA_KEY,DataType.UUID));
        }
        else if(holder.getPersistentDataContainer().has(LConstants.DATA_KEY)){
            lootinContainer = StorageConverterUtility.convert(holder);
        }

        return lootinContainer;
    }


//    @Subcommand("rwg loottable")
//    public void onRwgLoottableCommand(Player player){
//        if(!testRwg(player)) {
//            return;
//        }
//        RWGCompat compat = plugin.rwgCompat;
//        RwgInventory inventory = new RwgInventory(compat.getHeads(), compat.getApi().getChestStorage());
//        inventory.populate();
//        player.openInventory(inventory.getInventory());
//    }
//
//    @Subcommand("rwg elytra")
//    public void onRwgElytraCommand(Player player) {
//        if(!testRwg(player)) {
//            return;
//        }
//        ItemStack itemStack = new ItemStack(Material.OAK_SIGN);
//        ItemMeta meta = itemStack.getItemMeta();
//        meta.setDisplayName(ChatColor.GOLD + "RWG Elytra ItemFrame");
//        meta.getPersistentDataContainer().set(LConstants.RWG_IDENTITY_KEY, PersistentDataType.BYTE, (byte) 0);
//        itemStack.setItemMeta(meta);
//        player.getInventory().addItem(itemStack);
//        player.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "You received a Lootin Elytra ItemFrame Placeholder for RWG schematics");
//    }
//
//    private boolean testRwg(Player player) {
//        if(!player.hasPermission("lootin.command.rwg.loottable")){
//            plugin.sendPlayerMessage(LConstants.NO_PERMISSION,player);
//            return false;
//        }
//        RWGCompat compat = plugin.rwgCompat;
//        if(compat == null){
//            player.sendMessage(plugin.getPrefix()+ChatColor.RED+"You need Realistic World Generator plugin installed to use this command");
//            return false;
//        }
//        if(compat.isSetupFailed()) {
//            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "This feature is not available because the addon setup failed");
//            return false;
//        }
//        return true;
//    }

    @Subcommand("generatetables")
    @CommandCompletion("--reset")
    public void onGenerateTablesCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lootin.command.generatetables")) {
            sender.sendMessage(plugin.getMessage(LConstants.NO_PERMISSION, null));
            return;
        }

        LootOverrideManager manager = plugin.getLootOverrideManager();
        if (manager == null) {
            sender.sendMessage(plugin.getPrefix() + ChatColor.RED + "Loot override system is not initialized!");
            return;
        }

        boolean reset = args.length > 0 && args[0].equalsIgnoreCase("--reset");

        if (reset) {
            manager.regenerateDefaults();
            sender.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Loot tables reset to defaults and reloaded!");
        } else {
            manager.generateDefaultConfig();
            manager.reload();
            sender.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Default loot tables generated (existing file preserved if present)!");
        }
    }

    @Subcommand("reloadtables")
    public void onReloadTablesCommand(CommandSender sender) {
        if (!sender.hasPermission("lootin.command.reloadtables")) {
            sender.sendMessage(plugin.getMessage(LConstants.NO_PERMISSION, null));
            return;
        }

        LootOverrideManager manager = plugin.getLootOverrideManager();
        if (manager == null) {
            sender.sendMessage(plugin.getPrefix() + ChatColor.RED + "Loot override system is not initialized!");
            return;
        }

        manager.reload();
        sender.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Loot tables reloaded!");
    }

    @Subcommand("listitemedit")
    public void onListItemEditCommand(CommandSender sender) {
        if (!sender.hasPermission("lootin.command.listitemedit")) {
            sender.sendMessage(plugin.getMessage(LConstants.NO_PERMISSION, null));
            return;
        }

        LootOverrideManager manager = plugin.getLootOverrideManager();
        if (manager == null) {
            sender.sendMessage(plugin.getPrefix() + ChatColor.RED + "Loot override system is not initialized!");
            return;
        }

        if (!manager.isItemEditAvailable()) {
            sender.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "ItemEdit is not available. Install YskLib with ItemEdit enabled to use custom items.");
            return;
        }

        Set<String> itemIds = manager.getItemEditIds();
        if (itemIds.isEmpty()) {
            sender.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "No ItemEdit items found.");
            return;
        }

        sender.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Available ItemEdit items (" + itemIds.size() + "):");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String id : itemIds) {
            if (count > 0) sb.append(ChatColor.GRAY + ", ");
            sb.append(ChatColor.AQUA + "itemedit:" + id);
            count++;
            if (count >= 10) {
                sender.sendMessage("  " + sb.toString());
                sb = new StringBuilder();
                count = 0;
            }
        }
        if (sb.length() > 0) {
            sender.sendMessage("  " + sb.toString());
        }

        sender.sendMessage(plugin.getPrefix() + ChatColor.GRAY + "Use these IDs in loottables.yml (e.g., item: \"itemedit:your_item_id\")");
    }

    @Subcommand("resetvault")
    @CommandCompletion("@players|all")
    public void onResetVaultCommand(Player player, String[] args) {
        if (!player.hasPermission("lootin.command.resetvault")) {
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION, player);
            return;
        }

        VaultResetManager vaultManager = plugin.getVaultResetManager();
        if (vaultManager == null) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Vault reset system is not initialized!");
            return;
        }

        // Get block player is looking at
        RayTraceResult ray = player.rayTraceBlocks(5);
        if (ray == null || ray.getHitBlock() == null) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "You must look at a vault block!");
            return;
        }

        Block block = ray.getHitBlock();
        if (!vaultManager.isVault(block)) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "That block is not a vault! Looking at: " + block.getType().name());
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("all")) {
            // Reset for all players
            int count = vaultManager.resetVaultForAllPlayers(block);
            if (count > 0) {
                player.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Reset vault for " + count + " player(s)!");
            } else {
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "No players have used this vault yet.");
            }
        } else {
            // Reset for specific player
            String targetName = args[0];
            Player targetPlayer = Bukkit.getPlayer(targetName);
            UUID targetUUID;

            if (targetPlayer != null) {
                targetUUID = targetPlayer.getUniqueId();
            } else {
                // Try offline player
                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                if (!offlinePlayer.hasPlayedBefore() && offlinePlayer.getPlayer() == null) {
                    player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Player '" + targetName + "' not found!");
                    return;
                }
                targetUUID = offlinePlayer.getUniqueId();
            }

            boolean success = vaultManager.resetVaultForPlayer(block, targetUUID);
            if (success) {
                player.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Reset vault for player '" + targetName + "'!");
            } else {
                player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "Player '" + targetName + "' has not used this vault.");
            }
        }
    }

    @Subcommand("vaultinfo")
    public void onVaultInfoCommand(Player player) {
        if (!player.hasPermission("lootin.command.vaultinfo")) {
            plugin.sendPlayerMessage(LConstants.NO_PERMISSION, player);
            return;
        }

        VaultResetManager vaultManager = plugin.getVaultResetManager();
        if (vaultManager == null) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "Vault reset system is not initialized!");
            return;
        }

        // Get block player is looking at
        RayTraceResult ray = player.rayTraceBlocks(5);
        if (ray == null || ray.getHitBlock() == null) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "You must look at a vault block!");
            return;
        }

        Block block = ray.getHitBlock();
        if (!vaultManager.isVault(block)) {
            player.sendMessage(plugin.getPrefix() + ChatColor.RED + "That block is not a vault! Looking at: " + block.getType().name());
            return;
        }

        Collection<UUID> rewardedPlayers = vaultManager.getRewardedPlayers(block);
        if (rewardedPlayers.isEmpty()) {
            player.sendMessage(plugin.getPrefix() + ChatColor.YELLOW + "No players have used this vault yet.");
            return;
        }

        player.sendMessage(plugin.getPrefix() + ChatColor.GREEN + "Players who have used this vault (" + rewardedPlayers.size() + "):");
        for (UUID uuid : rewardedPlayers) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
            String name = offlinePlayer.getName() != null ? offlinePlayer.getName() : uuid.toString();
            player.sendMessage("  " + ChatColor.AQUA + "- " + name);
        }
    }

}
