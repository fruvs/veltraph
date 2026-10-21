package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.config.AutoSellMode;
import java.util.*;

/** Read-only two-observation inspector; no config, inventory, movement, menu or packet methods. */
public final class SellingInspector {
    private SellingInspector() {}
    public record Snapshot(Object player,Object world,Object inventory,Object menu,boolean alive,SellingObservation.Stack item) {}
    @FunctionalInterface public interface Reader { Snapshot read(int mainIndex); }
    public record Result(boolean valid,String message,String json,List<String> paths) {
        public Result { paths=List.copyOf(paths); }
        public static Result error(String why){return new Result(false,why,"",List.of());}
    }
    public static Result inspect(SellingInspectorRequest request,Reader reader){
        if(request==null||!request.valid())return Result.error(request==null?SellingInspectorRequest.USAGE:request.error());
        if(request.kind()==SellingInspectorRequest.Kind.HELP)return new Result(true,SellingInspectorRequest.USAGE,"",List.of());
        if(request.index()<9||request.index()>35)return Result.error("Only main inventory 9..35 is allowed");
        if(reader==null)return Result.error("Inventory reader unavailable");
        try{
            var first=reader.read(request.index());String invalid=invalid(first,request.index());if(invalid!=null)return Result.error(invalid);
            var item=first.item();
            if(request.kind()==SellingInspectorRequest.Kind.INSPECT){
                if(request.channel()==null||request.channel()==AutoSellMode.BAZAAR)return Result.error("Choose NPC, trash or George");
                if(item.wearable()&&request.channel()!=AutoSellMode.GEORGE_PETS)return Result.error("Wearable items are protected");
                if(request.channel()==AutoSellMode.GEORGE_PETS&&(item.count()!=1||!item.registryId().equals("minecraft:player_head")
                        ||!SellingInspectorRequest.validPath(request.petPath())||!item.customData().containsKey(request.petPath())))
                    return Result.error("George requires one exact player head and a present explicitly observed metadata path");
            }
            var second=reader.read(request.index());
            if(invalid(second,request.index())!=null||first.player()!=second.player()||first.world()!=second.world()
                    ||first.inventory()!=second.inventory()||first.menu()!=second.menu()||!item.equals(second.item()))
                return Result.error("Inventory slot/context changed while inspecting; run the command again");
            var paths=item.customData().keySet().stream().sorted().toList();
            if(request.kind()==SellingInspectorRequest.Kind.METADATA)return new Result(true,
                    paths.isEmpty()?"No custom-data paths on this exact stack":"Observed custom-data paths only; this does not classify an item as a pet","",paths);
            var json=new JsonObject();json.addProperty("channel",request.channel().name());json.addProperty("inventoryIndex",request.index());
            json.addProperty("registryId",item.registryId());json.addProperty("name",item.name());json.addProperty("components",item.components());
            json.addProperty("quantity",item.count());json.add("unitValue",JsonNull.INSTANCE);
            if(request.channel()!=AutoSellMode.TRASH)json.addProperty("quoteLabel","");
            if(request.channel()==AutoSellMode.GEORGE_PETS){json.addProperty("petDataPath",request.petPath());json.addProperty("petDataValue",item.customData().get(request.petPath()));}
            var array=new JsonArray();array.add(json);
            return new Result(true,"Exact identity template only: unitValue is null and any quoteLabel is empty; no setting or item was changed",array.toString(),paths);
        }catch(RuntimeException e){return Result.error("Inspection unavailable: "+e.getMessage());}
    }
    private static String invalid(Snapshot observed,int index){
        if(observed==null||observed.player()==null||observed.world()==null||observed.inventory()==null||!observed.alive())return "Living player/world inventory unavailable";
        var item=observed.item();if(item==null||item.inventoryIndex()!=index)return "Requested inventory slot is unavailable";
        if(item.empty())return "Requested slot is empty";if(item.count()<1||item.count()>64)return "Unsupported whole-stack count";
        if(item.protectedItem())return "Damageable tools, weapons and shields are protected";
        if(item.registryId()==null||item.registryId().isBlank()||item.name().isBlank()||item.components()==null||item.components().isBlank())return "Exact item identity is unavailable";
        return null;
    }
}
