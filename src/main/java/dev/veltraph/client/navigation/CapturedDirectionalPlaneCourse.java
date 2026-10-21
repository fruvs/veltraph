package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Optional;

/** Pure whole-course enclosure and plane-fact arithmetic. No route/body/input authority. */
public final class CapturedDirectionalPlaneCourse {
    private CapturedDirectionalPlaneCourse() {}
    public static final class Rectangle {
        private final Vec3 minimum,maximum;
        private Rectangle(Vec3 min,Vec3 max){minimum=min;maximum=max;}
        public Vec3 minimum(){return minimum;} public Vec3 maximum(){return maximum;}
    }
    /** Includes every complete polyline segment and every chord between its admitted points.
     * Caller must pass the actual eager course with original native initial point, not graph nodes
     * that an original walker will later lazily reshape. Native footprint/halo are checked separately.
     */
    public static Optional<Rectangle> describe(List<Vec3> exactCourse){
        if(exactCourse==null||exactCourse.isEmpty()||exactCourse.size()>6001)return Optional.empty();
        Vec3 first=exactCourse.getFirst();if(!GroundLocomotionMotion.finite(first))return Optional.empty();
        double minX=first.x(),maxX=first.x(),minZ=first.z(),maxZ=first.z();
        for(var point:exactCourse){
            if(!GroundLocomotionMotion.finite(point)||point.y()!=first.y())return Optional.empty();
            minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
            minZ=Math.min(minZ,point.z());maxZ=Math.max(maxZ,point.z());
        }
        return Optional.of(new Rectangle(new Vec3(minX,first.y(),minZ),new Vec3(maxX,first.y(),maxZ)));
    }
    public static final class LeadDomain {
        private final List<Rectangle> rectangles;
        private LeadDomain(List<Rectangle> rectangles){this.rectangles=List.copyOf(rectangles);}
        public List<Rectangle> rectangles(){return rectangles;}
    }
    /** Actual OpenTravelSteering projection checks index-2..index. Lead advances at most 1.25
     * arc units from that projected edge; .65 is the original waypoint handoff/current projection
     * region. Each local rectangle contains complete original edges and all local lead chords.
     * It does not include arbitrary chords between distant points. Initial/final endpoints remain.
     * A finite unsupported model domain is rejected BEFORE any geometry query, never after expiry.
     */
    /** The discarded global envelope is unnecessary for local-domain validity. Keep the original
     * describe encounter/short-circuit order, without its unused extrema and result allocations. */
    private static boolean validLevelCourse(List<Vec3> exactCourse){
        if(exactCourse==null||exactCourse.isEmpty()||exactCourse.size()>6001)return false;
        Vec3 first=exactCourse.getFirst();if(!GroundLocomotionMotion.finite(first))return false;
        for(var point:exactCourse)
            if(!GroundLocomotionMotion.finite(point)||point.y()!=first.y())return false;
        return true;
    }
    public static Optional<LeadDomain> leadDomain(List<Vec3> initialAndEagerCourse){
        if(!validLevelCourse(initialAndEagerCourse)||initialAndEagerCourse.size()>66)return Optional.empty();
        var rectangles=new java.util.ArrayList<Rectangle>();
        for(int index=1;index<initialAndEagerCourse.size();index++){
            int first=Math.max(0,index-3); // course index-2 edge starts one point earlier.
            Vec3 lo=initialAndEagerCourse.get(first),hi=lo;
            double minX=lo.x(),maxX=lo.x(),minZ=lo.z(),maxZ=lo.z();
            int last=index,completeLast=index;double remaining=1.25D;
            while(last+1<initialAndEagerCourse.size()&&remaining>0D){
                var from=initialAndEagerCourse.get(last);var to=initialAndEagerCourse.get(last+1);
                double length=from.horizontalDistanceTo(to);last++;
                if(length>remaining){
                    var end=TravelPath.lerp(from,to,remaining/length);
                    minX=Math.min(minX,end.x());maxX=Math.max(maxX,end.x());
                    minZ=Math.min(minZ,end.z());maxZ=Math.max(maxZ,end.z());break;
                }
                remaining-=length;completeLast=last;
            }
            for(int i=first;i<=completeLast;i++){
                var point=initialAndEagerCourse.get(i);
                minX=Math.min(minX,point.x());maxX=Math.max(maxX,point.x());
                minZ=Math.min(minZ,point.z());maxZ=Math.max(maxZ,point.z());
            }
            rectangles.add(new Rectangle(new Vec3(minX-.65D,lo.y(),minZ-.65D),new Vec3(maxX+.65D,lo.y(),maxZ+.65D)));
        }
        if(rectangles.isEmpty()){
            var point=initialAndEagerCourse.getFirst();rectangles.add(new Rectangle(point,point));
        }
        return Optional.of(new LeadDomain(rectangles));
    }
    /** The newly opted-in vanilla controller stays within its admitted projection domain.
     * Outside the original .65 region is terminal refusal, never post-physics old-walker fallback. */
    public static boolean inProjectionRegion(Vec3 actual,List<Vec3> initialAndEagerCourse){
        if(!GroundLocomotionMotion.finite(actual)||initialAndEagerCourse==null||initialAndEagerCourse.isEmpty())return false;
        Vec3 from=initialAndEagerCourse.getFirst();
        if(actual.y()!=from.y())return false;
        if(actual.horizontalDistanceTo(from)<=.65D)return true;
        for(int i=1;i<initialAndEagerCourse.size();i++){
            var to=initialAndEagerCourse.get(i);double dx=to.x()-from.x(),dz=to.z()-from.z(),squared=dx*dx+dz*dz;
            double t=squared<1E-8D?1D:Math.max(0D,Math.min(1D,((actual.x()-from.x())*dx+(actual.z()-from.z())*dz)/squared));
            if(actual.horizontalDistanceTo(TravelPath.lerp(from,to,t))<=.65D)return true;
            from=to;
        }
        return false;
    }
    /** Native producer supplies the actual freshly journalled state/shape facts. This Boolean only
     * classifies mathematical plane support; it cannot select or revive a native movement controller.
     */
    public static boolean supported(GroundGeometryCellJournal.Observation actual,int supportY,double planeY,float friction){
        if(actual==null||actual.nativeState()==null||actual.shape()==null||actual.shape().isEmpty()
                ||actual.supportShape()==null||!actual.sturdyUp()||!Double.isFinite(planeY)
                ||Float.floatToIntBits(actual.friction())!=Float.floatToIntBits(friction)
                ||Float.floatToIntBits(actual.speedFactor())!=Float.floatToIntBits(1F))return false;
        double top=Double.NEGATIVE_INFINITY;
        for(var box:actual.shape()){
            if(box==null||!Double.isFinite(box.minX())||!Double.isFinite(box.minY())||!Double.isFinite(box.minZ())
                    ||!Double.isFinite(box.maxX())||!Double.isFinite(box.maxY())||!Double.isFinite(box.maxZ())
                    ||box.minX()>box.maxX()||box.minY()>box.maxY()||box.minZ()>box.maxZ())return false;
            top=Math.max(top,box.maxY());
        }
        return Math.abs(supportY+top-planeY)<=.000001D;
    }
}
