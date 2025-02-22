package mekanism.common.tile;

import mekanism.common.base.FluidHandlerWrapper;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.base.IFluidHandlerWrapper;
import mekanism.common.content.tank.DynamicFluidTank;
import mekanism.common.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class TileEntityDynamicValve extends TileEntityDynamicTank implements IFluidHandlerWrapper, IComparatorSupport {

    public DynamicFluidTank fluidTank;
    private int currentRedstoneLevel;

    public TileEntityDynamicValve() {
        super("Dynamic Valve");
        fluidTank = new DynamicFluidTank(this);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (!world.isRemote) {
            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                world.updateComparatorOutputLevel(pos, getBlockType());
                currentRedstoneLevel = newRedstoneLevel;
            }
        }
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing from) {
        return ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) ? new FluidTankInfo[]{fluidTank.getInfo()} : PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return getTankInfo(null);
    }

    @Override
    public int fill(EnumFacing from, @Nonnull FluidStack resource, boolean doFill) {
        return fluidTank.fill(resource, doFill);
    }

    @Override
    @Nullable
    public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
        return fluidTank.drain(maxDrain, doDrain);
    }

    @Override
    public boolean canFill(EnumFacing from, @Nonnull FluidStack fluid) {
        return (!world.isRemote && structure != null) || (world.isRemote && clientHasStructure);
    }

    @Override
    public boolean canDrain(EnumFacing from, @Nullable FluidStack fluid) {
        return ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) && FluidContainerUtils.canDrain(structure.fluidStored, fluid);
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize("gui.dynamicTank");
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) {
            if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
                return true;
            }
        }
        return super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if ((!world.isRemote && structure != null) || (world.isRemote && clientHasStructure)) {
            if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
                return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
            }
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return !world.isRemote ? structure == null : !clientHasStructure;
        }
        return super.isCapabilityDisabled(capability, side);
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return (!world.isRemote && structure != null) || (world.isRemote && clientHasStructure) ? SLOTS : InventoryUtils.EMPTY;
    }

    @Override
    public boolean isItemValidForSlot(int slot, @Nonnull ItemStack stack) {
        //can be filled/emptied
        return slot == 0 && FluidContainerUtils.isFluidContainer(stack);
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
}
