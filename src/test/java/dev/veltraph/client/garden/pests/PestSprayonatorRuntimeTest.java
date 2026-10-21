package dev.veltraph.client.garden.pests;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestSprayonatorRuntimeTest {
    static final PestBindings.Item TOOL=new PestBindings.Item("minecraft:stick","Sprayonator","SPRAYONATOR");
    static final PestBindings.Item MATERIAL=new PestBindings.Item("minecraft:wheat","Compost","COMPOST");
    static final PestSprayonatorBindings.Escape ESCAPE=new PestSprayonatorBindings.Escape(new PestBindings.Entity("minecraft:armor_stand","Spray Trap",""),new Vec3(9.5,70,-96.5),.5);
    static final PestSprayonatorBindings B=new PestSprayonatorBindings(TOOL,MATERIAL,"Selected: ","Selected: Compost","Spray material",
        "Click to select!","Selected!",true,ESCAPE,List.of(plot(1,true,false),plot(2,false,false),plot(3,true,true),plot(4,true,false)));
    static PestSprayonatorBindings.Plot plot(int id,boolean enabled,boolean excluded){var b=GardenPlotBounds.ALL.stream().filter(p->p.id()==id).findFirst().orElseThrow();
        return new PestSprayonatorBindings.Plot(id,enabled,excluded,new Vec3((b.minX()+b.maxX())/2.,70,(b.minZ()+b.maxZ())/2.),0,0,"SPRAYONATOR! You sprayed Plot - "+id);}
    static final PestSprayonatorRuntime.Settings C=new PestSprayonatorRuntime.Settings(B,1,100,200,60000,8000);
    static MenuSnapshot menu(int revision,boolean selected){return new MenuSnapshot(7,B.menuTitle(),List.of(new MenuSnapshot.Slot(12,"Compost",List.of(selected?B.selectedMenuLore():B.availableLore()),false)),revision);}
    static PestSprayonatorRuntime.Observation o(int count,boolean selected,boolean inside,MenuSnapshot menu){return new PestSprayonatorRuntime.Observation(true,menu==null,true,true,"tool without selected lore","material",count,selected,menu,inside,false);}
    static PestSprayonatorRuntime.Observation trap(boolean nearby){return new PestSprayonatorRuntime.Observation(true,true,true,true,"tool without selected lore","material",2,false,null,false,nearby);}
    static class Actions implements PestSprayonatorRuntime.Actions {
        int selects,opens,clicks,closes,supply,uses,restores,returns,resumes,cancels,escapes;boolean valid=true;List<Integer> plots=new ArrayList<>();
        PestSprayonatorRuntime.Progress progress=PestSprayonatorRuntime.Progress.COMPLETE;
        public boolean valid(){return valid;}public boolean select(){selects++;return true;}public boolean openMenu(){opens++;return true;}
        public boolean escape(){escapes++;return true;}public PestSprayonatorRuntime.Progress escaped(){return progress;}
        public boolean click(MenuSnapshot menu,int slot){assertEquals(12,slot);clicks++;return true;}public boolean close(MenuSnapshot menu){closes++;return true;}
        public boolean acquire(int deficit){supply+=deficit;return true;}public PestSprayonatorRuntime.Progress acquired(){return progress;}
        public boolean travel(PestSprayonatorBindings.Plot p){plots.add(p.id());return true;}public PestSprayonatorRuntime.Progress travelled(PestSprayonatorBindings.Plot p){return progress;}
        public boolean use(PestSprayonatorRuntime.Observation e){uses++;return true;}public boolean restore(){restores++;return true;}
        public PestSprayonatorRuntime.Progress restored(){return progress;}public boolean beginReturn(){returns++;return true;}
        public PestSprayonatorRuntime.Progress returned(){return progress;}public boolean resume(){resumes++;return true;}public void cancel(){cancels++;}
    }
    @Test void completeParentSelectsChangedMaterialVisitsSourceFilteredPlotsAndReturnsSameFarm(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);assertTrue(r.start(C,o(2,false,false,null),0));
        r.tick(C,o(2,false,false,null),1);r.tick(C,o(2,false,false,null),2);assertEquals(1,a.opens);
        r.tick(C,o(2,false,false,menu(1,false)),3);assertEquals(1,a.clicks);
        r.tick(C,o(2,true,false,menu(1,true)),4);assertEquals(PestSprayonatorRuntime.State.WAIT_SELECTION,r.state());
        r.tick(C,o(2,true,false,menu(2,true)),5);assertEquals(PestSprayonatorRuntime.State.CLOSE_MENU,r.state());
        r.tick(C,o(2,true,false,menu(2,true)),6);r.tick(C,o(2,true,false,null),7);assertEquals(List.of(1),a.plots);
        r.tick(C,o(2,true,true,null),8);r.tick(C,o(2,true,true,null),9);assertEquals(1,a.uses);
        r.message("SPRAYONATOR! You sprayed Plot - 4");r.tick(C,o(1,true,true,null),10);assertEquals(PestSprayonatorRuntime.State.WAIT_ACK,r.state());
        r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(C,o(1,true,true,null),11);assertEquals(List.of(1,4),a.plots);
        r.tick(C,o(1,true,true,null),12);r.tick(C,o(1,true,true,null),13);r.message("SPRAYONATOR! You sprayed Plot - 4");r.tick(C,o(0,true,true,null),14);
        assertEquals(1,a.restores);r.tick(C,o(0,true,true,null),15);r.tick(C,o(0,true,true,null),16);
        assertEquals(PestSprayonatorRuntime.State.COMPLETE,r.state());assertEquals(2,a.uses);assertEquals(1,a.returns);assertEquals(1,a.resumes);assertEquals(0,a.cancels);
        assertEquals(List.of(1,4),r.receipts().stream().map(PestSprayonatorRuntime.Receipt::plotId).toList());
    }
    @Test void acquisitionIsExactAggregateDeficitAndRunsOnlyOnce(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);r.start(C,o(0,true,false,null),0);r.tick(C,o(0,true,false,null),1);r.tick(C,o(0,true,false,null),2);
        assertEquals(PestSprayonatorRuntime.State.SUPPLY,r.state());assertEquals(2,a.supply);
        a.progress=PestSprayonatorRuntime.Progress.ACTIVE;r.tick(C,o(1,true,false,null),3);r.tick(C,o(1,true,false,null),4);assertEquals(2,a.supply);
        a.progress=PestSprayonatorRuntime.Progress.COMPLETE;r.tick(C,o(2,true,false,null),5);assertEquals(List.of(1),a.plots);
    }
    @Test void staleRevisionToolLoreAloneOrMenuLoreAloneCannotConfirmSelection(){
        for(int variant=0;variant<3;variant++){
            var a=new Actions();var r=new PestSprayonatorRuntime(a);r.start(C,o(2,false,false,null),0);r.tick(C,o(2,false,false,null),1);r.tick(C,o(2,false,false,null),2);r.tick(C,o(2,false,false,menu(1,false)),3);
            r.tick(C,o(2,variant!=2,false,menu(variant==0?1:2,variant!=1)),4);
            assertEquals(PestSprayonatorRuntime.State.WAIT_SELECTION,r.state());assertEquals(0,a.uses);assertEquals(1,a.clicks);
            r.tick(C,o(2,variant!=2,false,menu(variant==0?1:2,variant!=1)),8003);assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(0,a.resumes);
        }
    }
    static void waiting(PestSprayonatorRuntime r){r.start(C,o(2,true,false,null),0);r.tick(C,o(2,true,false,null),1);r.tick(C,o(2,true,false,null),2);r.tick(C,o(2,true,true,null),3);r.tick(C,o(2,true,true,null),4);}
    @Test void chatOnlyAndConsumptionOnlyTimeoutWithoutRetryOrResume(){
        for(boolean chat:List.of(false,true)){var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);
            if(chat)r.message("SPRAYONATOR! You sprayed Plot - 1");for(int t=5;t<30;t++)r.tick(C,o(chat?2:1,true,true,null),t);
            assertEquals(1,a.uses);assertEquals(PestSprayonatorRuntime.State.WAIT_ACK,r.state());r.tick(C,o(chat?2:1,true,true,null),8004);
            assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(1,a.uses);assertEquals(0,a.resumes);}
    }
    @Test void recentPlotReceiptSkipsWithoutInventingConsumption(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.message("This plot was sprayed with that item recently! Try again soon!");r.tick(C,o(2,true,true,null),5);
        assertEquals(List.of(1,4),a.plots);assertFalse(r.receipts().getFirst().applied());assertEquals(2,r.receipts().getFirst().after());assertEquals(1,a.uses);
    }
    @Test void changedIdentitySettingsCursorScreenSupplyAndContextStopWithoutResume(){
        var variants=List.of(new PestSprayonatorRuntime.Observation(true,true,true,true,"changed tool","material",2,true,null,true,false),
            new PestSprayonatorRuntime.Observation(true,true,true,true,"tool without selected lore","changed material",2,true,null,true,false),
            new PestSprayonatorRuntime.Observation(true,true,false,true,"tool without selected lore","material",2,true,null,true,false),o(3,true,true,null),o(2,true,true,menu(1,true)));
        for(var changed:variants){var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.tick(C,changed,5);assertFalse(r.active());assertEquals(0,a.resumes);assertEquals(1,a.cancels);}
        var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);a.valid=false;r.tick(C,o(2,true,true,null),5);assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());assertEquals(0,a.resumes);
    }
    @Test void sourceRejectionsExcessConsumptionAndUnloadedPlotPreventFurtherUse(){
        for(String reject:List.of("This ability is on cooldown","You don't have any Compost","Invalid spray.","The server is shutting down!","please slow down")){
            var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.message(reject);assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(1,a.uses);assertEquals(0,a.resumes);}
        var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.tick(C,o(0,true,true,null),5);assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());
        a=new Actions();r=new PestSprayonatorRuntime(a);r.start(C,o(2,true,false,null),0);r.tick(C,o(2,true,false,null),1);r.tick(C,o(2,true,false,null),2);r.tick(C,o(2,true,false,null),3);assertEquals(0,a.uses);assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());
    }
    @Test void badReturnDoesNotResumeAndCancellationIsIdempotent(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.cancel();r.cancel();assertEquals(1,a.cancels);assertEquals(0,a.resumes);
        a=new Actions();r=new PestSprayonatorRuntime(a);waiting(r);r.message("SPRAYONATOR! You sprayed Plot - 1");r.tick(C,o(1,true,true,null),5);
        r.tick(C,o(1,true,true,null),6);r.tick(C,o(1,true,true,null),7);r.message("SPRAYONATOR! You sprayed Plot - 4");r.tick(C,o(0,true,true,null),8);r.tick(C,o(0,true,true,null),9);
        assertEquals(PestSprayonatorRuntime.State.RETURN,r.state());a.progress=PestSprayonatorRuntime.Progress.FAILED;r.tick(C,o(0,true,true,null),10);
        assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(0,a.resumes);assertEquals(1,a.cancels);
    }
    @Test void aggregateValueOverflowAndSourcePlotSelectionAreValidatedBeforeActions(){
        assertThrows(IllegalArgumentException.class,()->new PestSprayonatorRuntime.Settings(B,1,100,199,60000,8000));
        assertThrows(ArithmeticException.class,()->new PestSprayonatorRuntime.Settings(B,3,Long.MAX_VALUE,Long.MAX_VALUE,60000,8000));
        assertThrows(IllegalArgumentException.class,()->new PestSprayonatorRuntime.Settings(B,2,100,400,60000,8000));
        assertEquals(6,new PestSprayonatorRuntime.Settings(B,3,100,600,60000,8000).required());
        assertEquals(10,new PestSprayonatorRuntime.Settings(B,5,100,1000,60000,8000).required());
        assertThrows(IllegalArgumentException.class,()->new PestSprayonatorBindings(TOOL,MATERIAL,"Selected: ","Selected: Compost","Menu","Select","Selected",true,ESCAPE,List.of(plot(1,false,false))));
        assertEquals(List.of(1,4),B.selected().stream().map(PestSprayonatorBindings.Plot::id).toList());
    }
    @Test void explicitBindingsRejectMissingFiltersGestureAndDuplicateReceiptIdentity(){
        var json=new com.google.gson.Gson().toJsonTree(B).getAsJsonObject();assertEquals(B,PestSprayonatorBindings.read(json.toString()));
        json.remove("sneakToOpen");assertThrows(IllegalArgumentException.class,()->PestSprayonatorBindings.read(json.toString()));
        var missing=new com.google.gson.Gson().toJsonTree(B).getAsJsonObject();missing.getAsJsonArray("plots").get(0).getAsJsonObject().remove("excluded");
        assertThrows(IllegalArgumentException.class,()->PestSprayonatorBindings.read(missing.toString()));
        var p=plot(4,true,false);var duplicate=new PestSprayonatorBindings.Plot(4,true,false,p.spawn(),0,0,plot(1,true,false).successAck());
        assertThrows(IllegalArgumentException.class,()->new PestSprayonatorBindings(TOOL,MATERIAL,B.selectionPrefix(),B.selectedLore(),B.menuTitle(),B.availableLore(),B.selectedMenuLore(),true,ESCAPE,List.of(plot(1,true,false),duplicate)));
    }
    @Test void settingsOrMenuReplacementCancelBeforeUseAndDoNotRepeatClicks(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);waiting(r);r.tick(new PestSprayonatorRuntime.Settings(B,1,50,200,60000,8000),o(2,true,true,null),5);
        assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());assertEquals(1,a.uses);assertEquals(0,a.resumes);
        a=new Actions();r=new PestSprayonatorRuntime(a);r.start(C,o(2,false,false,null),0);r.tick(C,o(2,false,false,null),1);r.tick(C,o(2,false,false,null),2);r.tick(C,o(2,false,false,menu(1,false)),3);
        r.tick(C,o(2,true,false,new MenuSnapshot(8,B.menuTitle(),menu(2,true).slots(),2)),4);
        assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(1,a.clicks);assertEquals(0,a.uses);
    }
    @Test void sourceNearbyTrapEscapesBeforeMenuAndRevalidatesTrapFreeLanding(){
        var a=new Actions();var r=new PestSprayonatorRuntime(a);r.start(C,trap(true),0);r.tick(C,trap(true),1);r.tick(C,trap(true),2);
        assertEquals(PestSprayonatorRuntime.State.ESCAPE_PEST_TRAPS,r.state());assertEquals(1,a.escapes);assertEquals(0,a.opens);
        a.progress=PestSprayonatorRuntime.Progress.ACTIVE;r.tick(C,trap(true),3);assertEquals(0,a.opens);
        a.progress=PestSprayonatorRuntime.Progress.COMPLETE;r.tick(C,trap(false),4);r.tick(C,trap(false),5);assertEquals(1,a.opens);assertEquals(0,a.uses);
        a=new Actions();r=new PestSprayonatorRuntime(a);r.start(C,trap(true),0);r.tick(C,trap(true),1);r.tick(C,trap(true),2);r.tick(C,trap(true),3);
        assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(0,a.opens);assertEquals(0,a.resumes);
    }
    @Test void sourceEscapeCandidateCubeHeightDistanceAndExplicitFieldsAreRequired(){
        assertTrue(ESCAPE.sourceCandidate(new Vec3(.5,70,-96.5)));assertFalse(ESCAPE.sourceCandidate(new Vec3(-1,70,-96.5)));
        assertTrue(ESCAPE.outsideTrap(new Vec3(7.5,70,0),new Vec3(0,70,0)));assertFalse(ESCAPE.outsideTrap(new Vec3(7.49,70,0),new Vec3(0,70,0)));
        assertThrows(IllegalArgumentException.class,()->new PestSprayonatorBindings.Escape(ESCAPE.trap(),new Vec3(0,65.99,0),.5));
        var missing=new com.google.gson.Gson().toJsonTree(B).getAsJsonObject();missing.remove("escape");assertThrows(IllegalArgumentException.class,()->PestSprayonatorBindings.read(missing.toString()));
        var missingRadius=new com.google.gson.Gson().toJsonTree(B).getAsJsonObject();missingRadius.getAsJsonObject("escape").remove("arrivalRadius");var bad=missingRadius;
        assertThrows(IllegalArgumentException.class,()->PestSprayonatorBindings.read(bad.toString()));
    }
}
