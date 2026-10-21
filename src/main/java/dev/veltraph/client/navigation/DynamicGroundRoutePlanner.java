package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Modern local journey variety. The existing bounded graph remains the fallback authority. */
public final class DynamicGroundRoutePlanner {
    private DynamicGroundRoutePlanner() {}
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
        return planWindows(origin,nodes,seed,ordinal,geometry,false);
    }
    private static Plan planWindows(Vec3 origin,List<Vec3> nodes,long seed,long ordinal,Geometry geometry,boolean exactReturnApproach){
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
            Plan piece=plan(from,original,seed+window++*0x632be59bd9b4e019L,ordinal,geometry,exactReturnApproach);
            if(piece.outcome()==Outcome.REVOKED)return revoked();
            course.addAll(piece.course());broad.addAll(piece.broadEdges());varied|=piece.outcome()==Outcome.VARIED;
            from=nodes.get(end);cursor=end+1;
            if(course.size()>MAX_NODES)return new Plan(Outcome.ORIGINAL,nodes,"Expanded course exceeds finite bound");
        }
        return GroundDynamicPlanningDiagnostics.copy(17,geometry.current())?new Plan(varied?Outcome.VARIED:Outcome.ORIGINAL,course,
                varied?"Broad supported level windows with original step endpoints":"No complete broad window proof",broad):revoked();
    }
    /** Model-only broad approach; the original terminal run remains prescribed. */
    public static Plan planExactReturnWindows(Vec3 origin,List<Vec3> nodes,long seed,long ordinal,Geometry geometry){
        Objects.requireNonNull(geometry);
        if(!finite(origin)||nodes==null||nodes.isEmpty()||nodes.size()>MAX_NODES
                ||nodes.stream().anyMatch(p->!finite(p)))return revoked();
        if(!geometry.current())return revoked();
        int boundary=protectedTerminalBoundary(origin,nodes);
        if(boundary<0)return geometry.current()?new Plan(Outcome.ORIGINAL,nodes,"Entire original terminal run protected"):revoked();
        Plan approach=planWindows(origin,nodes.subList(0,boundary+1),seed,ordinal,geometry,true);
        if(approach.outcome()==Outcome.REVOKED||!geometry.current())return revoked();
        if(approach.outcome()!=Outcome.VARIED)return new Plan(Outcome.ORIGINAL,nodes,"No complete broad exact-return approach proof");
        var course=new ArrayList<>(approach.course());var broad=new ArrayList<>(approach.broadEdges());
        for(int i=boundary+1;i<nodes.size();i++){course.add(nodes.get(i));broad.add(false);}
        if(course.size()>MAX_NODES)return new Plan(Outcome.ORIGINAL,nodes,"Expanded exact-return approach exceeds finite bound");
        return geometry.current()?new Plan(Outcome.VARIED,course,"Broad approach with original prescribed terminal run",broad):revoked();
    }
    private static int protectedTerminalBoundary(Vec3 origin,List<Vec3> nodes){
        int last=nodes.size()-1;
        Vec3 goal=nodes.get(last),previous=last==0?origin:nodes.get(last-1);
        double dx=goal.x()-previous.x(),dz=goal.z()-previous.z(),length=Math.hypot(dx,dz);
        // Vertical or duplicate terminal edges remain wholly on the admitted path.
        if(length<.001||Math.abs(goal.y()-previous.y())>.02)return -1;
        dx/=length;dz/=length;
        int boundary=-1;Vec3 to=goal;
        for(int i=last-1;i>=-1;i--){
            Vec3 from=i<0?origin:nodes.get(i);double sx=to.x()-from.x(),sz=to.z()-from.z();
            if(Math.abs(from.y()-goal.y())>.02||Math.abs(sx*dz-sz*dx)>.001||sx*dx+sz*dz<-.001)break;
            boundary=i;
            if(from.horizontalDistanceTo(goal)>=2.35)break;
            to=from;
        }
        // Broad planning must stop before all three prescribed entry nodes, even when
        // a short corner or diagonal makes the collinear terminal run shorter.
        return Math.min(boundary,last-3);
    }
    private static final int MAX_NODES=6000,MAX_CANDIDATES=8;
    private static final double MIN_LENGTH=10,MAX_LENGTH=48,SIDE_MARGIN=.7,MAX_RATIO=1.45;

    /** The journey ordinal cycles four broad choices; one seed is sampled at navigator creation. */
    public static Plan plan(Vec3 origin,List<Vec3> admittedNodes,long sessionSeed,long journeyOrdinal,Geometry geometry) {
        return plan(origin,admittedNodes,sessionSeed,journeyOrdinal,geometry,false);
    }
    private static Plan plan(Vec3 origin,List<Vec3> admittedNodes,long sessionSeed,long journeyOrdinal,Geometry geometry,boolean exactReturnApproach) {
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
            // Exact-return approaches prove a same-side bypass before switching sides.
            // Ordinary planning retains its original candidate and RNG evaluation order.
            boolean curved=exactReturnApproach?(attempt&1)==0:attempt<4;
            double offset;
            if(exactReturnApproach){
                double preferred=choices[order[attempt/2]];
                offset=curved?preferred:Math.copySign(4.4+random.nextDouble()*.35,preferred);
            }else offset=attempt<4?choices[order[attempt]]:Math.copySign(4.4+random.nextDouble()*.35,choices[order[attempt-4]]);
            List<Vec3> candidate=curved?curve(origin,goal,offset,skew):bypass(origin,goal,offset);
            if(GroundDynamicPlanningDiagnostics.copy(23,length(candidate,origin)>length*MAX_RATIO)){GroundDynamicPlanningDiagnostics.returned("plan.originalCandidateLengthRefused");continue;}
            if(GroundDynamicPlanningDiagnostics.copy(24,certify(origin,candidate,geometry))) {
                if(GroundDynamicPlanningDiagnostics.copy(25,!geometry.current())){GroundDynamicPlanningDiagnostics.returned("plan.originalAfterCertifyCurrentRefused");return revoked();}
                return new Plan(Outcome.VARIED,candidate,curved?"Broad supported curve":"Supported alternate obstacle side",java.util.Collections.nCopies(candidate.size(),true));
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
            course.add(i==pieces?to:sideKnownLength(from,to,t,side,length));
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
    /** Curve endpoints are immutable; reuse the distance already computed for its sample count. */
    private static Vec3 sideKnownLength(Vec3 from,Vec3 to,double t,double offset,double length) {
        Vec3 p=TravelPath.lerp(from,to,t);
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
                for(int sideIndex=0;sideIndex<2;sideIndex++) {
                    double side=sideIndex==0?-1D:1D;
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
