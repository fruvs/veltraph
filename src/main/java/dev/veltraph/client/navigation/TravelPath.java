package dev.veltraph.client.navigation;

import dev.veltraph.client.world.*;
import java.util.*;
import java.util.function.BiPredicate;

/** Clearance-constrained route shaping. Randomness is sampled once per route, never per tick. */
public final class TravelPath {
    private TravelPath() {}
    public static boolean walkClear(GroundPathfinder.Terrain terrain,Vec3 a,Vec3 b) {
        return terrain.walkClear(a,b);
    }
    static boolean gridWalkClear(GroundPathfinder.Terrain terrain,Vec3 a,Vec3 b) {
        if(Math.abs(a.y()-b.y())>.05) return false;
        int count=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));
        for(int i=0;i<=count;i++) {
            Vec3 p=lerp(a,b,(double)i/count);
            for(double x:new double[]{-.32,.32}) for(double z:new double[]{-.32,.32}) {
                if(!GroundPathfinder.standable(terrain,new Vec3(p.x()+x,p.y()+.01,p.z()+z).toBlockPos())) return false;
            }
        }
        return true;
    }
    public static Vec3 lerp(Vec3 a,Vec3 b,double t) {
        return new Vec3(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t);
    }
    /** Flight has no stair-step exception: every emitted segment must satisfy required body clearance. */
    public static List<Vec3> shapeFlight(Vec3 start,List<Vec3> nodes,BiPredicate<Vec3,Vec3> required,
                                        BiPredicate<Vec3,Vec3> preferred,long seed) {
        var raw=shapeOpen(start,nodes,preferred,seed);
        Vec3 a=start;boolean valid=true;
        for(Vec3 b:raw) { if(!required.test(a,b)) valid=false;a=b; }
        if(valid) return raw;
        // A preferred wider curve may fail at an off-center start. Retain the validated A* edges,
        // simplifying only when actual body clearance permits it; never add an obstructed fallback.
        var result=new ArrayList<Vec3>();a=start;
        for(int i=0;i<nodes.size();) {
            int end=-1;
            for(int j=i;j<nodes.size();j++) if(required.test(a,nodes.get(j))) end=j;
            if(end<0) return List.of();
            result.add(nodes.get(end));a=nodes.get(end);i=end+1;
        }
        return List.copyOf(result);
    }
    /**
     * Ordinary open-space travel policy. This is a local model requested by the user, not an
     * unbound native curve. A broad seeded bend is admitted only with room on BOTH sides of
     * the entire segment; fitting the body alone is not evidence of an open area. Its first
     * and last two blocks are straight, including exact interaction/return destinations.
     */
    public static List<Vec3> shapeOpen(Vec3 start,List<Vec3> nodes,BiPredicate<Vec3,Vec3> clear,long seed) {
        var result=new ArrayList<Vec3>();var random=new Random(seed);Vec3 a=start;
        for(int i=0;i<nodes.size();) {
            int end=i;
            for(int j=i+1;j<nodes.size();j++) if(clear.test(a,nodes.get(j))) end=j;
            Vec3 b=nodes.get(end);double horizontal=a.horizontalDistanceTo(b);
            boolean open=horizontal>=7 && wideClear(a,b,clear);
            int pieces=Math.max(1,(int)Math.ceil(a.distanceTo(b)/(open?.6:1.5)));
            double amplitude=(random.nextBoolean()?1:-1)*(.45+random.nextDouble()*.5);
            double skew=random.nextDouble()*.7-.35;
            var curved=new ArrayList<Vec3>();
            for(int k=1;k<=pieces;k++) {
                double t=(double)k/pieces;Vec3 p=k==pieces?b:lerp(a,b,t);
                double along=t*horizontal;
                if(open&&along>2&&along<horizontal-2) {
                    double u=(along-2)/(horizontal-4),phase=u+skew*u*(1-u);
                    double offset=amplitude*Math.pow(Math.sin(Math.PI*phase),2);
                    p=new Vec3(p.x()-(b.z()-a.z())/horizontal*offset,p.y(),p.z()+(b.x()-a.x())/horizontal*offset);
                }
                curved.add(p);
            }
            Vec3 previous=a;boolean safe=true;
            for(Vec3 p:curved) { if(!clear.test(previous,p))safe=false;previous=p; }
            if(safe)result.addAll(curved);
            else if(clear.test(a,b))for(int k=1;k<=pieces;k++)result.add(k==pieces?b:lerp(a,b,(double)k/pieces));
            else result.add(b); // An original stair/jump edge keeps its existing controller proof.
            a=b;i=end+1;
        }
        return List.copyOf(result);
    }
    /** More than a two-block passage is required, independent of which side the bend takes. */
    public static boolean wideClear(Vec3 a,Vec3 b,BiPredicate<Vec3,Vec3> clear) {
        double horizontal=a.horizontalDistanceTo(b);
        if(horizontal<.01||!clear.test(a,b))return false;
        double x=-(b.z()-a.z())/horizontal*1.15,z=(b.x()-a.x())/horizontal*1.15;
        for(double side:new double[]{-1,1}) {
            Vec3 from=new Vec3(a.x()+side*x,a.y(),a.z()+side*z),to=new Vec3(b.x()+side*x,b.y(),b.z()+side*z);
            if(!clear.test(a,from)||!clear.test(from,to)||!clear.test(to,b))return false;
        }
        return true;
    }
    public static List<Vec3> shape(Vec3 start,List<Vec3> nodes,BiPredicate<Vec3,Vec3> clear,long seed) {
        var result=new ArrayList<Vec3>();var random=new Random(seed);Vec3 a=start;
        for(int i=0;i<nodes.size();) {
            int end=i;
            for(int j=i+1;j<nodes.size();j++) if(clear.test(a,nodes.get(j))) end=j;
            Vec3 b=nodes.get(end);double horizontal=a.horizontalDistanceTo(b);
            int pieces=Math.max(1,(int)Math.ceil(a.distanceTo(b)/1.5));
            double amplitude=(random.nextBoolean()?1:-1)*(.35+random.nextDouble()*.45)*Math.min(1,horizontal/6);
            List<Vec3> curved=new ArrayList<>();
            for(int k=1;k<=pieces;k++) {
                if(k==pieces) { curved.add(b);continue; }
                double t=(double)k/pieces;Vec3 p=lerp(a,b,t);
                double offset=amplitude*Math.sin(Math.PI*t)*Math.sin(Math.PI*t);
                curved.add(horizontal<.01?p:new Vec3(p.x()-(b.z()-a.z())/horizontal*offset,p.y(),p.z()+(b.x()-a.x())/horizontal*offset));
            }
            Vec3 previous=a;boolean safe=true;
            for(Vec3 p:curved) { if(!clear.test(previous,p)) safe=false; previous=p; }
            // Preserve the original node across stairs or tight bends; never invent a shortcut there.
            if(safe) result.addAll(curved);else if(clear.test(a,b)) {
                for(int k=1;k<=pieces;k++) result.add(lerp(a,b,(double)k/pieces));
            } else result.add(b);
            a=b;i=end+1;
        }
        return List.copyOf(result);
    }
}
