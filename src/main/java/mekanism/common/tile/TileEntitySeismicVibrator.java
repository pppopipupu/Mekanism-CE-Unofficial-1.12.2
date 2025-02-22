package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.TileNetworkList;
import mekanism.common.Mekanism;
import mekanism.common.base.IActiveState;
import mekanism.common.base.IBoundingBlock;
import mekanism.common.base.IRedstoneControl;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.config.MekanismConfig;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tile.component.TileComponentSecurity;
import mekanism.common.tile.prefab.TileEntityElectricBlock;
import mekanism.common.util.ChargeUtils;
import mekanism.common.util.MekanismUtils;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import javax.annotation.Nonnull;

public class TileEntitySeismicVibrator extends TileEntityElectricBlock implements IActiveState, IRedstoneControl, ISecurityTile, IBoundingBlock {

    private static final int[] SLOTS = {0};

    public boolean isActive;

    public boolean clientActive;

    public int updateDelay;

    public int clientPiston;

    public double BASE_ENERGY_PER_TICK = MachineType.SEISMIC_VIBRATOR.getUsage();

    public RedstoneControl controlType = RedstoneControl.DISABLED;

    public TileComponentSecurity securityComponent = new TileComponentSecurity(this);

    public TileEntitySeismicVibrator() {
        super("SeismicVibrator", MachineType.SEISMIC_VIBRATOR.getStorage());
        inventory = NonNullList.withSize(SLOTS.length, ItemStack.EMPTY);
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (world.isRemote) {
            if (isActive) {
                clientPiston++;
            }
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0 && clientActive != isActive) {
                    isActive = clientActive;
                    MekanismUtils.updateBlock(world, getPos());
                }
            }
        } else {
            if (updateDelay > 0) {
                updateDelay--;
                if (updateDelay == 0 && clientActive != isActive) {
                    Mekanism.packetHandler.sendUpdatePacket(this);
                }
            }

            ChargeUtils.discharge(0, this);
            if (MekanismUtils.canFunction(this) && getEnergy() >= BASE_ENERGY_PER_TICK) {
                setActive(true);
                setEnergy(getEnergy() - BASE_ENERGY_PER_TICK);
            } else {
                setActive(false);
            }
        }
        if (getActive()) {
            Mekanism.activeVibrators.add(Coord4D.get(this));
        } else {
            Mekanism.activeVibrators.remove(Coord4D.get(this));
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        Mekanism.activeVibrators.remove(Coord4D.get(this));
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setBoolean("isActive", isActive);
        nbtTags.setInteger("controlType", controlType.ordinal());
        return nbtTags;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        clientActive = isActive = nbtTags.getBoolean("isActive");
        controlType = RedstoneControl.values()[nbtTags.getInteger("controlType")];
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            clientActive = dataStream.readBoolean();
            controlType = RedstoneControl.values()[dataStream.readInt()];
            if (updateDelay == 0 && clientActive != isActive) {
                updateDelay = MekanismConfig.current().general.UPDATE_DELAY.val();
                isActive = clientActive;
                MekanismUtils.updateBlock(world, getPos());
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        data.add(isActive);
        data.add(controlType.ordinal());
        return data;
    }

    @Override
    public boolean getActive() {
        return isActive;
    }

    @Override
    public void setActive(boolean active) {
        isActive = active;
        if (clientActive != active && updateDelay == 0) {
            Mekanism.packetHandler.sendUpdatePacket(this);
            updateDelay = 10;
            clientActive = active;
        }
    }

    @Override
    public boolean renderUpdate() {
        return false;
    }

    @Override
    public boolean lightUpdate() {
        return true;
    }

    @Override
    public boolean canSetFacing(@Nonnull EnumFacing facing) {
        return facing != EnumFacing.DOWN && facing != EnumFacing.UP;
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
    public boolean sideIsConsumer(EnumFacing side) {
        return side == facing.getOpposite();
    }

    @Override
    public boolean canPulse() {
        return false;
    }

    @Override
    public TileComponentSecurity getSecurity() {
        return securityComponent;
    }

    @Override
    public void onPlace() {
        MekanismUtils.makeBoundingBlock(world, getPos().up(), Coord4D.get(this));
    }

    @Override
    public void onBreak() {
        world.setBlockToAir(getPos().up());
        world.setBlockToAir(getPos());
    }

    @Nonnull
    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        return INFINITE_EXTENT_AABB;
    }

    @Nonnull
    @Override
    public int[] getSlotsForFace(@Nonnull EnumFacing side) {
        return SLOTS;
    }

    @Override
    public boolean isItemValidForSlot(int slot, @Nonnull ItemStack stack) {
        return ChargeUtils.canBeDischarged(stack);
    }

    @Nonnull
    @Override
    public BlockFaceShape getOffsetBlockFaceShape(@Nonnull EnumFacing face, @Nonnull Vec3i offset) {
        return BlockFaceShape.SOLID;
    }
}
