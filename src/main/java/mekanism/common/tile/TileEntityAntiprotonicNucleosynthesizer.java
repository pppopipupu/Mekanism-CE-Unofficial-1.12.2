package mekanism.common.tile;

import io.netty.buffer.ByteBuf;
import mekanism.api.EnumColor;
import mekanism.api.TileNetworkList;
import mekanism.api.gas.*;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.MekanismBlocks;
import mekanism.common.SideData;
import mekanism.common.Upgrade;
import mekanism.common.base.IFactory;
import mekanism.common.base.ISustainedData;
import mekanism.common.base.ITankManager;
import mekanism.common.base.ITierUpgradeable;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.item.ItemUpgrade;
import mekanism.common.recipe.RecipeHandler;
import mekanism.common.recipe.inputs.NucleosynthesizerInput;
import mekanism.common.recipe.machines.NucleosynthesizerRecipe;
import mekanism.common.recipe.outputs.ItemStackOutput;
import mekanism.common.tier.BaseTier;
import mekanism.common.tile.component.TileComponentConfig;
import mekanism.common.tile.component.TileComponentEjector;
import mekanism.common.tile.prefab.TileEntityBasicMachine;
import mekanism.common.util.*;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.fml.common.FMLCommonHandler;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Objects;

public class TileEntityAntiprotonicNucleosynthesizer extends TileEntityBasicMachine<NucleosynthesizerInput, ItemStackOutput, NucleosynthesizerRecipe> implements IGasHandler,
        ISustainedData, ITankManager, ITierUpgradeable {

    private static final String[] methods = new String[]{"getEnergy", "getProgress", "isActive", "facing", "canOperate", "getMaxEnergy", "getEnergyNeeded",
            "getGasStored"};
    public GasTank inputGasTank = new GasTank(10000);


    public TileEntityAntiprotonicNucleosynthesizer() {
        super("prc", MachineType.ANTIPROTONIC_NUCLEOSYNTHESIZER, 3, 100);
        configComponent = new TileComponentConfig(this, TransmissionType.ITEM, TransmissionType.ENERGY, TransmissionType.GAS);

        configComponent.addOutput(TransmissionType.ITEM, new SideData("None", EnumColor.GREY, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Input", EnumColor.RED, new int[]{0}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Energy", EnumColor.BRIGHT_GREEN, new int[]{1}));
        configComponent.addOutput(TransmissionType.ITEM, new SideData("Output", EnumColor.INDIGO, new int[]{2}));
        configComponent.setConfig(TransmissionType.ITEM, new byte[]{2, 1, 0, 0, 0, 3});

        configComponent.setInputConfig(TransmissionType.GAS);
        configComponent.setInputConfig(TransmissionType.ENERGY);
        inventory = NonNullList.withSize(4, ItemStack.EMPTY);

        ejectorComponent = new TileComponentEjector(this);
        ejectorComponent.setOutputData(TransmissionType.ITEM, configComponent.getOutputs(TransmissionType.ITEM).get(3));
    }

    @Override
    public void onUpdate() {
        super.onUpdate();

        if (!world.isRemote) {
            NucleosynthesizerRecipe recipe = getRecipe();
            ChargeUtils.discharge(1, this);
            if (canOperate(recipe) && MekanismUtils.canFunction(this) && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy)) {
                boolean update = BASE_TICKS_REQUIRED != recipe.ticks;
                BASE_TICKS_REQUIRED = recipe.ticks;
                if (update) {
                    recalculateUpgradables(Upgrade.SPEED);
                }
                setActive(true);
                if ((operatingTicks + 1) < ticksRequired) {
                    operatingTicks++;
                    electricityStored -= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy);
                } else if ((operatingTicks + 1) >= ticksRequired && getEnergy() >= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy)) {
                    operate(recipe);
                    operatingTicks = 0;
                    electricityStored -= MekanismUtils.getEnergyPerTick(this, BASE_ENERGY_PER_TICK + recipe.extraEnergy);
                }
            } else {
                BASE_TICKS_REQUIRED = 100;
                if (prevEnergy >= getEnergy()) {
                    setActive(false);
                }
            }

            if (!canOperate(recipe)) {
                operatingTicks = 0;
            }
            prevEnergy = getEnergy();
        }
    }

    @Override
    public boolean upgrade(BaseTier upgradeTier) {
        if (upgradeTier != BaseTier.BASIC) {
            return false;
        }
        world.setBlockToAir(getPos());
        world.setBlockState(getPos(), MekanismBlocks.MachineBlock.getStateFromMeta(5), 3);

        TileEntityFactory factory = Objects.requireNonNull((TileEntityFactory) world.getTileEntity(getPos()));
        IFactory.RecipeType type = IFactory.RecipeType.NUCLEOSYNTHESIZER;

        //Basic
        factory.facing = facing;
        factory.clientFacing = clientFacing;
        factory.ticker = ticker;
        factory.redstone = redstone;
        factory.redstoneLastTick = redstoneLastTick;
        factory.doAutoSync = doAutoSync;

        //Electric
        factory.electricityStored = electricityStored;

        //Machine
        factory.progress[0] = operatingTicks;
        factory.setActive(isActive);
        factory.setControlType(getControlType());
        factory.prevEnergy = prevEnergy;
        factory.upgradeComponent.readFrom(upgradeComponent);
        factory.upgradeComponent.setUpgradeSlot(0);
        factory.ejectorComponent.readFrom(ejectorComponent);

        factory.ejectorComponent.setOutputData(TransmissionType.ITEM, factory.configComponent.getOutputs(TransmissionType.ITEM).get(3));
        factory.setRecipeType(type);
        factory.upgradeComponent.setSupported(Upgrade.GAS, type.fuelEnergyUpgrades());
        factory.securityComponent.readFrom(securityComponent);

        for (TransmissionType transmission : configComponent.getTransmissions()) {
            factory.configComponent.setConfig(transmission, configComponent.getConfig(transmission).asByteArray());
            factory.configComponent.setEjecting(transmission, configComponent.isEjecting(transmission));
        }

        factory.gasTank.setGas(inputGasTank.getGas());

        factory.inventory.set(5, inventory.get(0));
        factory.inventory.set(1, inventory.get(1));
        factory.inventory.set(5 + 3, inventory.get(2));
        factory.inventory.set(0, inventory.get(3));

        for (Upgrade upgrade : factory.upgradeComponent.getSupportedTypes()) {
            factory.recalculateUpgradables(upgrade);
        }
        factory.upgraded = true;
        factory.markDirty();
        return true;
    }

    @Override
    public boolean isItemValidForSlot(int slotID, @Nonnull ItemStack itemstack) {
        if (slotID == 0) {
            return RecipeHandler.isInNucleosynthesizerRecipe(itemstack);
        } else if (slotID == 1) {
            return ChargeUtils.canBeDischarged(itemstack);
        } else if (slotID == 3) {
            return itemstack.getItem() instanceof ItemUpgrade;
        }
        return false;
    }

    @Override
    public NucleosynthesizerRecipe getRecipe() {
        NucleosynthesizerInput input = getInput();
        if (cachedRecipe == null || !input.testEquality(cachedRecipe.getInput())) {
            cachedRecipe = RecipeHandler.getNucleosynthesizerRecipe(input);
        }
        return cachedRecipe;
    }

    @Override
    public NucleosynthesizerInput getInput() {
        return new NucleosynthesizerInput(inventory.get(0), inputGasTank.getGas());
    }

    @Override
    public void operate(NucleosynthesizerRecipe recipe) {
        recipe.operate(inventory, 0, inputGasTank, 2);
        markDirty();
    }

    @Override
    public boolean canOperate(NucleosynthesizerRecipe recipe) {
        return recipe != null && recipe.canOperate(inventory, 0, inputGasTank, 2);
    }

    @Override
    public boolean canExtractItem(int slotID, @Nonnull ItemStack itemstack, @Nonnull EnumFacing side) {
        if (slotID == 1) {
            return ChargeUtils.canBeOutputted(itemstack, false);
        }
        return slotID == 2 || slotID == 4;
    }

    @Override
    public TileNetworkList getNetworkedData(TileNetworkList data) {
        super.getNetworkedData(data);
        TileUtils.addTankData(data, inputGasTank);
        return data;
    }

    @Override
    public void handlePacketData(ByteBuf dataStream) {
        super.handlePacketData(dataStream);
        if (FMLCommonHandler.instance().getEffectiveSide().isClient()) {
            TileUtils.readTankData(dataStream, inputGasTank);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbtTags) {
        super.readFromNBT(nbtTags);
        inputGasTank.read(nbtTags.getCompoundTag("inputGasTank"));
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbtTags) {
        super.writeToNBT(nbtTags);
        nbtTags.setTag("inputGasTank", inputGasTank.write(new NBTTagCompound()));
        return nbtTags;
    }

    @Nonnull
    @Override
    public String getName() {
        return LangUtils.localize(getBlockType().getTranslationKey() + "." + fullName + ".name");
    }

    @Override
    public Map<NucleosynthesizerInput, NucleosynthesizerRecipe> getRecipes() {
        return null;
    }

    @Override
    public String[] getMethods() {
        return methods;
    }

    @Override
    public Object[] invoke(int method, Object[] arguments) throws NoSuchMethodException {
        return switch (method) {
            case 0 -> new Object[]{getEnergy()};
            case 1 -> new Object[]{operatingTicks};
            case 2 -> new Object[]{isActive};
            case 3 -> new Object[]{facing};
            case 4 -> new Object[]{canOperate(getRecipe())};
            case 5 -> new Object[]{getMaxEnergy()};
            case 6 -> new Object[]{getMaxEnergy() - getEnergy()};
            case 7 -> new Object[]{inputGasTank.getStored()};
            default -> throw new NoSuchMethodException();
        };
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (canReceiveGas(side, stack.getGas())) {
            return inputGasTank.receive(stack, doTransfer);
        }
        return 0;
    }

    @Override
    public GasStack drawGas(EnumFacing side, int amount, boolean doTransfer) {
        return null;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).ioState == SideData.IOState.INPUT && inputGasTank.canReceive(type);
    }

    @Override
    public boolean canDrawGas(EnumFacing side, Gas type) {
        return false;
    }

    @Nonnull
    @Override
    public GasTankInfo[] getTankInfo() {
        return new GasTankInfo[]{inputGasTank};
    }

    @Override
    public boolean hasCapability(@Nonnull Capability<?> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return false;
        }
        return capability == Capabilities.GAS_HANDLER_CAPABILITY || super.hasCapability(capability, side);
    }

    @Override
    public <T> T getCapability(@Nonnull Capability<T> capability, EnumFacing side) {
        if (isCapabilityDisabled(capability, side)) {
            return null;
        } else if (capability == Capabilities.GAS_HANDLER_CAPABILITY) {
            return Capabilities.GAS_HANDLER_CAPABILITY.cast(this);
        }
        return super.getCapability(capability, side);
    }

    @Override
    public boolean isCapabilityDisabled(@Nonnull Capability<?> capability, EnumFacing side) {
        return configComponent.isCapabilityDisabled(capability, side, facing) || super.isCapabilityDisabled(capability, side);
    }

    @Override
    public void writeSustainedData(ItemStack itemStack) {
        if (inputGasTank.getGas() != null) {
            ItemDataUtils.setCompound(itemStack, "inputGasTank", inputGasTank.getGas().write(new NBTTagCompound()));
        }
    }

    @Override
    public void readSustainedData(ItemStack itemStack) {
        inputGasTank.setGas(GasStack.readFromNBT(ItemDataUtils.getCompound(itemStack, "inputGasTank")));
    }

    @Override
    public Object[] getTanks() {
        return new Object[]{inputGasTank};
    }
}
