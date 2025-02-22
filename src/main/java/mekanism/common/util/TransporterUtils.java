package mekanism.common.util;

import mekanism.api.Coord4D;
import mekanism.api.EnumColor;
import mekanism.common.base.ILogisticalTransporter;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.transporter.TransitRequest;
import mekanism.common.content.transporter.TransitRequest.TransitResponse;
import mekanism.common.content.transporter.TransporterManager;
import mekanism.common.content.transporter.TransporterStack;
import mekanism.common.tile.TileEntityLogisticalSorter;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import java.util.Arrays;
import java.util.List;

public final class TransporterUtils {

    public static List<EnumColor> colors = Arrays.asList(EnumColor.DARK_BLUE, EnumColor.DARK_GREEN, EnumColor.DARK_AQUA, EnumColor.DARK_RED, EnumColor.PURPLE,
            EnumColor.INDIGO, EnumColor.BRIGHT_GREEN, EnumColor.AQUA, EnumColor.RED, EnumColor.PINK, EnumColor.YELLOW, EnumColor.BLACK);

    public static boolean isValidAcceptorOnSide(TileEntity tile, EnumFacing side) {
        if (CapabilityUtils.hasCapability(tile, Capabilities.GRID_TRANSMITTER_CAPABILITY, side.getOpposite())) {
            return false;
        }
        return InventoryUtils.isItemHandler(tile, side.getOpposite());
    }

    public static TransitResponse insert(TileEntity outputter, ILogisticalTransporter transporter, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        return transporter.insert(Coord4D.get(outputter), request, color, doEmit, min);
    }

    public static TransitResponse insertRR(TileEntityLogisticalSorter outputter, ILogisticalTransporter transporter, TransitRequest request, EnumColor color, boolean doEmit, int min) {
        return transporter.insertRR(outputter, request, color, doEmit, min);
    }

    public static EnumColor increment(EnumColor color) {
        if (color == null) {
            return colors.get(0);
        } else if (colors.indexOf(color) == colors.size() - 1) {
            return null;
        }
        return colors.get(colors.indexOf(color) + 1);
    }

    public static EnumColor decrement(EnumColor color) {
        if (color == null) {
            return colors.get(colors.size() - 1);
        } else if (colors.indexOf(color) == 0) {
            return null;
        }
        return colors.get(colors.indexOf(color) - 1);
    }

    public static void drop(ILogisticalTransporter tileEntity, TransporterStack stack) {
        float[] pos;
        if (stack.hasPath()) {
            pos = TransporterUtils.getStackPosition(tileEntity, stack, 0);
        } else {
            pos = new float[]{0, 0, 0};
        }
        TransporterManager.remove(stack);
        BlockPos blockPos = new BlockPos(tileEntity.coord().x + pos[0], tileEntity.coord().y + pos[1], tileEntity.coord().z + pos[2]);
        Block.spawnAsEntity(tileEntity.world(), blockPos, stack.itemStack);
    }

    public static float[] getStackPosition(ILogisticalTransporter tileEntity, TransporterStack stack, float partial) {
        EnumFacing side = stack.getSide(tileEntity);
        float progress = (((float) stack.progress + partial) / 100F) - 0.5F;
        return new float[]{0.5F + side.getXOffset() * progress, 0.25F + side.getYOffset() * progress, 0.5F + side.getZOffset() * progress};
    }

    public static void incrementColor(ILogisticalTransporter tileEntity) {
        if (tileEntity.getColor() == null) {
            tileEntity.setColor(colors.get(0));
        } else if (colors.indexOf(tileEntity.getColor()) == colors.size() - 1) {
            tileEntity.setColor(null);
        } else {
            int index = colors.indexOf(tileEntity.getColor());
            tileEntity.setColor(colors.get(index + 1));
        }
    }
}
