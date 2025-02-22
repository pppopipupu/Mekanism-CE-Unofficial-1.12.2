package mekanism.common.block;

import mekanism.api.IMekWrench;
import mekanism.api.energy.IEnergizedItem;
import mekanism.common.Mekanism;
import mekanism.common.MekanismBlocks;
import mekanism.common.base.ISideConfiguration;
import mekanism.common.base.ISustainedInventory;
import mekanism.common.base.ITierItem;
import mekanism.common.block.states.BlockStateEnergyCube;
import mekanism.common.block.states.BlockStateFacing;
import mekanism.common.integration.wrenches.Wrenches;
import mekanism.common.item.ItemBlockEnergyCube;
import mekanism.common.security.ISecurityItem;
import mekanism.common.security.ISecurityTile;
import mekanism.common.tier.EnergyCubeTier;
import mekanism.common.tile.TileEntityEnergyCube;
import mekanism.common.tile.prefab.TileEntityBasicBlock;
import mekanism.common.util.ItemDataUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.SecurityUtils;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nonnull;

/**
 * Block class for handling multiple energy cube block IDs. 0: Basic Energy Cube 1: Advanced Energy Cube 2: Elite Energy Cube 3: Ultimate Energy Cube 4: Creative Energy
 * Cube
 *
 * @author AidanBrady
 */
public class BlockEnergyCube extends BlockMekanismContainer {

    public BlockEnergyCube() {
        super(Material.IRON);
        setHardness(2F);
        setResistance(4F);
        setCreativeTab(Mekanism.tabMekanism);
    }

    @Nonnull
    @Override
    public BlockStateContainer createBlockState() {
        return new BlockStateEnergyCube(this);
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return 0;
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState();
    }

    @Nonnull
    @Override
    @Deprecated
    public IBlockState getActualState(@Nonnull IBlockState state, IBlockAccess worldIn, BlockPos pos) {
        TileEntity tile = MekanismUtils.getTileEntitySafe(worldIn, pos);
        if (tile instanceof TileEntityEnergyCube cube) {
            if (cube.facing != null) {
                state = state.withProperty(BlockStateFacing.facingProperty, cube.facing);
            }
            if (cube.tier != null) {
                state = state.withProperty(BlockStateEnergyCube.typeProperty, cube.tier);
            }
        }
        return state;
    }

