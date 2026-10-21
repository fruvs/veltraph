package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;

/** Read-only feasibility for an explicitly declared local flight protocol, not recovered Net physics.
 * The aim remains the source height/FIFO rule; this model only withholds unsafe requests. */
public final class HuntingFlight {
    private HuntingFlight() {}
    public enum Model { VANILLA_SNOWBALL_26_3 }
    public enum Medium { AIR, WATER, BOTH, UNSUPPORTED }
    public record Contract(Model model,int maxTicks,double maxTravel,int maxObservationLagTicks) {
        /** Older profiles have unknown observation timing, not an assumed zero-age packet. */
        public Contract(Model model,int maxTicks,double maxTravel){this(model,maxTicks,maxTravel,-1);}
        public Contract {
            if(model==null||maxTicks<1||maxTicks>40||!Double.isFinite(maxTravel)||maxTravel<1||maxTravel>16||maxObservationLagTicks < -1||maxObservationLagTicks>8)
                throw new IllegalArgumentException("Explicit bounded hunting flight protocol required");
        }
    }
    public record Box(double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {
        public Box {
            if(!finite(new Vec3(minX,minY,minZ))||!finite(new Vec3(maxX,maxY,maxZ))||minX>maxX||minY>maxY||minZ>maxZ)
                throw new IllegalArgumentException("Exact finite native target box required");
        }
        Box move(Vec3 delta){return new Box(minX+delta.x(),minY+delta.y(),minZ+delta.z(),maxX+delta.x(),maxY+delta.y(),maxZ+delta.z());}
        Vec3 center(){return new Vec3((minX+maxX)/2,(minY+maxY)/2,(minZ+maxZ)/2);}
        /** Segment entry in the exact target body. No synthetic contact margin/hitbox widening. */
        Clip clip(Vec3 from,Vec3 to){
            double lo=0,hi=1;double[] a={from.x(),from.y(),from.z()},d={to.x()-from.x(),to.y()-from.y(),to.z()-from.z()},min={minX,minY,minZ},max={maxX,maxY,maxZ};
            for(int i=0;i<3;i++){
                if(Math.abs(d[i])<1e-12){if(a[i]<min[i]||a[i]>max[i])return null;continue;}
                double x=(min[i]-a[i])/d[i],y=(max[i]-a[i])/d[i];lo=Math.max(lo,Math.min(x,y));hi=Math.min(hi,Math.max(x,y));if(lo>hi)return null;
            // Native AABB.clip has no entering face when the segment starts inside/on the body.
            // A player overlapping a fish must not manufacture a zero-distance projectile hit.
            }return lo<=0?null:new Clip(lo,hi);
        }
    }
    record Clip(double entry,double exit) {}
    public interface Geometry {
        /** Loaded world, border, actual native block collider and foreign entity clearance. */
        boolean clear(Vec3 from,Vec3 to);
        /** Native projectile body overlap; unmodeled current, bubbles and unloaded cells are unsupported. */
        Medium medium(Vec3 position);
        /** All possible native body positions for one affine spread-history branch. */
        default Medium media(Box positions){return medium(positions.center());}
        /** Conservative block/foreign-entity clearance for the complete bounded spread corridor.
         * This does not enlarge the target or alter any native projectile collision. */
        boolean clearEnvelope(Box bounds);
    }
    public record Check(boolean feasible,String reason,int contactTick,double contactFraction) {}
    public static final double EYE_OFFSET=(double).1f, SPEED=(double)1.5f, GRAVITY=.03;
    public static final double AIR_DRAG=(double).99f,WATER_DRAG=(double).8f,SPREAD=.0172275;

    public static Check check(Contract contract,Vec3 eye,Vec3 look,Vec3 shooterMotion,Box target,Vec3 targetMotion,double reach,Geometry geometry){
        if(contract==null)return deny("Unknown hunting flight protocol");
        if(contract.maxObservationLagTicks()<0)return deny("Unknown native target observation-lag budget");
        if(!finite(eye)||!finite(look)||!finite(shooterMotion)||!finite(targetMotion)||target==null||geometry==null||!Double.isFinite(reach)||reach<1||reach>3)return deny("Invalid native flight observation");
        double length=Math.sqrt(look.x()*look.x()+look.y()*look.y()+look.z()*look.z());if(length<1e-9)return deny("Missing native flight direction");
        var direction=scale(look,1/length);var origin=add(eye,new Vec3(0,-EYE_OFFSET,0));
        Vec3[] at=new Vec3[9],velocity=new Vec3[9];at[0]=origin;velocity[0]=add(scale(direction,SPEED),shooterMotion);int path=1;
        for(int x:new int[]{-1,1})for(int y:new int[]{-1,1})for(int z:new int[]{-1,1}){
            at[path]=origin;velocity[path++]=add(scale(add(direction,new Vec3(x*SPREAD,y*SPREAD,z*SPREAD)),SPEED),shooterMotion);
        }
        record Branch(Vec3[] at,Vec3[] velocity,Medium medium) {}
        var branches=new java.util.ArrayList<Branch>();branches.add(new Branch(at,velocity,Medium.AIR));
        // A freshly constructed native Snowball is not yet touching water. ThrowableProjectile
        // applies gravity then inertia then collision, and only afterwards Entity.tick updates fluid.
        for(int tick=1;tick<=contract.maxTicks();tick++){
            var advanced=new java.util.ArrayList<Branch>();
            for(var branch:branches){Vec3[] next=new Vec3[9],v=new Vec3[9];for(int i=0;i<9;i++){
                if(geometry.medium(branch.at()[i])==Medium.UNSUPPORTED)return deny("Unsupported native flight medium");
                v[i]=advance(branch.velocity()[i],branch.medium());next[i]=add(branch.at()[i],v[i]);
            }advanced.add(new Branch(next,v,branch.medium()));}
            // Native server entity order can expose the fish before or after its current tick.
            // Both exact translated boxes must intersect; this is conservative, not enlarged.
            double entry=0,exit=1;boolean allContact=true;
            boolean inReach=true;
            // Receipt of a new packet endpoint does not make it the current server body.
            // Cover the explicitly declared observation-age window without changing source aim.
            for(int age=0;age<=contract.maxObservationLagTicks();age++)for(var body:new Box[]{target.move(scale(targetMotion,tick-1+age)),target.move(scale(targetMotion,tick+age))}){
                inReach&=eye.distanceTo(body.center())<=reach;
                for(int branch=0;branch<branches.size();branch++)for(int i=0;i<9;i++){
                    var contact=body.clip(branches.get(branch).at()[i],advanced.get(branch).at()[i]);if(contact==null){allContact=false;continue;}
                    entry=Math.max(entry,contact.entry());exit=Math.min(exit,contact.exit());
                }
            }
            // With identical medium history every position is affine in the three bounded spread
            // components. A common interval inside the unchanged convex body bounds every draw;
            // checking only empty space until a nominal hit did not establish target contact.
            boolean contact=allContact&&entry<=exit;
            if(contact&&!inReach)return deny("Declared native contact range exceeded");
            for(int branch=0;branch<branches.size();branch++){
                Vec3[] from=branches.get(branch).at(),next=advanced.get(branch).at(),ends=new Vec3[9];
                for(int i=0;i<9;i++){
                ends[i]=contact?lerp(from[i],next[i],entry):next[i];
                if(!geometry.clear(from[i],ends[i]))return deny("Native flight blocked before spread-safe target contact");
                if(ends[i].distanceTo(origin)>contract.maxTravel())return deny("Declared native flight expires before spread-safe target contact");
                }
                if(!geometry.clearEnvelope(envelope(from,ends)))return deny("Native spread corridor meets obstruction");
            }
            if(contact)return new Check(true,"Declared native spread reaches observed moving body",tick,entry);
            branches.clear();
            for(var branch:advanced){var updated=geometry.media(envelope(branch.at(),branch.at()));if(updated==Medium.UNSUPPORTED)return deny("Unsupported native flight medium");
                if(updated==Medium.BOTH){branches.add(new Branch(branch.at(),branch.velocity(),Medium.AIR));branches.add(new Branch(branch.at(),branch.velocity(),Medium.WATER));}
                else branches.add(new Branch(branch.at(),branch.velocity(),updated));
            }
            if(branches.size()>512)return deny("Declared native medium-history model bound exceeded");
        }return deny("Source aim lacks a spread-safe moving body contact interval");
    }
    private static Box envelope(Vec3[] from,Vec3[] to){
        double x=Double.POSITIVE_INFINITY,y=x,z=x,X=Double.NEGATIVE_INFINITY,Y=X,Z=X;
        for(var points:new Vec3[][]{from,to})for(var p:points){x=Math.min(x,p.x());y=Math.min(y,p.y());z=Math.min(z,p.z());X=Math.max(X,p.x());Y=Math.max(Y,p.y());Z=Math.max(Z,p.z());}
        return new Box(x,y,z,X,Y,Z);
    }
    public static Vec3 advance(Vec3 velocity,Medium medium){return scale(add(velocity,new Vec3(0,-GRAVITY,0)),medium==Medium.WATER?WATER_DRAG:AIR_DRAG);}
    private static Check deny(String reason){return new Check(false,reason,0,Double.NaN);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x())&&Double.isFinite(p.y())&&Double.isFinite(p.z());}
    static Vec3 add(Vec3 a,Vec3 b){return new Vec3(a.x()+b.x(),a.y()+b.y(),a.z()+b.z());}
    static Vec3 scale(Vec3 p,double k){return new Vec3(p.x()*k,p.y()*k,p.z()*k);}
    static Vec3 lerp(Vec3 a,Vec3 b,double k){return add(a,scale(new Vec3(b.x()-a.x(),b.y()-a.y(),b.z()-a.z()),k));}
}
