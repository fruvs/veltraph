package dev.veltraph.client.combat.grind;

/** Ordinary instant shortbow clicks or a vanilla bow draw/release; unsafe draws are cancelled. */
public final class RangedShotController {
    public enum Action { NONE, USE, RELEASE, CANCEL }
    private int drawing=-1;
    private long started,nextShot;
    public void reset() { drawing=-1;started=nextShot=0; }
    public boolean drawing() { return drawing>=0; }
    public Action tick(int target,boolean safe,boolean shortbow,long now,long interval) {
        interval=Math.max(100,Math.min(3000,interval));
        if(drawing>=0) {
            if(!safe||target!=drawing||shortbow) {drawing=-1;nextShot=now+interval;return Action.CANCEL;}
            if(now-started>=1100) {drawing=-1;nextShot=now+interval;return Action.RELEASE;}
            return Action.NONE;
        }
        if(!safe||target<0||now<nextShot)return Action.NONE;
        if(shortbow)nextShot=now+interval;
        else {drawing=target;started=now;}
        return Action.USE;
    }
}
