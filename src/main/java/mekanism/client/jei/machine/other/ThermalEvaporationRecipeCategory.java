package mekanism.client.jei.machine.other;

import mekanism.client.gui.element.GuiBlackScreen;
import mekanism.client.gui.element.GuiProgress;
import mekanism.client.gui.element.GuiRateBarHorizontal;
import mekanism.client.gui.element.GuiSlot;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.jei.BaseRecipeCategory;
import mekanism.common.config.MekanismConfig;
import mekanism.common.recipe.RecipeHandler.Recipe;
import mekanism.common.recipe.machines.ThermalEvaporationRecipe;
import mekanism.common.util.LangUtils;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import net.minecraft.client.Minecraft;

public class ThermalEvaporationRecipeCategory<WRAPPER extends ThermalEvaporationRecipeWrapper<ThermalEvaporationRecipe>> extends BaseRecipeCategory<WRAPPER> {

    public ThermalEvaporationRecipeCategory(IGuiHelper helper) {
        super(helper, "mekanism:gui/GuiBlank.png",
              Recipe.THERMAL_EVAPORATION_PLANT.getJEICategory(), "gui.thermalEvaporationController.short", null, 3, 12, 170, 62);
    }
    @Override
    protected void addGuiElements() {
        guiElements.add(GuiFluidGauge.getDummy(GuiGauge.Type.STANDARD, this, guiLocation, 6, 13));
        guiElements.add(GuiFluidGauge.getDummy(GuiGauge.Type.STANDARD, this, guiLocation, 152, 13));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.NORMAL, this, guiLocation, 27, 19));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.NORMAL, this, guiLocation, 27, 50));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.NORMAL, this, guiLocation, 131, 19));
        guiElements.add(new GuiSlot(GuiSlot.SlotType.NORMAL, this, guiLocation, 131, 50));
        guiElements.add(new GuiProgress(new GuiProgress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return 0F;
            }
        }, GuiProgress.ProgressBar.DOWN_ARROW, this, guiLocation, 30, 38));
        guiElements.add(new GuiProgress(new GuiProgress.IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return 0F;
            }
        }, GuiProgress.ProgressBar.DOWN_ARROW, this, guiLocation, 134, 38));
        guiElements.add(new GuiBlackScreen(GuiBlackScreen.BlackScreen.BIO_EVAPORATION,this,guiLocation,48,19));
        guiElements.add(new GuiRateBarHorizontal(this, new GuiRateBarHorizontal.IRateInfoHandler() {
            @Override
            public double getLevel() {
                return 1F;
            }
        },guiLocation,48,62));
    }


    @Override
    public void setRecipe(IRecipeLayout recipeLayout, WRAPPER recipeWrapper, IIngredients ingredients) {
        ThermalEvaporationRecipe tempRecipe = recipeWrapper.getRecipe();
        IGuiFluidStackGroup fluidStacks = recipeLayout.getFluidStacks();
        fluidStacks.init(0, true, 7 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getInput().ingredient.amount, false,
              fluidOverlayLarge);
        fluidStacks.init(1, false, 153 - xOffset, 14 - yOffset, 16, 58, tempRecipe.getOutput().output.amount, false,
              fluidOverlayLarge);
        fluidStacks.set(0, tempRecipe.recipeInput.ingredient);
        fluidStacks.set(1, tempRecipe.recipeOutput.output);
    }
}