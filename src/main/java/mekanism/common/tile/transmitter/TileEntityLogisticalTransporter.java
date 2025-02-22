package mekanism.common.tile.transmitter;

import io.netty.buffer.ByteBuf;
import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.Mekanism;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.block.property.PropertyColor;
import mekanism.common.block.states.BlockStateTransmitter.TransmitterType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.transporter.PathfinderCache;
import mekanism.common.content.transporter.TransitRequest;
import mekanism.common.content.transporter.TransitRequest.TransitResponse;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.integration.multipart.MultipartTileNetworkJoiner;
import mekanism.common.tier.BaseTier;
import mekanism.common.tier.TransporterTier;
import mekanism.common.transmitters.TransporterImpl;
import mekanism.common.transmitters.grid.InventoryNetwork;
import mekanism.common.util.CapabilityUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.TextComponentGroup;
import mekanism.common.util.TransporterUtils;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.property.IExtendedBlockState;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TileEntityLogisticalTransporter extends TileEntityTransmitter<TileEntity, InventoryNetwork, Void> {

    private final int SYNC_PACKET = 1;
    private final int BATCH_PACKET = 2;

    public TransporterTier tier = TransporterTier.BASIC;

    private int delay = 0;
    private int delayCount = 0;
    private int Removetick = 0;
    public TileEntityLogisticalTransporter() {
        transmitterDelegate = new TransporterImpl(this);
    }

    @Override
    public BaseTier getBaseTier() {
        return tier.getBaseTier();
    }

    @Override
    public void setBaseTier(BaseTier baseTier) {
        tier = TransporterTier.get(baseTier);
    }

    @Override
    public TransmitterType getTransmitterType() {
        return TransmitterType.LOGISTICAL_TRANSPORTER;
    }

    @Override
    public TransmissionType getTransmissionType() {
        return TransmissionType.ITEM;
    }

    @Override
    public void onWorldSeparate() {
        super.onWorldSeparate();
        if (!getWorld().isRemote) {
            PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        }
    }

    @Override
    public TileEntity getCachedAcceptor(EnumFacing side) {
        return getCachedTile(side);
    }

    @Override
    public boolean isValidTransmitter(TileEntity tileEntity) {
        ILogisticalTransporter transporter = CapabilityUtils.getCapability(tileEntity, Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY, null);
        if (getTransmitter().getColor() == null || transporter.getColor() == null || getTransmitter().getColor() == transporter.getColor()) {
            return super.isValidTransmitter(tileEntity);
        }
        return false;
    }

    @Override
    public boolean isValidAcceptor(TileEntity tile, EnumFacing side) {
        return TransporterUtils.isValidAcceptorOnSide(tile, side);
    }

    @Override
    public boolean handlesRedstone() {
        return false;
    }

    @Override
    public void update() {
        super.update();
        getTransmitter().update();
    }

    public void pullItems() {
        // If a delay has been imposed, wait a bit
        if (delay > 0) {
            delay--;
            return;
        }

        // Reset delay to 3 ticks; if nothing is available to insert OR inserted, we'll try again
        // in 3 ticks
        delay = 3;

        // Attempt to pull
        for (EnumFacing side : getConnections(ConnectionType.PULL)) {
            final TileEntity tile = MekanismUtils.getTileEntity(world, getPos().offset(side));
            if (tile != null) {
                TransitRequest request = TransitRequest.buildInventoryMap(tile, side, tier.getPullAmount());
                // There's a stack available to insert into the network...
                if (!request.isEmpty()) {
                    TransitResponse response = TransporterUtils.insert(tile, getTransmitter(), request , getTransmitter().getColor(), true, 0);

                    // If the insert succeeded, remove the inserted count and try again for another 10 ticks
                    if (!response.isEmpty()) {
                        response.getInvStack(tile, side.getOpposite()).use(response.getSendingAmount());
                        delay = 10;
                    } else {
                        // Insert failed; increment the backoff and calculate delay. Note that we cap retries
                        // at a max of 40 ticks (2 seocnds), which would be 4 consecutive retries
                        delayCount++;
                        delay = Math.min(40, (int) Math.exp(delayCount));
                    }
                }
            }
        }
    }

    @Override
    public void onWorldJoin() {
        super.onWorldJoin();
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
    }

    @Override
    public InventoryNetwork createNewNetwork() {
        return new InventoryNetwork();
    }

    @Override
    public InventoryNetwork createNetworkByMerging(Collection<InventoryNetwork> networks) {
        return new InventoryNetwork(networks);
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) throws Exception {
        if (FMLCommonHandler.instance().getSide().isClient()) {
            int type = dataStream.readInt();
            if (type == 0) {
                super.handlePacketData(dataStream);
                tier = TransporterTier.values()[dataStream.readInt()];
                int c = dataStream.readInt();
                EnumColor prev = getTransmitter().getColor();
                if (c != -1) {
                    getTransmitter().setColor(TransporterUtils.colors.get(c));
                } else {
                    getTransmitter().setColor(null);
                }
                if (prev != getTransmitter().getColor()) {
                    MekanismUtils.updateBlock(world, pos);
                }
                getTransmitter().readFromPacket(dataStream);
            } else if (type == SYNC_PACKET) {
                readStack(dataStream);
            } else if (type == BATCH_PACKET) {
                int updates = dataStream.readInt();
                for (int i = 0; i < updates; i++) {
                    readStack(dataStream);
                }
                int deletes = dataStream.readInt();
                for (int i = 0; i < deletes; i++) {
                    getTransmitter().deleteStack(dataStream.readInt());
                }
            }
        }
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        data.add(0);
        super.getNetworkedData(data);
        data.add(tier.ordinal());
        if (getTransmitter().getColor() != null) {
            data.add(TransporterUtils.colors.indexOf(getTransmitter().getColor()));
        } else {
            data.add(-1);
        }

        // Serialize all the in-flight stacks (this includes their ID)
        getTransmitter().writeToPacket(data);
        return data;
    }

    public TileNetworkList makeSyncPacket(int stackId, TransporterStack stack) {
        TileNetworkList data = new TileNetworkList();
        if (Mekanism.hooks.MCMPLoaded) {
            MultipartTileNetworkJoiner.addMultipartHeader(this, data, null);
        }
        data.add(SYNC_PACKET);
        data.add(stackId);
        stack.write(getTransmitter(), data);
        return data;
    }

    public TileNetworkList makeBatchPacket(Map<Integer, TransporterStack> updates, Set<Integer> deletes) {
        TileNetworkList data = new TileNetworkList();
        if (Mekanism.hooks.MCMPLoaded) {
            MultipartTileNetworkJoiner.addMultipartHeader(this, data, null);
        }
        data.add(BATCH_PACKET);
        data.add(updates.size());
        for (Entry<Integer, TransporterStack> entry : updates.entrySet()) {
            data.add(entry.getKey());
            entry.getValue().write(getTransmitter(), data);
        }
        data.add(deletes.size());
        data.addAll(deletes);
        return data;
    }


    private void readStack(ByteBuf dataStream) {
        int id = dataStream.readInt();
        TransporterStack stack = TransporterStack.readFromPacket(dataStream);
        if (stack.progress == 0) {
            stack.progress = 5;
        }
        getTransmitter().addStack(id, stack);
    }


    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        if (nbtTags.hasKey("tier")) {
            tier = TransporterTier.values()[nbtTags.getInteger("tier")];
        }
        getTransmitter().readFromNBT(nbtTags);
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setInteger("tier", tier.ordinal());
        if (getTransmitter().getColor() != null) {
            nbtTags.setInteger("color", TransporterUtils.colors.indexOf(getTransmitter().getColor()));
        }
        NBTTagList stacks = new NBTTagList();
        for (TransporterStack stack : getTransmitter().getTransit()) {
            NBTTagCompound tagCompound = new NBTTagCompound();
            stack.write(tagCompound);
            stacks.appendTag(tagCompound);
        }
        if (stacks.tagCount() != 0) {
            nbtTags.setTag("stacks", stacks);
        }
        return nbtTags;
    }

    @Override
    protected EnumActionResult onConfigure(EntityPlayer player, int part, EnumFacing side) {
        TransporterUtils.incrementColor(getTransmitter());
        onPartChanged(null);
        PathfinderCache.onChanged(new Coord4D(getPos(), getWorld()));
        Mekanism.packetHandler.sendUpdatePacket(this);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
                .translation("tooltip.configurator.toggleColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumActionResult onRightClick(EntityPlayer player, EnumFacing side) {
        super.onRightClick(player, side);
        TextComponentGroup msg = new TextComponentGroup(TextFormatting.GRAY).string(Mekanism.LOG_TAG + " ", TextFormatting.DARK_BLUE)
                .translation("tooltip.configurator.viewColor").string(": ");

        if (getTransmitter().getColor() != null) {
            msg.appendSibling(getTransmitter().getColor().getTranslatedColouredComponent());
        } else {
            msg.translation("gui.none");
        }
        player.sendMessage(msg);
        return EnumActionResult.SUCCESS;
    }

    @Override
    public EnumColor getRenderColor() {
        return getTransmitter().getColor();
    }

    @Override
    public void onChunkUnload() {
        super.onChunkUnload();
        if (!getWorld().isRemote) {
            for (TransporterStack stack : getTransmitter().getTransit()) {
                TransporterUtils.drop(getTransmitter(), stack);
            }
        }
    }


    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public Void getBuffer() {
        return null;
    }

    @Override
    public void takeShare() {
    }

    @Override
    public void updateShare() {
    }

    @Override
    public TransporterImpl getTransmitter() {
        return (TransporterImpl) transmitterDelegate;
    }

    public double getCost() {
        return (double) TransporterTier.ULTIMATE.getSpeed() / (double) tier.getSpeed();
    }

    @Override
    public boolean upgrade(int tierOrdinal) {
        if (tier.ordinal() < BaseTier.ULTIMATE.ordinal() && tierOrdinal == tier.ordinal() + 1) {
            tier = TransporterTier.values()[tier.ordinal() + 1];
            markDirtyTransmitters();
            sendDesc = true;
            return true;
        }
        return false;
    }

    @Override
    public IBlockState getExtendedState(IBlockState state) {
        return ((IExtendedBlockState) super.getExtendedState(state)).withProperty(PropertyColor.INSTANCE, new PropertyColor(getRenderColor()));
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        return capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (capability == Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY) {
            return Capabilities.LOGISTICAL_TRANSPORTER_CAPABILITY.cast(getTransmitter());
        }
        return super.getCapability(capability, side);
    }
}
