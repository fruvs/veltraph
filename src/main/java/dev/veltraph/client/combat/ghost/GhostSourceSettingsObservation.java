package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;
import dev.veltraph.client.config.ConfigKey;
import dev.veltraph.client.config.ConfigService;

/** Immutable declared-key snapshot; actual values remain freshly read at every ownership boundary.
 * Preserves old String.valueOf comparison semantics without rebuilding a reflection registry/TreeMap/string each time. */
public final class GhostSourceSettingsObservation {
    private final List<ConfigKey<?>> keys;private final Object[] values;private final String[] oldText;private final Object area;
    private GhostSourceSettingsObservation(ConfigService source,List<ConfigKey<?>> declared,Object actualArea){
        var unique=new java.util.LinkedHashMap<String,ConfigKey<?>>();for(var key:declared)unique.put(key.name(),key);
        keys=List.copyOf(unique.values());values=new Object[keys.size()];oldText=new String[keys.size()];area=actualArea;
        for(int i=0;i<keys.size();i++){values[i]=source.get(keys.get(i));oldText[i]=String.valueOf(values[i]);}
    }
    public static GhostSourceSettingsObservation capture(ConfigService source,List<ConfigKey<?>> declared,Object actualArea){
        return new GhostSourceSettingsObservation(Objects.requireNonNull(source),Objects.requireNonNull(declared),actualArea);}
    public boolean current(ConfigService source,Object actualArea){
        if(source==null||area!=actualArea)return false;
        for(int i=0;i<keys.size();i++){Object now=source.get(keys.get(i)),before=values[i];
            if(immutableScalar(before)&&(before==now||Objects.equals(before,now)))continue;
            if(!oldText[i].equals(String.valueOf(now)))return false;
        }return true;
    }
    private static boolean immutableScalar(Object value){return value==null||value instanceof String||value instanceof Enum<?>
            ||value instanceof Boolean||value instanceof Integer||value instanceof Long||value instanceof Double||value instanceof Float
            ||value instanceof Short||value instanceof Byte||value instanceof Character;}
}
