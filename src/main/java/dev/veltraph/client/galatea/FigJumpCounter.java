package dev.veltraph.client.galatea;

/** Source target-completion lifetime and Fig counter; all world/input operands are observations. */
public final class FigJumpCounter {
    private int counter;
    private boolean figReset,helperComplete;
    private Long absentSince;
    private long delayMs=Long.MAX_VALUE;
    public void enable(int configuredSpeed,long jitterMs) {
        if(jitterMs < -10 || jitterMs >= 10)throw new IllegalArgumentException("Source jitter outside [-10,10)");
        counter=0;figReset=false;helperComplete=false;absentSince=null;
        delayMs=Math.max(350L-(long)configuredSpeed*50L+jitterMs,0L);
    }
    /** Source mode entry sets this flag until a subsequent target candidate calculation. */
    public void enterJumpOrThrow(){figReset=true;}
    public void calculatingCandidate(){figReset=false;}
    public void observeTarget(long now,boolean zeroTarget,boolean targetPassable,boolean excluded,boolean rotationBusy) {
        if(zeroTarget){helperComplete=true;return;}
        if(targetPassable||excluded){if(absentSince==null)absentSince=now;}
        else absentSince=null;
        helperComplete=absentSince!=null&&now-absentSince>delayMs&&!rotationBusy;
    }
    /** Exactly once per source jumping handler tick, before that tick changes owned jump input. */
    public void observeJumpTick(boolean onGround,boolean ownedJumpHeld) {
        counter=FigJumpAimPolicy.observeCounter(counter,helperComplete,figReset,onGround,ownedJumpHeld);
    }
    public int counter(){return counter;}
    public long delayMs(){return delayMs;}
    public boolean helperComplete(){return helperComplete;}
    public boolean figReset(){return figReset;}
    public Long absentSince(){return absentSince;}
}
