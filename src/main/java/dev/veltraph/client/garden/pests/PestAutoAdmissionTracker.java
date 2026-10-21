package dev.veltraph.client.garden.pests;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;

/** Pure observed-parent adapter. Native widgets must independently establish every parent operand. */
public final class PestAutoAdmissionTracker {
    private PestAutoAdmission.Scope scope;
    private PestAutoAdmissionBindings bindings;
    private String generation;
    private int scheduled,completed;
    private long lastTick=-1;
    private boolean generationInvalid,blocked;
    private PestAutoAdmission.Snapshot snapshot;
    private record Batch(PestAutoAdmissionBindings bindings,String generation) {}
    private final Set<String> retired=new HashSet<>();
    public static final class Claim {
        private final PestAutoAdmission.Snapshot snapshot;private final Batch batch;
        private Claim(PestAutoAdmission.Snapshot snapshot,Batch batch){this.snapshot=snapshot;this.batch=batch;}
        public PestAutoAdmissionBindings.Target target(){return batch.bindings().target();}
        public PestAutoAdmission.Snapshot snapshot(){return snapshot;}
        public String generation(){return batch.generation();}
    }
    private Claim claim;
    private String status="Unbound parent observation";

    /** Root supplies fresh case-preserved/color-stripped native rows once per current client tick. */
    public void observe(PestAutoAdmission.Scope candidate,long tick,PestAutoAdmissionBindings contract,List<String> tab,List<String> sidebar) {
        snapshot=null;claim=null;
        if(!valid(candidate)||contract==null||candidate.plot()!=contract.plot()||tick<0||tab==null||sidebar==null){reset();return;}
        if(!same(scope,candidate)||!Objects.equals(bindings,contract)||tick<lastTick){reset();scope=candidate;bindings=contract;}
        // A contradictory same-tick second read cannot replace the one sampled native frame.
        if(tick==lastTick){status="Repeated native frame was rejected";return;}
        lastTick=tick;
        String observedGeneration=marker(contract.batchGeneration(),tab,sidebar);
        if(observedGeneration==null){status="Missing or ambiguous observed batch generation";return;}
        if(retired.contains(observedGeneration)||retired.size()>=256){status="Observed parent batch is retired or retirement capacity is exhausted";return;}
        boolean changed=!observedGeneration.equals(generation);
        var total=total(contract.nativeTotal(),tab,sidebar);
        var entityExclusions=counter(contract.excludedEntities(),tab,sidebar);
        var plotExclusions=counter(contract.excludedPlots(),tab,sidebar);
        var target=counter(contract.scheduledTotal(),tab,sidebar);
        var done=counter(contract.completed(),tab,sidebar);
        var observedBlocked=flag(contract.sourceBlocked(),tab,sidebar);
        if(total.isEmpty()||entityExclusions.isEmpty()||plotExclusions.isEmpty()||target.isEmpty()||done.isEmpty()||observedBlocked==null){status="Missing or ambiguous independent native parent operands";return;}
        var counts=new PestAutoAdmission.Counts(total.getAsInt(),entityExclusions.getAsInt(),plotExclusions.getAsInt(),target.getAsInt(),done.getAsInt());
        if(counts.nativeTotal()<0||counts.excludedEntityEntries()<0||counts.excludedPlotEntries()<0||counts.scheduledTotal()<0||counts.acknowledgedCompletions()<0){status="Invalid signed native parent operands";return;}
        if(changed){generation=observedGeneration;scheduled=counts.scheduledTotal();completed=counts.acknowledgedCompletions();generationInvalid=false;blocked=false;}
        else if(counts.scheduledTotal()<scheduled||counts.acknowledgedCompletions()<completed){generationInvalid=true;status="Parent counters regressed without an observed reset";return;}
        if(generationInvalid){status="Observed batch remains invalid until a fresh reset identity";return;}
        scheduled=counts.scheduledTotal();completed=counts.acknowledgedCompletions();
        // Recovered source blocked flag clears only with parent reset; a false row alone cannot clear it.
        blocked|=observedBlocked;
        snapshot=new PestAutoAdmission.Snapshot(candidate,tick,counts,blocked);
        status="Fresh explicitly bound observed parent batch "+generation;
    }
    public Optional<PestAutoAdmission.Snapshot> snapshot(PestAutoAdmission.Scope current,long tick) {
        return snapshot!=null&&same(scope,current)&&snapshot.tick()==tick?Optional.of(snapshot):Optional.empty();
    }
    public PestAutoAdmissionBindings.Target target(){return bindings==null?null:bindings.target();}
    /** One opaque claim over the exact fresh observed frame; preview alone never retires a batch. */
    public Optional<Claim> claim(PestAutoAdmission.Scope current,long tick,boolean leaveOne){
        if(snapshot(current,tick).filter(observation->PestAutoAdmission.ready(observation,current,tick,leaveOne)).isEmpty())return Optional.empty();
        if(claim==null)claim=new Claim(snapshot,new Batch(bindings,generation));return Optional.of(claim);
    }
    /**
     * Core proves the actual token belongs to its selected registered helper and state is PREPARED.
     * Retirement follows that real reservation and survives cancel/unknown, scope changes and reset.
     */
    public boolean consume(Claim candidate,PestAutoAdmission.Scope current,long tick,FarmMaintenanceLease.Token token,BooleanSupplier exactPreparedReservation){
        if(candidate==null||candidate!=claim||candidate.snapshot!=snapshot||token==null||exactPreparedReservation==null
                ||snapshot(current,tick).isEmpty()||retired.contains(candidate.batch.generation())||!exactPreparedReservation.getAsBoolean())return false;
        retired.add(candidate.batch.generation());snapshot=null;claim=null;status="Observed batch retired after exact prepared farm reservation";return true;
    }
    /**
     * Root calls only after an actual activation attempt was refused, or after cancelling its exact
     * reservation when consumption failed. This records refusal, never a prepared/success receipt.
     * Read-only readiness and claims do not call this method and remain non-consuming.
     */
    public boolean reject(Claim candidate,PestAutoAdmission.Scope current,long tick){
        if(candidate==null||candidate!=claim||candidate.snapshot!=snapshot||snapshot(current,tick).isEmpty()
                ||retired.contains(candidate.batch.generation()))return false;
        retired.add(candidate.batch.generation());snapshot=null;claim=null;status="Observed batch retired after refused activation attempt";return true;
    }
    public String status(){return status;}
    public void reset(){scope=null;bindings=null;generation=null;scheduled=completed=0;lastTick=-1;generationInvalid=blocked=false;snapshot=null;claim=null;status="Unbound parent observation";}
    private static boolean valid(PestAutoAdmission.Scope candidate){return candidate!=null&&candidate.player()!=null&&candidate.world()!=null&&candidate.connection()!=null&&candidate.profile()!=null&&!candidate.profile().isBlank()&&candidate.plot()>=1&&candidate.plot()<=24;}
    private static boolean same(PestAutoAdmission.Scope first,PestAutoAdmission.Scope second){return first!=null&&second!=null&&first.player()==second.player()&&first.world()==second.world()&&first.connection()==second.connection()&&first.profile().equals(second.profile())&&first.plot()==second.plot();}
    private static List<String> rows(PestAutoAdmissionBindings.Source source,List<String> tab,List<String> sidebar){return source==PestAutoAdmissionBindings.Source.TAB?tab:sidebar;}
    private static OptionalInt counter(PestAutoAdmissionBindings.Counter binding,List<String> tab,List<String> sidebar){
        var matches=rows(binding.source(),tab,sidebar).stream().filter(Objects::nonNull).filter(row->row.startsWith(binding.prefix())).toList();
        if(matches.size()!=1)return OptionalInt.empty();try{return OptionalInt.of(Integer.parseInt(matches.getFirst().substring(binding.prefix().length())));}catch(NumberFormatException invalid){return OptionalInt.empty();}
    }
    private static String marker(PestAutoAdmissionBindings.Marker binding,List<String> tab,List<String> sidebar){
        var matches=rows(binding.source(),tab,sidebar).stream().filter(Objects::nonNull).filter(row->row.startsWith(binding.prefix())).toList();
        if(matches.size()!=1)return null;String value=matches.getFirst().substring(binding.prefix().length());return value.isBlank()?null:value;
    }
    private static Boolean flag(PestAutoAdmissionBindings.Flag binding,List<String> tab,List<String> sidebar){
        var matches=rows(binding.source(),tab,sidebar).stream().filter(Objects::nonNull).filter(row->row.equals(binding.trueRow())||row.equals(binding.falseRow())).toList();
        return matches.size()==1?matches.getFirst().equals(binding.trueRow()):null;
    }
    private static OptionalInt total(PestAutoAdmissionSignals.Bindings binding,List<String> tab,List<String> sidebar){
        long matchingTab=tab.stream().filter(Objects::nonNull).filter(row->row.startsWith(binding.tabPrefix())&&row.substring(row.lastIndexOf(' ')+1).startsWith("x")).count();
        if(matchingTab>1||matchingTab==0&&sidebar.stream().filter(Objects::nonNull).filter(row->row.startsWith(binding.sidebarPrefix())).count()>1)return OptionalInt.empty();
        return PestAutoAdmissionSignals.read(binding,tab,sidebar);
    }
}
