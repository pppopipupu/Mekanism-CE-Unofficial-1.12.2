package mekanism.client.gui;

import mekanism.client.gui.element.GuiGraph;
import mekanism.client.gui.element.GuiHeatInfo;
import mekanism.client.gui.element.tab.GuiBoilerTab;
import mekanism.client.gui.element.tab.GuiBoilerTab.BoilerTab;
import mekanism.common.config.MekanismConfig;
import mekanism.common.content.boiler.SynchronizedBoilerData;
import mekanism.common.inventory.container.ContainerNull;
import mekanism.common.tile.TileEntityBoilerCasing;
import mekanism.common.util.LangUtils;
import mekanism.common.util.UnitDisplayUtils;
import mekanism.common.util.UnitDisplayUtils.TemperatureUnit;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;

@SideOnly(Side.CLIENT)
public class GuiBoilerStats extends GuiMekanismTile<TileEntityBoilerCasing> {

    private final GuiGraph boilGraph;
    private final GuiGraph maxGraph;

    public GuiBoilerStats(InventoryPlayer inventory, TileEntityBoilerCasing tile) {
        super(tile, new ContainerNull(inventory.player, tile));
        ResourceLocation resource = getGuiLocation();
        addGuiElement(new GuiBoilerTab(this, tileEntity, BoilerTab.MAIN, resource));
        addGuiElement(new GuiHeatInfo(() -> {
            TemperatureUnit unit = TemperatureUnit.values()[MekanismConfig.current().general.tempUnit.val().ordinal()];
            String environment = UnitDisplayUtils.getDisplayShort(tileEntity.getLastEnvironmentLoss() * unit.intervalSize, false, unit);
            return Collections.singletonList(LangUtils.localize("gui.dissipated") + ": " + environment + "/t");
        }, this, resource));
        addGuiElement(boilGraph = new GuiGraph(this, resource, 7, 82, 162, 36, data -> LangUtils.localize("gui.boilRate") + ": " + data + " mB/t"));
        addGuiElement(maxGraph = new GuiGraph(this, resource, 7, 121, 162, 36, data -> LangUtils.localize("gui.maxBoil") + ": " + data + " mB/t"));
        maxGraph.enableFixedScale((int) ((tileEntity.getSuperheatingElements() * MekanismConfig.current().general.superheatingHeatTransfer.val()) /
                SynchronizedBoilerData.getHeatEnthalpy()));
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String stats = LangUtils.localize("gui.boilerStats");
        fontRenderer.drawString(stats, (xSize / 2) - (fontRenderer.getStringWidth(stats) / 2), 4, 0x404040);
        fontRenderer.drawString(LangUtils.localize("gui.maxWater") + ": " + tileEntity.clientWaterCapacity + " mB", 8, 26, 0x404040);
        fontRenderer.drawString(LangUtils.localize("gui.maxSteam") + ": " + tileEntity.clientSteamCapacity + " mB", 8, 35, 0x404040);
        fontRenderer.drawString(LangUtils.localize("gui.heatTransfer"), 8, 49, 0x797979);
        fontRenderer.drawString(LangUtils.localize("gui.superheaters") + ": " + tileEntity.getSuperheatingElements(), 14, 58, 0x404040);
        int boilCapacity = (int) (tileEntity.getSuperheatingElements() * MekanismConfig.current().general.superheatingHeatTransfer.val() / SynchronizedBoilerData.getHeatEnthalpy());
        fontRenderer.drawString(LangUtils.localize("gui.boilCapacity") + ": " + boilCapacity + " mB/t", 8, 72, 0x404040);
        super.drawGuiContainerForegroundLayer(mouseX, mouseY);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        boilGraph.addData(tileEntity.getLastBoilRate());
        maxGraph.addData(tileEntity.getLastMaxBoil());
    }


}
