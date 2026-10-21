package dev.veltraph.client.navigation;

import java.util.Objects;
import java.util.function.LongSupplier;

/** One actual startDynamic instance. Clock/data observations never supply control permission.
 * No world, actor, Root, original fence, query, deadline, seed or mutable global is retained.
 * mark has exactly ONE extra diagnostic clock call; skipped/invalid marks perform none. */
public final class GroundDynamicStageCost {
    private final LongSupplier diagnosticClock;
    private long visitedMask,failedMask;
    private int clockCalls;
    private long t0,t1,t2,t3,t4,t5,t6,t7,t8,t9,t10,t11,t12;
    private GroundDynamicStageCostSnapshot frozen;
    private final SnapshotFactory snapshotFactory;
    private boolean observationUnavailable;
    @FunctionalInterface interface SnapshotFactory { GroundDynamicStageCostSnapshot create(GroundDynamicStageCost observation); }
    public GroundDynamicStageCost(){this(System::nanoTime);}
    public GroundDynamicStageCost(LongSupplier diagnosticClock){this(diagnosticClock,GroundDynamicStageCost::snapshot);}
    GroundDynamicStageCost(LongSupplier diagnosticClock,SnapshotFactory snapshotFactory){this.diagnosticClock=Objects.requireNonNull(diagnosticClock);this.snapshotFactory=Objects.requireNonNull(snapshotFactory);}
    public boolean observationUnavailable(){return observationUnavailable;}
    public void mark(int site){
        if(site<0||site>=13||frozen!=null)return;
        clockCalls++;
        final long stamp;
        try{stamp=diagnosticClock.getAsLong();}catch(Throwable unavailable){observationUnavailable=true;failedMask|=1L<<site;visitedMask&=~(1L<<site);return;}
        visitedMask|=1L<<site;failedMask&=~(1L<<site);
        switch(site){
            case 0 -> t0=stamp;
            case 1 -> t1=stamp;
            case 2 -> t2=stamp;
            case 3 -> t3=stamp;
            case 4 -> t4=stamp;
            case 5 -> t5=stamp;
            case 6 -> t6=stamp;
            case 7 -> t7=stamp;
            case 8 -> t8=stamp;
            case 9 -> t9=stamp;
            case 10 -> t10=stamp;
            case 11 -> t11=stamp;
            case 12 -> t12=stamp;
            default -> { }
        }
    }
    private Long value(int site,long timestamp){return (visitedMask&(1L<<site))!=0?timestamp:null;}
    /** Called only at the parent's existing original refusal BEFORE stop. Success never freezes. */
    public GroundDynamicStageCostSnapshot freeze(){
        try{if(frozen==null)frozen=snapshotFactory.create(this);if(frozen==null)observationUnavailable=true;return frozen;}
        catch(Throwable unavailable){observationUnavailable=true;return null;}
    }
    private GroundDynamicStageCostSnapshot snapshot(){
        return new GroundDynamicStageCostSnapshot(visitedMask,failedMask,clockCalls,
                value(0,t0),value(1,t1),value(2,t2),value(3,t3),value(4,t4),value(5,t5),value(6,t6),value(7,t7),value(8,t8),value(9,t9),value(10,t10),value(11,t11),value(12,t12));
    }
}
