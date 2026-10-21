package dev.veltraph.client.garden.plotcleaner;
import dev.veltraph.client.config.*;
import java.util.*;

/** Verified server/menu contract, frozen for one transaction. {plot} is replaced by the exact plan ID. */
public record PlotCleanerBindings(String command,String desk,String configure,String plots,String plotTitle,String plotName,
                                  String superflat,String confirmTitle,String confirm,String readyLore,String progress) {
    public PlotCleanerBindings {for(String s:List.of(command,desk,configure,plots,plotTitle,plotName,superflat,confirmTitle,confirm,readyLore,progress))if(s.isBlank())throw new IllegalArgumentException("Unrecovered menu bindings must be supplied explicitly");}
    public static PlotCleanerBindings from(ConfigService c){return new PlotCleanerBindings(c.get(PlotCleanerConfig.PLOT_CLEANER_DESK_COMMAND),c.get(PlotCleanerConfig.PLOT_CLEANER_DESK_TITLE),c.get(PlotCleanerConfig.PLOT_CLEANER_CONFIGURE_NAME),c.get(PlotCleanerConfig.PLOT_CLEANER_PLOTS_TITLE),c.get(PlotCleanerConfig.PLOT_CLEANER_PLOT_TITLE),c.get(PlotCleanerConfig.PLOT_CLEANER_PLOT_NAME),c.get(PlotCleanerConfig.PLOT_CLEANER_SUPERFLAT_NAME),c.get(PlotCleanerConfig.PLOT_CLEANER_CONFIRM_TITLE),c.get(PlotCleanerConfig.PLOT_CLEANER_CONFIRM_NAME),c.get(PlotCleanerConfig.PLOT_CLEANER_CONFIRM_READY_LORE),c.get(PlotCleanerConfig.PLOT_CLEANER_PROGRESS_PREFIX));}
    public String bind(String s,PlotCleanerPlan p){return s.replace("{plot}",Integer.toString(p.plot()));}
}
