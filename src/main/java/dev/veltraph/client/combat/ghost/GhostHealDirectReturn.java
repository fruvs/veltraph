package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** Qualified direct ground actuator, never health ACK or arrival authority. */
public final class GhostHealDirectReturn {
    public enum Mode { UNSELECTED, DIRECT, PATH }
    public enum Outcome { DIRECT, PATH, PENDING, REJECTED }
    /** Exact existing recovery pose and the native objects present when recovery began. */
    public record Scope(Object parent,Object player,Object world,Object connection,GhostHealReturn.Pose pose) {
        public Scope {if(parent==null||player==null||world==null||connection==null||pose==null||pose.position()==null||!Double.isFinite(pose.position().x())||!Double.isFinite(pose.position().y())||!Double.isFinite(pose.position().z())||!Float.isFinite(pose.yaw())||!Float.isFinite(pose.pitch())||Math.abs(pose.pitch())>90)throw new IllegalArgumentException("Native recovery scope required");}
        public boolean same(Scope other){return other!=null&&parent==other.parent&&player==other.player&&world==other.world&&connection==other.connection&&pose==other.pose;}
    }
    public record Frame(Scope scope,long tick,Vec3 position,float yaw,float pitch,double velocityX,double velocityZ,boolean grounded) {}
    public enum Failure { BLOCKED, INCOMPLETE, UNKNOWN }
    record Envelope(Vec3 minimum,Vec3 maximum,Vec3 coast,Vec3 keys) {}
    public interface Geometry {
        boolean current();
        /** Cached diagnostics only; implementations must not query providers or control. */
        default String diagnostic(){return "clearance=UNAVAILABLE";}
        /** Fresh loaded standing body/support for the whole conservative continuous envelope. */
        boolean clearEnvelope(Vec3 from,Vec3 to);
        /** Cached classification only; unavailable evidence never grants PATH permission. */
        default Failure failure(){return Failure.UNKNOWN;}
    }
    public record Result(Outcome outcome,GhostSourceWalkSteering.Intent intent,String reason) {}
    private final GhostSourceWalkSteering steering=new GhostSourceWalkSteering();
    private Scope scope;private Mode mode=Mode.UNSELECTED;private long lastTick=Long.MIN_VALUE;
    public void start(Scope scope){reset();this.scope=java.util.Objects.requireNonNull(scope);}
    public void reset(){scope=null;mode=Mode.UNSELECTED;lastTick=Long.MIN_VALUE;steering.reset();}
    public Scope scope(){return scope;}
    public Mode mode(){return mode;}
    public Result advance(Frame f,Geometry geometry){
        if(!valid(f)||geometry==null||!geometry.current())return rejected("Recovery frame/custody unavailable",geometry);
        if(mode==Mode.PATH)return new Result(Outcome.PATH,null,"Original path return retained");
        if(lastTick!=Long.MIN_VALUE&&f.tick()!=lastTick+1)return rejected("Recovery tick duplicated, reversed or skipped");
        lastTick=f.tick();
        // A fresh single union contains the complete goal path, measured coast and
        // actual-yaw key+momentum sweep. Pure intent construction is not actuation.
        Vec3 goal=scope.pose().position();
        if(Math.abs(f.position().y()-goal.y())>=.02||f.position().distanceTo(goal)>32){
            if(!geometry.current())return rejected("Recovery custody changed before direct admission",geometry);
            if(mode==Mode.UNSELECTED){mode=Mode.PATH;return new Result(Outcome.PATH,null,"Direct level/search domain unavailable; original path return");}
            return rejected("Active direct level/search domain revoked",geometry);
        }
        var intent=steering.advance(new GhostSourceWalkSteering.Frame(f.tick(),point(f.position()),f.yaw(),f.pitch(),f.velocityX(),f.velocityZ(),f.grounded(),point(goal),false,scope.pose().pitch()));
        if(intent.outcome()==GhostSourceWalkSteering.Outcome.REJECTED)return rejected("Direct steering frame rejected");
        var envelope=envelope(f,goal,intent);
        // Combining individually bounded rays can exceed the original native
        // query domain. This is known unsupported geometry, not a budget event.
        if(!finite(envelope.minimum())||!finite(envelope.maximum())||envelope.minimum().distanceTo(envelope.maximum())>32){
            if(!geometry.current())return rejected("Recovery custody changed before union-domain fallback",geometry);
            if(mode==Mode.UNSELECTED){mode=Mode.PATH;return new Result(Outcome.PATH,null,"Merged envelope exceeds original read domain; original path return");}
            return rejected("Active direct merged-envelope domain revoked",geometry);
        }
        boolean complete=geometry.clearEnvelope(envelope.minimum(),envelope.maximum());
        if(!geometry.current())return rejected("Recovery custody changed during union geometry read",geometry);
        if(!complete){
            var failure=failure(geometry);
            if(!geometry.current())return rejected("Recovery custody changed during failed-proof classification",geometry);
            if(failure==Failure.INCOMPLETE||failure==Failure.UNKNOWN)
                return new Result(Outcome.PENDING,null,"Direct envelope proof pending: "+failure+" | "+diagnostic(geometry));
            if(mode==Mode.UNSELECTED){mode=Mode.PATH;return new Result(Outcome.PATH,null,"Direct envelope blocked; original path return | "+diagnostic(geometry));}
            return rejected("Active direct union envelope blocked",geometry);
        }
        mode=Mode.DIRECT;
        return new Result(Outcome.DIRECT,intent,"Owned direct return: "+intent.outcome());
    }
    /** Conservative continuous foot rectangle, not a prediction or cached permission. */
    static Envelope envelope(Frame f,Vec3 goal,GhostSourceWalkSteering.Intent intent){
        var coast=new Vec3(f.position().x()+f.velocityX(),f.position().y(),f.position().z()+f.velocityZ());
        int forward=(intent.forward()?1:0)-(intent.back()?1:0),side=(intent.left()?1:0)-(intent.right()?1:0);
        double n=Math.hypot(forward,side),angle=Math.toRadians(f.yaw());
        double dx=n==0?0:(-Math.sin(angle)*forward+Math.cos(angle)*side)/n*.35;
        double dz=n==0?0:(Math.cos(angle)*forward+Math.sin(angle)*side)/n*.35;
        var keys=new Vec3(coast.x()+dx,coast.y(),coast.z()+dz);
        double minX=Math.min(Math.min(f.position().x(),goal.x()),Math.min(coast.x(),keys.x()));
        double maxX=Math.max(Math.max(f.position().x(),goal.x()),Math.max(coast.x(),keys.x()));
        double minZ=Math.min(Math.min(f.position().z(),goal.z()),Math.min(coast.z(),keys.z()));
        double maxZ=Math.max(Math.max(f.position().z(),goal.z()),Math.max(coast.z(),keys.z()));
        return new Envelope(new Vec3(minX,f.position().y(),minZ),new Vec3(maxX,f.position().y(),maxZ),coast,keys);
    }
    private static Failure failure(Geometry geometry){try{var result=geometry.failure();return result==null?Failure.UNKNOWN:result;}catch(Throwable ignored){return Failure.UNKNOWN;}}
    private boolean valid(Frame f){return scope!=null&&f!=null&&scope.same(f.scope())&&f.tick()>=0&&f.position()!=null&&f.grounded()
            &&Double.isFinite(f.position().x())&&Double.isFinite(f.position().y())&&Double.isFinite(f.position().z())
            &&Float.isFinite(f.yaw())&&Float.isFinite(f.pitch())&&Math.abs(f.pitch())<=90
            &&Double.isFinite(f.velocityX())&&Double.isFinite(f.velocityZ());}
    private static boolean finite(Vec3 p){return Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    private static Point point(Vec3 p){return new Point(p.x(),p.y(),p.z());}
    private static String diagnostic(Geometry geometry){
        try{String text=geometry==null?"clearance=UNAVAILABLE":geometry.diagnostic();return text==null?"clearance=UNAVAILABLE":text.substring(0,Math.min(384,text.length()));}
        catch(Throwable ignored){return "clearance=DIAGNOSTIC_ERROR";}
    }
    private Result rejected(String reason,Geometry geometry){return rejected(reason+" | "+diagnostic(geometry));}
    private Result rejected(String reason){return new Result(Outcome.REJECTED,null,reason);}
}
