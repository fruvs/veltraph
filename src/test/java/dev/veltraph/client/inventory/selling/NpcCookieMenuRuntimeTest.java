package dev.veltraph.client.inventory.selling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;

class NpcCookieMenuRuntimeTest {
    static final NpcCookieBindings.Item ITEM=new NpcCookieBindings.Item("minecraft:paper","Observed",1,"components");
    static final NpcCookieBindings B=new NpcCookieBindings("private_menu",8,ITEM,"SB",ITEM,"Cookie",ITEM,"Shop",ITEM);
    static final class P implements NpcCookieMenuRuntime.Port {
        Object p=new Object(),w=new Object(),c=new Object(),menu;String title="";int selected=2,open,click,restore;boolean command;List<String> inv=List.of("original");List<NpcCookieMenuRuntime.Row> rows=List.of();boolean cursor=true;
        public NpcCookieMenuRuntime.Observation read(){return new NpcCookieMenuRuntime.Observation(true,p,w,c,menu,title,0,inv,rows,selected,cursor);}
        public boolean open(NpcCookieMenuRuntime.Observation o,NpcCookieBindings b,boolean command){this.command=command;open++;if(!command)selected=b.menuItemSlot();return true;}
        public boolean click(NpcCookieMenuRuntime.Observation o,int slot){click++;return true;}
        public boolean restoreSelection(NpcCookieMenuRuntime.Observation o,int slot){selected=slot;restore++;return true;}
        void menu(String title){menu=new Object();this.title=title;rows=List.of(new NpcCookieMenuRuntime.Row(10,false,false,ITEM));}
    }
    @Test void readableCallbackOrderForBothOpeners(){for(boolean command:new boolean[]{false,true}){var p=new P();var r=new NpcCookieMenuRuntime(p);assertTrue(r.start(B,command,0,1,40));r.tick(1);assertEquals(1,p.open);assertEquals(command,p.command);assertEquals(0,p.click);p.menu("SB");r.tick(2);r.tick(3);assertEquals(1,p.click);p.menu("Cookie");r.tick(4);r.tick(5);assertEquals(2,p.click);p.menu("Shop");r.tick(6);assertEquals(NpcCookieMenuRuntime.State.HANDOFF,r.state());assertTrue(r.handoffValid());assertEquals(2,p.selected);assertEquals(command?0:1,p.restore);assertEquals(1,r.attempts());}}
    @Test void noResponseExpiresWithoutRepeatedCommand(){var p=new P();var r=new NpcCookieMenuRuntime(p);r.start(B,true,0,1,8);for(int i=1;i<=20;i++)r.tick(i);assertEquals(NpcCookieMenuRuntime.State.FAILED,r.state());assertEquals(1,p.open);assertEquals(0,p.click);}
    @Test void unexpectedAmbiguousLockedAndPlayerRowsNeverClick(){for(int variant=0;variant<4;variant++){var p=new P();var r=new NpcCookieMenuRuntime(p);r.start(B,true,0,1,40);r.tick(1);p.menu(variant==0?"Foreign":"SB");if(variant==1)p.rows=List.of(new NpcCookieMenuRuntime.Row(10,false,false,ITEM),new NpcCookieMenuRuntime.Row(11,false,false,ITEM));if(variant==2)p.rows=List.of(new NpcCookieMenuRuntime.Row(10,false,true,ITEM));if(variant==3)p.rows=List.of(new NpcCookieMenuRuntime.Row(10,true,false,ITEM));r.tick(2);r.tick(3);assertEquals(NpcCookieMenuRuntime.State.FAILED,r.state());assertEquals(0,p.click);}}
    @Test void manualSlotInventoryCursorAndConnectionChangesCancel(){for(int variant=0;variant<4;variant++){var p=new P();var r=new NpcCookieMenuRuntime(p);r.start(B,true,0,1,40);r.tick(1);if(variant==0)p.selected=3;if(variant==1)p.inv=List.of("changed");if(variant==2)p.cursor=false;if(variant==3)p.c=new Object();r.tick(2);assertEquals(NpcCookieMenuRuntime.State.FAILED,r.state());assertEquals(0,p.click);}}
    @Test void sameContainerCannotPretendToAcknowledgeCookieReplacement(){var p=new P();var r=new NpcCookieMenuRuntime(p);r.start(B,true,0,1,8);r.tick(1);p.menu("SB");r.tick(2);r.tick(3);p.title="Cookie";for(int i=4;i<=10;i++)r.tick(i);assertEquals(NpcCookieMenuRuntime.State.FAILED,r.state());assertEquals(1,p.click);}
    @Test void cancellationAndBackwardsClockNeverRevive(){var p=new P();var r=new NpcCookieMenuRuntime(p);r.start(B,true,0,1,40);r.tick(1);r.cancel();p.menu("SB");r.tick(2);assertEquals(NpcCookieMenuRuntime.State.CANCELLED,r.state());assertEquals(0,p.click);r.start(B,true,3,1,40);assertEquals(NpcCookieMenuRuntime.State.FAILED,r.state());}
    @Test void explicitObservedSchemaRoundTripAndUnknownFields(){var json=new com.google.gson.Gson().toJsonTree(B).getAsJsonObject();json.addProperty("schema",1);assertEquals(B,NpcCookieBindings.parse(json.toString()));json.addProperty("unknown",true);assertThrows(IllegalArgumentException.class,()->NpcCookieBindings.parse(json.toString()));}
    @Test void sourceOptionDefaultAndNoGuessedBindings(){assertFalse(dev.veltraph.client.config.SellingConfig.AUTO_SELL_NPC_VIA_COMMAND.defaultValue());assertFalse(dev.veltraph.client.config.SellingConfig.SELLING_NPC_COOKIE_ENABLED.defaultValue());assertEquals("",dev.veltraph.client.config.SellingConfig.SELLING_NPC_COOKIE_BINDINGS.defaultValue());assertThrows(RuntimeException.class,()->NpcCookieBindings.parse("{}"));assertThrows(IllegalArgumentException.class,()->new NpcCookieBindings("/menu",8,ITEM,"SB",ITEM,"Cookie",ITEM,"Shop",ITEM));}
}
