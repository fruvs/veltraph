package dev.veltraph.client.foraging;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Readable source proximity history IlIlIlIIII...: near<3 +2 capped100, away -1, absent removed,
 * pressure when any native level-player count>80. Identity and positions are current observations. */
public final class ParkCrowdTracker {
    private final Map<UUID,Integer> counts=new HashMap<>();
    public boolean observe(Vec3 local,Map<UUID,Vec3> nativePlayers) {
        counts.keySet().removeIf(id->!nativePlayers.containsKey(id));
        nativePlayers.forEach((id,position)->{
            if(position.distanceTo(local)<3)counts.put(id,Math.min(100,counts.getOrDefault(id,0)+2));
            else if(counts.containsKey(id)) {
                int value=counts.get(id)-1;
                if(value<=0)counts.remove(id);else counts.put(id,value);
            }
        });
        return crowded();
    }
    public boolean crowded(){return counts.values().stream().anyMatch(value->value>80);}
    public void clear(){counts.clear();}
}
