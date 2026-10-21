package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Objects;
import net.minecraft.world.entity.monster.Creeper;

/** Typed actual SEARCH job with bounded native capture, pure worker, private result provenance and current geometry guards. */
public final class GhostSourceWalkJob {
    public enum State { CREATED, CAPTURING, RESULTS_READY, TERMINAL, REVOKED }
    private State state=State.CREATED;private final java.util.LinkedHashMap<Creeper,McGhostSourceWalkPipeline> pipelines=new java.util.LinkedHashMap<>();
    private final java.util.IdentityHashMap<Creeper,java.util.UUID> diagnosticCandidateIds=new java.util.IdentityHashMap<>();
    private McGhostSourceWalkPipeline.ProducedPath completed;private int nextPump;
    private final GhostGrindModule parent;
    private final McGhostSourceNativeCohortSnapshot cohort;
    private final Object child;
    private final List<McGhostSourceNativeCohortSnapshot.Body> candidates;
    private GhostSourceIssuedSearchJob.Receipt receipt;
    private GhostSourceWalkJob(GhostGrindModule parent,McGhostSourceNativeCohortSnapshot cohort){
        this.parent=Objects.requireNonNull(parent);this.cohort=Objects.requireNonNull(cohort);
        if(!cohort.current())throw new IllegalStateException("Actual current owned cohort required");
        child=cohort.sourceChildIdentity();
        if(child==null||parent.sourceNativeChildIdentity(parent)!=child)throw new IllegalStateException("Actual canonical source SEARCH child required");
        // Independent fresh-per-comparison source range; migration RNG stream is explicitly qualified.
        candidates=cohort.ranked(()->java.util.concurrent.ThreadLocalRandom.current().nextDouble(1,30));
        if(!cohort.current())throw new IllegalStateException("Source cohort changed during job issuance");
    }
    static GhostSourceWalkJob issue(GhostGrindModule parent,McGhostSourceNativeCohortSnapshot cohort){
        var job=new GhostSourceWalkJob(parent,cohort);job.receipt=parent.sourceRegisterWalkJob(job);
        if(job.receipt==null||!job.receipt.scopeCurrent())throw new IllegalStateException("Actual read-only worker registration failed");return job;
    }
    public GhostSourceIssuedSearchJob.Receipt issuedReceipt(){return receipt;}
    public GhostGrindModule parent(){return parent;}
    public McGhostSourceNativeCohortSnapshot issuedCohort(){return cohort;}
    public Object sourceChildIdentity(){return child;}
    public List<McGhostSourceNativeCohortSnapshot.Body> candidateBodies(){return candidates;}
    /** Until actual immutable terrain/standing/cost capture seals a running worker, no caller-created WALK path is accepted. */
    public boolean ownsCompletedResult(Creeper actualTarget,GhostSourcePathJobTransaction.WalkPath actualPath){
        return receipt!=null&&receipt.scopeCurrent()&&state==State.TERMINAL&&completed!=null&&completed.ownedBy(this)&&completed.target()==actualTarget&&completed.path()==actualPath;
    }
    /** Actual sealed terrain mutations, fresh protected-actor ray and issued-job scope guard remaining geometry. */
    public boolean remainingGeometryCurrent(net.minecraft.client.Minecraft client,Creeper actualTarget,GhostSourcePathJobTransaction.WalkPath actualPath){
        return client!=null&&client.isSameThread()&&ownsCompletedResult(actualTarget,actualPath)&&receipt.scopeCurrent()
                &&pipelines.get(actualTarget).remainingGeometryCurrent();
    }
    public record CandidateDiagnostic(java.util.UUID target,McGhostSourceWalkPipeline.Diagnostic pipeline) {}
    public java.util.List<CandidateDiagnostic> cachedCandidateDiagnostics(){return pipelines.entrySet().stream().map(e->new CandidateDiagnostic(diagnosticCandidateIds.get(e.getKey()),e.getValue().cachedDiagnostic())).toList();}
    public State state(){return state;}
    /** Actual handler starts read-only candidates, without assigning parent target or committed SEARCH state. */
    void beginNativeCapture(net.minecraft.client.Minecraft client){
        if(state!=State.CREATED||!receipt.scopeCurrent()||!cohort.current())throw new IllegalStateException("Current actual job can start capture only once");
        for(var body:candidates){diagnosticCandidateIds.put(body.entity(),body.candidate().id());pipelines.put(body.entity(),McGhostSourceWalkPipeline.begin(client,this,body.entity()));}state=State.CAPTURING;
    }
    void pumpNativeCapture(net.minecraft.client.Minecraft client){
        if(client==null||!client.isSameThread()||!receipt.scopeCurrent()){revoke();return;}
        if(state!=State.CAPTURING)return;var values=java.util.List.copyOf(pipelines.values());long start=System.nanoTime();
        for(int i=0;i<values.size();i++){long remaining=McGhostSourceWalkPipeline.MODERN_CAPTURE_BUDGET_NANOS-(System.nanoTime()-start);if(remaining<=0)break;
            values.get((nextPump+i)%values.size()).pump(remaining);}
        if(!values.isEmpty())nextPump=(nextPump+1)%values.size();
        if(values.stream().anyMatch(p->p.state()==McGhostSourceWalkPipeline.State.FAILED||p.state()==McGhostSourceWalkPipeline.State.REVOKED)){revoke();return;}
        if(values.stream().allMatch(p->p.state()==McGhostSourceWalkPipeline.State.READY))state=State.RESULTS_READY;
    }
    java.util.List<Creeper> readyNoPathTargets(){
        if(state!=State.RESULTS_READY||!receipt.scopeCurrent())throw new IllegalStateException("Actual READY-only source failures required");
        return pipelines.entrySet().stream().filter(e->e.getValue().readyNoPath()).map(java.util.Map.Entry::getKey).toList();
    }
    java.util.List<McGhostSourceWalkPipeline.ProducedPath> generatedPaths(){
        if(state!=State.RESULTS_READY||!receipt.scopeCurrent())throw new IllegalStateException("Actual generated source results not ready");
        return pipelines.values().stream().filter(p->p.state()==McGhostSourceWalkPipeline.State.READY).map(McGhostSourceWalkPipeline::produced).filter(java.util.Objects::nonNull).toList();
    }
    /** Called only after Q's actual native metrics and M2 choose. Every selected source identity must be privately produced here. */
    void finalizeFromMappedSelection(GhostSourcePathResultScoring.Selection actualSelection){
        if(state!=State.RESULTS_READY||actualSelection==null||!actualSelection.observed()||!receipt.scopeCurrent())throw new IllegalStateException("Observed current mapped selection required");
        var paths=generatedPaths();
        if(actualSelection.sorted().size()!=paths.size())throw new IllegalStateException("Actual source result map is incomplete");
        var seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for(var scored:actualSelection.sorted()){var c=scored.candidate();if(!seen.add(c.sourcePathIdentity())||paths.stream().noneMatch(p->p==c.sourcePathIdentity()&&p.target()==c.nativeTarget()&&p.target().getUUID().equals(c.nativeUuid())&&p.path().edges().equals(c.edges())&&c.walkingResult()))throw new IllegalStateException("Caller-created path or foreign target rejected");}
        if(actualSelection.selected()!=null){var c=actualSelection.selected().candidate();completed=paths.stream().filter(p->p==c.sourcePathIdentity()&&p.target()==c.nativeTarget()).findFirst().orElseThrow();
            if(!pipelines.get(completed.target()).remainingGeometryCurrent())throw new IllegalStateException("Generated native geometry changed before selection");}
        state=State.TERMINAL;
    }
    McGhostSourceWalkPipeline.ProducedPath completedPath(){return completed;}
    void revoke(){state=State.REVOKED;pipelines.values().forEach(McGhostSourceWalkPipeline::revoke);}

}
