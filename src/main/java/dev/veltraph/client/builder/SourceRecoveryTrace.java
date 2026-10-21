package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;

/** Immutable observations of a real owned recovery. These never supply movement or arrival facts. */
record SourceRecoveryTrace(long ticket, BlockPos origin, Vec3 start, BlockPos selected, Vec3 goal,
                           Vec3 actual, String navigation, long packetAgeMillis, long recoveredGapAgeMillis,
                           boolean lagBlocked, boolean clickBlocked, int successes, int failures, int cancellations,
                           String outcome) {
    static SourceRecoveryTrace started(SourceRecoveryTrace prior,long ticket,BlockPos origin,Vec3 start,BlockPos selected,Vec3 goal) {
        return new SourceRecoveryTrace(ticket,origin,start,selected,goal,start,"Starting",-1,-1,true,true,
                prior==null?0:prior.successes(),prior==null?0:prior.failures(),prior==null?0:prior.cancellations(),"ACTIVE");
    }
    SourceRecoveryTrace observed(Vec3 actual,String navigation,BuilderResourcePacketClock.Sample timing,long now) {
        return new SourceRecoveryTrace(ticket,origin,start,selected,goal,actual,navigation,
                timing==null||timing.packet()<0?-1:now-timing.packet(),
                timing==null||timing.recoveredGap()<0?-1:now-timing.recoveredGap(),
                BuilderResourcePacketClock.lag(timing,now),BuilderResourcePacketClock.clickBlocked(timing,now),
                successes,failures,cancellations,outcome);
    }
    SourceRecoveryTrace finished(boolean arrived) {return terminal(arrived?"ARRIVED":"BLOCKED",arrived?1:0,arrived?0:1,0);}
    SourceRecoveryTrace cancelled() {return terminal("CANCELLED",0,0,1);}
    private SourceRecoveryTrace terminal(String outcome,int success,int failure,int cancel) {
        return new SourceRecoveryTrace(ticket,origin,start,selected,goal,actual,navigation,packetAgeMillis,recoveredGapAgeMillis,
                lagBlocked,clickBlocked,successes+success,failures+failure,cancellations+cancel,outcome);
    }
}
