package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseCapturedMenus.*;

/** Advisory local offsets only. No server deadline, plant transition or action permission is produced. */
public final class GreenhouseInspectionEstimates {
    public enum Timer { GROWTH, WATER, DECAY }
    public enum State { FRESH, STALE, REVOKED, CLOCK_INVALID }
    public enum ClockBasis { LOCAL_OBSERVATION_MILLIS, UNKNOWN }
    public enum Timing { PENDING, INSPECTION_DUE, UNKNOWN, OBSERVED_DEAD }
    /** Absent scope fields remain explicitly unknown; this record is not a physical plant binding. */
    public record Scope(String profile,String plot,String dimension,Optional<GreenhouseModel.Cell> target,ClockBasis clockBasis) {
        public Scope {profile=Objects.requireNonNull(profile);plot=Objects.requireNonNull(plot);dimension=Objects.requireNonNull(dimension);target=Objects.requireNonNull(target);Objects.requireNonNull(clockBasis);}
        public static Scope unbound(){return new Scope("","","",Optional.empty(),ClockBasis.LOCAL_OBSERVATION_MILLIS);}
        public String summary(){return "profile="+known(profile)+", plot="+known(plot)+", dimension="+known(dimension)+", target="+target.map(Object::toString).orElse("UNKNOWN unbound");}
        private static String known(String value){return value.isBlank()?"UNKNOWN unbound":value;}
    }
    public record Estimate(Timer timer,Timing timing,Optional<Duration> observedDuration,
                           OptionalLong estimatedAtLocalMillis,OptionalLong remainingMillis,String reason) {}
    public record View(State state,Scope scope,Optional<Diagnostics> observation,Map<Timer,Estimate> estimates,
                       OptionalLong earliestInspectionAtLocalMillis,String reason) {
        public View {estimates=Map.copyOf(estimates);}
        public boolean authorizesAction(){return false;}
        public String summary(){
            StringBuilder text=new StringBuilder("ESTIMATE (local observation clock; ").append(state).append("): ");
            for(Timer timer:Timer.values()){
                if(timer!=Timer.GROWTH)text.append("; ");
                Estimate estimate=estimates.get(timer);text.append(timer.name().toLowerCase(Locale.ROOT)).append('=');
                switch(estimate.timing()){
                    case PENDING -> text.append(estimate.remainingMillis().orElseThrow()).append("ms until inspection estimate");
                    case INSPECTION_DUE -> text.append("inspection due; refresh actual plant state");
                    case OBSERVED_DEAD -> text.append("not applicable to observed DEAD crop");
                    case UNKNOWN -> text.append("UNKNOWN (").append(estimate.reason()).append(')');
                }
            }
            observation.ifPresent(d->text.append(" | observed local millis=").append(d.source().observedAtMillis())
                .append(", crop=").append(d.definition().name()).append(", menu=").append(d.source().title())
                .append(", container=").append(d.source().containerId()).append(", revision=").append(d.source().revision())
                .append(", provenance=").append(d.source().provenance()));
            return text.append(" | ").append(scope.summary()).append(" | ").append(reason)
                .append(" | no server deadline or action authority").toString();
        }
    }
    private record Identity(Diagnostics diagnostic,Scope scope) {}
    private Identity current;
    private long lastAcceptedObservation=-1,lastRead=Long.MIN_VALUE;
    private boolean clockInvalid;
    private String invalidation="No current diagnostic observation";

    /** A replacement must be newer. Re-reading the same immutable observation does not reset its clock. */
    public void observe(Diagnostics diagnostic,Scope scope){
        Objects.requireNonNull(scope);
        if(diagnostic==null){invalidate("Diagnostic menu replaced or unavailable");return;}
        Identity next=new Identity(diagnostic,scope);
        if(next.equals(current))return;
        Source source=diagnostic.source();
        if(source==null||source.observedAtMillis()<0||source.provenance()==null||source.provenance().isBlank()
                ||!source.equals(diagnostic.definition().source())||source.observedAtMillis()<=lastAcceptedObservation){
            invalidate("Replacement requires a strictly newer diagnostic with original provenance");return;
        }
        current=next;lastAcceptedObservation=source.observedAtMillis();lastRead=Long.MIN_VALUE;
        clockInvalid=false;invalidation="";
    }
    public void invalidate(String reason){current=null;lastRead=Long.MIN_VALUE;clockInvalid=false;invalidation=Objects.requireNonNull(reason);}

