package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.BiPredicate;

/** Local advisory course shaping. Only the existing native loaded-body/custody publisher can fly it.
 * Seeds belong to journeys, never frames. Final poses, takeoff and landing columns are unchanged.
 */
public final class OpenFlightCourse {
    private OpenFlightCourse() {}
    public static List<Vec3> shape(Vec3 start,List<Vec3> nodes,BiPredicate<Vec3,Vec3> required,
                                   BiPredicate<Vec3,Vec3> preferred,long seed) {
        Objects.requireNonNull(required);Objects.requireNonNull(preferred);
        if(!SharedMovementPolicy.finite(start)||nodes==null||nodes.isEmpty()||nodes.size()>50000
                ||nodes.stream().anyMatch(p->!SharedMovementPolicy.finite(p)))return List.of();
        var result=new ArrayList<Vec3>();var random=new Random(seed);Vec3 origin=start;
        for(int index=0;index<nodes.size();) {
            int end=-1;
            for(int next=index;next<nodes.size();next++)if(required.test(origin,nodes.get(next)))end=next;
            if(end<0)return List.of();
            Vec3 destination=nodes.get(end);double segmentLength=origin.distanceTo(destination);
            // Native search spans at most a 128-cell cube; reject unbounded public model inputs.
            if(!Double.isFinite(segmentLength)||segmentLength>512)return List.of();
            double horizontal=origin.horizontalDistanceTo(destination);
            double lateral=(random.nextBoolean()?1:-1)*(.8+random.nextDouble()*Math.min(2.0,horizontal*.08));
            double vertical=(random.nextBoolean()?1:-1)*(.2+random.nextDouble()*.8);
            double skew=random.nextDouble()*.5-.25;
            // A full surrounding envelope, including diagonals, prevents borrowing narrow openings.
            boolean open=horizontal>=10&&envelope(origin,destination,Math.abs(lateral)+.4,Math.abs(vertical)+.4,preferred);
            int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(destination)/(open?.6:1.5)));
            var curved=new ArrayList<Vec3>();
            for(int part=1;part<=pieces;part++) {
                double t=(double)part/pieces;Vec3 point=part==pieces?destination:TravelPath.lerp(origin,destination,t);
                double distance=t*horizontal;
                if(open&&distance>2&&distance<horizontal-2) {
                    double u=(distance-2)/(horizontal-4),phase=u+skew*u*(1-u);
                    double blend=Math.pow(Math.sin(Math.PI*phase),2);
                    point=new Vec3(point.x()-(destination.z()-origin.z())/horizontal*lateral*blend,
                            point.y()+vertical*blend,point.z()+(destination.x()-origin.x())/horizontal*lateral*blend);
                }
                curved.add(point);
            }
            if(!clear(origin,curved,required)||length(origin,curved)>origin.distanceTo(destination)*1.25+1e-8) {
                curved.clear();
                for(int part=1;part<=pieces;part++)curved.add(part==pieces?destination:TravelPath.lerp(origin,destination,(double)part/pieces));
                if(!clear(origin,curved,required))return List.of();
            }
            if(result.size()+curved.size()>100000)return List.of();
            result.addAll(curved);origin=destination;index=end+1;
        }
        // The caller and each executed lead recheck fresh loaded native geometry and publication custody.
        return List.copyOf(result);
    }
    private static boolean envelope(Vec3 from,Vec3 to,double side,double height,BiPredicate<Vec3,Vec3> clear) {
        double horizontal=from.horizontalDistanceTo(to),x=-(to.z()-from.z())/horizontal,z=(to.x()-from.x())/horizontal;
        if(!clear.test(from,to))return false;
        for(int lateral=-1;lateral<=1;lateral++)for(int vertical=-1;vertical<=1;vertical++) {
            if(lateral==0&&vertical==0)continue;
            Vec3 a=new Vec3(from.x()+x*side*lateral,from.y()+height*vertical,from.z()+z*side*lateral);
            Vec3 b=new Vec3(to.x()+x*side*lateral,to.y()+height*vertical,to.z()+z*side*lateral);
            if(!clear.test(from,a)||!clear.test(a,b)||!clear.test(b,to))return false;
        }
        return true;
    }
    private static boolean clear(Vec3 origin,List<Vec3> nodes,BiPredicate<Vec3,Vec3> check) {
        for(Vec3 point:nodes){if(!check.test(origin,point))return false;origin=point;}return true;
    }
    private static double length(Vec3 origin,List<Vec3> nodes) {
        double total=0;for(Vec3 point:nodes){total+=origin.distanceTo(point);origin=point;}return total;
    }
}
