package dev.veltraph.client.inventory.selling;

import com.google.gson.JsonParser;
import dev.veltraph.client.config.AutoSellMode;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SellingInspectorTest {
    private final Object player=new Object(),world=new Object(),inventory=new Object(),menu=new Object();
    private static SellingObservation.Stack item(String components,int count,boolean tool,boolean wearable,Map<String,String> tags){
        return new SellingObservation.Stack(-1,9,wearable?"minecraft:player_head":"minecraft:wheat",wearable?"Pet":"Wheat",components,count,tool,wearable,tags);
    }
    private SellingInspector.Snapshot snapshot(SellingObservation.Stack item){return new SellingInspector.Snapshot(player,world,inventory,menu,true,item);}
    @Test void commandGrammarAndAliases(){
        assertEquals(AutoSellMode.NPC,SellingInspectorRequest.parse("inspect npc 9").channel());
        assertEquals(AutoSellMode.TRASH,SellingInspectorRequest.parse("inspect TRASH 35").channel());
        assertEquals("ExtraAttributes.pet",SellingInspectorRequest.parse("inspect george_pets 9 ExtraAttributes.pet").petPath());
        assertEquals(SellingInspectorRequest.Kind.METADATA,SellingInspectorRequest.parse("metadata 12").kind());
        assertEquals(SellingInspectorRequest.Kind.HELP,SellingInspectorRequest.parse("").kind());
    }
    @Test void invalidSlotsCommandsAndPathsAreRejectedBeforeReading(){
        for(String command:List.of("inspect npc 0","inspect npc 8","inspect npc 36","inspect npc -1","inspect npc 9.5","metadata 40","inspect bazaar 9",
                "inspect george 9","inspect george 9 a.b.c.d.e.f","inspect george 9 ../pet","inspect npc 9 extra","set npc 9","metadata 9 extra")){
            var reads=new AtomicInteger();var result=SellingInspector.inspect(SellingInspectorRequest.parse(command),i->{reads.incrementAndGet();return null;});
            assertFalse(result.valid(),command);assertEquals(0,reads.get());assertTrue(result.json().isEmpty());
        }
    }
    @Test void exactFullComponentsArePreservedAndPriceQuoteStayUnset(){
        String components="full components \"quoted\" \\ slash ♥\n".repeat(800);var observed=snapshot(item(components,4,false,false,Map.of()));
        var reads=new AtomicInteger();var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->{reads.incrementAndGet();return observed;});
        assertTrue(result.valid(),result.message());assertEquals(2,reads.get());var json=JsonParser.parseString(result.json()).getAsJsonArray().get(0).getAsJsonObject();
        assertEquals(components,json.get("components").getAsString());assertEquals(4,json.get("quantity").getAsInt());
        assertTrue(json.get("unitValue").isJsonNull());assertEquals("",json.get("quoteLabel").getAsString());
        assertEquals("NPC",json.get("channel").getAsString());assertEquals("minecraft:wheat",json.get("registryId").getAsString());
    }
    @Test void trashTemplateNeverInventsZeroValue(){var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect trash 9"),i->snapshot(item("exact",4,false,false,Map.of())));
        var json=JsonParser.parseString(result.json()).getAsJsonArray().get(0).getAsJsonObject();assertTrue(result.valid());assertTrue(json.get("unitValue").isJsonNull());assertFalse(json.has("quoteLabel"));}
    @Test void protectedEmptyAndWearableItemsDoNotProduceSellingTemplates(){
        for(var item:List.of(item("exact",0,false,false,Map.of()),item("exact",1,true,false,Map.of()),item("exact",1,false,true,Map.of()))){
            var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->snapshot(item));assertFalse(result.valid());assertTrue(result.json().isEmpty());}
    }
    @Test void georgeRequiresExactPresentMarkerAndOneHead(){
        var good=item("full pet components",1,false,true,Map.of("ExtraAttributes.pet","\"literal pet data\""));
        var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect george 9 ExtraAttributes.pet"),i->snapshot(good));assertTrue(result.valid(),result.message());
        var json=JsonParser.parseString(result.json()).getAsJsonArray().get(0).getAsJsonObject();assertEquals("\"literal pet data\"",json.get("petDataValue").getAsString());
        assertTrue(json.get("unitValue").isJsonNull());assertEquals("",json.get("quoteLabel").getAsString());
        assertFalse(SellingInspector.inspect(SellingInspectorRequest.parse("inspect george 9 missing"),i->snapshot(good)).valid());
        assertFalse(SellingInspector.inspect(SellingInspectorRequest.parse("inspect george 9 ExtraAttributes.pet"),i->snapshot(item("exact",2,false,true,good.customData()))).valid());
    }
    @Test void metadataListsObservedPathsWithoutValuesOrTemplate(){
        var result=SellingInspector.inspect(SellingInspectorRequest.parse("metadata 9"),i->snapshot(item("exact",1,false,true,Map.of("z","private value","a.path","another value"))));
        assertTrue(result.valid());assertEquals(List.of("a.path","z"),result.paths());assertTrue(result.json().isEmpty());assertFalse(result.message().contains("private value"));
    }
    @Test void changedSlotNeverReturnsStaleJson(){
        var initial=item("first",4,false,false,Map.of());
        for(var changed:List.of(item("changed components",4,false,false,Map.of()),item("first",3,false,false,Map.of()),item("first",0,false,false,Map.of()))){
            var reads=new AtomicInteger();var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->snapshot(reads.getAndIncrement()==0?initial:changed));
            assertFalse(result.valid());assertTrue(result.message().contains("changed"));assertTrue(result.json().isEmpty());
        }
    }
    @Test void replacedWorldPlayerInventoryOrMenuRejectsInspection(){
        var observed=snapshot(item("exact",4,false,false,Map.of()));
        for(var changed:List.of(new SellingInspector.Snapshot(new Object(),world,inventory,menu,true,observed.item()),
                new SellingInspector.Snapshot(player,new Object(),inventory,menu,true,observed.item()),
                new SellingInspector.Snapshot(player,world,new Object(),menu,true,observed.item()),
                new SellingInspector.Snapshot(player,world,inventory,new Object(),true,observed.item()))){
            var reads=new AtomicInteger();var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->reads.getAndIncrement()==0?observed:changed);assertFalse(result.valid());assertTrue(result.json().isEmpty());}
    }
    @Test void noPlayerDeadAndWrongIndexAreClearErrors(){
        assertFalse(SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->null).valid());
        var value=item("exact",4,false,false,Map.of());assertFalse(SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->new SellingInspector.Snapshot(player,world,inventory,menu,false,value)).valid());
        assertFalse(SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 10"),i->snapshot(value)).valid());
    }
    @Test void helpDoesNotReadAnythingAndReaderErrorsStayLocal(){
        assertTrue(SellingInspector.inspect(SellingInspectorRequest.parse("help"),i->{throw new AssertionError();}).valid());
        var result=SellingInspector.inspect(SellingInspectorRequest.parse("inspect npc 9"),i->{throw new IllegalStateException("transient inventory");});
        assertFalse(result.valid());assertTrue(result.message().contains("transient inventory"));assertTrue(result.json().isEmpty());
    }
}
