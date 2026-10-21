package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Local ground adapter for source chunk-goal progression; never authorizes unknown terrain. */
public final class LoadedGroundReturnController {
    public static final int SEARCH_NODES=2500,SEARCH_RADIUS=32;
    /** One vanilla chunk of intermediate progress; not an inferred encrypted source constant. */
    public static final double HOP_DISTANCE=16;
    public static final int MAX_CANDIDATE_STARTS=2,MAX_CANDIDATES=64;
    public enum State { IDLE, MOVING, ARRIVED, FAILED, CANCELLED }
    public enum Progress { ACTIVE, ARRIVED, FAILED }
    public record Observation(Object player,Object world,Object connection,Vec3 position,boolean valid,long now) {}
    public interface Driver {
        boolean ready(Vec3 goal);
        List<Vec3> loadedProgressCandidates(Vec3 origin,Vec3 finalGoal);
        boolean start(Vec3 goal,int maxNodes,int radius,double terminalRadius);
        /** Compatibility ground path; native dispatchers additionally receive the genuine selected token. */
        default boolean startSelected(SelectedSegment selected,int maxNodes,int radius,double terminalRadius){
            return start(selected.segmentPosition(),maxNodes,radius,terminalRadius);
        }
        Progress tick();void stop();
    }
    private final Driver driver;private State state=State.IDLE;private Observation origin;private Vec3 target,segment;
    private boolean finalSegment;private double terminalRadius;private long started,last;private int hops;private String reason="Idle";
    private SelectedSegment selected;
    private long selectionSerial;
    private boolean stopping,stopReentered;
    /** Actual controller selection provenance only; it is never a native publication or device permission. */
    public final class SelectedSegment {
        private final Observation atSelection;
        private final Vec3 canonical,chosen;
        private final long serial,originalStart;
        private final double radius;
        private final int attempt;
        private boolean dispatching,returned,failed;
        private SelectedSegment(Observation atSelection,Vec3 chosen,int attempt){
            this.atSelection=atSelection;this.chosen=chosen;this.attempt=attempt;
            canonical=target;originalStart=started;radius=terminalRadius;serial=++selectionSerial;
        }
        public LoadedGroundReturnController controllerIdentity(){return LoadedGroundReturnController.this;}
        public Vec3 canonicalTarget(){return canonical;}public Vec3 segmentPosition(){return chosen;}
        public Observation selectionObservation(){return atSelection;}
        public long originalStartedAt(){return originalStart;}
        public long originalDeadline(){return originalStart>Long.MAX_VALUE-45000?-1:originalStart+45000;}
        public int rankedAttempt(){return attempt;}public int searchNodes(){return SEARCH_NODES;}public int searchRadius(){return SEARCH_RADIUS;}
        public double terminalRadius(){return radius;}public boolean canonicalFinalSegment(){return chosen.equals(canonical);}
        /** Native wrapper must also bind this controller to its exact private Journey/root source. */
        public boolean selectionCurrent(){return selected==this&&!failed&&serial==selectionSerial&&target==canonical&&started==originalStart
            &&Double.doubleToLongBits(terminalRadius)==Double.doubleToLongBits(radius)&&origin!=null
            &&atSelection.player()==origin.player()&&atSelection.world()==origin.world()&&atSelection.connection()==origin.connection()
            &&attempt>=0&&attempt<MAX_CANDIDATE_STARTS&&finite(chosen)&&finite(canonical)&&healthy(atSelection)
            &&(canonicalFinalSegment()?atSelection.position().distanceTo(chosen)<=SEARCH_RADIUS
                :atSelection.position().distanceTo(chosen)<=HOP_DISTANCE&&chosen.distanceTo(canonical)<atSelection.position().distanceTo(canonical))
            &&(dispatching||returned&&state==State.MOVING&&segment==chosen);}
        /** Only provenance during the actual selected driver's retirement; never permits more movement. */
        public boolean cleanupProvenanceCurrent(){return selected==this&&serial==selectionSerial&&target==canonical&&started==originalStart
            &&origin!=null&&atSelection.player()==origin.player()&&atSelection.world()==origin.world()&&atSelection.connection()==origin.connection();}
        public boolean selectingOriginalStart(){return dispatching&&selectionCurrent();}
        public boolean normallyReturnedStart(){return returned&&selectionCurrent();}
        private void retire(){failed=true;dispatching=false;}
    }
    public LoadedGroundReturnController(Driver driver){this.driver=Objects.requireNonNull(driver);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static boolean healthy(Observation o){return o!=null&&o.valid()&&o.player()!=null&&o.world()!=null&&o.connection()!=null&&finite(o.position());}
    public boolean start(Vec3 target,double radius,Observation observation){
        stop();if(!finite(target)||!Double.isFinite(radius)||radius<=0||radius>.35||!healthy(observation))return fail("Exact loaded-return owner or target unavailable");
        this.target=target;terminalRadius=radius;origin=observation;started=last=observation.now();hops=0;return next(observation);
    }
    private boolean next(Observation o){
        double distance=o.position().distanceTo(target);var ranked=new ArrayList<Vec3>();finalSegment=false;
        if(distance<=SEARCH_RADIUS&&driver.ready(target))ranked.add(target);
        var candidates=driver.loadedProgressCandidates(o.position(),target);
        if(candidates!=null)for(int i=0;i<Math.min(candidates.size(),MAX_CANDIDATES);i++){
            var candidate=candidates.get(i);if(finite(candidate)&&candidate.distanceTo(o.position())<=HOP_DISTANCE
                    &&candidate.distanceTo(target)<distance&&driver.ready(candidate)&&!ranked.contains(candidate))ranked.add(candidate);
        }
        ranked.sort(Comparator.comparingDouble((Vec3 p)->p.distanceTo(target)).thenComparingDouble(Vec3::x).thenComparingDouble(Vec3::y).thenComparingDouble(Vec3::z));
        if(ranked.isEmpty())return fail("No loaded supported progress goal within unchanged search budget");
        for(int i=0;i<Math.min(ranked.size(),MAX_CANDIDATE_STARTS);i++){
            var chosen=ranked.get(i);if(!driver.ready(chosen))continue;
            if(selectionSerial==Long.MAX_VALUE)return fail("Loaded selection generation exhausted");
            if(selected!=null)selected.retire();var actual=new SelectedSegment(o,chosen,i);selected=actual;actual.dispatching=true;
            boolean began;
            try{began=driver.startSelected(actual,SEARCH_NODES,SEARCH_RADIUS,terminalRadius);}
            catch(RuntimeException|Error failure){actual.retire();throw failure;}
            finally{actual.dispatching=false;}
            if(!began){actual.retire();stopDriverRetainingCustody();continue;}
            segment=chosen;finalSegment=chosen.equals(target);hops++;state=State.MOVING;actual.returned=true;
            reason=finalSegment?"Walking exact final pose":"Walking loaded progress hop "+hops;return true;
        }
        return fail("Bounded loaded progress candidates were not reachable by ground search");
    }
    public State tick(Observation o){
        if(state!=State.MOVING)return state;
        if(!healthy(o)||o.player()!=origin.player()||o.world()!=origin.world()||o.connection()!=origin.connection())return failState("Loaded return owner/context changed");
        if(o.now()<last||o.now()-started>=45000)return failState("Original total return deadline expired");last=o.now();
        if(!driver.ready(segment))return failState("Current progress goal became unloaded or obstructed");
        var progress=driver.tick();if(progress==Progress.FAILED)return failState("Loaded walking path became blocked");
        if(progress==Progress.ARRIVED){
            if(selected!=null)selected.retire();stopDriverRetainingCustody();if(o.position().distanceTo(segment)>=terminalRadius)return failState("Native walking arrival did not reach the exact segment pose");
            if(finalSegment){if(!driver.ready(target)||o.position().distanceTo(target)>=terminalRadius)return failState("Final original endpoint is not loaded and reached");state=State.ARRIVED;reason="Exact original endpoint reached by loaded walking";}
            else next(o);
        }
        return state;
    }
    private void stopDriverRetainingCustody(){
        if(stopping){stopReentered=true;state=State.FAILED;reason="Loaded cleanup reentered; original custody retained";
            throw new IllegalStateException(reason);}
        stopping=true;stopReentered=false;
        try{
            driver.stop();
            if(stopReentered)throw new IllegalStateException("Loaded cleanup reentered; original custody retained");
        }catch(RuntimeException|Error failure){state=State.FAILED;reason="Loaded cleanup refused; original custody retained";throw failure;}
        finally{stopping=false;}
    }
    private State failState(String why){fail(why);return state;}
    private boolean fail(String why){if(selected!=null)selected.retire();state=State.FAILED;reason=why;stopDriverRetainingCustody();return false;}
    public void stop(){
        if(selected!=null)selected.retire();stopDriverRetainingCustody();
        selected=null;state=State.IDLE;origin=null;target=segment=null;finalSegment=false;reason="Stopped";
    }
    public long observedClockMillis(){return last;}
    public State state(){return state;}public Vec3 segment(){return segment;}public int hops(){return hops;}public String status(){return state+": "+reason;}
}
