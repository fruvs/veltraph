package dev.veltraph.client.galatea;

/** Bush harvest acknowledgement. A temporary air block or predicted maturity reset is reversible;
 * only a stable outcome is credited. Assumed clicks remain explicitly unconfirmed. */
public final class MaterialHarvestCycle {
    public enum Outcome { WAITING, CONFIRMED, ASSUMED, FAILED }
    private long usedAt,changedAt=-1;
    private int originalAge,originalRewardCount;
    private boolean clicked,feedback;
    public void begin(int age,int rewardCount) {
        originalAge=age;originalRewardCount=rewardCount;usedAt=0;changedAt=-1;clicked=feedback=false;
    }
    public void clicked(long now) { if(!clicked) { clicked=true;usedAt=now; } }
    public boolean wasClicked() { return clicked; }
    public void feedback() { if(clicked) feedback=true; }
    public Outcome observe(long now,boolean loaded,boolean present,int age,int rewardCount,long settleMs,long timeoutMs,boolean assume) {
        if(!loaded) return Outcome.FAILED;
        if(!clicked) return Outcome.WAITING;
        // A harvested berry bush remains in place but its AGE decreases. Growth and arbitrary
        // property changes are not harvest acknowledgements.
        boolean changed=feedback||rewardCount>originalRewardCount||!present||(originalAge>=2&&age>=0&&age<originalAge);
        if(changed) {
            if(changedAt<0) changedAt=now;
            return now-changedAt>=Math.max(100,settleMs)?Outcome.CONFIRMED:Outcome.WAITING;
        }
        changedAt=-1;
        if(assume&&now-usedAt>=500) return Outcome.ASSUMED;
        return now-usedAt>=Math.max(500,timeoutMs)?Outcome.FAILED:Outcome.WAITING;
    }
}
