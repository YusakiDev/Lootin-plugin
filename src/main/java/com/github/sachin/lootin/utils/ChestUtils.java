package com.github.sachin.lootin.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.github.sachin.lootin.Lootin;
import com.github.sachin.lootin.api.LootinInventoryOpenEvent;
import com.github.sachin.lootin.utils.cooldown.Cooldown;
import com.github.sachin.lootin.utils.storage.ItemSerializer;
import com.github.sachin.lootin.utils.storage.LootinContainer;
import com.github.sachin.lootin.utils.storage.PlayerLootData;
import com.github.sachin.lootin.utils.storage.StorageConverterUtility;
import com.jeff_media.morepersistentdatatypes.DataType;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.Lootable;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


/**
 * Class for loot container utils
 */
public class ChestUtils{

    private static final Lootin plugin = Lootin.getPlugin();

    public static boolean hasPlayerLoot(@Nullable Entity minecart,@Nullable BlockState block,@NotNull Player player,@NotNull ContainerType type){
        PersistentDataHolder holder = minecart != null ? minecart : ((TileState)block);
        LootinContainer lootinContainer = null;
        if(holder==null) return false;
        if(holder.getPersistentDataContainer().has(LConstants.STORAGE_DATA_KEY)){
            lootinContainer = StorageConverterUtility.getContainerData(holder.getPersistentDataContainer().get(LConstants.STORAGE_DATA_KEY,DataType.UUID));
        }
        else{
            lootinContainer = StorageConverterUtility.convert(holder);
        }
        return lootinContainer.getPlayerDataMap().containsKey(player.getUniqueId());


//        NamespacedKey key = Lootin.getKey(player.getUniqueId().toString());
//        if(type == ContainerType.CHEST){
//            Chest chest = (Chest) block;
//            PersistentDataContainer data = chest.getPersistentDataContainer();
//            return data.has(key,PersistentDataType.STRING) || data.has(key, DataType.ITEM_STACK_ARRAY);
//        }
//        else if(type == ContainerType.BARREL){
//            Barrel barrel = (Barrel) block;
//            PersistentDataContainer data = barrel.getPersistentDataContainer();
//            return data.has(key,PersistentDataType.STRING) || data.has(key, DataType.ITEM_STACK_ARRAY);
//        }
//        else if(type == ContainerType.MINECART){
//            StorageMinecart tileCart = (StorageMinecart) minecart;
//            PersistentDataContainer data = tileCart.getPersistentDataContainer();
//            return data.has(key,PersistentDataType.STRING) || data.has(key, DataType.ITEM_STACK_ARRAY);
//        }
//        else if(type == ContainerType.DOUBLE_CHEST){
//            DoubleChest doubleChest = getDoubleChest(block);
//            PersistentDataContainer d1 = ((Chest)doubleChest.getLeftSide()).getPersistentDataContainer();
//            PersistentDataContainer d2 = ((Chest)doubleChest.getRightSide()).getPersistentDataContainer();
//            return (d1.has(key,PersistentDataType.STRING) || d1.has(key, DataType.ITEM_STACK_ARRAY)) && (d2.has(key,PersistentDataType.STRING) || d2.has(key, DataType.ITEM_STACK_ARRAY));
//        }
    }


    /**
     * Determines weather given container has lootin tag or not
     * @param minecart only StorageMinecart if container is a Entity or can be null
     * @param block Blockstate of container or can be null if using minecart
     * @param type ContainerType {@link com.github.sachin.lootin.utils.ContainerType}
     * @return weather given container has lootin tag or not
     */
    public static boolean isLootinContainer(@Nullable Entity minecart,@Nullable BlockState block,@NotNull ContainerType type){
        if(type == ContainerType.MINECART && minecart != null){
            return hasKey(((StorageMinecart)minecart).getPersistentDataContainer());
        }
        if(block != null){
            if(type == ContainerType.CHEST){
                return hasKey(((Chest)block).getPersistentDataContainer());
            }
            if(type == ContainerType.BARREL){
                return hasKey(((Barrel)block).getPersistentDataContainer());
            }
            if(type == ContainerType.DOUBLE_CHEST && isDoubleChest(block)){
                DoubleChest doubleChest = getDoubleChest(block);
                PersistentDataContainer d1 = ((Chest)doubleChest.getLeftSide()).getPersistentDataContainer();
                PersistentDataContainer d2 = ((Chest)doubleChest.getRightSide()).getPersistentDataContainer();
                return hasKey(d1) && hasKey(d2);
            }
        }
        return false;
    }

