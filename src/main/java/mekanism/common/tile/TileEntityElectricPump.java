package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.IConfigurable;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.MekanismFluids;
import mekanism.common.Upgrade;
import mekanism.common.base.*;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.config.MekanismConfig;
import mekanism.common.integration.computer.IComputerIntegration;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.component.TileComponentUpgrade;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.fluids.*;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class TileEntityElectricPump extends TileEntityElectricBlock implements IFluidHandlerWrapper, ISustainedTank, IConfigurable, IRedstoneControl, IUpgradeTile,
        ITankManager, IComputerIntegration, ISecurityTile, IComparatorSupport {

    private static final int[] UPSLOTS = {0};
    private static final int[] DOWNSLOTS = {1};
    private static final int[] SIDESLOTS = {2};

    private static final String[] methods = new String[]{"reset"};
    /**
     * This pump's tank
     */
    public FluidTank fluidTank = new FluidTank(10000);
    /**
     * The type of fluid this pump is pumping
     */
    public Fluid activeType;
    public boolean suckedLastOperation;
    /**
     * How much energy this machine consumes per-tick.
     */
    public double BASE_ENERGY_PER_TICK = MachineType.ELECTRIC_PUMP.getUsage();
    public double energyPerTick = BASE_ENERGY_PER_TICK;
    /**
     * How many ticks it takes to run an operation.
     */
    public int BASE_TICKS_REQUIRED = 20;
    public int ticksRequired = BASE_TICKS_REQUIRED;
    /**
     * How many ticks this machine has been operating for.
     */
    public int operatingTicks;
    /**
     * The nodes that have full sources near them or in them
     */
    public Set<Coord4D> recurringNodes = new HashSet<>();
    /**
     * This machine's current RedstoneControl type.
     */
    public RedstoneControl controlType = RedstoneControl.DISABLED;
    public TileComponentUpgrade upgradeComponent = new TileComponentUpgrade(this, 3);
    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    private int currentRedstoneLevel;

    public TileEntityElectricPump() {
        super("ElectricPump", MachineType.ELECTRIC_PUMP.getStorage());
        inventory = NonNullList.withSize(4, ItemStack.EMPTY);
        upgradeComponent.setSupported(Upgrade.FILTER);
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote) {
            ChargeUtils.discharge(2, this);
            if (fluidTank.getFluid() != null) {
                if (FluidContainerUtils.isFluidContainer(inventory.get(0))) {
                    FluidContainerUtils.handleContainerItemFill(this, fluidTank, 0, 1);
                }
            }
            if (MekanismUtils.canFunction(this) && getEnergy() >= energyPerTick) {
                if (suckedLastOperation) {
                    setEnergy(getEnergy() - energyPerTick);
                }
                if ((operatingTicks + 1) < ticksRequired) {
                    operatingTicks++;
                } else {
                    if (fluidTank.getFluid() == null || fluidTank.getFluid().amount + Fluid.BUCKET_VOLUME <= fluidTank.getCapacity()) {
                        if (!suck(true)) {
                            suckedLastOperation = false;
                            reset();
                        } else {
                            suckedLastOperation = true;
                        }
                    } else {
                        suckedLastOperation = false;
                    }
                    operatingTicks = 0;
                }
            } else {
                suckedLastOperation = false;
            }
        }

        super.onUpdate();

        if (!world.isRemote) {
            if (fluidTank.getFluid() != null) {
                TileEntity tileEntity = Coord4D.get(this).offset(EnumFacing.UP).getTileEntity(world);
                if (CapabilityUtils.hasCapability(tileEntity, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.DOWN)) {
                    IFluidHandler handler = CapabilityUtils.getCapability(tileEntity, CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY, EnumFacing.DOWN);
                    FluidStack toDrain = new FluidStack(fluidTank.getFluid(), Math.min(256 * (upgradeComponent.getUpgrades(Upgrade.SPEED) + 1), fluidTank.getFluidAmount()));
                    fluidTank.drain(handler.fill(toDrain, true), true);
                }
            }
            int newRedstoneLevel = getRedstoneLevel();
            if (newRedstoneLevel != currentRedstoneLevel) {
                world.updateComparatorOutputLevel(pos, getBlockType());
                currentRedstoneLevel = newRedstoneLevel;
            }
        }
    }

    public boolean hasFilter() {
        return upgradeComponent.getInstalledTypes().contains(Upgrade.FILTER);
    }

    public boolean suck(boolean take) {
        List<Coord4D> tempPumpList = Arrays.asList(recurringNodes.toArray(new Coord4D[0]));
        Collections.shuffle(tempPumpList);

        //First see if there are any fluid blocks touching the pump - if so, sucks and adds the location to the recurring list
        for (EnumFacing orientation : EnumFacing.VALUES) {
            Coord4D wrapper = Coord4D.get(this).offset(orientation);
            FluidStack fluid = MekanismUtils.getFluid(world, wrapper, hasFilter());
            if (fluid != null && (activeType == null || fluid.getFluid() == activeType) && (fluidTank.getFluid() == null || fluidTank.getFluid().isFluidEqual(fluid))) {
                if (take) {
                    activeType = fluid.getFluid();
                    recurringNodes.add(wrapper);
                    fluidTank.fill(fluid, true);
                    if (shouldTake(fluid, wrapper)) {
                        world.setBlockToAir(wrapper.getPos());
                    }
                }
                return true;
            }
        }

        //Finally, go over the recurring list of nodes and see if there is a fluid block available to suck - if not, will iterate around the recurring block, attempt to suck,
        //and then add the adjacent block to the recurring list
        for (Coord4D wrapper : tempPumpList) {
            FluidStack fluid = MekanismUtils.getFluid(world, wrapper, hasFilter());
            if (fluid != null && (activeType == null || fluid.getFluid() == activeType) && (fluidTank.getFluid() == null || fluidTank.getFluid().isFluidEqual(fluid))) {
                if (take) {
                    activeType = fluid.getFluid();
                    fluidTank.fill(fluid, true);
                    if (shouldTake(fluid, wrapper)) {
                        world.setBlockToAir(wrapper.getPos());
                    }
                }
                return true;
            }

            //Add all the blocks surrounding this recurring node to the recurring node list
            for (EnumFacing orientation : EnumFacing.VALUES) {
                Coord4D side = wrapper.offset(orientation);
                if (Coord4D.get(this).distanceTo(side) <= MekanismConfig.current().general.maxPumpRange.val()) {
                    fluid = MekanismUtils.getFluid(world, side, hasFilter());
                    if (fluid != null && (activeType == null || fluid.getFluid() == activeType) && (fluidTank.getFluid() == null || fluidTank.getFluid().isFluidEqual(fluid))) {
                        if (take) {
                            activeType = fluid.getFluid();
                            recurringNodes.add(side);
                            fluidTank.fill(fluid, true);
                            if (shouldTake(fluid, side)) {
                                world.setBlockToAir(side.getPos());
                            }
                        }
                        return true;
                    }
                }
            }
            recurringNodes.remove(wrapper);
        }
        return false;
    }

    public void reset() {
        activeType = null;
        recurringNodes.clear();
    }

    private boolean shouldTake(FluidStack fluid, Coord4D coord) {
        if (fluid.getFluid() == FluidRegistry.WATER || fluid.getFluid() == MekanismFluids.HeavyWater) {
            return MekanismConfig.current().general.pumpWaterSources.val();
        }
        return true;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, fluidTank);
            controlType = RedstoneControl.values()[dataStream.readInt()];
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, fluidTank);
        data.add(controlType.ordinal());
        return data;
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);
        nbtTags.setBoolean("suckedLastOperation", suckedLastOperation);

        if (activeType != null) {
            nbtTags.setString("activeType", FluidRegistry.getFluidName(activeType));
        }

        if (fluidTank.getFluid() != null) {
            nbtTags.setTag("fluidTank", fluidTank.writeToNBT(new NBTTagCompound()));
        }

        nbtTags.setInteger("controlType", controlType.ordinal());

        NBTTagList recurringList = new NBTTagList();
        for (Coord4D wrapper : recurringNodes) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            wrapper.write(tagCompound);
            recurringList.appendTag(tagCompound);
        }
        if (recurringList.tagCount() != 0) {
            nbtTags.setTag("recurringNodes", recurringList);
        }
        return nbtTags;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
        suckedLastOperation = nbtTags.getBoolean("suckedLastOperation");
        if (nbtTags.hasKey("activeType")) {
            activeType = FluidRegistry.getFluid(nbtTags.getString("activeType"));
        }
        if (nbtTags.hasKey("fluidTank")) {
            fluidTank.readFromNBT(nbtTags.getCompoundTag("fluidTank"));
        }
        if (nbtTags.hasKey("controlType")) {
            controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];
        }
        if (nbtTags.hasKey("recurringNodes")) {
            NBTTagList tagList = nbtTags.getTagList("recurringNodes", NBT.TAG_COMPOUND);
            for (int i = 0; i < tagList.tagCount(); i++) {
                recurringNodes.add(Coord4D.read(tagList.getCompoundTagAt(i)));
            }
        }
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            //Only allow empty fluid containers
            return FluidContainerUtils.isFluidContainer(itemstack) && FluidUtil.getFluidContained(itemstack) == null;
        } else if (slotID == 2) {
            return ChargeUtils.canBeDischarged(itemstack);
        }
        return false;
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 2) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 1;
    }

    @Override
    public boolean sideIsConsumer(EnumFacing side) {
        return facing.getOpposite() == side;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        if (side == EnumFacing.UP) {
            return UPSLOTS;
        } else if (side == EnumFacing.DOWN) {
            return DOWNSLOTS;
        }
        return SIDESLOTS;
    }

    @Override
    public FluidTankInfo[] getTankInfo(EnumFacing direction) {
        if (direction == EnumFacing.UP) {
            return new FluidTankInfo[]{fluidTank.getInfo()};
        }
        return PipeUtils.EMPTY;
    }

    @Override
    public FluidTankInfo[] getAllTanks() {
        return getTankInfo(EnumFacing.UP);
    }

    @Override
    public void setFluidStack(FluidStack fluidStack, Object... data) {
        fluidTank.setFluid(fluidStack);
    }

    @Override
    public FluidStack getFluidStack(Object... data) {
        return fluidTank.getFluid();
    }

    @Override
    public boolean hasTank(Object... data) {
        return true;
    }

    @Override
    @Nullable
    public FluidStack drain(EnumFacing from, int maxDrain, boolean doDrain) {
        return fluidTank.drain(maxDrain, doDrain);
    }

    @Override
    public boolean canDrain(EnumFacing from, @Nullable FluidStack fluid) {
        return from == EnumFacing.byIndex(1) && FluidContainerUtils.canDrain(fluidTank.getFluid(), fluid);
    }

    @Override
    public EnumActionResult onSneakRightClick(EntityPlayer player, EnumFacing side) {
        reset();
        player.sendMessage(new TextComponentString(EnumColor.DARK_BLUE + Mekanism.LOG_TAG + " " + EnumColor.GREY + LangUtils.localize("tooltip.configurator.pumpReset")));
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        return EnumActionResult.PASS;
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.CONFIGURABLE_CAPABILITY || capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.CONFIGURABLE_CAPABILITY) {
            return Capabilities.CONFIGURABLE_CAPABILITY.cast(this);
        }
        if (capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY) {
            return CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY.cast(new FluidHandlerWrapper(this, side));
        }
        return super.getCapability(capability, side);
    }

    @Override
    public RedstoneControl getControlType() {
        return controlType;
    }

    @Override
    public void setControlType(RedstoneControl type) {
        controlType = type;
        MekanismUtils.saveChunk(this);
    }

    @Override
    public boolean canPulse() {
        return true;
    }

    @Override
    public TileComponentUpgrade getComponent() {
        return upgradeComponent;
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{fluidTank};
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        if (method == 0) {
            reset();
            return new Object[]{"Pump calculation reset."};
        }
        throw new NoSuchMethodException();
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        switch (upgrade) {
            case SPEED:
                ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
            case ENERGY:
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
                maxEnergy = MekanismUtils.getMaxEnergy(this, BASE_MAX_ENERGY);
                setEnergy(Math.min(getMaxEnergy(), getEnergy()));
            default:
                break;
        }
    }

    @Override
    public int getRedstoneLevel() {
        return MekanismUtils.redstoneLevelFromContents(fluidTank.getFluidAmount(), fluidTank.getCapacity());
    }
}
