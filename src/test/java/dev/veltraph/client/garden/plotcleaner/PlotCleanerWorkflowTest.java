package dev.veltraph.client.garden.plotcleaner;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PlotCleanerWorkflowTest {
    final PlotCleanerPlan p=new PlotCleanerPlan(12,0,66,0,2,72,1);
    final PlotCleanerBindings b=new PlotCleanerBindings("desk","Desk","Configure Plots","Plot Configuration","Plot {plot}","Plot {plot}","Superflat","Confirm Superflat","Confirm","Ready to clear","Cleaning:");
    MenuSnapshot menu(int id,String title,String name,String item,List<String> lore){return new MenuSnapshot(id,title,List.of(new MenuSnapshot.Slot(13,name,lore,false,item,1,false)),id);}
    List<String> scope(){return List.of(p.plotMarker(),p.scopeMarker());}
    PlotCleanerWorkflow start(){var f=new PlotCleanerWorkflow(p,b);f.preflightComplete();f.arrivedInAir();assertTrue(f.needDeskCommand());f.commandSent();return f;}
    void firstThree(PlotCleanerWorkflow f){
        var desk=menu(1,"Desk","Configure Plots","minecraft:grass_block",List.of());var a=f.menu(desk).orElseThrow();f.clicked(a,1);assertTrue(f.menu(desk).isEmpty());
        a=f.menu(menu(2,"Plot Configuration","Plot 12","minecraft:grass_block",scope())).orElseThrow();f.clicked(a,2);
        a=f.menu(menu(3,"Plot 12","Superflat","minecraft:grass_block",scope())).orElseThrow();f.clicked(a,3);
    }
    @Test void everyClickNeedsObservedNextScreenAndConfirmationReadiness(){
        var f=start();firstThree(f);
        assertTrue(f.menu(menu(3,"Plot 12","Superflat","minecraft:grass_block",scope())).isEmpty());
        var lore=new ArrayList<>(scope());lore.add("Ready to clear");var a=f.menu(menu(4,"Confirm Superflat","Confirm","minecraft:lime_terracotta",lore)).orElseThrow();f.clicked(a,10);
        assertEquals(PlotCleanerWorkflow.Phase.WAIT,f.phase());assertFalse(f.progress(List.of("Plot: 12","Cleaning: 90.0%"),false,11));
        assertFalse(f.progress(List.of("Plot: 11","Cleaning: 100.0%"),true,11));assertTrue(f.progress(List.of("Plot: 12","Cleaning: 90.0%"),true,11));
        f.rowsComplete();assertEquals(PlotCleanerWorkflow.Phase.BREAK_DIRT,f.phase());f.dirtComplete();f.returned();assertFalse(f.active());
    }
    @Test void wrongSelectedPlotOrBoundsNeverProducesAConfirmation(){var f=start();firstThree(f);assertTrue(f.menu(menu(4,"Confirm Superflat","Confirm","minecraft:lime_terracotta",List.of("Plot: 11",p.scopeMarker(),"Ready to clear"))).isEmpty());assertEquals(PlotCleanerWorkflow.Phase.FAILED,f.phase());}
    @Test void notReadyConfirmationIsRejectedEvenWithCorrectScope(){var f=start();firstThree(f);assertTrue(f.menu(menu(4,"Confirm Superflat","Confirm","minecraft:lime_terracotta",scope())).isEmpty());assertEquals(PlotCleanerWorkflow.Phase.FAILED,f.phase());}
    @Test void sourceGrassSelectorDoesNotAcceptSimilarPaper(){var f=start();assertTrue(f.menu(menu(1,"Desk","Configure Plots","minecraft:paper",List.of())).isEmpty());assertEquals(PlotCleanerWorkflow.Phase.FAILED,f.phase());}
    @Test void playerInventoryLockedAndDuplicateButtonsCannotAct(){
        for(var slots:List.of(List.of(new MenuSnapshot.Slot(13,"Configure Plots",List.of(),true,"minecraft:grass_block")),List.of(new MenuSnapshot.Slot(13,"Configure Plots",List.of(),false,"minecraft:barrier")),List.of(new MenuSnapshot.Slot(13,"Configure Plots",List.of(),false,"minecraft:grass_block"),new MenuSnapshot.Slot(14,"Configure Plots",List.of(),false,"minecraft:grass_block")))){
            var f=start();assertTrue(f.menu(new MenuSnapshot(1,"Desk",slots)).isEmpty());assertFalse(f.active());
        }
    }
    @Test void zeroCountAndNameNearMatchAreNotButtons(){for(var slot:List.of(new MenuSnapshot.Slot(13,"Configure Plots",List.of(),false,"minecraft:grass_block",0),new MenuSnapshot.Slot(13,"Do Not Configure Plots",List.of(),false,"minecraft:grass_block"))){var f=start();assertTrue(f.menu(new MenuSnapshot(1,"Desk",List.of(slot))).isEmpty());assertFalse(f.active());}}
    @Test void sourceProgressIsFiniteUniqueTargetedAndAtLeastNinety(){var f=start();firstThree(f);var lore=new ArrayList<>(scope());lore.add("Ready to clear");f.clicked(f.menu(menu(4,"Confirm Superflat","Confirm","minecraft:lime_terracotta",lore)).orElseThrow(),10);
        for(var rows:List.of(List.of("Plot: 12","Cleaning: NaN%"),List.of("Plot: 12","Cleaning: 101%"),List.of("Plot: 12","Cleaning: 89.9%"),List.of("Plot: 12","Cleaning: 95%","Cleaning: 96%")))assertFalse(f.progress(rows,true,11));assertTrue(f.progress(List.of("Plot: 12","Cleaning: 90%"),true,11));}
    @Test void stalePreconfirmationPercentageAndUnrelatedSidebarChangeAreNotAck(){var f=start();firstThree(f);var lore=new ArrayList<>(scope());lore.add("Ready to clear");f.clicked(f.menu(menu(4,"Confirm Superflat","Confirm","minecraft:emerald",lore)).orElseThrow(),10,List.of("Plot: 12","Cleaning: 100%"));assertFalse(f.progress(List.of("Plot: 12","Cleaning: 100%","Unrelated: changed"),true,11));assertTrue(f.progress(List.of("Plot: 12","Cleaning: 90%"),true,12));}
    @Test void cancellationDoesNotTurnIntoProgress(){var f=start();f.cancel("stop");assertFalse(f.active());assertTrue(f.menu(menu(1,"Desk","Configure Plots","minecraft:grass_block",List.of())).isEmpty());}
    @Test void noUnrecoveredBindingCanDefaultToAnAutomationButton(){assertThrows(IllegalArgumentException.class,()->new PlotCleanerBindings("desk","","a","b","c","d","e","f","g","h","i"));}
    @Test void explicitBoundsLimitAndFloorAreRequired(){assertThrows(IllegalArgumentException.class,()->new PlotCleanerPlan(0,0,66,0,1,70,1));assertThrows(IllegalArgumentException.class,()->new PlotCleanerPlan(1,0,65,0,1,70,1));assertThrows(IllegalArgumentException.class,()->new PlotCleanerPlan(1,0,66,0,96,70,1));assertFalse(p.contains(new BlockPos(3,70,0)));assertTrue(p.cleanup(new BlockPos(0,71,0)));assertFalse(p.cleanup(new BlockPos(0,70,0)));assertFalse(PlotCleanerPlan.natural("minecraft:farmland"));assertFalse(PlotCleanerPlan.natural("minecraft:stone"));}
    @Test void rowCounterpartStartsAtNearestCornerAndDescendsWithinItsColumn(){var cells=new ArrayList<>(List.of(new BlockPos(0,66,0),new BlockPos(2,70,0),new BlockPos(2,66,0),new BlockPos(1,70,0)));cells.sort(p.rowOrder(3,0));assertEquals(List.of(new BlockPos(2,70,0),new BlockPos(2,66,0),new BlockPos(1,70,0),new BlockPos(0,66,0)),cells);}
}
