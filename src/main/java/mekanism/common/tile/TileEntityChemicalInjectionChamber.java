package mekanism.common.tile;

import mekanism.api.EnumColor;
import mekanism.api.gas.Gas;
import mekanism.api.gas.GasStack;
import mekanism.api.transmitters.TransmissionType;
import mekanism.common.SideData;
import mekanism.common.block.states.BlockStateMachine.MachineType;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.inputs.AdvancedMachineInput;
import mekanism.common.recipe.machines.InjectionRecipe;
import mekanism.common.tile.prefab.TileEntityAdvancedElectricMachine;
import mekanism.common.util.InventoryUtils;
import net.minecraft.util.EnumFacing;

import java.util.Map;

public class TileEntityChemicalInjectionChamber extends TileEntityAdvancedElectricMachine<InjectionRecipe> {

    public TileEntityChemicalInjectionChamber() {
        super("injection", MachineType.CHEMICAL_INJECTION_CHAMBER, BASE_TICKS_REQUIRED, BASE_GAS_PER_TICK);
        configComponent.addSupported(TransmissionType.GAS);
        configComponent.addOutput(TransmissionType.GAS, new SideData("None", EnumColor.GREY, InventoryUtils.EMPTY));
        configComponent.addOutput(TransmissionType.GAS, new SideData("Gas", EnumColor.DARK_RED, new int[]{0}));
        configComponent.fillConfig(TransmissionType.GAS, 1);
        configComponent.setCanEject(TransmissionType.GAS, false);
    }

    @Override
    public Map<AdvancedMachineInput, InjectionRecipe> getRecipes() {
        return Recipe.CHEMICAL_INJECTION_CHAMBER.get();
    }

    @Override
    public int receiveGas(EnumFacing side, GasStack stack, boolean doTransfer) {
        if (canReceiveGas(side, stack.getGas())) {
            return gasTank.receive(stack, doTransfer);
        }
        return 0;
    }

    @Override
    public boolean canReceiveGas(EnumFacing side, Gas type) {
        return configComponent.getOutput(TransmissionType.GAS, side, facing).hasSlot(0) && gasTank.canReceive(type) && isValidGas(type);

    }

    @Override
    public boolean isValidGas(Gas gas) {
        return Recipe.CHEMICAL_INJECTION_CHAMBER.containsRecipe(gas);
    }

    @Override
    public boolean upgradeableSecondaryEfficiency() {
        return true;
    }

    @Override
    public boolean useStatisticalMechanics() {
        return true;
    }
}