    /**
     * Sets a lootin tag for container
     * @param minecart only StorageMinecart if container is a Entity or can be null
     * @param block Blockstate of container or can be null if using minecart
     * @param type ContainerType {@link com.github.sachin.lootin.utils.ContainerType}
     */
    public static void setLootinContainer(@Nullable Entity minecart,@Nullable BlockState block,@NotNull ContainerType type){
        if(isLootinContainer(minecart, block, type)) return;
        if(type == ContainerType.CHEST){
            Chest chest = (Chest) block;
            PersistentDataContainer data = chest.getPersistentDataContainer();
            data.set(LConstants.IDENTITY_KEY, PersistentDataType.STRING, "");
            chest.update();
        }
        else if(type == ContainerType.MINECART){
            minecart.getPersistentDataContainer().set(LConstants.IDENTITY_KEY, PersistentDataType.STRING, "");
        }
        else if(type == ContainerType.DOUBLE_CHEST && isDoubleChest(block)){
            DoubleChest doubleChest = getDoubleChest(block);
            Chest c1 = ((Chest)doubleChest.getLeftSide());
            Chest c2 = ((Chest)doubleChest.getRightSide());
            setLootinContainer(null, c1, ContainerType.CHEST);
            setLootinContainer(null, c2, ContainerType.CHEST);
        }
        else if(type == ContainerType.BARREL){
            Barrel barrel = (Barrel) block;
            PersistentDataContainer data = barrel.getPersistentDataContainer();
            data.set(LConstants.IDENTITY_KEY,PersistentDataType.STRING,"");
            barrel.update();
        }
    }

