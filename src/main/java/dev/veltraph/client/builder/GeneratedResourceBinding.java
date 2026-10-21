package dev.veltraph.client.builder;

import com.google.gson.JsonParser;
import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Salted tool/menu values are explicitly supplied. A gauge is observed material count, never a ready flag. */
public record GeneratedResourceBinding(String toolItem,String resourceItem,String menuTitle,String gaugeLabel,String countPrefix,String toolName,String resourceName) {
    public GeneratedResourceBinding(String tool,String resource,String title,String gauge,String prefix){this(tool,resource,title,gauge,prefix,"","");}
    public static GeneratedResourceBinding decode(String json) {
        if(json==null||json.isBlank())throw new IllegalArgumentException("Blank source95 tool/resource/menu/gauge binding");
        if(json.length()>8192)throw new IllegalArgumentException("Oversized source95 binding");
        var o=JsonParser.parseString(json).getAsJsonObject();
        return new GeneratedResourceBinding(o.get("toolItem").getAsString(),o.get("resourceItem").getAsString(),o.get("menuTitle").getAsString(),o.has("gaugeLabel")?o.get("gaugeLabel").getAsString():"",o.has("countPrefix")?o.get("countPrefix").getAsString():"",o.has("toolName")?o.get("toolName").getAsString():"",o.has("resourceName")?o.get("resourceName").getAsString():"");
    }
    public GeneratedResourceBinding {
        for(var id:List.of(toolItem,resourceItem))if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Tool/resource registry id required");
        if(menuTitle.isBlank()||menuTitle.length()>256||gaugeLabel.length()>256||countPrefix.length()>256||gaugeLabel.isBlank()!=countPrefix.isBlank())throw new IllegalArgumentException("Exact menu and paired gauge/count-prefix contracts required");
        if(toolName.length()>256||resourceName.length()>256)throw new IllegalArgumentException("Oversized resource/tool identity label");
    }
    public OptionalInt storedCount(MenuSnapshot menu) {
        if(gaugeLabel.isBlank()||countPrefix.isBlank())return OptionalInt.empty();
        if(menu==null||!menu.title().equals(menuTitle))return OptionalInt.empty();
        var slots=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.name().equals(gaugeLabel)).toList();
        if(slots.size()!=1)return OptionalInt.empty();
        var lines=slots.getFirst().lore().stream().map(MenuSnapshot::clean).filter(s->s.startsWith(countPrefix)).toList();
        if(lines.size()!=1)return OptionalInt.empty();
        var digits=lines.getFirst().substring(countPrefix.length()).strip().replace(",","");
        if(!digits.matches("[0-9]{1,9}"))return OptionalInt.empty();
        return OptionalInt.of(Integer.parseInt(digits));
    }
    public static Map<String,String> palette(String json) {
        if(json==null||json.isBlank())return Map.of();
        if(json.length()>8192)throw new IllegalArgumentException("Oversized source92 palette");
        var out=new LinkedHashMap<String,String>();
        for(var e:JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            if(!e.getKey().matches("source92-[a-g]"))throw new IllegalArgumentException("Unknown source92 resource key");
            var id=e.getValue().getAsString();if(!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||id.equals("minecraft:air"))throw new IllegalArgumentException("Invalid source92 material binding");
            out.put(e.getKey(),id);
        }
        return Map.copyOf(out);
    }
}
