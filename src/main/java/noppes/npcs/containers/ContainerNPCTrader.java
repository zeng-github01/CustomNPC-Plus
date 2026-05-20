package noppes.npcs.containers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import noppes.npcs.NoppesUtilPlayer;
import noppes.npcs.config.ConfigExperimental;
import noppes.npcs.controllers.data.PlayerData;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.roles.RoleTrader;


public class ContainerNPCTrader extends ContainerNpcInterface {
    public RoleTrader role;
    private final EntityNPCInterface npc;

    // Layout constants for 256px wide GUI
    public static final int COLUMN_WIDTH = 80;
    public static final int COLUMN_START_X = 8;
    public static final int ROW_HEIGHT = 22;
    public static final int ROW_START_Y = 6;

    // Slot offsets within each column (for 18x18 slots, items render centered)
    public static final int CURRENCY1_OFFSET = 2;   // Primary currency slot
    public static final int CURRENCY2_OFFSET = 20;  // Secondary currency slot
    public static final int OUTPUT_OFFSET = 50;     // Output slot

    public ContainerNPCTrader(EntityNPCInterface npc, EntityPlayer player) {
        super(player);
        this.npc = npc;
        role = (RoleTrader) npc.roleInterface;

        // Register this player as viewing the trader (server-side only)
        // Needed for shared stock (not per-player) to sync updates to other viewers
        // Works for both linked markets (MarketRegistry) and normal traders (local viewers)
        if (player instanceof EntityPlayerMP && !role.stock.perPlayer) {
            role.registerViewer((EntityPlayerMP) player);
        }

        // Trade slots: 3 columns x 6 rows, 80px column width
        // Layout per column: [Slot1][Slot2] = [Output]
        for (int i = 0; i < 18; i++) {
            int col = i % 3;
            int row = i / 3;
            int x = COLUMN_START_X + col * COLUMN_WIDTH;
            int y = ROW_START_Y + row * ROW_HEIGHT;
            // Output slot position (slot texture is 18x18, item renders centered at +1)
            addSlotToContainer(new Slot(role.inventorySold, i, x + OUTPUT_OFFSET + 1, y + 1));
        }

        for (int i1 = 0; i1 < 3; i1++) {
            for (int l1 = 0; l1 < 9; l1++) {
                addSlotToContainer(new Slot(player.inventory, l1 + i1 * 9 + 9, 48 + l1 * 18, 137 + i1 * 18));
            }
        }

        // Player hotbar
        for (int j1 = 0; j1 < 9; j1++) {
            addSlotToContainer(new Slot(player.inventory, j1, 48 + j1 * 18, 195));
        }
    }

    @Override
    public void onContainerClosed(EntityPlayer player) {
        super.onContainerClosed(player);
        // Unregister this player from viewer tracking (only if registered for shared stock)
        if (player instanceof EntityPlayerMP && !role.stock.perPlayer) {
            role.unregisterViewer((EntityPlayerMP) player);
        }
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer par1EntityPlayer, int i) {
        return null;
    }

