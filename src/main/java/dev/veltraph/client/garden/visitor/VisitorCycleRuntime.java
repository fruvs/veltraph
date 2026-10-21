package dev.veltraph.client.garden.visitor;

import java.util.*;

/** Ordered visitor jobs retain a maintenance lease until original gear and exact farm pose are restored. */
public final class VisitorCycleRuntime {
    public enum State { IDLE, APPLY_LOADOUT, SERVING, RESTORE_LOADOUT, AUTO_SELL, RETURNING, COMPLETE, FAILED, CANCELLED }
    public enum Progress { ACTIVE, COMPLETE, SKIPPED, FAILED, CANCELLED }
    public record Result(Progress progress,long spent){public Result{Objects.requireNonNull(progress);}}
    public record Settings(boolean enabled,boolean swap,String loadout,long maxSpending,long timeoutMs,boolean autoSellAfter){
        public Settings(boolean enabled,boolean swap,String loadout,long maxSpending,long timeoutMs){this(enabled,swap,loadout,maxSpending,timeoutMs,false);}
        public Settings{loadout=loadout==null?"":loadout.strip();}
    }
    public interface Actions {
        boolean acquire();
        boolean valid();
        boolean beginLoadout(String name);
        Progress loadout();
        boolean startVisitor(VisitorQueue.Entry entry,long remainingSpending);
        Result visitor();
        boolean restoreLoadout();
        Progress restoredLoadout();
        /** ACTIVE owns one selling job; SKIPPED proves no authorized native stack; all other results fail closed. */
        default Progress beginPostQueueAutoSell(){return Progress.FAILED;}
        /** COMPLETE requires seller acknowledgment and owned cleanup; parent retains its captured return. */
        default Progress postQueueAutoSell(){return Progress.FAILED;}
        boolean startReturn();
        Progress returned();
        boolean resume();
        /** No resume on failure/manual stop; releases worker/input and cancels owned restoration. */
        void cancel();
    }
    private final Actions actions;private List<VisitorQueue.Entry> queue=List.of();private Settings settings;
    private State state=State.IDLE;private int index,completed,skipped;private long spent,started,last;private String reason="Idle";
    public VisitorCycleRuntime(Actions actions){this.actions=Objects.requireNonNull(actions);}
    public boolean start(List<VisitorQueue.Entry> queue,Settings settings,long now){
        if(active())throw new IllegalStateException("Stop visitor cycle before restarting it");
        this.queue=List.copyOf(queue);this.settings=settings;index=completed=skipped=0;spent=0;started=last=now;
        if(!settings.enabled()||queue.isEmpty()||queue.size()>32||settings.maxSpending()<0||settings.timeoutMs()<1||settings.swap()&&settings.loadout().isBlank())return finish(State.FAILED,"Invalid visitor cycle opt-in, queue, loadout or budget");
        try{
            if(!actions.acquire()||!actions.valid())return finish(State.FAILED,"Maintenance lease capture rejected");
            if(settings.swap()){
                if(!actions.beginLoadout(settings.loadout()))return finish(State.FAILED,"Visitor loadout request rejected");
                state=State.APPLY_LOADOUT;reason="Awaiting named visitor loadout and actual equipment";
            }else if(!startVisitor())return false;
            return active();
        }catch(RuntimeException failure){finish(State.FAILED,"Visitor cycle start failed: "+failure.getMessage());return false;}
    }
    public void tick(boolean optedIn,long now){
        if(!active())return;
        try{
            if(!optedIn||!actions.valid()){finish(State.CANCELLED,"Visitor cycle context, farm capture or opt-in changed");return;}
            if(now<last||now-started>=settings.timeoutMs()){finish(State.FAILED,"Visitor cycle timed out without retrying an uncertain action");return;}last=now;
            switch(state){
                case APPLY_LOADOUT -> {var result=actions.loadout();if(result==Progress.COMPLETE)startVisitor();else if(result!=Progress.ACTIVE)finish(State.FAILED,"Visitor loadout not acknowledged");}
                case SERVING -> {
                    var result=actions.visitor();if(result.progress()==Progress.ACTIVE)return;
                    if(result.progress()!=Progress.COMPLETE&&result.progress()!=Progress.SKIPPED){finish(result.progress()==Progress.CANCELLED?State.CANCELLED:State.FAILED,"Visitor transaction ended without accepted acknowledgment");return;}
                    if(result.spent()<0||result.progress()==Progress.SKIPPED&&result.spent()!=0){finish(State.FAILED,"Invalid visitor spending acknowledgment");return;}
                    spent=Math.addExact(spent,result.spent());if(spent>settings.maxSpending()){finish(State.FAILED,"Visitor cycle aggregate budget exceeded");return;}
                    if(result.progress()==Progress.COMPLETE)completed++;else skipped++;index++;
                    if(index<queue.size())startVisitor();
                    else if(settings.swap()){
                        if(!actions.restoreLoadout()){finish(State.FAILED,"Original named loadout restoration rejected");return;}
                        state=State.RESTORE_LOADOUT;reason="Awaiting original loadout and exact equipment restoration";
                    }else afterQueue();
                }
                case RESTORE_LOADOUT -> {var result=actions.restoredLoadout();if(result==Progress.COMPLETE)afterQueue();else if(result!=Progress.ACTIVE)finish(State.FAILED,"Original loadout not restored; farm remains stopped");}
                case AUTO_SELL -> {
                    var result=actions.postQueueAutoSell();
                    if(result==Progress.COMPLETE)startReturn();
                    else if(result!=Progress.ACTIVE)finish(result==Progress.CANCELLED?State.CANCELLED:State.FAILED,"Post-queue sale lacked an acknowledged outcome and cleanup");
                }
                case RETURNING -> {var result=actions.returned();if(result==Progress.COMPLETE){
                    if(!actions.valid()||!actions.resume()){finish(State.FAILED,"Exact farm pose/profile/tool guard rejected resume");return;}
                    state=State.COMPLETE;reason="Ordered visitors complete; original equipment and captured farm engine resumed";
                }else if(result!=Progress.ACTIVE)finish(State.FAILED,"Captured farm pose return failed");}
                default -> {}
            }
        }catch(RuntimeException failure){finish(State.FAILED,"Visitor cycle failed: "+failure.getMessage());}
    }
    private boolean startVisitor(){
        if(!actions.valid()||!actions.startVisitor(queue.get(index),settings.maxSpending()-spent))return finish(State.FAILED,"Captured visitor unavailable or transaction rejected");
        state=State.SERVING;reason="Visitor "+(index+1)+"/"+queue.size()+": "+queue.get(index).name();return true;
    }
    private void afterQueue(){
        if(!settings.autoSellAfter()){startReturn();return;}
        var result=actions.beginPostQueueAutoSell();
        if(result==Progress.SKIPPED){startReturn();return;}
        if(result!=Progress.ACTIVE){finish(result==Progress.CANCELLED?State.CANCELLED:State.FAILED,"Post-queue selling activation rejected");return;}
        state=State.AUTO_SELL;reason="Queue exhausted; awaiting one owned auto-sell acknowledgment and cleanup";
    }
    private void startReturn(){if(!actions.startReturn()){finish(State.FAILED,"Captured farm return unavailable");return;}state=State.RETURNING;reason="Returning to exact captured farm pose";}
    public void cancel(){if(active())finish(State.CANCELLED,"Manual stop; farming is not resumed");}
    private boolean finish(State terminal,String why){state=terminal;reason=why;actions.cancel();return false;}
    public boolean active(){return state==State.APPLY_LOADOUT||state==State.SERVING||state==State.RESTORE_LOADOUT||state==State.AUTO_SELL||state==State.RETURNING;}
    public State state(){return state;}public long spent(){return spent;}public int completed(){return completed;}public int skipped(){return skipped;}
    public String status(){return state+": "+reason+" | served "+completed+", skipped "+skipped+", spent "+spent;}
}
