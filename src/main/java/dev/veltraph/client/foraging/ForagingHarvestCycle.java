package dev.veltraph.client.foraging;
/** Server-observed multi-log outcome window. No predicted log geometry or local block removal. */
public final class ForagingHarvestCycle {
 private int removed,currentBatch,largestBatch,multiLogBatches,activations;
 private long quietUntil;private boolean active,useActive;
 public void activate(boolean use,long now,long responseWindowMs) {
  if(!active) { currentBatch=0;activations++;active=true; }
  useActive=use;
  if(use) quietUntil=now+Math.max(0,responseWindowMs);
 }
 public void observe(int removedNow,boolean multiLog,long now,long responseWindowMs) {
  int delta=removedNow-removed;removed=removedNow;
  if(delta<0) { currentBatch=0;active=false;useActive=false;quietUntil=now+Math.max(0,responseWindowMs);return; }
  if(delta==0)return;
  int prior=currentBatch;currentBatch+=delta;largestBatch=Math.max(largestBatch,currentBatch);
  if(prior<=1&&currentBatch>1)multiLogBatches++;
  active=false;useActive=false;
  quietUntil=multiLog||currentBatch>1?now+Math.max(0,responseWindowMs):now;
 }
 public boolean waiting(long now) { if(useActive&&now>=quietUntil){active=false;useActive=false;}return now<quietUntil; }
 public boolean active() { return active; }
 public int largestBatch() {return largestBatch;}
 public int multiLogBatches() {return multiLogBatches;}
 public int activations() {return activations;}
 public void reset() {removed=currentBatch=largestBatch=multiLogBatches=activations=0;quietUntil=0;active=useActive=false;}
}
