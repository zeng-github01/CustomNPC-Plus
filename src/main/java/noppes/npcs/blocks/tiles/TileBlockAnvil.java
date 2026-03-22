package noppes.npcs.blocks.tiles;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.EnumSkyBlock;

public class TileBlockAnvil extends TileEntity {

    public boolean firstTick = true;
    public ItemStack[] items = new ItemStack[16];

    public TileBlockAnvil() {
    }

    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (hasWorldObj() && firstTick && !getWorldObj().isRemote) {
            firstTick = false;
            getWorldObj().updateLightByType(EnumSkyBlock.Block, xCoord, yCoord, zCoord);
            getWorldObj().markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);

        NBTTagList list = new NBTTagList();

        for (int i = 0; i < items.length; i++) {
            if (items[i] != null) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setByte("Slot", (byte) i);
                items[i].writeToNBT(tag);
                list.appendTag(tag);
            }
        }

        compound.setTag("Items", list);


    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);

        NBTTagList list = compound.getTagList("Items", 10); // 10 = NBTTagCompound
        this.items = new ItemStack[16];

        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            int slot = tag.getByte("Slot") & 255;

            if (slot >= 0 && slot < items.length) {
                items[slot] = ItemStack.loadItemStackFromNBT(tag);
            }
        }
    }
}
