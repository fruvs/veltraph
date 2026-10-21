package dev.veltraph.client.inventory.selling;

import com.google.gson.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Explicit source Hub arrival/NPC and Garden command receipt vocabulary. No invented source atlas. */
public record GeorgeSourceHubProfile(String hubCommand,String hubReceipt,String returnCommand,String returnReceipt,List<String> rejections,
                                     GeorgeWarpPermit.Destination hub,UUID npcUuid,String npcName,int timeoutTicks) {
    public GeorgeSourceHubProfile {rejections=List.copyOf(rejections);if(!command(hubCommand)||!command(returnCommand)||hubReceipt==null||hubReceipt.isBlank()||returnReceipt==null||returnReceipt.isBlank()||hubReceipt.equals(returnReceipt)||rejections.isEmpty()||rejections.stream().anyMatch(String::isBlank)||rejections.contains(hubReceipt)||rejections.contains(returnReceipt)||hub==null||hub.area()!=GameArea.HUB||npcUuid==null||npcName==null||npcName.isBlank()||timeoutTicks<1||timeoutTicks>1200)throw new IllegalArgumentException("Required source Hub/NPC/return bindings missing");}
    private static boolean command(String s){return s!=null&&s.matches("[a-z][a-z0-9_]{0,31}(?: [A-Za-z0-9 _'-]{1,96})?");}
    public static GeorgeSourceHubProfile parse(String json){var o=JsonParser.parseString(json).getAsJsonObject();if(!o.keySet().equals(Set.of("hubCommand","hubReceipt","returnCommand","returnReceipt","rejections","hub","npcUuid","npcName","timeoutTicks")))throw new IllegalArgumentException("Exact George Hub profile fields required");var h=o.getAsJsonObject("hub");if(!h.keySet().equals(Set.of("dimension","x","y","z","radius")))throw new IllegalArgumentException("Exact loaded Hub landing required");var rejects=new ArrayList<String>();for(var v:o.getAsJsonArray("rejections")){if(!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Literal rejection required");rejects.add(v.getAsString());}var uuid=UUID.fromString(string(o,"npcUuid"));if(!uuid.toString().equals(string(o,"npcUuid")))throw new IllegalArgumentException("Canonical NPC UUID required");return new GeorgeSourceHubProfile(string(o,"hubCommand"),string(o,"hubReceipt"),string(o,"returnCommand"),string(o,"returnReceipt"),rejects,new GeorgeWarpPermit.Destination(GameArea.HUB,string(h,"dimension"),new Vec3(number(h,"x"),number(h,"y"),number(h,"z")),number(h,"radius")),uuid,string(o,"npcName"),new java.math.BigDecimal(o.get("timeoutTicks").getAsString()).intValueExact());}
    private static String string(JsonObject o,String k){var v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Required literal "+k);return v.getAsString();}
    private static double number(JsonObject o,String k){var v=o.get(k);if(v==null||!v.isJsonPrimitive()||!v.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Required native number "+k);return v.getAsDouble();}
}
