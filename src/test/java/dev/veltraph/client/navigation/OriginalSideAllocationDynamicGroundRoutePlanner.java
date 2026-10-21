package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Modern local journey variety. The existing bounded graph remains the fallback authority. */
final class OriginalSideAllocationDynamicGroundRoutePlanner {
    private OriginalSideAllocationDynamicGroundRoutePlanner() {}
    public interface Geometry {
        boolean current();
        boolean standing(Vec3 point);
        /** Complete loaded support and swept body proof; false also represents unknown terrain. */
        boolean sweep(Vec3 from,Vec3 to);
    }
    public enum Outcome { VARIED, ORIGINAL, REVOKED }
    public record Plan(Outcome outcome,List<Vec3> course,String reason,List<Boolean> broadEdges) {
        public Plan {course=List.copyOf(course);broadEdges=List.copyOf(broadEdges);if(broadEdges.size()!=course.size())throw new IllegalArgumentException("Edge provenance size mismatch");}
        public Plan(Outcome outcome,List<Vec3> course,String reason){this(outcome,course,reason,java.util.Collections.nCopies(course.size(),false));}
    }
    /** Shape maximal bounded level windows; preserve every original height-change endpoint. */
    public static Plan planWindows(Vec3 origin,List<Vec3> nodes,long seed,long ordinal,Geometry geometry){
        if(nodes==null||nodes.isEmpty()||nodes.size()>MAX_NODES||!finite(origin)
                ||nodes.stream().anyMatch(p->!finite(p)))return revoked();
        var course=new ArrayList<Vec3>();var broad=new ArrayList<Boolean>();Vec3 from=origin;boolean varied=false;int cursor=0,window=0;
        while(cursor<nodes.size()){
            if(GroundDynamicPlanningDiagnostics.copy(16,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("planWindows.originalWindowCurrentRefused");return revoked();}
            int end=cursor;
            if(Math.abs(nodes.get(cursor).y()-from.y())<.02){
                while(end+1<nodes.size()&&Math.abs(nodes.get(end+1).y()-from.y())<.02
                        &&from.horizontalDistanceTo(nodes.get(end+1))<=MAX_LENGTH)end++;
            }
            var original=nodes.subList(cursor,end+1);
            Plan piece=plan(from,original,seed+window++*0x632be59bd9b4e019L,ordinal,geometry);
            if(piece.outcome()==Outcome.REVOKED)return revoked();
            course.addAll(piece.course());broad.addAll(piece.broadEdges());varied|=piece.outcome()==Outcome.VARIED;
            from=nodes.get(end);cursor=end+1;
            if(course.size()>MAX_NODES)return new Plan(Outcome.ORIGINAL,nodes,"Expanded course exceeds finite bound");
        }
        return GroundDynamicPlanningDiagnostics.copy(17,geometry.current())?new Plan(varied?Outcome.VARIED:Outcome.ORIGINAL,course,
                varied?"Broad supported level windows with original step endpoints":"No complete broad window proof",broad):revoked();
    }
    private static final int MAX_NODES=6000,MAX_CANDIDATES=8;
    private static final double MIN_LENGTH=10,MAX_LENGTH=48,SIDE_MARGIN=.7,MAX_RATIO=1.45;

    /** The journey ordinal cycles four broad choices; one seed is sampled at navigator creation. */
    public static Plan plan(Vec3 origin,List<Vec3> admittedNodes,long sessionSeed,long journeyOrdinal,Geometry geometry) {
        Objects.requireNonNull(geometry);
        if(!finite(origin)||admittedNodes==null||admittedNodes.isEmpty()||admittedNodes.size()>MAX_NODES
                ||admittedNodes.stream().anyMatch(p->!finite(p)))return new Plan(Outcome.REVOKED,List.of(),"Invalid finite route");
        if(GroundDynamicPlanningDiagnostics.copy(18,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("plan.originalEntryCurrentRefused");return revoked();}
        List<Vec3> original=List.copyOf(admittedNodes);
        Vec3 goal=original.getLast();double length=origin.horizontalDistanceTo(goal);
        GroundDynamicPlanningDiagnostics.model(origin,goal,sessionSeed,journeyOrdinal);
        if(GroundDynamicPlanningDiagnostics.copy(19,Math.abs(goal.y()-origin.y())>.02)||GroundDynamicPlanningDiagnostics.copy(20,length<MIN_LENGTH)||GroundDynamicPlanningDiagnostics.copy(21,length>MAX_LENGTH))
            {GroundDynamicPlanningDiagnostics.returned("plan.originalDiscreteShortOrBounded");return new Plan(Outcome.ORIGINAL,original,"Discrete, short or bounded journey");}
        // No second graph search and no per-tick RNG. Full candidate proof precedes publication.
        Random base=new Random(sessionSeed);int phase=(int)Math.floorMod(journeyOrdinal+base.nextInt(4),4);
        // Disjoint broad bands avoid immediate reuse, while a fresh journey mix changes every
        // actual curve. These are clearance candidates, never fixed paths or tick jitter.
        Random random=new Random(mix(sessionSeed+journeyOrdinal*0x9e3779b97f4a7c15L));
        double near=1.25+random.nextDouble()*.25,far=3.3+random.nextDouble()*.5;
        double[] choices={-far,-near,near,far};double skew=(random.nextDouble()-.5)*.6;
        int[] order={phase,(phase+1)%4,(phase+3)%4,(phase+2)%4};
        for(int attempt=0;attempt<MAX_CANDIDATES;attempt++) {
            GroundDynamicPlanningDiagnostics.candidate(attempt);
            if(GroundDynamicPlanningDiagnostics.copy(22,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("plan.originalCandidateCurrentRefused");return revoked();}
            double offset=attempt<4?choices[order[attempt]]:Math.copySign(4.4+random.nextDouble()*.35,choices[order[attempt-4]]);
            List<Vec3> candidate=attempt<4?curve(origin,goal,offset,skew):bypass(origin,goal,offset);
            if(GroundDynamicPlanningDiagnostics.copy(23,length(candidate,origin)>length*MAX_RATIO)){GroundDynamicPlanningDiagnostics.returned("plan.originalCandidateLengthRefused");continue;}
            if(GroundDynamicPlanningDiagnostics.copy(24,certify(origin,candidate,geometry))) {
                if(GroundDynamicPlanningDiagnostics.copy(25,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("plan.originalAfterCertifyCurrentRefused");return revoked();}
                return new Plan(Outcome.VARIED,candidate,attempt<4?"Broad supported curve":"Supported alternate obstacle side",java.util.Collections.nCopies(candidate.size(),true));
            }
        }
        GroundDynamicPlanningDiagnostics.returned("plan.originalAllCandidatesNotCertified");
        return GroundDynamicPlanningDiagnostics.copy(26,geometry.current())?new Plan(Outcome.ORIGINAL,original,"No complete broad route proof"):revoked();
    }
    private static List<Vec3> curve(Vec3 from,Vec3 to,double offset,double skew) {
        double length=from.horizontalDistanceTo(to);int pieces=(int)Math.ceil(length/.6);
        var course=new ArrayList<Vec3>(pieces);
        for(int i=1;i<=pieces;i++) {
            double t=i/(double)pieces,along=t*length,side=0;
            if(along>2&&along<length-2) {
                double u=(along-2)/(length-4),phase=u+skew*u*(1-u);
                side=offset*Math.pow(Math.sin(Math.PI*phase),2);
            }
            course.add(i==pieces?to:side(from,to,t,side));
        }
        return List.copyOf(course);
    }
    /** Two long visible legs select another side of a central obstruction without a new search. */
    private static List<Vec3> bypass(Vec3 from,Vec3 to,double offset) {
        Vec3 a=side(from,to,.3,offset),b=side(from,to,.7,offset);
        var course=new ArrayList<Vec3>();Vec3 previous=from;
        for(Vec3 endpoint:List.of(a,b,to)) {
            int pieces=Math.max(1,(int)Math.ceil(previous.distanceTo(endpoint)/.6));
            for(int i=1;i<=pieces;i++)course.add(i==pieces?endpoint:TravelPath.lerp(previous,endpoint,i/(double)pieces));
            previous=endpoint;
        }
        return List.copyOf(course);
    }
    private static Vec3 side(Vec3 from,Vec3 to,double t,double offset) {
        double length=from.horizontalDistanceTo(to);Vec3 p=TravelPath.lerp(from,to,t);
        return new Vec3(p.x()-(to.z()-from.z())/length*offset,p.y(),p.z()+(to.x()-from.x())/length*offset);
    }
    private static boolean certify(Vec3 origin,List<Vec3> course,Geometry geometry) {
        GroundDynamicPlanningDiagnostics.noteCurrent("certify.origin");
        if(GroundDynamicPlanningDiagnostics.copy(27,!geometry.standing(origin))||GroundDynamicPlanningDiagnostics.copy(28,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("certify.originalOriginRefused");return false;}
        Vec3 previous=origin;
        for(Vec3 point:course) {
            GroundDynamicPlanningDiagnostics.point();GroundDynamicPlanningDiagnostics.noteCurrent("certify.center");
            if(GroundDynamicPlanningDiagnostics.copy(29,!geometry.current())||GroundDynamicPlanningDiagnostics.copy(30,!geometry.standing(point))||GroundDynamicPlanningDiagnostics.copy(31,!geometry.sweep(previous,point))){GroundDynamicPlanningDiagnostics.returned("certify.originalCenterRefused");return false;}
            double distance=previous.horizontalDistanceTo(point);
            if(distance>.001) {
                double nx=-(point.z()-previous.z())/distance*SIDE_MARGIN,nz=(point.x()-previous.x())/distance*SIDE_MARGIN;
                for(double side:new double[]{-1,1}) {
                    Vec3 a=new Vec3(previous.x()+side*nx,previous.y(),previous.z()+side*nz),b=new Vec3(point.x()+side*nx,point.y(),point.z()+side*nz);
                    // Space on both sides is mandatory. A body-sized passage never becomes a detour.
                    GroundDynamicPlanningDiagnostics.noteCurrent("certify.side");
                    if(GroundDynamicPlanningDiagnostics.copy(32,!geometry.current())||GroundDynamicPlanningDiagnostics.copy(33,!geometry.standing(a))||GroundDynamicPlanningDiagnostics.copy(34,!geometry.standing(b))
                            ||GroundDynamicPlanningDiagnostics.copy(35,!geometry.sweep(previous,a))||GroundDynamicPlanningDiagnostics.copy(36,!geometry.sweep(a,b))||GroundDynamicPlanningDiagnostics.copy(37,!geometry.sweep(b,point))){GroundDynamicPlanningDiagnostics.returned("certify.originalSideRefused");return false;}
                }
            }
            previous=point;
        }
        boolean original=GroundDynamicPlanningDiagnostics.copy(38,geometry.current());if(!original)GroundDynamicPlanningDiagnostics.returned("certify.originalFinalCurrentRefused");return original;
    }
    private static double length(List<Vec3> course,Vec3 origin) {double sum=0;Vec3 p=origin;for(Vec3 q:course){sum+=p.distanceTo(q);p=q;}return sum;}
    private static long mix(long x){x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;x=(x^(x>>>27))*0x94d049bb133111ebL;return x^(x>>>31);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static Plan revoked(){return new Plan(Outcome.REVOKED,List.of(),"Planning context revoked");}
}