    @Override
    @Deprecated
    public void neighborChanged(IBlockState state, World world, BlockPos pos, Block neighborBlock, BlockPos neighborPos) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(pos);
            if (tileEntity instanceof TileEntityBasicBlock) {
                ((TileEntityBasicBlock) tileEntity).onNeighborChange(neighborBlock);
            }
        }
    }

    @Override
    public void onBlockPlacedBy(World world, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
        TileEntityBasicBlock tileEntity = (TileEntityBasicBlock) world.getTileEntity(pos);
        if (tileEntity == null) {
            return;
        }
        int height = Math.round(placer.rotationPitch);
        EnumFacing change = EnumFacing.SOUTH;

        if (height >= 65) {
            change = EnumFacing.UP;
        } else if (height <= -65) {
            change = EnumFacing.DOWN;
        } else {
            int side = MathHelper.floor((double) (placer.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3;
            change = switch (side) {
                case 0 -> EnumFacing.NORTH;
                case 1 -> EnumFacing.EAST;
                case 2 -> EnumFacing.SOUTH;
                case 3 -> EnumFacing.WEST;
                default -> change;
            };
        }
        tileEntity.setFacing(change);
        tileEntity.redstone = world.getRedstonePowerFromNeighbors(pos) > 0;
    }

    @Override
    public void getSubBlocks(CreativeTabs creativetabs, NonNullList<ItemStack> list) {
        for (EnergyCubeTier tier : EnergyCubeTier.values()) {
            ItemStack discharged = new ItemStack(this);
            ((ItemBlockEnergyCube) discharged.getItem()).setBaseTier(discharged, tier.getBaseTier());
            list.add(discharged);
            ItemStack charged = new ItemStack(this);
            ((ItemBlockEnergyCube) charged.getItem()).setBaseTier(charged, tier.getBaseTier());
            ((ItemBlockEnergyCube) charged.getItem()).setEnergy(charged, tier.getMaxEnergy());
            list.add(charged);
        }
    }

    @Override
    @Deprecated
    public float getPlayerRelativeBlockHardness(IBlockState state, @Nonnull EntityPlayer player, @Nonnull World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        return SecurityUtils.canAccess(player, tile) ? super.getPlayerRelativeBlockHardness(state, player, world, pos) : 0.0F;
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer entityplayer, EnumHand hand, EnumFacing side, float hitX, float hitY, float hitZ) {
        if (world.isRemote) {
            return true;
        }

        TileEntityEnergyCube tileEntity = (TileEntityEnergyCube) world.getTileEntity(pos);
        ItemStack stack = entityplayer.getHeldItem(hand);
        if (!stack.isEmpty()) {
            IMekWrench wrenchHandler = Wrenches.getHandler(stack);
            if (wrenchHandler != null) {
                RayTraceResult raytrace = new RayTraceResult(new Vec3d(hitX, hitY, hitZ), side, pos);
                if (wrenchHandler.canUseWrench(entityplayer, hand, stack, raytrace)) {
                    if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                        wrenchHandler.wrenchUsed(entityplayer, hand, stack, raytrace);
                        if (entityplayer.isSneaking()) {
                            MekanismUtils.dismantleBlock(this, state, world, pos);
                            return true;
                        }
                        if (tileEntity != null) {
                            tileEntity.setFacing(tileEntity.facing.rotateAround(side.getAxis()));
                            world.notifyNeighborsOfStateChange(pos, this, true);
                        }
                    } else {
                        SecurityUtils.displayNoAccess(entityplayer);
                    }
                    return true;
                }
            }
        }

        if (tileEntity != null) {
            if (!entityplayer.isSneaking()) {
                if (SecurityUtils.canAccess(entityplayer, tileEntity)) {
                    entityplayer.openGui(Mekanism.instance, 8, world, pos.getX(), pos.getY(), pos.getZ());
                } else {
                    SecurityUtils.displayNoAccess(entityplayer);
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(@Nonnull World world, int meta) {
        return new TileEntityEnergyCube();
    }

    @Override
    @Deprecated
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Nonnull
    @Override
    @Deprecated
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Nonnull
    @Override
    protected ItemStack getDropItem(@Nonnull IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos) {
        TileEntityEnergyCube tileEntity = (TileEntityEnergyCube) world.getTileEntity(pos);
        ItemStack itemStack = new ItemStack(MekanismBlocks.EnergyCube);

        if (!itemStack.hasTagCompound()) {
            itemStack.setTagCompound(new NBTTagCompound());
        }
        if (tileEntity != null) {
            ISecurityItem securityItem = (ISecurityItem) itemStack.getItem();

            if (securityItem.hasSecurity(itemStack)) {
                securityItem.setOwnerUUID(itemStack, ((ISecurityTile) tileEntity).getSecurity().getOwnerUUID());
                securityItem.setSecurity(itemStack, ((ISecurityTile) tileEntity).getSecurity().getMode());
            }
            ((ISideConfiguration) tileEntity).getConfig().write(ItemDataUtils.getDataMap(itemStack));
            ((ISideConfiguration) tileEntity).getEjector().write(ItemDataUtils.getDataMap(itemStack));

            ITierItem tierItem = (ITierItem) itemStack.getItem();
            tierItem.setBaseTier(itemStack, tileEntity.tier.getBaseTier());

            IEnergizedItem energizedItem = (IEnergizedItem) itemStack.getItem();
            energizedItem.setEnergy(itemStack, tileEntity.electricityStored);

            ISustainedInventory inventory = (ISustainedInventory) itemStack.getItem();
            inventory.setInventory(tileEntity.getInventory(), itemStack);
        }


        return itemStack;
    }

    @Override
    @Deprecated
    public boolean hasComparatorInputOverride(IBlockState state) {
        return true;
    }

    @Override
    @Deprecated
    public int getComparatorInputOverride(IBlockState state, World world, BlockPos pos) {
        TileEntityEnergyCube tileEntity = (TileEntityEnergyCube) world.getTileEntity(pos);
        return tileEntity.getRedstoneLevel();
    }

    @Override
    @Deprecated
    public boolean isSideSolid(IBlockState state, @Nonnull IBlockAccess world, @Nonnull BlockPos pos, EnumFacing side) {
        return true;
    }

    @Override
    public EnumFacing[] getValidRotations(World world, @Nonnull BlockPos pos) {
        TileEntity tile = world.getTileEntity(pos);
        EnumFacing[] valid = new EnumFacing[6];
        if (tile instanceof TileEntityBasicBlock basicTile) {
            for (EnumFacing dir : EnumFacing.VALUES) {
                if (basicTile.canSetFacing(dir)) {
                    valid[dir.ordinal()] = dir;
                }
            }
        }
        return valid;
    }

    @Override
    public boolean rotateBlock(World world, @Nonnull BlockPos pos, @Nonnull EnumFacing axis) {
        TileEntity tile = world.getTileEntity(pos);
        if (tile instanceof TileEntityBasicBlock basicTile) {
            if (basicTile.canSetFacing(axis)) {
                basicTile.setFacing(axis);
                return true;
            }
        }
        return false;
    }
}
