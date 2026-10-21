package dev.veltraph.client.rotation;

import java.util.Optional;

/** Tick-planned actual-pose trajectory. Frame evaluation contains no callbacks or gameplay actions. */
public final class OwnedRotationTrajectory {
    public record Pose(float yaw,float pitch) {
        public Pose {
            if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)
                throw new IllegalArgumentException("Invalid camera pose");
        }
    }
    public record Scope(Object owner,Object player,Object world,Object connection) {
        public Scope {
            java.util.Objects.requireNonNull(owner);java.util.Objects.requireNonNull(player);
            java.util.Objects.requireNonNull(world);java.util.Objects.requireNonNull(connection);
        }
        private boolean same(Scope other) {
            return other!=null&&owner==other.owner&&player==other.player&&world==other.world&&connection==other.connection;
        }
    }
    public record Application(long ticket,Pose pose,boolean endpoint) {}
    private Scope scope,completedScope;
    private Pose start,end,applied;
    private long tick,ticket,serial,completedTicket,epoch;
    private float fraction,baseFraction;
    public long plan(Scope next,long tick,Pose actual,Pose requested) {
        if(scope==null||!scope.same(next)||this.tick!=tick) {
            if(scope!=null){epoch++;clear();}
            if(completedScope==null||!completedScope.same(next)) {completedTicket=0;completedScope=null;}
            scope=next;this.tick=tick;start=applied=actual;fraction=baseFraction=0;
        } else if(!samePose(actual,applied)) {
            // Native/server/manual correction owns its new pose; never pull it back into stale work.
            epoch++;clear();scope=next;this.tick=tick;start=applied=actual;
        }
        if(fraction>0){start=applied=actual;baseFraction=fraction;}
        end=new Pose(start.yaw()+SourceRotationStepper.wrap(requested.yaw()-start.yaw()),requested.pitch());
        ticket=++serial;return ticket;
    }
    /** No mutation/cancellation; privately bound to the exact original queued application. */
    public final class ApplicationPreview {
        private final Scope owned=scope,completed=completedScope;private final Pose originalStart=start,originalEnd=end,originalApplied=applied;
        private final long plannedTick=OwnedRotationTrajectory.this.tick,plannedTicket=ticket,plannedSerial=serial,plannedEpoch=epoch,completedId=completedTicket;
        private final float originalFraction=fraction,originalBase=baseFraction;private final Application application;
        private ApplicationPreview(Application application){this.application=application;}
        public Application application(){return application;}
        public boolean current(Pose actual){return scope==owned&&completedScope==completed&&start==originalStart&&end==originalEnd&&applied==originalApplied
            &&tick==plannedTick&&ticket==plannedTicket&&serial==plannedSerial&&epoch==plannedEpoch&&completedTicket==completedId
            &&fraction==originalFraction&&baseFraction==originalBase&&samePose(actual,originalApplied);}
    }
    public ApplicationPreview previewFrame(Scope current,long tick,float partial,Pose actual){
        if(scope==null||!valid(current,tick,actual)||!Float.isFinite(partial)||partial<0||partial>1||partial<fraction)return null;
        float progress=baseFraction==1?1:(partial-baseFraction)/(1-baseFraction);
        var pose=new Pose(start.yaw()+(end.yaw()-start.yaw())*progress,start.pitch()+(end.pitch()-start.pitch())*progress);
        return new ApplicationPreview(new Application(ticket,pose,partial==1));
    }
    public ApplicationPreview previewBeforeTick(Scope current,long tick,Pose actual){return scope!=null&&valid(current,tick,actual)?new ApplicationPreview(new Application(ticket,end,true)):null;}
    public Optional<Application> frame(Scope current,long tick,float partial,Pose actual) {
        // Tick-HEAD already applied this endpoint. An idle renderer must not consume its
        // receipt before the originating source service can dispatch its guarded callback.
        if(scope==null){validate(current,tick,actual);return Optional.empty();}
        if(!valid(current,tick,actual)||!Float.isFinite(partial)||partial<0||partial>1||partial<fraction) {
            if(scope!=null||completedScope!=null){epoch++;clear();}return Optional.empty();
        }
        fraction=partial;
        float progress=baseFraction==1?1:(partial-baseFraction)/(1-baseFraction);
        applied=new Pose(start.yaw()+(end.yaw()-start.yaw())*progress,start.pitch()+(end.pitch()-start.pitch())*progress);
        if(partial==1){completedTicket=ticket;completedScope=scope;}
        return Optional.of(new Application(ticket,applied,partial==1));
    }
    /** Source tick-HEAD restore: apply the preceding planned endpoint before native tick consumers. */
    public Optional<Application> beforeTick(Scope current,long tick,Pose actual) {
        if(scope==null){validate(current,tick,actual);return Optional.empty();} // Retain a consumed receipt; never replay its pose.
        if(!valid(current,tick,actual)) { if(scope!=null||completedScope!=null){epoch++;clear();}return Optional.empty(); }
        applied=end;completedTicket=ticket;completedScope=scope;
        var result=new Application(ticket,end,true);
        scope=null;start=end=null;fraction=baseFraction=0; // Consumed; an unrenewed frame cannot rewind it.
        return Optional.of(result);
    }
    public Pose requested(Scope current,long tick,Pose actual) {
        return valid(current,tick,actual)?end:actual;
    }
    public boolean completed(Scope current,long ticket) { return ticket>0&&completedTicket==ticket&&completedScope!=null&&completedScope.same(current); }
    /** Private read-only actual trajectory state. This observes a real plan and grants no gameplay authority. */
    public static final class QueuedRead {
        private final OwnedRotationTrajectory issuer;private final Scope scope;private final Pose endpoint;
        private final long tick,ticket,epoch;private final boolean pending;
        private QueuedRead(OwnedRotationTrajectory issuer,Scope scope,Pose endpoint,long tick,long ticket,long epoch,boolean pending){
            this.issuer=issuer;this.scope=scope;this.endpoint=endpoint;this.tick=tick;this.ticket=ticket;this.epoch=epoch;this.pending=pending;}
        public Scope scopeForObservation(){return scope;}
        public Pose endpoint(){return endpoint;}public boolean pending(){return pending;}
        /** Continuation of THIS authentic original read only, after actual owned tick-HEAD consumption. */
        public boolean appliedCurrent(long nativeTick,Pose actual){
            if(actual==null||tick==Long.MAX_VALUE||nativeTick!=tick+1||issuer.epoch!=epoch||issuer.serial!=ticket||ticket<=0
                ||issuer.scope!=null||issuer.completedScope==null||!issuer.completedScope.same(scope)||issuer.completedTicket!=ticket
                ||issuer.applied!=endpoint)return false;
            var control=issuer.controlSnapshot();return control!=null&&control.ticket()==ticket&&control.epoch()==epoch
                &&control.current()&&control.poseCurrent(actual);
        }

        public boolean current(Pose actual){
            var now=issuer.readQueued(scope,tick,actual,ticket,epoch);
            return now!=null&&now.pending==pending&&samePose(now.endpoint,endpoint);
        }
    }
    /** No frame/beforeTick/plan/validate call, serial change, scope supplier or mutation occurs. */
    public QueuedRead readQueued(Scope expected,long nativeTick,Pose actual,long actualOwnedTicket,long actualEpoch){
        if(actualOwnedTicket<=0||actualOwnedTicket!=serial||actualEpoch!=epoch||expected==null||actual==null)return null;
        if(scope!=null){
            if(ticket!=actualOwnedTicket||!valid(expected,nativeTick,actual))return null;
            return new QueuedRead(this,expected,end,nativeTick,actualOwnedTicket,actualEpoch,true);
        }
        if(completedScope!=null&&completedScope.same(expected)&&completedTicket==actualOwnedTicket&&samePose(actual,applied))
            return new QueuedRead(this,expected,applied,nativeTick,actualOwnedTicket,actualEpoch,false);
        return null;
    }
    public boolean active() { return scope!=null; }
    public void validate(Scope current,long tick,Pose actual) {
        if(scope!=null&&!valid(current,tick,actual)
                ||scope==null&&completedScope!=null&&(!completedScope.same(current)||!samePose(actual,applied)))cancel();
    }
    /** Revocation invalidates receipts and does not restore any historical angle. */
    public void cancel() {epoch++;clear();}
    /** Monotonic actual plan serial; cancellation/consumption never fabricates a new publication. */
    public long publicationSerial(){return serial;}
    public long epoch() {return epoch;}
    /** Exact private stored control, separate from public diagnostic operands. Not gameplay authority. */
    public static final class ControlSnapshot {
        private final OwnedRotationTrajectory trajectory;private final Scope scope;private final Pose applied;private final long epoch,ticket;
        private ControlSnapshot(OwnedRotationTrajectory trajectory,Scope scope,Pose applied,long epoch,long ticket){this.trajectory=trajectory;this.scope=scope;this.applied=applied;this.epoch=epoch;this.ticket=ticket;}
        public Scope scope(){return scope;}public long epoch(){return epoch;}public long ticket(){return ticket;}
        public boolean poseCurrent(Pose actual){return samePose(actual,applied);}
        public boolean current(){var t=trajectory;return t.epoch==epoch&&t.serial==ticket&&ticket>0&&t.applied==applied
            &&(t.scope==scope&&t.ticket==ticket||t.scope==null&&t.completedScope==scope&&t.completedTicket==ticket);}
    }
    public ControlSnapshot controlSnapshot(){var own=scope!=null?scope:completedScope;long actualTicket=scope!=null?ticket:completedTicket;
        return own==null||applied==null||actualTicket<=0||actualTicket!=serial||epoch==Long.MAX_VALUE?null:new ControlSnapshot(this,own,applied,epoch,actualTicket);}
    /** Empty after an actual revocation; serial is only a veto paired with the private predecessor. */
    public boolean emptyAt(long exactEpoch,long exactSerial){return epoch==exactEpoch&&serial==exactSerial&&scope==null&&completedScope==null&&ticket==0&&completedTicket==0&&applied==null;}
    /** Cached diagnostic operands only. This snapshot grants no publication/cleanup permission. */
    public record DebugSnapshot(Scope scope,Scope completedScope,long tick,long ticket,long completedTicket,long epoch,long serial){}
    public DebugSnapshot debugSnapshot(){return new DebugSnapshot(scope,completedScope,tick,ticket,completedTicket,epoch,serial);}
    private boolean valid(Scope current,long tick,Pose actual) {
        return scope!=null&&scope.same(current)&&this.tick==tick&&samePose(actual,applied);
    }
    private static boolean samePose(Pose left,Pose right) {
        return right!=null&&Math.abs(SourceRotationStepper.wrap(left.yaw()-right.yaw()))<.001
                &&Math.abs(left.pitch()-right.pitch())<.001;
    }
    private void clear() { scope=completedScope=null;start=end=applied=null;fraction=baseFraction=0;completedTicket=0;ticket=0; }
}
