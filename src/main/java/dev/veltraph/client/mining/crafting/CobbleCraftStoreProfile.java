package dev.veltraph.client.mining.crafting;

import com.google.gson.*;
import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Explicit server contract; salted source recipe identities are never inferred from its icon. */
public record CobbleCraftStoreProfile(boolean nativeTable,Target craft,Target store,List<Input> inputs,
        Result output,int storeSlot,int sink,int batches,int maxConsumed,int maxOutput,int timeout,int delay,String currencyPrefix) {
    public record Target(BlockPos position,String block,String title) {}
    public record Identity(String id,String name,String components,List<String> lore) {
        public Identity { lore=List.copyOf(lore); }
    }
    public record Input(int slot,Identity item,int units) {}
    public record Result(int slot,Identity item,int units,boolean cursor) {}
    public CobbleCraftStoreProfile { inputs=List.copyOf(inputs); }
    public static CobbleCraftStoreProfile parse(String json) {
        if(json==null||json.length()>24000)throw new IllegalArgumentException("Craft/store profile exceeds bounds");
        var p=JsonParser.parseString(json).getAsJsonObject();
        String adapter=str(p,"adapter");if(!Set.of("native_table","source_menu").contains(adapter))throw new IllegalArgumentException("Unknown craft adapter");
        var craft=target(p.getAsJsonObject("craft"));var store=target(p.getAsJsonObject("store"));
        if(!Set.of("minecraft:chest","minecraft:trapped_chest","minecraft:barrel","minecraft:ender_chest").contains(store.block()))throw new IllegalArgumentException("Storage must be a configured ordinary container");
        if(adapter.equals("native_table")&&!craft.block().equals("minecraft:crafting_table"))throw new IllegalArgumentException("Native adapter requires crafting table");
        var inputs=new ArrayList<Input>();var cells=new HashSet<Integer>();
        for(var e:p.getAsJsonArray("inputs")){var i=e.getAsJsonObject();int cell=num(i,"slot",1,512);if(!cells.add(cell))throw new IllegalArgumentException("Duplicate recipe cell");inputs.add(new Input(cell,identity(i),num(i,"units",1,64)));}
        if(inputs.size()!=8)throw new IllegalArgumentException("Recovered cobble craft orchestration requires eight configured cells");
        var o=p.getAsJsonObject("output");String delivery=str(o,"delivery");if(!Set.of("cursor","inventory").contains(delivery))throw new IllegalArgumentException("Unknown output delivery");
        var output=new Result(num(o,"slot",0,512),identity(o),num(o,"units",1,64),delivery.equals("cursor"));
        if(cells.contains(output.slot())||output.item().id().equals("minecraft:barrier")||output.item().id().equals("minecraft:bedrock"))throw new IllegalArgumentException("Output overlaps input or is sentinel");
        if(inputs.stream().anyMatch(i->i.item().id().equals(output.item().id())))throw new IllegalArgumentException("Output must differ from inputs");
        int batches=num(p,"batches",1,8),consumed=num(p,"maxConsumed",8,4096),maxOut=num(p,"maxOutput",1,64);
        if(inputs.stream().mapToInt(Input::units).sum()*batches>consumed||output.units()*batches>maxOut)throw new IllegalArgumentException("Recipe exceeds explicit session resource/output caps");
        if(p.get("maxSpend").getAsBigDecimal().compareTo(java.math.BigDecimal.ZERO)!=0)throw new IllegalArgumentException("Unquoted paid recipes are unsupported; maxSpend must be zero");
        if(adapter.equals("native_table")&&(!cells.equals(Set.of(1,2,3,4,6,7,8,9))||output.slot()!=0||!output.cursor()))throw new IllegalArgumentException("Native ring/layout/output delivery differs");
        return new CobbleCraftStoreProfile(adapter.equals("native_table"),craft,store,inputs,output,num(p,"storeSlot",0,512),num(p,"sink",9,35),batches,consumed,maxOut,num(p,"timeoutTicks",40,1200),num(p,"delayTicks",2,20),p.has("currencyPrefix")?str(p,"currencyPrefix"):"");
    }
    private static Target target(JsonObject p){var xyz=str(p,"position").split(",",-1);if(xyz.length!=3)throw new IllegalArgumentException("Target requires x,y,z");var b=new BlockPos(Integer.parseInt(xyz[0].trim()),Integer.parseInt(xyz[1].trim()),Integer.parseInt(xyz[2].trim()));if(Math.abs((long)b.x())>30000000||Math.abs((long)b.z())>30000000||b.y() < -2048||b.y()>2048)throw new IllegalArgumentException("Target exceeds world bounds");return new Target(b,str(p,"block"),str(p,"title"));}
    private static Identity identity(JsonObject p){String id=str(p,"id");if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Invalid item ID");var lore=new ArrayList<String>();for(var l:p.getAsJsonArray("lore"))lore.add(l.getAsString());return new Identity(id,str(p,"name"),str(p,"components"),lore);}
    private static String str(JsonObject p,String key){String s=p.get(key).getAsString();if(s.isBlank()||s.length()>6000)throw new IllegalArgumentException("Empty/oversized "+key);return s;}
    private static int num(JsonObject p,String key,int min,int max){int n=p.get(key).getAsBigDecimal().intValueExact();if(n<min||n>max)throw new IllegalArgumentException("Out of bounds "+key);return n;}
}
