package dev.veltraph.client.combat.slayer;

/** Head-targeted alternating input; completion is determined by entity removal, never click count. */
public final class SpringInteraction {
    public enum Action { NONE,ATTACK,INTERACT }
    private int target=-1;private boolean right;private long next,headReady=-1;
    public Action tick(int id,boolean headHittable,long now) {
        if(id!=target) {reset();target=id;}
        if(!headHittable) {headReady=-1;return Action.NONE;}
        if(headReady<0)headReady=now;
        if(now-headReady<100||now<next)return Action.NONE;
        next=now+100;Action action=right?Action.INTERACT:Action.ATTACK;right=!right;return action;
    }
    public void reset() {target=-1;right=false;next=0;headReady=-1;}
}
