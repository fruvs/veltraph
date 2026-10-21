package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.function.BiPredicate;

/** Continuous polyline lead for wide travel; never replaces the exact arrival/step target. */
public final class OpenTravelSteering {
    private OpenTravelSteering() {}
    public static Vec3 target(Vec3 origin,List<Vec3> course,int index,Vec3 position,double lead,
                              BiPredicate<Vec3,Vec3> clear) {
        Vec3 current=course.get(index),goal=course.getLast();
        if(index==course.size()-1||position.distanceTo(goal)<3||lead<=0)return current;
        // Node arrival intentionally advances before its exact center. Project onto the
        // nearby previous edges too, so that early handoff cannot jump the camera lead.
        Vec3 cursor=current;int projected=index;double nearest=Double.POSITIVE_INFINITY;
        for(int edge=Math.max(0,index-2);edge<=index;edge++) {
            Vec3 from=edge==0?origin:course.get(edge-1),to=course.get(edge);
            double dx=to.x()-from.x(),dy=to.y()-from.y(),dz=to.z()-from.z();
            double lengthSquared=dx*dx+dy*dy+dz*dz;
            double t=lengthSquared<1e-8?1:Math.clamp(((position.x()-from.x())*dx+(position.y()-from.y())*dy+(position.z()-from.z())*dz)/lengthSquared,0,1);
            Vec3 point=TravelPath.lerp(from,to,t);double distance=position.distanceTo(point);
            if(distance<nearest) { nearest=distance;cursor=point;projected=edge; }
        }
        Vec3 candidate=current;double remaining=lead;
        for(int node=projected;node<course.size();node++) {
            Vec3 next=course.get(node);double distance=cursor.distanceTo(next);
            if(distance>=remaining) { candidate=TravelPath.lerp(cursor,next,remaining/distance);break; }
            remaining-=distance;cursor=next;candidate=next;
        }
        // Dynamic loaded-body proof rejects a newly obstructed lead. Both side sweeps also
        // prevent corner cutting into a one/two-block corridor, even on an otherwise open route.
        return TravelPath.wideClear(position,candidate,clear)?candidate:current;
    }
}
