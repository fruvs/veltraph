package dev.veltraph.client.fishing.support;

import com.google.gson.Gson;
import java.util.Objects;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Explicit replacements for source-salted Black Hole item, marker/effect, cost and renewal values. */
public record BlackHoleProfile(String itemName, String itemId, String effectMarkerName,
                               String ownerMarkerTemplate, Integer manaCost, Integer itemConsumption,
                               Long renewalIntervalMillis, Long requestTimeoutMillis) {
    public BlackHoleProfile {
        for (String value : new String[]{itemName,itemId,effectMarkerName,ownerMarkerTemplate})
            if (value==null || value.isBlank() || value.length()>128)
                throw new IllegalArgumentException("Declare exact Black Hole tool and native effect marker names");
        if (!ownerMarkerTemplate.contains("{player}") || effectMarkerName.equals(ownerMarkerTemplate))
            throw new IllegalArgumentException("Declare a distinct Black Hole owner marker containing {player}");
        if (manaCost==null || itemConsumption==null || renewalIntervalMillis==null || requestTimeoutMillis==null
                || manaCost<0 || manaCost>100000 || itemConsumption<0 || itemConsumption>1
                || manaCost+itemConsumption==0 || renewalIntervalMillis<0
                || requestTimeoutMillis<250 || requestTimeoutMillis>10000)
            throw new IllegalArgumentException("Declare explicit Black Hole consumption, renewal and request bounds");
    }
    public String ownerMarker(String player) { return ownerMarkerTemplate.replace("{player}",player); }
    public record Marker(UUID nativeId,String nativeName) {}
    /** Source requires both marker predicates. Exact names, distinct entities and freshness are rebuild acceptance. */
    public boolean markerPair(List<Marker> markers,String player,Set<UUID> before) {
        UUID effect=null,owner=null;
        for(var marker:markers) {
            if(before.contains(marker.nativeId()))continue;
            String name=marker.nativeName().replaceAll("§.","");
            if(name.equals(effectMarkerName)){if(effect!=null)return false;effect=marker.nativeId();}
            if(name.equals(ownerMarker(player))){if(owner!=null)return false;owner=marker.nativeId();}
        }
        return effect!=null&&owner!=null&&!effect.equals(owner);
    }
    public static BlackHoleProfile parse(String json) {
        try {
            if(json==null || json.length()>4096)throw new IllegalArgumentException();
            return Objects.requireNonNull(new Gson().fromJson(json,BlackHoleProfile.class));
        } catch(RuntimeException e) {
            throw new IllegalArgumentException("Configure exact Black Hole tool, markers, consumption and renewal profile",e);
        }
    }
}
