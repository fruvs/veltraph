package dev.veltraph.client.combat.ghost;

import java.util.*;

/** Source chat drop notices are separate from owned-hit deaths and actual inventory pickup deltas. */
public final class GhostLedger {
    public static final Set<String> DROPS=Set.of("Sorrow","Crystal Fragment","Plasma","Volta");
    private record Hit(float before,long sent) {}
    private final Map<UUID,Hit> pending=new HashMap<>();private final Set<UUID> ownedHits=new HashSet<>(),credited=new HashSet<>();
    private final Map<String,Integer> collected=new HashMap<>();private int kills,notices,waves;
    private String pickup;private int beforePickup;
    public void attack(UUID id,float health,long now){pending.put(id,new Hit(health,now));}
    public void observe(UUID id,float health,boolean alive,boolean protectedTarget,long now){
        if(protectedTarget){pending.remove(id);ownedHits.remove(id);return;}var p=pending.get(id);
        if(p!=null){if(now-p.sent()>2000)pending.remove(id);else if(health<p.before()){ownedHits.add(id);pending.remove(id);}}
        if(!alive&&ownedHits.contains(id)&&credited.add(id))kills++;
    }
    public void beginPickup(String name,int count){if(pickup==null){pickup=name;beforePickup=count;}}
    public boolean pickup(String name,int count){if(pickup!=null&&pickup.equals(name)&&count>beforePickup){collected.merge(name,count-beforePickup,Integer::sum);pickup=null;return true;}return false;}
    /** Statistics only: canonical caller supplies newly confirmed packet+inventory units, never disappearance. */
    public void confirmedPickupUnits(String sourceServerId,int newlyConfirmedUnits){
        if(newlyConfirmedUnits<=0)throw new IllegalArgumentException("Positive newly confirmed units required");
        String name=switch(sourceServerId){case "SORROW"->"Sorrow";case "CRYSTAL_FRAGMENT"->"Crystal Fragment";case "PLASMA"->"Plasma";case "VOLTA"->"Volta";default->throw new IllegalArgumentException("Unregistered Ghost drop ID");};
        collected.merge(name,newlyConfirmedUnits,Math::addExact);
    }
    public void abandonPickup(){pickup=null;}
    /** Old-world witnesses cannot credit a new entity; session counters survive scheduled rest. */
    public void clearNativeContext(){pending.clear();ownedHits.clear();credited.clear();pickup=null;}
    public void onMessage(String text){if(text!=null&&text.replaceAll("§.","").contains("RARE DROP! Sorrow"))notices++;}
    public void wave(){waves++;pending.clear();ownedHits.clear();}
    public int kills(){return kills;}public int sorrowNotices(){return notices;}public int waves(){return waves;}
    public int collected(String name){return collected.getOrDefault(name,0);}public int collectedTotal(){return collected.values().stream().mapToInt(Integer::intValue).sum();}
}
