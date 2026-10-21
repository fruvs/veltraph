package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import java.util.*;

/** User-requested modern steering adapter; never changes recovered graph/edge/cursor identities. */
public final class GhostOpenWalkVariation {
    public interface Geometry {
        boolean current();
        /** Exact loaded supported full-body sweep, including hazards and protected living actors. */
        boolean supportedSweep(Point from,Point to);
    }
    public enum Outcome { ORIGINAL, VARIED, REVOKED }
    public record Result(Outcome outcome,Point point) {}
    private static final double MIN_LENGTH=4,MAX_LENGTH=48,SIDE_CLEARANCE=1.15,LEAD=1.25;
    private final Object edgeIdentity;
    private final Point from,to;
    private final double length,amplitude,skew,endMargin;
    private final List<Point> curve;
    private final boolean admitted;
    private GhostOpenWalkVariation(Object identity,Point from,Point to,long seed,Geometry geometry){
        edgeIdentity=Objects.requireNonNull(identity);this.from=Objects.requireNonNull(from);this.to=Objects.requireNonNull(to);
        length=Math.hypot(to.x()-from.x(),to.z()-from.z());
        // Source G normally limits ordinary edges to roughStrength 4..6. A seven-block
        // admission threshold would never vary those actual flat source edges.
        endMargin=length>=7?2:1; // Always exceeds the original .75/.5 completion radii.
        var rng=new Random(seed);double direction=rng.nextBoolean()?1:-1;
        amplitude=direction*(length>=7?.45+rng.nextDouble()*.5:.22+rng.nextDouble()*.1);
        skew=rng.nextDouble()*.7-.35;
        boolean eligible=from.y()==to.y()&&length>=MIN_LENGTH&&length<=MAX_LENGTH&&geometry.current()&&wide(from,to,geometry);
        var points=new ArrayList<Point>();
        if(eligible){int pieces=(int)Math.ceil(length/.6);Point previous=from;for(int i=1;i<=pieces;i++){Point p=at(i/(double)pieces);points.add(p);if(!geometry.supportedSweep(previous,p)){eligible=false;break;}previous=p;}}
        admitted=eligible&&geometry.current();curve=admitted?List.copyOf(points):List.of();
    }
    /** One seed is supplied once per installed cursor; this object performs no random draws on ticks. */
    public static GhostOpenWalkVariation prepare(Object actualEdgeIdentity,Point from,Point to,long seed,Geometry geometry){
        return new GhostOpenWalkVariation(actualEdgeIdentity,from,to,seed,Objects.requireNonNull(geometry));
    }
    public Object edgeIdentity(){return edgeIdentity;}
    public boolean admitted(){return admitted;}
    public boolean sameEndpoints(Point actualFrom,Point actualTo){return from.equals(actualFrom)&&to.equals(actualTo);}
    public List<Point> cachedCurve(){return curve;}
    /** Only flat grounded non-jump cursor steering may vary. Source terminal/jump observations stay original. */
    public Result adapt(Object actualEdgeIdentity,Point actual,Point sourcePoint,boolean grounded,boolean sourceJump,Geometry geometry){
        Objects.requireNonNull(actual);Objects.requireNonNull(sourcePoint);Objects.requireNonNull(geometry);
        if(actualEdgeIdentity!=edgeIdentity||!geometry.current())return new Result(Outcome.REVOKED,null);
        if(!admitted||sourceJump||!grounded||Math.abs(actual.y()-from.y())>.02||Math.abs(sourcePoint.y()-from.y())>.02)
            return new Result(Outcome.ORIGINAL,sourcePoint);
        double limit=projection(sourcePoint),sourceLateral=lateral(sourcePoint);
        if(limit<0||limit>1||Math.abs(sourceLateral)>.02)return new Result(Outcome.ORIGINAL,sourcePoint);
        // Project continuously from actual progress; the original mutable cursor point is
        // an upper bound, so a modern lead never skips the source's current steering sample.
        double progress=Math.clamp(projection(actual),0,limit);
        double t=Math.min(limit,progress+LEAD/length);
        Point point=at(t);
        if(!geometry.supportedSweep(actual,point)||!wide(line(t),point,geometry)||!geometry.current())return new Result(Outcome.REVOKED,null);
        return new Result(Outcome.VARIED,point);
    }
    private double projection(Point p){return ((p.x()-from.x())*(to.x()-from.x())+(p.z()-from.z())*(to.z()-from.z()))/(length*length);}
    private double lateral(Point p){return (-(to.z()-from.z())*(p.x()-from.x())+(to.x()-from.x())*(p.z()-from.z()))/length;}
    private Point line(double t){return new Point(from.x()+(to.x()-from.x())*t,from.y(),from.z()+(to.z()-from.z())*t);}
    private Point at(double t){if(t<=0)return from;if(t>=1)return to;Point p=line(t);double distance=t*length,offset=0;
        if(distance>endMargin&&distance<length-endMargin){double u=(distance-endMargin)/(length-2*endMargin),phase=u+skew*u*(1-u);offset=amplitude*Math.pow(Math.sin(Math.PI*phase),2);}
        return new Point(p.x()-(to.z()-from.z())/length*offset,p.y(),p.z()+(to.x()-from.x())/length*offset);
    }
    private boolean wide(Point a,Point b,Geometry geometry){
        // For initial admission use the entire original edge; each live tick additionally
        // sweeps a small transverse connection near the candidate on both original sides.
        if(!geometry.current()||!geometry.supportedSweep(a,b))return false;
        double nx=-(to.z()-from.z())/length*SIDE_CLEARANCE,nz=(to.x()-from.x())/length*SIDE_CLEARANCE;
        for(double side:new double[]{-1,1}){
            Point aa=new Point(a.x()+side*nx,a.y(),a.z()+side*nz),bb=new Point(b.x()+side*nx,b.y(),b.z()+side*nz);
            if(!geometry.supportedSweep(a,aa)||!geometry.supportedSweep(aa,bb)||!geometry.supportedSweep(bb,b))return false;
        }
        return geometry.current();
    }
}
