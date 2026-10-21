package dev.veltraph.client.combat.slayer.tara;

/** Parent lifetime gate. Only an actual completed egg effect ends the owned WAITING/KILLING cycle. */
public final class TaraParentCycle {
    public enum Phase { IDLE, WAITING, KILLING, FAILED }
    private Phase phase=Phase.IDLE;private Object child,boss;private int completed;
    public boolean acquire(Object enabledChild,Object selectedBoss){
        if(phase!=Phase.IDLE||enabledChild==null||selectedBoss==null)return false;
        child=enabledChild;boss=selectedBoss;phase=Phase.WAITING;return true;
    }
    public boolean owns(Object enabledChild,Object selectedBoss){return pending()&&child==enabledChild&&boss==selectedBoss;}
    public boolean waitingEffect(Object enabledChild,Object selectedBoss,boolean uuidReceiptAndNativeHealth){
        if(phase!=Phase.WAITING||!owns(enabledChild,selectedBoss)||!uuidReceiptAndNativeHealth)return false;
        phase=Phase.KILLING;return true;
    }
    public boolean eggEffect(Object enabledChild,Object selectedBoss,boolean actualExactBodyRemoval){
        if(phase!=Phase.KILLING||!owns(enabledChild,selectedBoss)||!actualExactBodyRemoval)return false;
        completed++;phase=Phase.IDLE;child=boss=null;return true;
    }
    public void fail(){phase=Phase.FAILED;child=boss=null;}
    public void reset(){phase=Phase.IDLE;child=boss=null;completed=0;}
    public boolean pending(){return phase==Phase.WAITING||phase==Phase.KILLING;}
    public Phase phase(){return phase;}public int completed(){return completed;}
}
