package dev.veltraph.client.combat.grind;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.regex.Pattern;

/** Local LOCATE -> WALK -> AIM -> ATTACK -> WAIT loop from MACRO_DECODE_slayers/ghost/combat.
 * Weapon-specific abilities and remote target ownership are not inferred by this controller.
 */
public final class CombatLoop {
    public enum Action { WAIT, WALK, AIM, ATTACK }
    public record Decision(Action action,CombatTarget target) {}
    private final Map<Integer,Long> blacklist=new HashMap<>();
    private CombatTarget target;
    private long acquired,lastAttack;
    private int kills;
    public Decision tick(List<CombatTarget> candidates,Vec3 player,String names,double reach,
                         double radius,boolean aligned,boolean attackReady,long now,long timeout) {
        blacklist.entrySet().removeIf(e->now-e.getValue()>10_000);
        CombatTarget current=target==null?null:candidates.stream().filter(t->t.id()==target.id()).findFirst().orElse(null);
        if(current!=null&&!current.alive()) kills++;
        if(current==null||!current.alive()||current.protectedTarget()
                ||!matches(current.name(),names)||player.distanceTo(current.position())>radius) target=null;
        else target=current;
        if(target!=null&&now-acquired>Math.max(1000,timeout)) { blacklist.put(target.id(),now); target=null; }
        if(target==null) {
            target=candidates.stream().filter(t->t.alive()&&!t.protectedTarget()&&!blacklist.containsKey(t.id())
                    &&matches(t.name(),names)&&player.distanceTo(t.position())<=radius)
                    .min(Comparator.comparingDouble(t->player.squaredDistanceTo(t.position()))).orElse(null);
            acquired=now;
            if(target!=null) aligned=false; // the alignment observation belonged to the previous target
        }
        if(target==null) return new Decision(Action.WAIT,null);
        if(player.distanceTo(target.position())>reach) return new Decision(Action.WALK,target);
        if(!aligned) return new Decision(Action.AIM,target);
        if(attackReady&&now-lastAttack>=100) { lastAttack=now; acquired=now; return new Decision(Action.ATTACK,target); }
        return new Decision(Action.AIM,target);
    }
    public static boolean matches(String name,String csv) {
        if(name==null||csv==null) return false;
        String cleaned=name.replaceAll("§.","").toLowerCase(Locale.ROOT);
        for(String raw:csv.split(",")) {
            String token=raw.strip().toLowerCase(Locale.ROOT);
            if(!token.isEmpty()&&Pattern.compile("(?<![a-z])"+Pattern.quote(token)+"(?![a-z])").matcher(cleaned).find()) return true;
        }
        return false;
    }
    public int targetId() { return target==null?-1:target.id(); }
    /** A real observed mechanic is still active target work; it never counts as an attack or kill. */
    public void preserveTarget(int expectedTarget,long now) {
        if(target!=null&&target.id()==expectedTarget)acquired=now;
    }
    public int kills() { return kills; }
    /** Adapter calls only for the same tracked physical body after observing native health <= 0. */
    public void confirmedDeath(int expectedId){if(target!=null&&target.id()==expectedId){kills++;target=null;}}
    public void unreachable(long now) { if(target!=null) blacklist.put(target.id(),now); target=null; }
    /** Fresh native IDs/timers cannot cross a scheduled rejoin; confirmed progression remains. */
    public void rebind(long now){target=null;blacklist.clear();acquired=now;lastAttack=0;}
    public void reset() { target=null; blacklist.clear(); acquired=0; lastAttack=0; kills=0; }
}
