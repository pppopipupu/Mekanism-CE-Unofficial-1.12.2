package mekanism.common.tile.prefab;

import io.netty.buffer.ByteBuf;
import mekanism.api.TileNetworkList;
import mekanism.common.Upgrade;
import mekanism.common.base.IComparatorSupport;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.util.MekanismUtils;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;

public abstract class TileEntityOperationalMachine extends TileEntityMachine implements IComparatorSupport {

    public int operatingTicks;

    public int BASE_TICKS_REQUIRED;

    public int ticksRequired;

    protected TileEntityOperationalMachine(String sound, MachineType type, int upgradeSlot, int baseTicksRequired) {
        super(sound, type, upgradeSlot);
        ticksRequired = BASE_TICKS_REQUIRED = baseTicksRequired;
    }

    public double getScaledProgress() {
        return Math.min((double) operatingTicks / ticksRequired, 1F);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            operatingTicks = dataStream.readInt();
            ticksRequired = dataStream.readInt();
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(operatingTicks);
        data.add(ticksRequired);
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        operatingTicks = nbtTags.getInteger("operatingTicks");
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("operatingTicks", operatingTicks);
        return nbtTags;
    }

    @Override
    public void recalculateUpgradables(Upgrade upgrade) {
        super.recalculateUpgradables(upgrade);
        switch (upgrade) {
            case ENERGY ->
                    energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK); // incorporate speed upgrades
            case SPEED -> {
                ticksRequired = MekanismUtils.getTicks(this, BASE_TICKS_REQUIRED);
                energyPerTick = MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK);
            }
            default -> {
            }
        }
    }

    @Override
    public int getRedstoneLevel() {
        return Container.calcRedstoneFromInventory(this);
    }
}
