package dev.veltraph.client.combat.slayer;

import java.util.List;
import java.util.Locale;

/** Observed readiness gates a reusable left-click finisher; sending a click is not proof of a kill. */
public final class VampireFinisher {
    public enum Action { ATTACK,WAIT,MELEE }
    public record Decision(Action action,int slot,String status) {}
    private int target=-1,attempts;private long sent;
    public static boolean ready(String label) {
        return label!=null&&label.contains("҉")&&SlayerSignals.family(label)==SlayerSignals.Family.VAMPIRE;
    }
    public static int findSlot(List<String> names) {
        for(int i=0;i<Math.min(9,names.size());i++) {
            String name=names.get(i).replaceAll("§.","").strip().toLowerCase(Locale.ROOT);
            if(name.equals("steak stake")||name.endsWith(" steak stake"))return i;
        }
        return -1;
    }
    public Decision tick(int entityId,boolean ready,List<String> names,boolean hittable,long now) {
        if(entityId!=target) {reset();target=entityId;}
        if(!ready) {return new Decision(Action.MELEE,-1,"Melee");}
        int slot=findSlot(names);
        if(slot<0)return new Decision(Action.MELEE,-1,"No Steak Stake: continuing melee");
        if(attempts>0&&now-sent<1000)return new Decision(Action.WAIT,slot,"Waiting for finisher outcome");
        if(attempts>=3)return new Decision(Action.MELEE,-1,"Finisher not resolved: continuing melee");
        if(!hittable)return new Decision(Action.WAIT,slot,"Approaching/aiming Steak Stake");
        attempts++;sent=now;return new Decision(Action.ATTACK,slot,"Steak Stake attempt "+attempts);
    }
    public void reset() {target=-1;attempts=0;sent=0;}
}