    /** The caller uses the same local millisecond basis that stamped the native menu observation. */
    public View view(long now,long freshnessMillis){
        if(current==null)return unavailable(State.REVOKED,Scope.unbound(),Optional.empty(),invalidation);
        Diagnostics diagnostic=current.diagnostic();Scope scope=current.scope();long at=diagnostic.source().observedAtMillis();
        if(now<at||now<lastRead)clockInvalid=true;
        lastRead=Math.max(lastRead,now);
        if(clockInvalid)return unavailable(State.CLOCK_INVALID,scope,Optional.of(diagnostic),"Local clock rolled back; obtain a newer diagnostic");
        if(scope.clockBasis()!=ClockBasis.LOCAL_OBSERVATION_MILLIS)
            return unavailable(State.CLOCK_INVALID,scope,Optional.of(diagnostic),"Observation clock basis is unknown");
        boolean stale=freshnessMillis<1||now-at>freshnessMillis;
        var estimates=new EnumMap<Timer,Estimate>(Timer.class);long earliest=Long.MAX_VALUE;boolean known=false;
        for(Timer timer:Timer.values()){
            Optional<Duration> duration=switch(timer){case GROWTH->diagnostic.nextStage();case WATER->diagnostic.waterRemaining();case DECAY->diagnostic.decay();};
            Estimate estimate=estimate(timer,duration,diagnostic,now);estimates.put(timer,estimate);
            if(estimate.estimatedAtLocalMillis().isPresent()){earliest=Math.min(earliest,estimate.estimatedAtLocalMillis().getAsLong());known=true;}
        }
        return new View(stale?State.STALE:State.FRESH,scope,Optional.of(diagnostic),estimates,
            known?OptionalLong.of(earliest):OptionalLong.empty(),stale?"Original observation is stale; estimates schedule inspection only":"Original remaining durations retained independently; inspect before care");
    }
    private Estimate estimate(Timer timer,Optional<Duration> value,Diagnostics diagnostic,long now){
        if(diagnostic.status()==Status.DEAD)return new Estimate(timer,Timing.OBSERVED_DEAD,value,OptionalLong.empty(),OptionalLong.empty(),"Actual menu reports DEAD; no readiness inferred");
        if(diagnostic.status()!=Status.GROWING)return unknown(timer,value,"Plant status is not recognized");
        if(value.isEmpty())return unknown(timer,value,"Remaining duration was absent or unparseable");
        Duration duration=value.get();String prefix=switch(timer){case GROWTH->"Next Stage: ";case WATER->"Lasts for: ";case DECAY->"Decay: ";};
        int slot=timer==Timer.WATER?21:20;
        if(!diagnostic.source().equals(duration.source())||duration.slot()!=slot||!prefix.equals(duration.prefix()))
            return unknown(timer,value,"Duration provenance differs from this diagnostic field");
        var parsed=GreenhouseCapturedMenus.parseDuration(duration.text(),duration.source(),slot,prefix);
        if(parsed.isEmpty()||parsed.get().seconds()!=duration.seconds())return unknown(timer,value,"Duration unit or value is unknown");
        try{
            long due=Math.addExact(diagnostic.source().observedAtMillis(),Math.multiplyExact(duration.seconds(),1000L));
            long remaining=due<=now?0:Math.subtractExact(due,now);
            return new Estimate(timer,due<=now?Timing.INSPECTION_DUE:Timing.PENDING,value,OptionalLong.of(due),OptionalLong.of(remaining),"Local offset from original remaining duration");
        }catch(ArithmeticException invalid){return unknown(timer,value,"Local duration estimate exceeds supported range");}
    }
    private static Estimate unknown(Timer timer,Optional<Duration> value,String reason){return new Estimate(timer,Timing.UNKNOWN,value,OptionalLong.empty(),OptionalLong.empty(),reason);}
    private static View unavailable(State state,Scope scope,Optional<Diagnostics> diagnostic,String reason){
        var estimates=new EnumMap<Timer,Estimate>(Timer.class);
        for(Timer timer:Timer.values())estimates.put(timer,unknown(timer,Optional.empty(),reason));
        return new View(state,scope,diagnostic,estimates,OptionalLong.empty(),reason);
    }
}
