package dev.veltraph.client.inventory.equipment;

import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class EquipmentWorkflowTest {
    static final EquipmentWorkflow.Item EMPTY=new EquipmentWorkflow.Item("","",0,"");
    static final EquipmentWorkflow.Item DEVICE=new EquipmentWorkflow.Item("minecraft:player_head","Personal Compactor 7000",1,"unique-device");
    static final class Fake implements EquipmentWorkflow.Port {
        Object player=new Object(),world=new Object(),menu;int selected=2,swaps,uses,commands,clicks,closes;boolean cursor=true,prediction=false,revision=true,settled=true;
        List<EquipmentWorkflow.Item> items=new ArrayList<>(Collections.nCopies(36,EMPTY));List<EquipmentWorkflow.Item> armor=List.of(EMPTY,EMPTY,EMPTY,EMPTY);MenuSnapshot snapshot;
        @Override public EquipmentWorkflow.Observation read(){return new EquipmentWorkflow.Observation(player,world,menu,snapshot,items,armor,selected,cursor,prediction,revision,settled);}
        @Override public boolean swap(EquipmentWorkflow.Observation o,int from,int to){swaps++;Collections.swap(items,from,to);revision=false;settled=false;return true;}
        @Override public boolean select(EquipmentWorkflow.Observation o,int slot){selected=slot;return true;}
        @Override public boolean use(EquipmentWorkflow.Observation o){uses++;return true;}
        @Override public boolean command(EquipmentWorkflow.Observation o,String command){commands++;return true;}
        @Override public boolean click(EquipmentWorkflow.Observation o,int slot){clicks++;revision=false;return true;}
        @Override public boolean close(EquipmentWorkflow.Observation o){closes++;snapshot=null;menu=null;return true;}
        void menu(String title,String name,String marker){if(menu==null)menu=new Object();snapshot=new MenuSnapshot(3,title,List.of(new MenuSnapshot.Slot(13,name,List.of(marker),false,"minecraft:paper",1)),7);revision=true;}
    }
    static EquipmentWorkflow.Request device(){return new EquipmentWorkflow.Request(EquipmentWorkflow.Kind.COMPACTOR,"Personal Compactor","Toggle","minecraft:paper","minecraft:paper","","Enabled","Disabled","","minecraft:player_head","Personal Compactor 7000",1,List.of(),List.of(),"","",1,50,1);}
    static void tick(EquipmentWorkflow workflow,int from,int through){for(int i=from;i<=through;i++)workflow.tick(i);}
    @Test void deviceNeedsServerMoveThenExactStateAndRestoresInventory(){
        Fake f=new Fake();f.items.set(17,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);
        assertEquals(EquipmentWorkflow.State.WAIT_MOVE,w.state());w.tick(2);assertEquals(0,f.uses,"Native transfer waits its correction tick");
        f.settled=true;tick(w,3,4);assertEquals(1,f.uses);f.menu("Personal Compactor","Toggle","Enabled");tick(w,5,6);assertEquals(1,f.clicks);
        f.menu("Personal Compactor","Toggle","Disabled");tick(w,7,10);assertEquals(2,f.swaps);assertEquals(EquipmentWorkflow.State.WAIT_RESTORE,w.state());
        w.tick(11);assertFalse(w.terminal(),"Restoration waits a vanilla correction tick");f.settled=true;w.tick(12);
        assertEquals(EquipmentWorkflow.State.COMPLETE,w.state());assertEquals(DEVICE,f.items.get(17));assertTrue(f.items.getFirst().empty());assertEquals(2,f.selected);
    }
    @Test void deviceDuplicateCountAndOccupiedHotbarAreRefused(){
        for(int mode=0;mode<3;mode++){
            Fake f=new Fake();f.items.set(17,DEVICE);
            if(mode==0)f.items.set(18,DEVICE);if(mode==1)f.items.set(17,new EquipmentWorkflow.Item(DEVICE.id(),DEVICE.name(),2,DEVICE.components()));
            if(mode==2)for(int i=0;i<9;i++)f.items.set(i,new EquipmentWorkflow.Item("minecraft:stone","Stone",64,""));
            EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);assertEquals(EquipmentWorkflow.State.FAILED,w.state());assertEquals(0,f.swaps+f.uses+f.clicks);
        }
    }
    @Test void foreignCursorAndWorldReplacementNeverMoveItems(){
        Fake f=new Fake();f.items.set(17,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);f.world=new Object();w.tick(1);assertEquals(0,f.swaps);assertEquals(EquipmentWorkflow.State.FAILED,w.state());
        f=new Fake();f.items.set(17,DEVICE);w=new EquipmentWorkflow(f);w.start(device(),0);f.cursor=false;w.tick(1);assertEquals(0,f.swaps);assertEquals(EquipmentWorkflow.State.FAILED,w.state());
    }
    @Test void cancelRestoresWithoutOpeningOrClickingMenu(){
        Fake f=new Fake();f.items.set(17,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);w.cancel();assertTrue(w.active());f.settled=true;tick(w,2,4);
        assertEquals(2,f.swaps);assertEquals(0,f.uses+f.clicks);f.settled=true;w.tick(5);assertEquals(EquipmentWorkflow.State.CANCELLED,w.state());assertEquals(DEVICE,f.items.get(17));assertEquals(2,f.selected);
    }
    @Test void menuUpdateWithoutRequestedStateTimesOutAndRestores(){
        Fake f=new Fake();f.items.set(0,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);f.menu("Personal Compactor","Toggle","Enabled");tick(w,2,3);
        f.revision=true;w.tick(4);assertEquals(EquipmentWorkflow.State.WAIT_ACK,w.state());w.tick(51);tick(w,52,55);assertEquals(EquipmentWorkflow.State.FAILED,w.state());assertEquals(1,f.clicks);assertEquals(2,f.selected);
    }
    @Test void sameTitleReplacementCannotReceiveAStaleClick(){
        Fake f=new Fake();f.items.set(0,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);f.menu("Personal Compactor","Toggle","Enabled");w.tick(2);f.menu=new Object();w.tick(3);assertEquals(0,f.clicks);assertEquals(EquipmentWorkflow.State.FAILED,w.state());assertEquals(0,f.closes);
    }
    @Test void petRequiresBothUpdatedStateAndPostClickServerMessage(){
        Fake f=new Fake();EquipmentWorkflow w=new EquipmentWorkflow(f);var request=new EquipmentWorkflow.Request(EquipmentWorkflow.Kind.PET,"Pets","Guardian","minecraft:paper","minecraft:paper","","Ready","Selected","pets","","",1,List.of(),List.of(),"Guardian summoned","",1,50,1);
        w.start(request,0);w.onServerMessage("Guardian summoned");w.tick(1);f.menu("Pets","Guardian","Ready");tick(w,2,3);
        f.menu("Pets","Guardian","Selected");w.tick(4);assertEquals(EquipmentWorkflow.State.WAIT_ACK,w.state());w.onServerMessage("Guardian summoned");tick(w,5,9);assertEquals(EquipmentWorkflow.State.COMPLETE,w.state());assertEquals(1,f.clicks);
    }
    @Test void wardrobeRequiresActualExpectedArmorAsWellAsEquippedMarker(){
        Fake f=new Fake();EquipmentWorkflow w=new EquipmentWorkflow(f);List<String> armor=List.of("minecraft:diamond_helmet","minecraft:diamond_chestplate","minecraft:diamond_leggings","minecraft:diamond_boots");
        var request=new EquipmentWorkflow.Request(EquipmentWorkflow.Kind.WARDROBE,"Wardrobe","Slot","minecraft:paper","minecraft:paper","","Ready",": Equipped","wardrobe","","",2,armor,List.of("Helmet","Chest","Legs","Boots"),"","",1,50,1);
        w.start(request,0);w.tick(1);f.menu("Wardrobe","Slot 2: Ready","Ready");tick(w,2,3);f.menu("Wardrobe","Slot 2: Equipped",": Equipped");w.tick(4);assertEquals(EquipmentWorkflow.State.WAIT_ACK,w.state());f.armor=java.util.stream.IntStream.range(0,4).mapToObj(i->new EquipmentWorkflow.Item(armor.get(i),List.of("Helmet","Chest","Legs","Boots").get(i),1,"")).toList();tick(w,5,9);assertEquals(EquipmentWorkflow.State.COMPLETE,w.state());
    }
    @Test void namedButtonWithWrongRegistryIdentityNeverClicks(){
        Fake f=new Fake();f.items.set(0,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);
        f.menu("Personal Compactor","Toggle","Enabled");f.snapshot=new MenuSnapshot(3,"Personal Compactor",List.of(new MenuSnapshot.Slot(13,"Toggle",List.of("Enabled"),false,"minecraft:tnt",1)),7);
        tick(w,2,6);assertEquals(0,f.clicks);assertEquals(EquipmentWorkflow.State.FAILED,w.state());
    }
    @Test void unknownLabelsAndTimingsCannotStartFromDefaults(){
        var config=new dev.veltraph.client.config.MapConfigService();
        assertThrows(IllegalArgumentException.class,()->dev.veltraph.client.config.EquipmentConfig.request(config));
    }
    @Test void wardrobeNamesPreventSameRegistryDifferentSetFalseAcknowledgment(){
        Fake f=new Fake();EquipmentWorkflow w=new EquipmentWorkflow(f);var ids=List.of("minecraft:leather_helmet","minecraft:leather_chestplate","minecraft:leather_leggings","minecraft:leather_boots");
        var names=List.of("Wanted Helm","Wanted Chest","Wanted Legs","Wanted Boots");
        var request=new EquipmentWorkflow.Request(EquipmentWorkflow.Kind.WARDROBE,"Wardrobe","Slot","minecraft:paper","minecraft:paper","","Ready",": Equipped","wardrobe","","",2,ids,names,"","",1,50,1);
        w.start(request,0);w.tick(1);f.menu("Wardrobe","Slot 2: Ready","Ready");tick(w,2,3);f.menu("Wardrobe","Slot 2: Equipped",": Equipped");
        f.armor=ids.stream().map(id->new EquipmentWorkflow.Item(id,"Other set",1,"")).toList();w.tick(4);assertEquals(EquipmentWorkflow.State.WAIT_ACK,w.state());
    }
    @Test void acknowledgedDisabledBarrierMayBeObservedWithoutEverClickingIt(){
        Fake f=new Fake();f.items.set(0,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);
        var request=new EquipmentWorkflow.Request(EquipmentWorkflow.Kind.COMPACTOR,"Personal Compactor","Toggle","minecraft:paper","minecraft:barrier","","Enabled","Disabled","","minecraft:player_head","Personal Compactor 7000",1,List.of(),List.of(),"","",1,50,1);
        w.start(request,0);w.tick(1);f.menu("Personal Compactor","Toggle","Enabled");tick(w,2,3);
        f.snapshot=new MenuSnapshot(3,"Personal Compactor",List.of(new MenuSnapshot.Slot(13,"Toggle",List.of("Disabled"),false,"minecraft:barrier",1)),8);f.revision=true;
        tick(w,4,8);assertEquals(EquipmentWorkflow.State.COMPLETE,w.state());assertEquals(1,f.clicks);
    }
    @Test void deviceIdentityCannotDisappearBetweenOpeningAndMenuClick(){
        Fake f=new Fake();f.items.set(0,DEVICE);EquipmentWorkflow w=new EquipmentWorkflow(f);w.start(device(),0);w.tick(1);
        f.menu("Personal Compactor","Toggle","Enabled");w.tick(2);f.items.set(0,EMPTY);tick(w,3,7);
        assertEquals(0,f.clicks);assertEquals(EquipmentWorkflow.State.FAILED,w.state());
    }
}
