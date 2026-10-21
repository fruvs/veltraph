package dev.veltraph.client.garden.jacob;

import dev.veltraph.client.game.LocationText;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local source 20-tick contest tracking. No commands, menus, reward inference or remote notifications. */
public final class JacobContestTracker {
    public static final int SAMPLE_TICKS=20;
    public static final String ENDED_MESSAGE="The Farming Contest is over!";
    /** Native identities and the actual selected farm profile; adapters must supply fresh evidence. */
    public record Scope(Object player,Object world,Object connection,String profile){
        public Scope { Objects.requireNonNull(player);Objects.requireNonNull(world);Objects.requireNonNull(connection);Objects.requireNonNull(profile); }
    }
    public record Ended(long contest,JacobContestObservation lastObservation,Reason reason){}
    public enum Reason { HEADER_ENDED, SERVER_MESSAGE }
    private Scope scope;private long lastTick=-1,lastSample=-1,contest;private int callbacks;
    private JacobContestObservation observation=JacobContestObservation.inactive();
    private boolean trapConsumed,waitForAbsentHeader;private Ended ended;
    private boolean same(Scope candidate){return scope!=null&&candidate!=null&&scope.player()==candidate.player()
        &&scope.world()==candidate.world()&&scope.connection()==candidate.connection()&&scope.profile().equals(candidate.profile());}
    /** Root calls once per client tick after resolving its runtime/location ownership. */
    public void tick(Scope candidate,boolean farmingInGarden,List<String> sidebar,long tick){
        if(!farmingInGarden||candidate==null||tick<0){reset();return;}
        if(!same(candidate)||tick<lastTick){reset();scope=candidate;}
        if(tick==lastTick)return;
        lastTick=tick;
        // Source uses ++counter == 20: the first nineteen callbacks cannot create data.
        // Count actual callbacks, never infer twenty invocations from a global tick offset.
        if(++callbacks<SAMPLE_TICKS)return;
        callbacks=0;lastSample=tick;
        var next=JacobContestObservation.read(sidebar);
        if(waitForAbsentHeader){if(!next.active())waitForAbsentHeader=false;observation=JacobContestObservation.inactive();return;}
        if(!next.active()){
            if(observation.active())end(Reason.HEADER_ENDED);
            observation=next;trapConsumed=false;return;
        }
        if(!observation.active())contest++;
        // Source replaces its mutable contest record on every sample, including its trap latch.
        trapConsumed=false;observation=next;
    }
    /** Source case-sensitive marker inside a native non-overlay system message in the owned session. */
    public void message(Scope candidate,String message){
        if(!same(candidate)||!observation.active()||message==null||!LocationText.stripColorCodes(message).contains(ENDED_MESSAGE))return;
        end(Reason.SERVER_MESSAGE);observation=JacobContestObservation.inactive();trapConsumed=true;waitForAbsentHeader=true;
    }
    private void end(Reason reason){ended=new Ended(contest,observation,reason);}
    public JacobContestObservation observation(Scope candidate,long tick){
        return same(candidate)&&lastSample>=0&&tick>=lastSample&&tick-lastSample<SAMPLE_TICKS?observation:JacobContestObservation.inactive();
    }
    /** Source uses a strict remaining-time cutoff and consumes this readiness once per sampled source record.
     * The unreadable native constant remains an explicit adapter policy; -1 never qualifies. */
    public boolean consumeTrapWindow(Scope candidate,long tick,boolean collectTraps,boolean contestOnly,
                                     int readyTraps,long explicitCutoffSeconds){
        var current=observation(candidate,tick);
        if(!collectTraps||!contestOnly||readyTraps<=0||explicitCutoffSeconds<0||trapConsumed||!current.clockKnown()
                ||current.remainingSeconds()>=explicitCutoffSeconds)return false;
        trapConsumed=true;return true;
    }
    public Optional<Ended> takeEnded(){var event=ended;ended=null;return Optional.ofNullable(event);}
    public boolean trapConsumed(){return trapConsumed;}
    public void reset(){scope=null;lastTick=-1;lastSample=-1;callbacks=0;observation=JacobContestObservation.inactive();trapConsumed=false;waitForAbsentHeader=false;ended=null;}
}