    public static void fillLoot(Player player,PersistentDataContainer data,Lootable container,Inventory inventory){
        String lootTableKey = null;

        NamespacedKey playerLootKey = Lootin.getKey(player.getUniqueId().toString());

        if(data.has(playerLootKey,PersistentDataType.STRING) || data.has(playerLootKey,DataType.ITEM_STACK_ARRAY)) {
            Lootin.getPlugin().debug("&e[DEBUG] fillLoot returning early - player already has loot");
            return;
        }

//        if(plugin.isRunningBetterStructures && plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()) && data.has(LConstants.BETTER_STRUC_KEY,PersistentDataType.STRING)){
//
//            Chest chest = (Chest) container;
//            BetterStructuresListener.refillChest(chest);
//            return;
//        }
//        if(plugin.isRunningCustomStructures &&
//           plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()) &&
//           data.has(LConstants.CUSTOM_STRUC_KEY,PersistentDataType.STRING)){
//            if(CustomStructuresListener.isMinecraftLoottable((Container) container)){
//                lootTableKey = CustomStructuresListener.getLoottables((Container) container).next().getName();
//            }
//            else{
//                CustomStructuresListener.reFillContainer((Container) container);
//                return;
//            }
//        }
        if(container.getLootTable() != null){
            lootTableKey = container.getLootTable().getKey().toString();
            data.set(LConstants.LOOTTABLE_KEY,PersistentDataType.STRING,lootTableKey);
            container.setLootTable(null);
            if(container instanceof BlockState){
                ((BlockState)container).update();
            }
        }
        else if(data.has(LConstants.LOOTTABLE_KEY, PersistentDataType.STRING)){
            lootTableKey = data.get(LConstants.LOOTTABLE_KEY,PersistentDataType.STRING);
        }
        if(lootTableKey != null){
            inventory.clear();
            plugin.getPrilib().getNmsHandler().fill(player,container,lootTableKey,plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()));
        }
    }

    public static List<ItemStack> getPlayerLootItems(Lootable container,Player player){
        List<ItemStack> items = new ArrayList<>();
        String uuid = player.getUniqueId().toString();
        if(container instanceof PersistentDataHolder && container instanceof InventoryHolder){
            PersistentDataContainer data = (PersistentDataContainer) ((PersistentDataHolder)container).getPersistentDataContainer();
            if(data.has(Lootin.getKey(uuid), PersistentDataType.STRING)){
                items = ItemSerializer.deserialize(data.get(Lootin.getKey(uuid),PersistentDataType.STRING));
                ChestUtils.updatePersistentStorageTypes(data,((InventoryHolder)container).getInventory(),items,Lootin.getKey(uuid));
            }
            else if(data.has(Lootin.getKey(uuid), DataType.ITEM_STACK_ARRAY)){
                items = Arrays.asList(data.get(Lootin.getKey(uuid), DataType.ITEM_STACK_ARRAY));
            }
        }
        return items;
    }

    public static List<ItemStack> getDefaultItems(Lootable container){
        List<ItemStack> items = new ArrayList<>();
        if(container instanceof PersistentDataHolder && container instanceof InventoryHolder){
            PersistentDataHolder dataHolder = (PersistentDataHolder) container;
            InventoryHolder invHolder = (InventoryHolder) container;
            Inventory inventory = invHolder.getInventory();

            PersistentDataContainer data = dataHolder.getPersistentDataContainer();
            if(data.has(LConstants.DATA_KEY,PersistentDataType.STRING)){
                items = ItemSerializer.deserialize(data.get(LConstants.DATA_KEY,PersistentDataType.STRING));
                updatePersistentStorageTypes(data,inventory,items,LConstants.DATA_KEY);
                if(plugin.getConfig().getBoolean(LConstants.RESET_SEED,false) && !inventory.isEmpty()){
                    items = Arrays.asList(inventory.getContents());
                }
                inventory.clear();
                return items;
            }
            else if(data.has(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY)){
                if(plugin.getConfig().getBoolean(LConstants.RESET_SEED,false) && !inventory.isEmpty()){
                    items = Arrays.asList(inventory.getContents());
                }
                else{
                    items = Arrays.asList(data.get(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY));
                }
                inventory.clear();
                return items;
            }
        }
        return items;
    }


    /**
     * Retrives list of items unique to player or the default loot from lootable if there isnt any loot unique to player yet
     * @param minecart only StorageMinecart if container is a Entity or can be null
     * @param block Blockstate of container or can be null if using minecart
     * @param type ContainerType {@link com.github.sachin.lootin.utils.ContainerType}
     * @param player Player object whose unique loot will be retrived
     * @return list of items unique to player
     */
    public static List<ItemStack> getContainerItems(@Nullable Entity minecart,@Nullable BlockState block,@NotNull ContainerType type,@NotNull Player player){
        String uuid = player.getUniqueId().toString();
        PersistentDataContainer data = null;
        Inventory inventory = null;
        Lootable lootable = null;
        String loottable = null;
        if(type == ContainerType.CHEST){
            Chest chest = (Chest) block;
            data = chest.getPersistentDataContainer();
            inventory = chest.getBlockInventory();
            lootable = chest;
//            fillLoot(player,data,chest,inventory);
        }
        else if(type == ContainerType.MINECART){
            StorageMinecart tileCart = (StorageMinecart) minecart;
            data = tileCart.getPersistentDataContainer();
            inventory = tileCart.getInventory();
            lootable = tileCart;
//            fillLoot(player,data,tileCart,inventory);
        }
        else if(type == ContainerType.BARREL){
            Barrel barrel = (Barrel) block;
            data = barrel.getPersistentDataContainer();
            inventory = barrel.getInventory();
            lootable = barrel;
//            fillLoot(player,data,barrel,inventory);
        }
        else if(type == ContainerType.DOUBLE_CHEST && isDoubleChest(block)){
            DoubleChest doubleChest = getDoubleChest(block);
            Chest chestLeft = ((Chest) doubleChest.getLeftSide());
            Chest chestRight = ((Chest) doubleChest.getRightSide());
//            boolean changed = false;
            ArrayList<ItemStack> chestContents = new ArrayList<>();
            chestContents.addAll(getContainerItems(null,chestLeft,ContainerType.CHEST,player));
            chestContents.addAll(getContainerItems(null,chestRight,ContainerType.CHEST,player));
            inventory = doubleChest.getInventory();
            if(!chestContents.isEmpty()){
                return chestContents;
            }
        }
        else{
            return null;
        }

        if(data != null){
            List<ItemStack> items = new ArrayList<>();
            if(data.has(LConstants.STORAGE_DATA_KEY)){
                LootinContainer lootinContainer = StorageConverterUtility.getContainerData(data.get(LConstants.STORAGE_DATA_KEY,DataType.UUID));
                if(lootinContainer.getPlayerDataMap().containsKey(player.getUniqueId())){
                    PlayerLootData playerLootData = lootinContainer.getPlayerDataMap().get(player.getUniqueId());
                    boolean isRefill = false;
                    if(playerLootData.isRefillRequired(System.currentTimeMillis(),player.getWorld())){
                        if(!plugin.getWorldManager().shouldRefillCustomChests(player.getWorld().getName()) && data.has(LConstants.CUSTOM_CONTAINER_KEY)) return playerLootData.getItems();
                        fillLoot(player,data,lootable,inventory);
                        items = Arrays.asList(inventory.getContents());
                        if(inventory.isEmpty()){
                            if(data.has(LConstants.DATA_KEY,DataType.ITEM_STACK_ARRAY)){
                                items = Arrays.asList(data.get(LConstants.DATA_KEY,DataType.ITEM_STACK_ARRAY));
                            }
                            else if(data.has(LConstants.DATA_KEY,PersistentDataType.STRING)){
                                items = ItemSerializer.deserialize(data.get(LConstants.DATA_KEY,PersistentDataType.STRING));
                                updatePersistentStorageTypes(data,inventory,items,LConstants.DATA_KEY);
                            }
                        }
                        inventory.clear();
                        if(block != null) block.update();
                        playerLootData.setRefills(playerLootData.getRefills()+1);
                        playerLootData.setLastLootTime(System.currentTimeMillis());
                        isRefill = true;
                    }else{
                        items = playerLootData.getItems();
                    }
                    LootinInventoryOpenEvent event = new LootinInventoryOpenEvent(player,lootable,items,isRefill);
                    Bukkit.getServer().getPluginManager().callEvent(event);
                    if(event.isCancelled()) return null;
                    return event.getItems();
                }
            }
            fillLoot(player,data,lootable,inventory);
            if(data.has(Lootin.getKey(uuid),PersistentDataType.STRING)){
                items = ItemSerializer.deserialize(data.get(Lootin.getKey(uuid),PersistentDataType.STRING));
                updatePersistentStorageTypes(data,inventory,items,Lootin.getKey(uuid));
                return items;
            }
            else if(data.has(Lootin.getKey(uuid),DataType.ITEM_STACK_ARRAY)){
                return Arrays.asList(data.get(Lootin.getKey(uuid), DataType.ITEM_STACK_ARRAY));
            }
            else if(data.has(LConstants.DATA_KEY,PersistentDataType.STRING)){
                items = ItemSerializer.deserialize(data.get(LConstants.DATA_KEY,PersistentDataType.STRING));
                updatePersistentStorageTypes(data,inventory,items,LConstants.DATA_KEY);
                if(plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()) && !inventory.isEmpty()){
                    items = Arrays.asList(inventory.getContents());
                }
                inventory.clear();
                return items;
            }
            else if(data.has(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY)){
                if(plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()) && !inventory.isEmpty()){
                    items = Arrays.asList(inventory.getContents());
                }
                else{
                    items = Arrays.asList(data.get(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY));
                }
                inventory.clear();
                return items;
            }
        }
        ArrayList<ItemStack> chestContents = new ArrayList<>();
        Collections.addAll(chestContents, inventory.getContents());
        LootinInventoryOpenEvent event = new LootinInventoryOpenEvent(player,lootable,chestContents,true);
        Bukkit.getServer().getPluginManager().callEvent(event);
        if(event.isCancelled()) return null;
        setContainerItems(minecart, block, type, event.getItems(), LConstants.DATA_KEY.getKey());
        inventory.clear();
        return event.getItems();
    }

