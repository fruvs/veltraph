package dev.veltraph.client.garden.loadout;
import dev.veltraph.client.gui.MenuSnapshot;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NamedLoadoutFlowTest {
    static Map<String,NamedLoadoutFlow.Gear> gear(String id){var map=new HashMap<String,NamedLoadoutFlow.Gear>();for(String slot:List.of("HEAD","CHEST","LEGS","FEET","OFFHAND"))map.put(slot,new NamedLoadoutFlow.Gear(id,1,"components:"+id+":"+slot));return Map.copyOf(map);}
    static final Map<String,NamedLoadoutFlow.Gear> FARM=gear("farm"),PEST=gear("pest");
    static final NamedLoadoutFlow.Settings SETTINGS=new NamedLoadoutFlow.Settings("loadout","Loadouts","Active","Click to equip",List.of(new NamedLoadoutFlow.Profile("Loadout 1","minecraft:paper","minecraft:emerald",FARM),new NamedLoadoutFlow.Profile("Loadout 2","minecraft:paper","minecraft:emerald",PEST)),1,20);
    static class Port implements NamedLoadoutFlow.Port {
        Object player=new Object(),world=new Object(),connection=new Object(),menu;int revision,commands,clicks,closes;String active="Loadout 1";Map<String,NamedLoadoutFlow.Gear> worn=FARM;boolean ack=true,gearAck=true,duplicate;
        public NamedLoadoutFlow.Observation read(){
            List<MenuSnapshot.Slot> rows=new ArrayList<>();for(int i=0;i<2;i++){String name="Loadout "+(i+1);boolean on=name.equals(active);rows.add(new MenuSnapshot.Slot(i,name,List.of(on?"Active":"Click to equip"),false,on?"minecraft:emerald":"minecraft:paper"));}
            if(duplicate)rows.add(new MenuSnapshot.Slot(3,"Loadout 1",List.of("Active"),false,"minecraft:emerald"));
            return new NamedLoadoutFlow.Observation(player,world,connection,menu,menu==null?null:new MenuSnapshot(4,"Loadouts",rows,revision),worn,true,false,revision>0);
        }
        public boolean command(NamedLoadoutFlow.Observation e,String c){commands++;if(ack)active=worn.equals(FARM)?"Loadout 1":"Loadout 2";menu=new Object();return true;}
        public boolean click(NamedLoadoutFlow.Observation e,int slot){clicks++;if(ack){active="Loadout "+(slot+1);revision++;}if(gearAck)worn=slot==0?FARM:PEST;return true;}
        public boolean close(NamedLoadoutFlow.Observation e){if(menu!=e.menu())return false;closes++;menu=null;return true;}
    }
    static void ticks(NamedLoadoutFlow flow,int from,int to){for(int t=from;t<=to;t++)flow.tick(t);}
    @Test void appliesAndRestoresCapturedNamedGear(){var p=new Port();var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,8);
        assertEquals(NamedLoadoutFlow.State.APPLIED,f.state());assertEquals("Loadout 1",f.original().name());assertEquals(PEST,f.receipt().gear());assertFalse(f.receipt().restoring());
        f.restore(9);ticks(f,9,17);assertEquals(NamedLoadoutFlow.State.RESTORED,f.state());assertEquals(FARM,f.receipt().gear());assertTrue(f.receipt().restoring());assertEquals(2,p.clicks);}
    @Test void markerAloneDoesNotAcknowledgeGear(){var p=new Port();p.gearAck=false;var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,21);
        assertEquals(NamedLoadoutFlow.State.FAILED,f.state());assertEquals(1,p.clicks);assertNull(f.receipt());}
    @Test void unknownOriginalGearDoesNotClick(){var p=new Port();p.worn=gear("unknown");var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,8);
        assertEquals(NamedLoadoutFlow.State.FAILED,f.state());assertEquals(0,p.clicks);}
    @Test void duplicateActiveNameDoesNotClick(){var p=new Port();p.duplicate=true;var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,8);
        assertEquals(NamedLoadoutFlow.State.FAILED,f.state());assertEquals(0,p.clicks);}
    @Test void replacementContextNeverRestores(){var p=new Port();var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,2);p.connection=new Object();f.tick(3);
        assertEquals(NamedLoadoutFlow.State.FAILED,f.state());f.cancel(4);assertEquals(1,p.clicks);assertFalse(f.active());}
    @Test void cancellationAfterKnownGearCompensatesWithoutTargetRetry(){var p=new Port();p.ack=false;var f=new NamedLoadoutFlow(p);f.start("Loadout 2",SETTINGS,0);ticks(f,0,3);
        assertEquals(NamedLoadoutFlow.State.WAIT_ACK,f.state());assertEquals(PEST,p.worn);p.ack=true;f.cancel(4);assertTrue(f.cleanupActive());ticks(f,4,15);
        assertEquals(NamedLoadoutFlow.State.CANCELLED,f.state());assertEquals(FARM,p.worn);assertEquals(2,p.clicks);}
}