    @Override
    public ItemStack slotClick(int i, int j, int par3, EntityPlayer entityplayer) {
        if (par3 == 6) par3 = 0;
        if (i < 0 || i >= 18)
            return super.slotClick(i, j, par3, entityplayer);
        if (j == 1)
            return null;

        if (ConfigExperimental.useLegacyTrader) {
            ItemStack c1 = role.inventoryCurrency.getStackInSlot(i);
            ItemStack c2 = role.inventoryCurrency.getStackInSlot(i + 18);
            if (c1 == null && c2 == null) {
                return null;
            }
        }

        Slot slot = (Slot) inventorySlots.get(i);
        if (slot == null || slot.getStack() == null)
            return null;

        ItemStack item = slot.getStack();
        if (!canGivePlayer(item, entityplayer) || !isSlotEnabled(i, entityplayer))
            return null;

        String playerName = entityplayer.getCommandSenderName();
        long currencyCost = role.getCurrencyCost(i);

        // --- 逻辑分歧点 ---

        if (par3 == 1) {
            // 【情况 A: Shift 点击】 -> 批量购买并放入背包
            int boughtCount = 0;
            // 循环条件：有库存 && 够钱买物品 && 够钱买金币 && 背包有空位
            while (role.hasStock(i, playerName, 1) && canBuy(i, entityplayer) &&
                (currencyCost <= 0 || PlayerData.get(entityplayer).tradeData.getBalance() >= currencyCost) &&
                entityplayer.inventory.getFirstEmptyStack() != -1) {

                // 1. 消耗物品货币
                NoppesUtilPlayer.consumeItem(entityplayer, role.inventoryCurrency.getStackInSlot(i), role.ignoreDamage, role.ignoreNBT);
                NoppesUtilPlayer.consumeItem(entityplayer, role.inventoryCurrency.getStackInSlot(i + 18), role.ignoreDamage, role.ignoreNBT);

                // 2. 扣除金币货币 (必须在循环内，否则会刷钱)
                if (currencyCost > 0) {
                    PlayerData.get(entityplayer).tradeData.withdraw(currencyCost);
                }

                // 3. 消耗库存
                role.consumeStock(i, playerName, 1);

                // 4. 给予物品到背包
                ItemStack soldItem = item.copy();
                entityplayer.inventory.addItemStackToInventory(soldItem);
                role.addPurchase(i, entityplayer.getDisplayName());
                boughtCount++;
            }

            if (boughtCount > 0 && entityplayer instanceof EntityPlayerMP) {
                role.syncToPlayer((EntityPlayerMP) entityplayer);
            }
            return null; // Shift点击通常返回null或空

        } else {
            // 【情况 B: 普通点击】 -> 购买一个并放在鼠标光标上

            // 检查：有库存 && 够钱买物品 && 够钱买金币
            if (role.hasStock(i, playerName, 1) && canBuy(i, entityplayer) &&
                (currencyCost <= 0 || PlayerData.get(entityplayer).tradeData.getBalance() >= currencyCost)) {

                // 只有当玩家手上没拿着东西时，才允许购买到手上（防止覆盖手上的东西）
                if (entityplayer.inventory.getItemStack() == null) {

                    // 1. 消耗物品货币
                    NoppesUtilPlayer.consumeItem(entityplayer, role.inventoryCurrency.getStackInSlot(i), role.ignoreDamage, role.ignoreNBT);
                    NoppesUtilPlayer.consumeItem(entityplayer, role.inventoryCurrency.getStackInSlot(i + 18), role.ignoreDamage, role.ignoreNBT);

                    // 2. 扣除金币货币
                    if (currencyCost > 0) {
                        PlayerData.get(entityplayer).tradeData.withdraw(currencyCost);
                    }

                    // 3. 消耗库存
                    role.consumeStock(i, playerName, 1);

                    // 4. 关键点：将物品放在鼠标指针上，而不是直接进背包
                    ItemStack soldItem = item.copy();
                    entityplayer.inventory.setItemStack(soldItem); // <--- 修复BUG的核心

                    role.addPurchase(i, entityplayer.getDisplayName());

                    if (entityplayer instanceof EntityPlayerMP) {
                        role.syncToPlayer((EntityPlayerMP) entityplayer);
                    }

                    return soldItem;
                }
            }
        }

        return null;
    }

    public boolean isSlotEnabled(int slot, EntityPlayer player) {
        return role.isSlotEnabled(slot, player.getDisplayName());
    }

    public boolean canBuy(int slot, EntityPlayer player) {
        ItemStack currency = role.inventoryCurrency.getStackInSlot(slot);
        ItemStack currency2 = role.inventoryCurrency.getStackInSlot(slot + 18);
        if (ConfigExperimental.useLegacyTrader && currency == null && currency2 == null) {
            return false;
        }
        if (currency == null && currency2 == null)
            return true;
        if (currency == null) {
            currency = currency2;
            currency2 = null;
        }
        if (NoppesUtilPlayer.compareItems(currency, currency2, role.ignoreDamage, role.ignoreNBT)) {
            currency = currency.copy();
            currency.stackSize += currency2.stackSize;
            currency2 = null;
        }
        if (currency2 == null)
            return NoppesUtilPlayer.compareItems(player, currency, role.ignoreDamage, role.ignoreNBT);
        return NoppesUtilPlayer.compareItems(player, currency, role.ignoreDamage, role.ignoreNBT) && NoppesUtilPlayer.compareItems(player, currency2, role.ignoreDamage, role.ignoreNBT);

    }

    private boolean canGivePlayer(ItemStack item, EntityPlayer entityplayer) {//check Item being held with the mouse
        ItemStack itemstack3 = entityplayer.inventory.getItemStack();
        if (itemstack3 == null) {
            return true;
        } else if (NoppesUtilPlayer.compareItems(itemstack3, item, false, false)) {
            int k1 = item.stackSize;
            if (k1 > 0 && k1 + itemstack3.stackSize <= itemstack3.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    private void givePlayer(ItemStack item, EntityPlayer entityplayer) {//set item bought to the held mouse item
        ItemStack itemstack3 = entityplayer.inventory.getItemStack();
        if (itemstack3 == null) {
            entityplayer.inventory.setItemStack(item);
        } else if (NoppesUtilPlayer.compareItems(itemstack3, item, false, false)) {

            int k1 = item.stackSize;
            if (k1 > 0 && k1 + itemstack3.stackSize <= itemstack3.getMaxStackSize()) {
                itemstack3.stackSize += k1;
            }
        }
    }
}
