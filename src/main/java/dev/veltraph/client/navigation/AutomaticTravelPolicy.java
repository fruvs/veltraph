package dev.veltraph.client.navigation;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.*;

/** Source mixed-route composition/additive cost, with explicit local costs and capability contracts. */
public final class AutomaticTravelPolicy {
    public record Options(boolean walk,boolean flight,boolean etherShortcuts,boolean rankedWarps,double walkCost,double flightCost,int recoveries) {
        public Options {if(!Double.isFinite(walkCost)||!Double.isFinite(flightCost)||walkCost<=0||flightCost<=0||walkCost>100||flightCost>100||recoveries<0||recoveries>2)
            throw new IllegalArgumentException("Invalid bounded travel policy");}
    }
    public record Request(Vec3 goal,GameArea area,String dimension,Options options) {
        public Request {if(!TravelAtlas.finite(goal)||area==null||!area.isSkyBlock()||dimension==null||!dimension.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||options==null)
            throw new IllegalArgumentException("Known scoped travel goal required");}
    }
    public record Access(boolean mayFly,boolean etherTool,int mana,Set<String> permissions) {public Access {permissions=Set.copyOf(permissions);}}
    public record Route(LocalRoute.Kind kind,Vec3 destination,double cost) {}
    public record Leg(Route physical,TravelAtlas.Shortcut shortcut) {
        public Leg {if((physical==null)==(shortcut==null))throw new IllegalArgumentException("One leg kind required");}
        public Vec3 destination(){return physical!=null?physical.destination():shortcut.arrival();}
    }
    public record Plan(List<Leg> legs,double cost,String shortcutId,String reason,List<String> denied) {
        public Plan {legs=List.copyOf(legs);denied=List.copyOf(denied);}
        public boolean found(){return reason.isEmpty();}
    }
    public interface Scene {
        /** Returns finite additive path distance, or positive infinity for unproven geometry. */
        double distance(LocalRoute.Kind kind,Vec3 from,Vec3 to);
        String shortcutRejection(TravelAtlas.Shortcut shortcut,Vec3 from);
    }
    private AutomaticTravelPolicy() {}
    private static Route physical(Scene scene,Request r,Access access,Vec3 from,Vec3 to) {
        Route best=null;
        for(LocalRoute.Kind kind:new LocalRoute.Kind[]{LocalRoute.Kind.WALK,LocalRoute.Kind.FLY}) {
            if(kind==LocalRoute.Kind.WALK?!r.options().walk():!r.options().flight()||!access.mayFly())continue;
            double d=scene.distance(kind,from,to),cost=d*(kind==LocalRoute.Kind.WALK?r.options().walkCost():r.options().flightCost());
            if(Double.isFinite(cost)&&cost>=0&&(best==null||cost<best.cost()))best=new Route(kind,to,cost);
        }
        return best;
    }
    public static Plan select(Scene scene,Request request,TravelAtlas atlas,Access access,Vec3 origin,Set<String> failed) {
        if(!TravelAtlas.finite(origin))return new Plan(List.of(),0,"","Actual origin unavailable",List.of());
        var denied=new ArrayList<String>();Route direct=physical(scene,request,access,origin,request.goal());
        Plan best=direct==null?null:new Plan(List.of(new Leg(direct,null)),direct.cost(),"","",List.of());
        for(var s:atlas.shortcuts()) {
            String why="";
            if(failed.contains(s.id()))why="previous attempt blacklisted";
            else if(s.area()!=request.area()||!s.dimension().equals(request.dimension()))why="provider scope differs";
            else if(s.kind()==TravelAtlas.Kind.ETHERWARP&&(!request.options().etherShortcuts()||!access.etherTool()||access.mana()<s.manaCost()))why="ether option/tool/observed mana unavailable";
            else if(s.kind()==TravelAtlas.Kind.FAST_WARP&&(!request.options().rankedWarps()||!access.permissions().contains(s.permissionKey())))why="ranked warp lacks observed permission";
            else why=scene.shortcutRejection(s,s.entrance());
            if(!why.isEmpty()){denied.add(s.id()+": "+why);continue;}
            Route prefix=physical(scene,request,access,origin,s.entrance()),suffix=physical(scene,request,access,s.arrival(),request.goal());
            if(prefix==null||suffix==null){denied.add(s.id()+": prefix/suffix geometry unproven");continue;}
            double cost=prefix.cost()+s.transitCost()+suffix.cost();
            // Readable source preserves prefix, authorized ether leg and a validated walk suffix.
            // Strict improvement keeps direct paths and atlas order stable at equal cost.
            if(best==null||cost<best.cost())best=new Plan(List.of(new Leg(prefix,null),new Leg(null,s),new Leg(suffix,null)),cost,s.id(),"",List.of());
        }
        return best==null?new Plan(List.of(),0,"","No permitted loaded route",denied):new Plan(best.legs(),best.cost(),best.shortcutId(),"",denied);
    }
}