//    this is used to change from old way to store items as String to new way of storing them as ConfigurationSection
    public static void updatePersistentStorageTypes(PersistentDataContainer data,Inventory inv,List<ItemStack> items,NamespacedKey key){
        data.remove(key);
        data.set(key,DataType.ITEM_STACK_ARRAY,items.toArray(new ItemStack[0]));
        if(inv.getHolder() instanceof BlockState){
            ((BlockState)inv.getHolder()).update();
        }
    }

//    private static void fillDoubleLoot(String uuid, Chest chest, ArrayList<ItemStack> items) {
//        PersistentDataContainer data = chest.getPersistentDataContainer();
//        if(data.has(Lootin.getKey(uuid), PersistentDataType.STRING)){
//            items.addAll(ItemSerializer.deserialize(data.get(Lootin.getKey(uuid), PersistentDataType.STRING)));
//            return;
//        }else if(!data.has(LConstants.DATA_KEY, PersistentDataType.STRING)){
//            return;
//        }
//        items.addAll(ItemSerializer.deserialize(data.get(LConstants.DATA_KEY, PersistentDataType.STRING)));
//    }

    /**
     * Stores the list of items into given container
     * @param minecart only StorageMinecart if container is a Entity or can be null
     * @param block Blockstate of container or can be null if using minecart
     * @param type ContainerType {@link com.github.sachin.lootin.utils.ContainerType}
     * @param items list of items to be stored
     * @param key a key, generally use player's uuid as key
     */
    public static void setContainerItems(@Nullable Entity minecart,@Nullable BlockState block,@NotNull ContainerType type,@NotNull List<ItemStack> items,String key){
        PersistentDataContainer data = null;
        if(block != null && block instanceof PersistentDataHolder){
            if(type == ContainerType.DOUBLE_CHEST && isDoubleChest(block)){
                DoubleChest doubleChest = getDoubleChest(block);
                Chest c1 = ((Chest)doubleChest.getLeftSide());
                Chest c2 = ((Chest)doubleChest.getRightSide());
                setContainerItems(null, c1, ContainerType.CHEST, items.subList(0, 26), key);
                setContainerItems(null, c2, ContainerType.CHEST, items.subList(26, 53), key);
                return;
            }
            data = ((PersistentDataHolder)block).getPersistentDataContainer();
        }
        else if(minecart != null){
            data = minecart.getPersistentDataContainer();
        }
        if(data != null){
            if(StorageConverterUtility.isValidUUID(key)){
                PersistentDataHolder holder = minecart != null ? minecart : (PersistentDataHolder) block;
                if(data.has(LConstants.STORAGE_DATA_KEY,DataType.UUID)){
                    StorageConverterUtility.update(holder,key,items);
                }
                else{
                    StorageConverterUtility.convert(holder);
                    StorageConverterUtility.update(holder,key,items);
                }
            }
            else{
                data.set(Lootin.getKey(key), DataType.ITEM_STACK_ARRAY,items.toArray(new ItemStack[0]));
                if(block != null){block.update();}
            }
        }
    }

    public static boolean openLootinInventory(Lootable lootable, Player player, Location location, @Nullable List<ItemStack> overrideItems){
        Lootin plugin = Lootin.getPlugin();
        if(plugin.isRunningWG && !plugin.getWGflag().queryFlag(player,location)) return false;
        if(plugin.isBlackListWorld(player.getWorld())) return false;
        ContainerType containerType = ChestUtils.getContainerType(lootable);
        if(containerType==null) return false;
        LootTable lootTable = lootable.getLootTable();
        if(lootTable != null){
            if(plugin.isBlackListedLootable(lootTable,player.getWorld())) return false;
            ChestUtils.setLootinContainer(lootable,containerType);
        }
        if(!ChestUtils.isLootinContainer(lootable,containerType) && overrideItems == null) return false;

        if (player.isSneaking() || player.getGameMode() == GameMode.SPECTATOR) return false;

        Cooldown cooldown = plugin.interactCooldown.get(player.getUniqueId());
        if (!cooldown.isTriggerable()) return false;
        cooldown.trigger();

        // Check global shared mode setting
        if (plugin.getConfig().getBoolean(LConstants.SHARED_MODE, false)) {
            return openSharedContainer(lootable, player, location, containerType);
        }

        // Per-player logic (original behavior)
        if(containerType==ContainerType.MINECART){
            if(plugin.currentMinecartviewers.contains((StorageMinecart) lootable)){
                plugin.sendPlayerMessage(LConstants.CHEST_EDITED,player);
                return true;
            }
            new LootinGui(player,containerType,lootable,overrideItems).open();
            return true;
        }
        if(plugin.currentChestviewers.contains(location)) {
            plugin.sendPlayerMessage(LConstants.CHEST_EDITED,player);
            return true;
        }
        new LootinGui(player,containerType,lootable,overrideItems).open();
        return true;
    }

    public static DoubleChest getDoubleChest(BlockState block){
        Chest chest = (Chest) block;
        return ((DoubleChest)chest.getInventory().getHolder());
    }

    public static boolean isDoubleChest(BlockState block){
        return (block instanceof Chest) && (((Chest)block).getInventory().getHolder() instanceof DoubleChest);
    }

    public static ContainerType getContainerType(Lootable lootable){
        if(lootable instanceof BlockState){
            BlockState block = (BlockState) lootable;
            if(isDoubleChest(block)) return ContainerType.DOUBLE_CHEST;
            else if(block.getType()==Material.CHEST || block.getType()==Material.TRAPPED_CHEST) return ContainerType.CHEST;
            else if(block.getType()==Material.BARREL) return ContainerType.BARREL;
            else return null;
        } else if (lootable instanceof StorageMinecart) {
            return ContainerType.MINECART;
        }
        return null;
    }



    public static void setLootinContainer(Lootable lootable, ContainerType type){
        setLootinContainer((type==ContainerType.MINECART) ? ((Entity) lootable) : null,(type!=ContainerType.MINECART) ? ((BlockState)lootable) : null,type);
    }

    public static boolean isLootinContainer(Lootable lootable, ContainerType type){
        return isLootinContainer((type==ContainerType.MINECART) ? ((Entity) lootable) : null,(type!=ContainerType.MINECART) ? ((BlockState)lootable) : null,type);
    }

    private static boolean hasKey(PersistentDataContainer data){
        return data.has(LConstants.IDENTITY_KEY, PersistentDataType.STRING);
    }

    public static boolean isChest(Material mat){
        return mat==Material.CHEST || mat==Material.TRAPPED_CHEST;
    }

    /**
     * Immediately refills a shared container (used by /lootin refill command)
     */
    public static boolean refillSharedContainer(BlockState state, Player player) {
        if(!(state instanceof Lootable) || !(state instanceof Container)) {
            return false;
        }
        Lootable lootable = (Lootable) state;
        ContainerType containerType = getContainerType(lootable);
        if(containerType == null) {
            return false;
        }
        Block block = state.getBlock();
        Inventory inventory = refillSharedInventory(containerType, block, null, ((Container)state).getInventory(), player);
        return inventory != null && !inventory.isEmpty();
    }

    /**
     * Immediately refills a shared minecart (used by /lootin refill command)
     */
    public static boolean refillSharedContainer(StorageMinecart minecart, Player player) {
        Inventory inventory = refillSharedInventory(ContainerType.MINECART, null, minecart, minecart.getInventory(), player);
        return inventory != null && !inventory.isEmpty();
    }

    /**
     * Opens a shared container (not per-player) with global refill logic
     */
    private static boolean openSharedContainer(Lootable lootable, Player player, Location location, ContainerType containerType) {
        plugin.debug("&e[DEBUG] Opening shared container for " + player.getName() + " at " + location);

        Inventory inventory = null;
        PersistentDataContainer data = null;
        StorageMinecart minecart = null;
        Block block = location.getBlock();
        BlockState state = block.getState();

        if (containerType == ContainerType.MINECART) {
            minecart = (StorageMinecart) lootable;
            inventory = minecart.getInventory();
            data = minecart.getPersistentDataContainer();
        } else if (containerType == ContainerType.CHEST || containerType == ContainerType.DOUBLE_CHEST) {
            if (!(state instanceof Chest)) {
                plugin.debug("&e[DEBUG] Expected chest but found " + state.getType());
                return false;
            }
            Chest chest = (Chest) state;
            inventory = chest.getBlockInventory();
            data = chest.getPersistentDataContainer();
        } else if (containerType == ContainerType.BARREL) {
            if (!(state instanceof Barrel)) {
                plugin.debug("&e[DEBUG] Expected barrel but found " + state.getType());
                return false;
            }
            Barrel barrel = (Barrel) state;
            inventory = barrel.getInventory();
            data = barrel.getPersistentDataContainer();
        } else {
            plugin.debug("&e[DEBUG] Shared mode unsupported container type " + containerType);
            return false;
        }

        if (data == null || inventory == null) {
            plugin.debug("&e[DEBUG] Data or inventory missing for shared container");
            return false;
        }

        boolean hasTimestamp = data.has(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG);
        boolean needsRefill = !hasTimestamp || isSharedRefillRequired(data);
        plugin.debug("&e[DEBUG] Shared refill check -> needsRefill: " + needsRefill);

        if (needsRefill) {
            inventory = refillSharedInventory(containerType, block, minecart, inventory, player);
        }

        plugin.debug("&e[DEBUG] About to open inventory, empty: " + inventory.isEmpty());
        player.openInventory(inventory);
        return true;
    }

    private static Inventory refillSharedInventory(ContainerType containerType, Block block, StorageMinecart minecart, Inventory fallbackInventory, Player player) {
        long timestamp = System.currentTimeMillis();
        boolean randomize = plugin.getConfig().getBoolean(LConstants.SHARED_RESET_SEED, true);

        if (containerType == ContainerType.MINECART) {
            return refillMinecartInventory(minecart, randomize, timestamp, player);
        }

        BlockState state = block.getState();
        Lootable lootState;
        PersistentDataContainer data;

        if ((containerType == ContainerType.CHEST || containerType == ContainerType.DOUBLE_CHEST) && state instanceof Chest) {
            Chest chest = (Chest) state;
            lootState = chest;
            data = chest.getPersistentDataContainer();
        } else if (containerType == ContainerType.BARREL && state instanceof Barrel) {
            Barrel barrel = (Barrel) state;
            lootState = barrel;
            data = barrel.getPersistentDataContainer();
        } else {
            return fallbackInventory;
        }

        // Clear the world's actual inventory before filling
        if (state instanceof Container) {
            ((Container) state).getInventory().clear();
        }

        boolean filled = false;
        if (randomize) {
            // NMS fill operates on the world's actual block entity, not the snapshot
            filled = fillFromLootTable(player, lootState, data);
        }
        if (!filled) {
            // For template restore, we need to work with a fresh state after any NMS operations
            BlockState freshState = block.getState();
            if (freshState instanceof Container) {
                filled = restoreTemplate(((Container) freshState).getInventory(), data);
            }
        }

        // Get fresh state to set timestamp and return updated inventory
        BlockState finalState = block.getState();
        if (finalState instanceof TileState) {
            PersistentDataContainer finalData = ((TileState) finalState).getPersistentDataContainer();
            finalData.set(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG, timestamp);
            finalState.update();
        }

        // Return the world's actual inventory (from fresh state)
        if (finalState instanceof Container) {
            return ((Container) finalState).getInventory();
        }
        return fallbackInventory;
    }

    private static Inventory refillMinecartInventory(StorageMinecart minecart, boolean randomize, long timestamp, Player player) {
        if (minecart == null) {
            return null;
        }
        Inventory inventory = minecart.getInventory();
        PersistentDataContainer data = minecart.getPersistentDataContainer();
        inventory.clear();

        boolean filled = false;
        if (randomize) {
            // For minecarts, the entity itself is the live object (not a snapshot like blocks)
            filled = fillFromLootTable(player, minecart, data);
        }
        if (!filled) {
            // Minecart inventory is live, so we can use it directly
            filled = restoreTemplate(inventory, data);
        }
        if (!filled) {
            plugin.debug("&e[DEBUG] Shared refill could not determine loot for minecart");
        }

        data.set(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG, timestamp);
        return minecart.getInventory(); // Return fresh reference
    }

    private static boolean fillFromLootTable(Player player, Lootable lootable, PersistentDataContainer data) {
        String key = ensureLootTableKey(lootable, data);
        if (key == null) {
            plugin.debug("&e[DEBUG] Shared refill missing loot table key");
            return false;
        }
        plugin.debug("&e[DEBUG] Shared refill applying loot table: " + key);
        
        // Get fresh inventory from world before fill
        Inventory targetInventory = null;
        if (lootable instanceof BlockState) {
            BlockState freshState = ((BlockState) lootable).getBlock().getState();
            if (freshState instanceof Container) {
                targetInventory = ((Container) freshState).getInventory();
            }
        } else if (lootable instanceof InventoryHolder) {
            targetInventory = ((InventoryHolder) lootable).getInventory();
        }
        
        if (targetInventory == null) {
            plugin.debug("&e[DEBUG] Could not get target inventory for loot fill");
            return false;
        }
        
        // Clear and fill using Bukkit API
        targetInventory.clear();
        
        // Try to use Bukkit's LootTable API first
        NamespacedKey lootTableNsKey = NamespacedKey.fromString(key);
        if (lootTableNsKey != null) {
            LootTable lootTable = Bukkit.getLootTable(lootTableNsKey);
            if (lootTable != null) {
                // Build loot context
                Location loc = null;
                if (lootable instanceof BlockState) {
                    loc = ((BlockState) lootable).getLocation();
                } else if (lootable instanceof Entity) {
                    loc = ((Entity) lootable).getLocation();
                }
                
                if (loc != null) {
                    java.util.Random random = new java.util.Random();
                    if (plugin.getWorldManager().shouldResetSeed(player.getWorld().getName())) {
                        random = new java.util.Random(); // New random seed each time
                    }
                    
                    org.bukkit.loot.LootContext.Builder contextBuilder = new org.bukkit.loot.LootContext.Builder(loc);
                    contextBuilder.killer(player);
                    
                    try {
                        lootTable.fillInventory(targetInventory, random, contextBuilder.build());
                        plugin.debug("&e[DEBUG] Bukkit LootTable.fillInventory completed");
                    } catch (Exception e) {
                        plugin.debug("&e[DEBUG] Bukkit LootTable.fillInventory failed: " + e.getMessage());
                        // Fall back to NMS
                        plugin.getPrilib().getNmsHandler().fill(player, lootable, key, plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()));
                    }
                } else {
                    // Fall back to NMS if we can't get location
                    plugin.getPrilib().getNmsHandler().fill(player, lootable, key, plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()));
                }
            } else {
                plugin.debug("&e[DEBUG] Could not find loot table: " + key + ", falling back to NMS");
                plugin.getPrilib().getNmsHandler().fill(player, lootable, key, plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()));
            }
        } else {
            plugin.debug("&e[DEBUG] Invalid loot table key: " + key + ", falling back to NMS");
            plugin.getPrilib().getNmsHandler().fill(player, lootable, key, plugin.getWorldManager().shouldResetSeed(player.getWorld().getName()));
        }
        
        // Check result
        Inventory freshInventory = null;
        if (lootable instanceof BlockState) {
            BlockState state = ((BlockState) lootable).getBlock().getState();
            if (state instanceof Container) {
                freshInventory = ((Container) state).getInventory();
            }
        } else if (lootable instanceof InventoryHolder) {
            freshInventory = ((InventoryHolder) lootable).getInventory();
        }
        
        boolean success = freshInventory != null && !freshInventory.isEmpty();
        plugin.debug("&e[DEBUG] After fill, inventory empty: " + (freshInventory == null || freshInventory.isEmpty()));
        plugin.debug("&e[DEBUG] fillFromLootTable result: " + success);
        return success;
    }

    private static String ensureLootTableKey(Lootable lootable, PersistentDataContainer data) {
        if (data.has(LConstants.LOOTTABLE_KEY, PersistentDataType.STRING)) {
            return data.get(LConstants.LOOTTABLE_KEY, PersistentDataType.STRING);
        }
        if (lootable.getLootTable() != null) {
            String tableKey = lootable.getLootTable().getKey().toString();
            data.set(LConstants.LOOTTABLE_KEY, PersistentDataType.STRING, tableKey);
            lootable.setLootTable(null);
            if (lootable instanceof BlockState) {
                ((BlockState) lootable).update();
            }
            return tableKey;
        }
        return null;
    }

    private static boolean restoreTemplate(Inventory inventory, PersistentDataContainer data) {
        if (data.has(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY)) {
            ItemStack[] template = data.get(LConstants.DATA_KEY, DataType.ITEM_STACK_ARRAY);
            plugin.debug("&e[DEBUG] Restoring template with " + template.length + " slots");
            inventory.setContents(template);
            plugin.debug("&e[DEBUG] After template restore, inventory empty: " + inventory.isEmpty());
            return true;
        }
        if (data.has(LConstants.DATA_KEY, PersistentDataType.STRING)) {
            List<ItemStack> items = ItemSerializer.deserialize(data.get(LConstants.DATA_KEY, PersistentDataType.STRING));
            plugin.debug("&e[DEBUG] Restoring template (legacy) with " + items.size() + " items");
            inventory.setContents(items.toArray(new ItemStack[0]));
            updatePersistentStorageTypes(data, inventory, items, LConstants.DATA_KEY);
            return true;
        }
        plugin.debug("&e[DEBUG] No template found in DATA_KEY");
        return false;
    }

    /**
     * Checks if shared container needs refill based on refill time
     */
    public static boolean isSharedRefillRequired(PersistentDataContainer data) {
        if (!data.has(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG)) {
            return false; // First time opening, no refill needed
        }

        long lastRefill = data.get(LConstants.SHARED_LAST_REFILL_KEY, PersistentDataType.LONG);
        long currentTime = System.currentTimeMillis();
        String refillTimeStr = plugin.getConfig().getString(LConstants.SHARED_REFILL_TIME, "7d");
        long refillInterval = parseTimeString(refillTimeStr);
        long diff = currentTime - lastRefill;
        plugin.debug("&e[DEBUG] Shared refill check -> last:" + lastRefill + " diff:" + diff + " interval:" + refillInterval);

        return diff >= refillInterval;
    }

    /**
     * Parses time string like "7d", "12h", "30m", "60s" to milliseconds
     */
    private static long parseTimeString(String timeStr) {
        java.util.regex.Matcher matcher = LConstants.TIME_UNITS_PATTERN.matcher(timeStr);
        long totalMillis = 0;

        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);

            switch (unit) {
                case "d":
                    totalMillis += value * 24L * 60 * 60 * 1000;
                    break;
                case "h":
                    totalMillis += value * 60L * 60 * 1000;
                    break;
                case "m":
                    totalMillis += value * 60L * 1000;
                    break;
                case "s":
                    totalMillis += value * 1000L;
                    break;
            }
        }

        return totalMillis > 0 ? totalMillis : 7L * 24 * 60 * 60 * 1000; // Default to 7 days
    }
}