package mekanism.common.config;

import mekanism.common.config.options.BooleanOption;


public class MEKCEConfig extends BaseConfig {

    public final BooleanOption EnableDiamondCompat = new BooleanOption(this, "mekce", "EnableDiamondCompat", true,
            "Allows oredict'ed diamonds to be used in the enrichment chamber, like synthetic diamonds.");

    public final BooleanOption EnablePoorOresCompat = new BooleanOption(this, "mekce", "EnablePoorOresCompat", true,
            "Allows poor ores from railcraft to be used in the purification chamber and gives one clump ie one ingot.");

    public final BooleanOption EnableQuartzCompat = new BooleanOption(this, "mekce", "EnableQuartzCompat", true,
            "Allows quartz dust to be enriched into quartz Also allows quartz ore to be enriched into quartz dust");

    public final BooleanOption EnableSiliconCompat = new BooleanOption(this, "mekce", "EnableSiliconCompat", true,
            "When a mod that adds silicon (galacticraft, enderio, projectred and ae2) is detected, recipe for control circuit is changed from using iron to silicon in the metalurgic infuser");

    //public final BooleanOption enableBoPProgression = new BooleanOption(this, "mekce", "enableBoPProgression", true,
    //        "when true and biome's o plenty is installed atomic alloy is made by using ender instead of obsidian");

    //public final BooleanOption EnableSingleUseCardboxes = new BooleanOption(this, "mekce", "EnableSingleUseCardboxes", true,
    //        "This allows to force single use on cardboxes or not");

    public final BooleanOption ShoHiddenGas = new BooleanOption(this, "mekce", "ShowHiddenGases", true, "Displays hidden gas in creative gas tanks, which is invalid if PrefilledGasTanks is not enabled");

    public final BooleanOption EmptyToCreateBin = new BooleanOption(this, "mekce", "EmptytoCreateBin", false, "Let Configurator clear Create Bin");

    public final BooleanOption EmptyToCreateGasTank = new BooleanOption(this, "mekce", "EmptyToCreateGasTank", false, "Let Configurator clear Create Gas Tank");

    public final BooleanOption EmptytoCreateFluidTank = new BooleanOption(this, "mekce", "EmptytoCreateFluidTank", false, "Let Configurator clear Create Fluid Tank");

}