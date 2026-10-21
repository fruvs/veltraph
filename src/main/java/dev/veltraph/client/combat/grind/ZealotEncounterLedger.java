package dev.veltraph.client.combat.grind;
import java.util.*;
/** Client-observed owned attack/beam opportunities and health/death acknowledgments, not bestiary claims. */
public final class ZealotEncounterLedger {
    private record Hit(float before,long time){}private final Map<UUID,Hit> pending=new HashMap<>();private final Set<UUID> owned=new HashSet<>(),credited=new HashSet<>();private int kills,specialKills;
    public void attack(UUID id,float hp,long now){pending.putIfAbsent(id,new Hit(hp,now));}
    public void observe(UUID id,float hp,boolean alive,boolean protect,boolean special,long now){if(protect){pending.remove(id);owned.remove(id);return;}var h=pending.get(id);if(h!=null){if(now-h.time()>2000)pending.remove(id);else if(hp<h.before()){pending.remove(id);owned.add(id);}}if(!alive&&owned.contains(id)&&credited.add(id)){kills++;if(special)specialKills++;}}
    public void rebind(){pending.clear();owned.clear();credited.clear();}public void reset(){rebind();kills=specialKills=0;}public int kills(){return kills;}public int specialKills(){return specialKills;}
}
