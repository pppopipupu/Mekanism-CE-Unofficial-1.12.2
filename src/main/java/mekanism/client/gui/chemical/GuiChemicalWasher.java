package mekanism.client.gui.chemical;

import java.util.Arrays;

import mekanism.client.gui.GuiMekanismTile;
import mekanism.client.gui.element.*;
import mekanism.client.gui.element.GuiProgress.IProgressInfoHandler;
import mekanism.client.gui.element.GuiProgress.ProgressBar;
import mekanism.client.gui.element.GuiSlot.SlotOverlay;
import mekanism.client.gui.element.GuiSlot.SlotType;
import mekanism.client.gui.element.gauge.GuiFluidGauge;
import mekanism.client.gui.element.gauge.GuiGasGauge;
import mekanism.client.gui.element.gauge.GuiGauge;
import mekanism.client.gui.element.gauge.GuiGauge.Type;
import mekanism.client.gui.element.tab.GuiSecurityTab;
import mekanism.client.gui.element.tab.GuiUpgradeTab;
import mekanism.common.inventory.container.ContainerChemicalWasher;
import mekanism.common.tile.TileEntityChemicalWasher;
import mekanism.common.util.LangUtils;
import mekanism.common.util.MekanismUtils;
import mekanism.common.util.MekanismUtils.ResourceType;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public class GuiChemicalWasher extends GuiMekanismTile<TileEntityChemicalWasher> {

    public GuiChemicalWasher(InventoryPlayer inventory, TileEntityChemicalWasher tile) {
        super(tile, new ContainerChemicalWasher(inventory, tile));
        ResourceLocation resource = getGuiLocation();
        addGuiElement(new GuiSecurityTab(this, tileEntity, resource));
        addGuiElement(new GuiRedstoneControl(this, tileEntity, resource));
        addGuiElement(new GuiUpgradeTab(this, tileEntity, resource));
        addGuiElement(new GuiPowerBarHorizontal(this, tileEntity, resource, 115 , 74));
        addGuiElement(new GuiEnergyInfo(() -> {
            String usage = MekanismUtils.getEnergyDisplay(tileEntity.clientEnergyUsed);
            return Arrays.asList(LangUtils.localize("gui.using") + ": " + usage + "/t",
                  LangUtils.localize("gui.needed") + ": " + MekanismUtils.getEnergyDisplay(tileEntity.getMaxEnergy() - tileEntity.getEnergy()));
        }, this, resource));
        addGuiElement(new GuiFluidGauge(() -> tileEntity.fluidTank, Type.STANDARD, this, resource, 5, 4));
        addGuiElement(new GuiGasGauge(() -> tileEntity.inputTank, GuiGauge.Type.STANDARD, this, resource, 26, 13));
        addGuiElement(new GuiGasGauge(() -> tileEntity.outputTank, GuiGauge.Type.STANDARD, this, resource, 133, 13));
        addGuiElement(new GuiBucketIcon(GuiBucketIcon.IconType.BUCKET,this, resource,8,66));
        addGuiElement(new GuiBucketIO(this, resource));
        addGuiElement(new GuiSlot(SlotType.NORMAL, this, resource, 154, 4).with(SlotOverlay.POWER));
        addGuiElement(new GuiSlot(SlotType.NORMAL, this, resource, 154, 55).with(SlotOverlay.MINUS));
        addGuiElement(new GuiProgress(new IProgressInfoHandler() {
            @Override
            public double getProgress() {
                return tileEntity.getActive() ? 1 : 0;
            }
        }, ProgressBar.LARGE_RIGHT, this, resource, 62, 38));
    }

    @Override
    protected ResourceLocation getGuiLocation() {
        return MekanismUtils.getResource(ResourceType.GUI, "GuiBlank.png");
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRenderer.drawString(tileEntity.getName(), 45, 4, 0x404040);
    }
}