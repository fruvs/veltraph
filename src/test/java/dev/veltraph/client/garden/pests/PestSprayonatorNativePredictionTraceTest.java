package dev.veltraph.client.garden.pests;

import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** K native trace: click at client80, owned button cursor at81, server83 already has ACK at rev3. */
class PestSprayonatorNativePredictionTraceTest {
    record Stack(String item,String name,int count,Map<String,Object> components) {}
    static final Stack EMPTY=new Stack("minecraft:air","Air",0,Map.of());
    static final Stack BUTTON=new Stack("minecraft:wheat","Compost",1,Map.of("id","COMPOST","lore",List.of("Click to select!")));
    static final String TOOL="7d3176b363fc7a61c85e32990566f15e23972d77bc1a0abb21db5e8553ec6261",MATERIAL="aac1f6c5a0797e43cd8736bec1d89237f92350a1427293c4c69edb322a3623d7";
    static final Object PLAYER=new Object(),WORLD=new Object(),CONNECTION=new Object(),NATIVE_MENU=new Object();
    static final PestSprayonatorBindings B=new PestSprayonatorBindings(new PestBindings.Item("minecraft:stick","Sprayonator","SPRAYONATOR"),
        new PestBindings.Item("minecraft:wheat","Compost","COMPOST"),"Selected: ","Selected: Compost","Spray material","Click to select!","Selected!",true,
        new PestSprayonatorBindings.Escape(new PestBindings.Entity("minecraft:armor_stand","Spray Trap",""),new Vec3(9.5,70,-96.5),.5),List.of(
        new PestSprayonatorBindings.Plot(1,true,false,new Vec3(.5,70,-96.5),0,0,"SPRAYONATOR! You sprayed Plot - 1"),
        new PestSprayonatorBindings.Plot(5,true,false,new Vec3(-96.5,70,-96.5),0,0,"SPRAYONATOR! You sprayed Plot - 5")));
    static final PestSprayonatorRuntime.Settings C=new PestSprayonatorRuntime.Settings(B,1,100,200,120000,3000);
    static MenuSnapshot menu(int revision,boolean prediction,boolean selected){return new MenuSnapshot(1,"Spray material",List.of(prediction?
        new MenuSnapshot.Slot(12,"",List.of(),false,"minecraft:air",0):new MenuSnapshot.Slot(12,"Compost",List.of(selected?"Selected!":"Click to select!"),false,"minecraft:wheat",1)),revision);}
    static PestSprayonatorRuntime.Observation o(boolean cursorEmpty,boolean toolReady,boolean selected,MenuSnapshot menu){return new PestSprayonatorRuntime.Observation(true,menu==null,cursorEmpty,toolReady,TOOL,MATERIAL,4,selected,menu,true,false);}
    static List<Object> rows(Object button){var result=new ArrayList<Object>(Collections.nCopies(63,EMPTY));result.set(12,button);return result;}
    static List<Object> inventory(){var inv=new ArrayList<Object>(Collections.nCopies(36,EMPTY));inv.set(8,new Stack("minecraft:stick","Sprayonator",1,Map.of("id","SPRAYONATOR","lore",List.of("Selected: Dung","Source tool durability"))));inv.set(10,new Stack("minecraft:apple","Apple",3,Map.of()));inv.set(11,new Stack("minecraft:wheat","Compost",4,Map.of("id","COMPOST")));return inv;}
    static PestSpraySelectionCustody.Scope scope(int tick,int id,int revision,Object nativeMenu,Object cursor,List<?> slots,List<?> inv){return new PestSpraySelectionCustody.Scope(PLAYER,WORLD,CONNECTION,nativeMenu,id,revision,8,tick,slots,inv,Map.of("unchanged-gear",true),cursor,cursor.equals(EMPTY),EMPTY);}
    static class Actions implements PestSprayonatorRuntime.Actions {
        int clicks,uses,closes,travel,retired,cancels;boolean valid=true,permit=true;
        MenuSnapshot request;PestSpraySelectionCustody.Intent intent;PestSpraySelectionCustody.Scope current=scope(81,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory());
        public boolean valid(){return valid;}public boolean select(){return true;}public boolean escape(){return false;}public PestSprayonatorRuntime.Progress escaped(){return PestSprayonatorRuntime.Progress.FAILED;}public boolean openMenu(){return true;}
        public boolean click(MenuSnapshot m,int slot){clicks++;request=m;intent=PestSpraySelectionCustody.capture(scope(80,1,1,NATIVE_MENU,EMPTY,rows(BUTTON),inventory()),m,slot,true,scope(80,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory())).orElseThrow();return true;}
        // No Override annotation: this exact red test also compiles against old K Actions, which never calls the policy.
        public boolean waitingForOwnSelectionPrediction(PestSprayonatorRuntime.Observation observed,MenuSnapshot issued,int slot){return permit&&intent!=null&&intent.pendingMatches(current,issued,slot);}
        public void selectionAcknowledged(){retired++;intent=null;}
        public boolean close(MenuSnapshot m){closes++;return true;}public boolean acquire(int n){return false;}public PestSprayonatorRuntime.Progress acquired(){return PestSprayonatorRuntime.Progress.FAILED;}
        public boolean travel(PestSprayonatorBindings.Plot p){travel++;return true;}public PestSprayonatorRuntime.Progress travelled(PestSprayonatorBindings.Plot p){return PestSprayonatorRuntime.Progress.ACTIVE;}
        public boolean use(PestSprayonatorRuntime.Observation x){uses++;return true;}public boolean restore(){return true;}public PestSprayonatorRuntime.Progress restored(){return PestSprayonatorRuntime.Progress.ACTIVE;}
        public boolean beginReturn(){return true;}public PestSprayonatorRuntime.Progress returned(){return PestSprayonatorRuntime.Progress.ACTIVE;}public boolean resume(){return true;}public void cancel(){cancels++;}
    }
    static PestSprayonatorRuntime waiting(Actions a){var r=new PestSprayonatorRuntime(a);assertTrue(r.start(C,o(true,false,false,null),0));r.tick(C,o(true,false,false,null),1);r.tick(C,o(true,false,false,null),2);r.tick(C,o(true,true,false,null),3);r.tick(C,o(true,false,false,null),4);r.tick(C,o(true,false,false,null),5);r.tick(C,o(true,false,false,menu(1,false,false)),6);assertEquals(PestSprayonatorRuntime.State.WAIT_SELECTION,r.state());return r;}
    @Test void observedNativeKTick81WaitsWithoutAdvancingOrDuplicatingBeforeActualAck(){
        var a=new Actions();var r=waiting(a);r.tick(C,o(false,false,false,menu(1,true,false)),7);
        assertEquals(PestSprayonatorRuntime.State.WAIT_SELECTION,r.state(),"K seq7/client81: only owned PICKUP cursor differed; old guard cancelled before server rev3 arrived");
        assertEquals(1,a.clicks);assertEquals(0,a.uses);assertEquals(0,a.closes);assertEquals(0,a.travel);assertEquals(0,a.retired);assertEquals(0,a.cancels);
    }
    @Test void realAckRequiresClearedCursorChangedRevisionMenuMarkerAndToolSelection(){
        var a=new Actions();var r=waiting(a);r.tick(C,o(false,false,false,menu(1,true,false)),7);r.tick(C,o(true,false,false,menu(3,false,true)),8);
        assertEquals(PestSprayonatorRuntime.State.WAIT_SELECTION,r.state());assertEquals(0,a.retired);
        r.tick(C,o(true,false,true,menu(3,false,true)),9);assertEquals(PestSprayonatorRuntime.State.CLOSE_MENU,r.state());assertEquals(1,a.retired);assertNull(a.intent);assertEquals(0,a.closes);assertEquals(0,a.uses);
    }
    @Test void sameNameForeignLoresCountsComponentsMenusRevisionsSlotsAndInventoryAreDenied(){
        var variants=new ArrayList<PestSpraySelectionCustody.Scope>();
        variants.add(scope(81,1,1,NATIVE_MENU,new Stack("minecraft:wheat","Compost",1,Map.of("id","COMPOST","lore",List.of("Selected!"))),rows(EMPTY),inventory()));
        variants.add(scope(81,1,1,NATIVE_MENU,new Stack("minecraft:wheat","Compost",2,BUTTON.components()),rows(EMPTY),inventory()));
        variants.add(scope(81,1,1,NATIVE_MENU,new Stack("minecraft:wheat","Compost",1,Map.of("id","COMPOST","lore",List.of("Click to select!"),"unknown",true)),rows(EMPTY),inventory()));
        variants.add(scope(81,2,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory()));variants.add(scope(81,1,3,NATIVE_MENU,BUTTON,rows(EMPTY),inventory()));variants.add(scope(81,1,1,new Object(),BUTTON,rows(EMPTY),inventory()));
        variants.add(scope(81,1,1,NATIVE_MENU,BUTTON,rows(BUTTON),inventory()));var wrongSlot=rows(EMPTY);wrongSlot.set(13,BUTTON);variants.add(scope(81,1,1,NATIVE_MENU,BUTTON,wrongSlot,inventory()));
        var drift=inventory();drift.set(11,new Stack("minecraft:wheat","Compost",3,Map.of("id","COMPOST")));variants.add(scope(81,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),drift));
        for(var variant:variants){var a=new Actions();var r=waiting(a);a.current=variant;r.tick(C,o(false,false,false,menu(1,true,false)),7);assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());assertEquals(1,a.clicks);assertEquals(0,a.uses);assertEquals(0,a.closes);}
    }
    @Test void pendingOwnershipCannotBypassActionsValiditySupplyChangesOrOriginalAckDeadline(){
        var a=new Actions();var r=waiting(a);a.valid=false;r.tick(C,o(false,false,false,menu(1,true,false)),7);assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());
        a=new Actions();r=waiting(a);var bad=new PestSprayonatorRuntime.Observation(true,false,false,false,TOOL,MATERIAL,3,false,menu(1,true,false),true,false);r.tick(C,bad,7);assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());
        a=new Actions();r=waiting(a);r.tick(C,o(false,false,false,menu(1,true,false)),7);a.current=scope(120,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory());r.tick(C,o(false,false,false,menu(1,true,false)),3006);assertEquals(PestSprayonatorRuntime.State.FAILED,r.state());assertEquals(1,a.clicks);assertEquals(0,a.uses);assertEquals(0,a.retired);
    }
    @Test void defaultDeniedPolicyAndUnissuedUnknownScopeNeverAuthorizesPendingCursor(){
        var a=new Actions();var r=waiting(a);a.permit=false;r.tick(C,o(false,false,false,menu(1,true,false)),7);assertEquals(PestSprayonatorRuntime.State.CANCELLED,r.state());
        var before=scope(80,1,1,NATIVE_MENU,EMPTY,rows(BUTTON),inventory());var after=scope(80,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory());
        assertTrue(PestSpraySelectionCustody.capture(before,menu(1,false,false),12,false,after).isEmpty());
        assertTrue(PestSpraySelectionCustody.capture(before,menu(1,false,false),12,true,scope(81,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory())).isEmpty());
        assertThrows(IllegalArgumentException.class,()->scope(81,-1,1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory()));assertThrows(IllegalArgumentException.class,()->scope(81,1,-1,NATIVE_MENU,BUTTON,rows(EMPTY),inventory()));
        assertThrows(IllegalArgumentException.class,()->scope(81,1,1,NATIVE_MENU,BUTTON,rows(EMPTY),List.of()));
        assertThrows(IllegalArgumentException.class,()->new PestSpraySelectionCustody.Scope(PLAYER,WORLD,CONNECTION,NATIVE_MENU,1,1,9,81,rows(EMPTY),inventory(),Map.of(),BUTTON,false,EMPTY));
        assertTrue(PestSpraySelectionCustody.capture(before,menu(1,false,false),13,true,after).isEmpty());
    }
}
