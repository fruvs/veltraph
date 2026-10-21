package dev.veltraph.client.combat.ghost;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** Actual source fallback pair producer, issued only from the current failed ordinary SEARCH job before its callback. */
public final class McGhostSourceFallbackSearch {
    public enum State { CAPTURING, READY, REVOKED }
    public static final class Pair {
        private final McGhostSourceFallbackSearch producer;private final GhostSourceFallbackAnchors.Candidate candidate;private final McGhostSourceCoordinatePipeline.Path path;
        private Pair(McGhostSourceFallbackSearch p,GhostSourceFallbackAnchors.Candidate c,McGhostSourceCoordinatePipeline.Path path){producer=p;candidate=c;this.path=path;}
        public Cell anchor(){return candidate.anchor();}public Object sourcePathIdentity(){return path.sourceIdentity();}
        /** Cached private produced worker evidence; never calls current() or resolves the native scope. */
        public GhostSourceCoordinateGraphEvidence cachedGraphEvidence(){return path.cachedGraphEvidence();}
        boolean current(){return producer.state==State.READY&&producer.selected==this&&producer.scopeCurrent()&&path.current();}
        McGhostSourceCoordinateCursor cursor(){if(!current())throw new IllegalStateException("Captured actual fallback pair revoked");return new McGhostSourceCoordinateCursor(producer.client,path,producer::scopeCurrent,producer.pipelines.get(producer.candidates.indexOf(candidate)));}
    }
    private final Minecraft client;private final GhostGrindModule parent;private final GhostSourceOwnedParentArea.Stamp stamp;
    private final Object originalChild;private final GhostSourceJobCameraCustody camera;
    private final GhostSourceFallbackScopeFacts parentFacts;
    private final java.util.function.BooleanSupplier registered;
    private final boolean jumpBoost,smoothing,tailVeto;private final int rough;private final String policy;private final long developerRevision;
    private final Cell capturedStart;private final List<GhostSourceFallbackAnchors.Candidate> candidates;private final java.util.ArrayList<McGhostSourceCoordinatePipeline> pipelines=new java.util.ArrayList<>();
    private final GhostSourceScopeCosts scopeCosts=new GhostSourceScopeCosts(64);
    public GhostSourceScopeCosts.Evidence cachedScopeCosts(){return scopeCosts.cached();}
    private State state=State.CAPTURING;private Pair selected;private int next;private McGhostSourceLeavingChild namedLeavingChild;private final GhostSourceNamedChildHandoff childHandoff;
    private McGhostSourceFallbackSearch(Minecraft client,GhostSourceWalkJob failed,GhostSourceFallbackLifetime lifetime,java.util.function.BooleanSupplier registered){
        this.client=client;parent=failed.parent();this.registered=registered;stamp=failed.issuedReceipt().scope().parent();originalChild=failed.sourceChildIdentity();parentFacts=new GhostSourceFallbackScopeFacts(stamp,parent.sourceParentAreaReceipt(parent).orElseThrow(()->new IllegalStateException("Actual construction parent area receipt required")));childHandoff=new GhostSourceNamedChildHandoff(originalChild);
        camera=new GhostSourceJobCameraCustody(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
        var c=VeltraphBootstrap.config();jumpBoost=client.player.getEffect(MobEffects.JUMP_BOOST)!=null;smoothing=c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED);rough=c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE);tailVeto=c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS);policy=c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS);developerRevision=GhostSourceGraphDeveloperAreaOverride.runtime().revision();
        // Source captures the player's exact position once. Filter/sort use those doubles;
        // Graph consumes the block conversion of that same captured point.
        var player=client.player.position();var capturedPosition=new GhostSourceFallbackAnchors.Position(player.x,player.y,player.z);
        capturedStart=capturedPosition.blockCell();
        candidates=GhostSourceFallbackAnchors.capture(capturedPosition,lifetime.lastAnchor(),(a,b)->java.util.concurrent.ThreadLocalRandom.current().nextInt(a,b));
        // Pipelines start only after parent stores this issued producer, so scope does not silently rebase.
    }
    static McGhostSourceFallbackSearch issue(Minecraft client,GhostSourceWalkJob failed,GhostSourceFallbackLifetime lifetime,java.util.function.BooleanSupplier registered){
        if(client==null||!client.isSameThread()||failed==null||failed.state()!=GhostSourceWalkJob.State.TERMINAL||failed.completedPath()!=null||!failed.issuedReceipt().scopeCurrent())throw new IllegalStateException("Actual failed ordinary source result required");
        return new McGhostSourceFallbackSearch(client,failed,lifetime,registered);
    }
    void begin(){if(state!=State.CAPTURING||!scopeCurrent()||!pipelines.isEmpty())throw new IllegalStateException("Actual registered fallback producer required");
        for(var c:candidates)pipelines.add(McGhostSourceCoordinatePipeline.begin(client,this::scopeCurrent,capturedStart,c.endpoint(),400,jumpBoost,smoothing,rough,tailVeto,policy));}
    public State state(){return state;}
    /** Actual cached attempted graphs, including no-path results; no native access or scope query. */
    public List<GhostSourceCoordinateGraphEvidence> cachedCandidateGraphEvidence(){return pipelines.stream().map(McGhostSourceCoordinatePipeline::cachedGraphEvidence).toList();}
    public record CandidateDiagnostic(int index,GhostSourceFallbackAnchors.Candidate candidate,McGhostSourceCoordinatePipeline.Diagnostic pipeline) {}
    public List<CandidateDiagnostic> cachedCandidateDiagnostics(){var result=new java.util.ArrayList<CandidateDiagnostic>();for(int i=0;i<pipelines.size();i++)result.add(new CandidateDiagnostic(i,candidates.get(i),pipelines.get(i).cachedDiagnostic()));return List.copyOf(result);}
    public Pair capturedPair(){if(state!=State.READY||!scopeCurrent())throw new IllegalStateException("Fallback pair not ready/current");return selected;}
    boolean scopeCurrent(){var previous=GhostSourceScopeCosts.attachSampled(scopeCosts);long began=GhostSourceScopeCosts.begin(GhostSourceScopeCosts.Part.FULL_SUPPLIER);
        try{return profiledScopeCurrent();}finally{GhostSourceScopeCosts.end(GhostSourceScopeCosts.Part.FULL_SUPPLIER,began);GhostSourceScopeCosts.restore(previous);}}
    private boolean profiledScopeCurrent(){
        if(!client.isSameThread()||!GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.REGISTRATION,registered))return false;var actual=GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.PARENT_STAMP_FIRST,()->parent.sourceParentAreaStamp(parent)).orElse(null);if(actual==null||!stamp.same(actual))return false;
        var observedChild=GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.CHILD_GETTER,()->parent.sourceChildFromValidatedStamp(actual));
        if(!childCurrent(observedChild))return false;
        var mm=VeltraphBootstrap.moduleManager();var c=VeltraphBootstrap.config();
        return mm!=null&&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.INPUT_OWNER,mm::inputOwner).orElse(null)==parent&&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.ENABLED_CONTROLLERS_SIZE,mm::enabledControllerCount)==1&&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.ENABLED_CONTROLLERS_FIRST,mm::firstEnabledController)==parent
            &&camera.current(GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.CAMERA_OWNER,()->dev.veltraph.client.service.mc.McOwnedRotation.authority(client)),GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.CAMERA_EPOCH,()->dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)))
            &&GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.AREA_CURRENT,()->parentFacts.areaCurrent(actual))
            &&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.DEVELOPER_REVISION,()->GhostSourceGraphDeveloperAreaOverride.runtime().revision())==developerRevision&&GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.DEVELOPER_SELECTION,()->GhostSourceGraphDeveloperAreaOverride.runtime().selected().isEmpty())
            &&(GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.NATIVE_JUMP_FLAG,()->client.player.getEffect(MobEffects.JUMP_BOOST))!=null)==jumpBoost&&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.WALK_SETTING,()->c.get(GhostGrindConfig.GHOST_PATH_SMOOTHING_ENABLED))==smoothing
            &&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.ROUGH_SETTING,()->c.get(GhostGrindConfig.GHOST_PATH_SHORTCUT_DISTANCE))==rough&&GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.TAIL_SETTING,()->c.get(GhostGrindConfig.GHOST_PATH_KEEP_FINE_ADJUSTMENTS))==tailVeto&&java.util.Objects.equals(GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.FORBIDDEN_SETTING,()->c.get(GhostGrindConfig.GHOST_WALK_FORBIDDEN_CELLS)),policy)
            &&finalFactsCurrent(observedChild);
    }
    private boolean finalFactsCurrent(Object observedChild){
        return parentFacts.finalCheck(()->GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.FINAL_REGISTRATION,registered),
            ()->GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.FINAL_PARENT_STAMP,()->parent.sourceParentAreaStamp(parent)).orElse(null),
            fresh->GhostSourceScopeCosts.value(GhostSourceScopeCosts.Part.FINAL_FACTS,()->parent.sourceChildFromValidatedStamp(fresh)),observedChild);
    }
    /** One explicit named child transition; never refreshes stamp, camera custody or selected pair. Call before parent publishes this exact child. */
    boolean authorizeSixthChildHandoff(Pair actualPair,McGhostSourceLeavingChild actualChild){
        if(namedLeavingChild!=null||state!=State.READY||actualPair!=selected||actualPair!=null&&!actualPair.current()
                ||actualChild==null||!scopeCurrent()||!actualChild.handoffMatches(parent,stamp,camera))return false;
        if(!childHandoff.authorize(parent.sourceNativeChildIdentity(parent),actualChild))return false;namedLeavingChild=actualChild;return true;
    }
    private boolean childCurrent(Object actual){return GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.CHILD_HANDOFF,()->childHandoff.current(actual))&&(actual!=namedLeavingChild||GhostSourceScopeCosts.flag(GhostSourceScopeCosts.Part.NAMED_CHILD_CURRENT,()->namedLeavingChild.current()));}
    void pump(){if(state!=State.CAPTURING)return;if(!scopeCurrent()){revoke();return;}long began=System.nanoTime();
        for(int i=0;i<pipelines.size();i++){long budget=4_000_000L-(System.nanoTime()-began);if(budget<=0)break;pipelines.get((next+i)%pipelines.size()).pump(budget);}if(!pipelines.isEmpty())next=(next+1)%pipelines.size();
        if(pipelines.stream().anyMatch(p->p.state()==McGhostSourceCoordinatePipeline.State.REVOKED)){revoke();return;}
        if(pipelines.stream().allMatch(p->p.state()==McGhostSourceCoordinatePipeline.State.READY)){var successes=new java.util.ArrayList<Pair>();for(int i=0;i<pipelines.size();i++){var path=pipelines.get(i).path();if(path!=null)successes.add(new Pair(this,candidates.get(i),path));}
            selected=GhostSourceFallbackAnchors.select(successes,v->v.candidate,n->java.util.concurrent.ThreadLocalRandom.current().nextInt(n));state=State.READY;}}
    void revoke(){state=State.REVOKED;pipelines.forEach(McGhostSourceCoordinatePipeline::revoke);}
}
