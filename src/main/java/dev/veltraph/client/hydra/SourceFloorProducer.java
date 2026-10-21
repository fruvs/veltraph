package dev.veltraph.client.hydra;
import java.util.*;
/** Source verified-bedrock producer plus explicit modern delayed-callback custody guards. */
public final class SourceFloorProducer {
    public record Scope(Object player,Object world,Object connection,Object owner,long generation,boolean enabled){}
    private record Pending(Scope scope,long eventTick,long dueTick){}
    public record Append(long eventTick,long dueTick,long executionTick,long executionMillis){}
    public record Receipt(long tick,int accepted,int appended,int invocation,boolean evaluated,long evaluationMillis,int count,long oldestAgeMs,boolean detected){}
    private final ArrayDeque<Append> appendReceipts=new ArrayDeque<>();
    private Receipt receipt,detectedReceipt;
    private java.util.List<Append> detectedAppends=java.util.List.of();
    public Receipt detectedReceipt(){return detectedReceipt;}public java.util.List<Append> detectedAppends(){return detectedAppends;}
    private int acceptedTotal,appendedTotal;
    private java.util.function.Consumer<String> reportProbe=phase->{};
    /** Optional passive reporter used by deterministic fault regressions; no control state. */
    public void reportProbe(java.util.function.Consumer<String> probe){reportProbe=java.util.Objects.requireNonNull(probe);}
    public Receipt receipt(){return receipt;}public java.util.List<Append> appends(){return java.util.List.copyOf(appendReceipts);}
    private final ArrayDeque<Pending> pending=new ArrayDeque<>();
    private final ArrayDeque<Long> timestamps=new ArrayDeque<>();
    private Scope current;
    private long previousTick=Long.MIN_VALUE,previousTime=Long.MIN_VALUE;
    private int evaluationCounter;
    public boolean accept(Scope scope,boolean incomingBedrock,boolean priorBedrock,double px,double py,double pz,int x,int y,int z,long tick){
        synchronize(scope);if(!authorized(scope)||!incomingBedrock||priorBedrock)return false;
        double distance=Math.sqrt(Math.pow(px-x,2)+Math.pow(py-y,2)+Math.pow(pz-z,2));if(!(distance<18.0))return false;
        pending.addLast(new Pending(scope,tick,tick+1));acceptedTotal++;return true;
    }
    public boolean tick(Scope scope,long tick,long now){return tick(scope,tick,()->now);}
    public boolean tick(Scope scope,long tick,java.util.function.LongSupplier executionClock){
        long now=executionClock.getAsLong();
        synchronize(scope);
        if(tick<previousTick||now<previousTime)clear();previousTick=tick;previousTime=now;
        if(!authorized(scope))return false;
        for(var iterator=pending.iterator();iterator.hasNext();){var event=iterator.next();if(event.dueTick<=tick){iterator.remove();if(same(event.scope,scope)){long execution=executionClock.getAsLong();timestamps.addLast(execution);appendedTotal++;
                    try{reportProbe.accept("append");if(appendReceipts.size()==64)appendReceipts.removeFirst();appendReceipts.addLast(new Append(event.eventTick,event.dueTick,tick,execution));}catch(Throwable ignored){}}}}
        int prior=evaluationCounter++;boolean evaluated=prior>=7;long evaluationTime=now;boolean detected=false;
        if(evaluated){evaluationCounter=0;evaluationTime=executionClock.getAsLong();long cutoff=evaluationTime;timestamps.removeIf(time->cutoff-time>450);detected=timestamps.size()>10;}
        // Authoritative callbacks/counter/window/result are complete before cache publication.
        receipt=null;if(detected){detectedReceipt=null;detectedAppends=java.util.List.of();}
        try{reportProbe.accept("receipt");var next=new Receipt(tick,acceptedTotal,appendedTotal,prior+1,evaluated,evaluationTime,timestamps.size(),timestamps.isEmpty()?0:evaluationTime-timestamps.getFirst(),detected);
            if(detected){reportProbe.accept("detected-copy");var copies=java.util.List.copyOf(appendReceipts);detectedReceipt=next;detectedAppends=copies;}receipt=next;
        }catch(Throwable ignored){}
        return detected;
    }

    private static boolean authorized(Scope scope){return scope!=null&&scope.enabled()&&scope.owner()!=null&&scope.player()!=null&&scope.world()!=null&&scope.connection()!=null;}
    private void synchronize(Scope scope){if(!same(current,scope)){clear();current=scope;}}
    private static boolean same(Scope a,Scope b){return a!=null&&b!=null&&a.player()==b.player()&&a.world()==b.world()&&a.connection()==b.connection()&&a.owner()==b.owner()&&a.generation()==b.generation()&&a.enabled()==b.enabled();}
    public void clear(){pending.clear();timestamps.clear();evaluationCounter=0;acceptedTotal=0;appendedTotal=0;appendReceipts.clear();}
    public int pendingCount(){return pending.size();}public int count(){return timestamps.size();}
}
