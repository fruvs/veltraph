package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.Optional;
/** Fresh braking-ray hint for an actually active owned sprint on its admitted broad course. */
public final class GroundSprintContinuation {
    private GroundSprintContinuation(){}
    public static Optional<Vec3> hint(boolean owned,boolean nativeSprinting,Vec3 actual,float yaw,float outgoingYaw,WalkController.ContinuationBasis basis){
        if(!owned||!nativeSprinting||!GroundLocomotionMotion.finite(actual)||!Float.isFinite(yaw)||!Float.isFinite(outgoingYaw)||basis==null||!GroundLocomotionMotion.finite(basis.previous())||basis.points().isEmpty()||basis.points().size()>33||basis.broadEdges().size()!=basis.points().size())return Optional.empty();
        List<Vec3> course=basis.points();
        float turn=WalkController.wrap(outgoingYaw-yaw);if(Math.abs(turn)>=16)return Optional.empty();float middle=yaw+turn/2;
        double angle=Math.toRadians(middle);Vec3 end=new Vec3(actual.x()-Math.sin(angle)*3.000001,actual.y(),actual.z()+Math.cos(angle)*3.000001);
        if(actual.horizontalDistanceTo(course.getLast())<3.000001)return Optional.empty();
        Vec3 previous=basis.previous();
        for(Vec3 next:course){if(!GroundLocomotionMotion.finite(next)||Math.abs(next.y()-actual.y())>=.05)return Optional.empty();previous=next;}
        boolean actualCovered=false;previous=basis.previous();
        for(int i=0;i<course.size();i++){var next=course.get(i);if(basis.broadEdges().get(i)&&inside(actual,previous,next))actualCovered=true;previous=next;}
        if(!actualCovered)return Optional.empty();
        if(!covered(actual,end,basis))return Optional.empty();
        return Optional.of(end);
    }
    /** Exact rational clipping of the represented-double ray against finite original strips.
     * Squared lateral inequalities avoid normalized-dot rounding at shared edge boundaries. */
    static boolean covered(Vec3 a,Vec3 b,WalkController.ContinuationBasis basis){
        if(!GroundLocomotionMotion.finite(a)||!GroundLocomotionMotion.finite(b)||basis==null||basis.points().size()!=basis.broadEdges().size())return false;
        var intervals=new java.util.ArrayList<Interval>();Vec3 previous=basis.previous();
        for(int i=0;i<basis.points().size();i++){Vec3 next=basis.points().get(i);if(basis.broadEdges().get(i)){var interval=clip(a,b,previous,next);if(interval!=null)intervals.add(interval);}previous=next;}
        intervals.sort((x,y)->x.low.compareTo(y.low));Rational through=Rational.ZERO;boolean started=false;
        for(var interval:intervals){if(!started){if(interval.low.compareTo(Rational.ZERO)>0)return false;started=true;}if(interval.low.compareTo(through)>0)return false;if(interval.high.compareTo(through)>0)through=interval.high;if(through.compareTo(Rational.ONE)>=0)return true;}
        return false;
    }
    private record Interval(Rational low,Rational high){}
    private record Rational(java.math.BigDecimal numerator,java.math.BigDecimal denominator) implements Comparable<Rational>{
        static final Rational ZERO=new Rational(java.math.BigDecimal.ZERO,java.math.BigDecimal.ONE),ONE=new Rational(java.math.BigDecimal.ONE,java.math.BigDecimal.ONE);
        Rational {if(denominator.signum()<0){numerator=numerator.negate();denominator=denominator.negate();}if(denominator.signum()==0)throw new IllegalArgumentException("Zero rational denominator");}
        public int compareTo(Rational other){return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));}
    }
    private static java.math.BigDecimal exact(double value){return new java.math.BigDecimal(value);}
    private static Interval clip(Vec3 start,Vec3 end,Vec3 a,Vec3 b){
        if(!GroundLocomotionMotion.finite(a)||!GroundLocomotionMotion.finite(b))return null;
        var dx=exact(b.x()).subtract(exact(a.x()));var dz=exact(b.z()).subtract(exact(a.z()));var length2=dx.multiply(dx).add(dz.multiply(dz));
        if(length2.compareTo(exact(.001).multiply(exact(.001)))<=0)return null;
        var x=exact(start.x()).subtract(exact(a.x()));var z=exact(start.z()).subtract(exact(a.z()));var rx=exact(end.x()).subtract(exact(start.x()));var rz=exact(end.z()).subtract(exact(start.z()));
        var along=x.multiply(dx).add(z.multiply(dz));var alongDelta=rx.multiply(dx).add(rz.multiply(dz));Rational low=Rational.ZERO,high=Rational.ONE;
        if(alongDelta.signum()==0){if(along.signum()<0||along.compareTo(length2)>0)return null;}
        else {var first=new Rational(along.negate(),alongDelta);var last=new Rational(length2.subtract(along),alongDelta);var lo=first.compareTo(last)<0?first:last;var hi=first.compareTo(last)<0?last:first;if(lo.compareTo(low)>0)low=lo;if(hi.compareTo(high)<0)high=hi;}
        if(low.compareTo(high)>0)return null;
        // A rational interval can cover lateral boundaries only when both endpoint cross
        // products satisfy cross² <= .7²*length². Convexity then covers its entire interior.
        var cross=x.multiply(dz).subtract(z.multiply(dx));var crossDelta=rx.multiply(dz).subtract(rz.multiply(dx));
        if(!lateral(low,cross,crossDelta,length2)||!lateral(high,cross,crossDelta,length2))return null;
        return new Interval(low,high);
    }
    private static boolean lateral(Rational t,java.math.BigDecimal origin,java.math.BigDecimal delta,java.math.BigDecimal length2){
        var value=origin.multiply(t.denominator).add(delta.multiply(t.numerator));var margin=exact(.7);
        return value.multiply(value).compareTo(margin.multiply(margin).multiply(length2).multiply(t.denominator.multiply(t.denominator)))<=0;
    }
    private static boolean inside(Vec3 p,Vec3 a,Vec3 b){double dx=b.x()-a.x(),dz=b.z()-a.z(),length=Math.hypot(dx,dz);if(length<=.001)return false;dx/=length;dz/=length;double x=p.x()-a.x(),z=p.z()-a.z();double along=x*dx+z*dz;return along>=0&&along<=length&&Math.abs(x*dz-z*dx)<=.7;}
}
