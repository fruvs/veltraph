package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HuntingFlightTest {
    private static final HuntingFlight.Contract FLIGHT=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,0);
    private static final Vec3 ZERO=new Vec3(0,0,0);
    private static HuntingFlight.Geometry floor(double y){return new HuntingFlight.Geometry(){
        public boolean clear(Vec3 from,Vec3 to){return from.y()>y&&to.y()>y;}
        public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}
        public boolean clearEnvelope(HuntingFlight.Box b){return b.minY()>y;}
    };}
    private static HuntingFlight.Box box(double x,double y){return new HuntingFlight.Box(x,y,-.25,x+.5,y+.4,.25);}
    @Test void reconstructsRecordedNativeFloorContactUsingActualGravityBeforeInertia(){
        var initial=new Vec3(-1.4217861497443174,-.19045737466121113,-.42088687498145316);
        var origin=new Vec3(4.7986363332813315,70.40000000596046-HuntingFlight.EYE_OFFSET,-1.0698420105145612);
        var first=HuntingFlight.advance(initial,HuntingFlight.Medium.AIR);var at=HuntingFlight.add(origin,first);
        var second=HuntingFlight.advance(first,HuntingFlight.Medium.WATER);
        assertEquals(-1.1260546582243887,second.x(),1e-14);assertEquals(-.19860224537303953,second.y(),1e-14);
        var hit=HuntingFlight.lerp(at,HuntingFlight.add(at,second),(70-at.y())/second.y());
        assertEquals(2.9275696615529667,hit.x(),1e-13);assertEquals(70,hit.y(),1e-13);assertEquals(-1.623727983090197,hit.z(),1e-13);
    }
    @Test void clearStraightEyeRayDoesNotAdmitLowerFallingNativeFlight(){
        var eye=new Vec3(0,.4,0);var aim=new Vec3(2.5,.18,0);
        assertTrue(floor(0).clear(eye,aim));
        var check=HuntingFlight.check(FLIGHT,eye,new Vec3(2.5,-.22,0),ZERO,box(2.3,.1),ZERO,3,floor(0));
        assertFalse(check.feasible());assertTrue(check.reason().contains("blocked"));
    }
    @Test void aCloserClearApproachCanReachSameUnchangedNativeBodyBeforeFloor(){
        var body=box(2.3,.1);var eye=new Vec3(1.4,.4,0);
        var check=HuntingFlight.check(FLIGHT,eye,new Vec3(1.15,-.12,0),ZERO,body,ZERO,3,floor(0));
        assertTrue(check.feasible(),check.reason());assertEquals(1,check.contactTick());
        assertEquals(.4,body.maxY()-body.minY(),1e-12);
    }
    @Test void downwardVanillaSpreadWithholdsMarginalFloorClearanceWithoutChangingLaunch(){
        var check=HuntingFlight.check(FLIGHT,new Vec3(0,.4,0),new Vec3(1,-.107,0),ZERO,box(2.1,.01),ZERO,3,floor(0));
        assertFalse(check.feasible());assertTrue(check.reason().contains("blocked")||check.reason().contains("spread"),check.reason());
    }
    @Test void targetContactBeforeFartherWallIsAllowedButWallBeforeTargetIsDenied(){
        var eye=new Vec3(0,1.4,0);var body=box(2.1,1.15);
        var far=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return b.x()<2.8&&a.y()>0&&b.y()>0;}public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}public boolean clearEnvelope(HuntingFlight.Box b){return b.maxX()<2.8&&b.minY()>0;}};
        assertTrue(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,ZERO,3,far).feasible());
        var near=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return b.x()<1.8;}public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}public boolean clearEnvelope(HuntingFlight.Box b){return b.maxX()<1.8;}};
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,ZERO,3,near).feasible());
    }
    @Test void nativeMovingBoxForecastAndShooterInertiaCanWithholdOtherwiseClearAim(){
        var eye=new Vec3(0,1.4,0);var body=box(2.1,1.15);
        assertTrue(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,new Vec3(.03,0,0),3,floor(0)).feasible());
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,new Vec3(.9,0,0),3,floor(0)).feasible());
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),new Vec3(0,-.8,0),body,ZERO,3,floor(0)).feasible());
    }
    @Test void unknownOrUnsupportedProtocolMediumAndInvalidNativeObservationNeverGuess(){
        var eye=new Vec3(0,1.4,0);var body=box(2.1,1.15);
        assertFalse(HuntingFlight.check(null,eye,new Vec3(1,0,0),ZERO,body,ZERO,3,floor(0)).feasible());
        var unknown=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return true;}public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.UNSUPPORTED;}public boolean clearEnvelope(HuntingFlight.Box b){return true;}};
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,ZERO,3,unknown).feasible());
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(Double.NaN,0,0),ZERO,body,ZERO,3,floor(0)).feasible());
        assertThrows(IllegalArgumentException.class,()->new HuntingFlight.Contract(null,15,3.3));
        assertThrows(IllegalArgumentException.class,()->new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,41,3.3));
    }
    @Test void lifetimeAndContactReachRemainHardCaps(){
        var eye=new Vec3(0,1.4,0);var body=box(2.1,1.15);
        assertFalse(HuntingFlight.check(new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,1,3.3,0),eye,new Vec3(1,0,0),ZERO,body,ZERO,3,floor(0)).feasible());
        assertFalse(HuntingFlight.check(FLIGHT,eye,new Vec3(1,0,0),ZERO,body,ZERO,2,floor(0)).feasible());
    }
    private static HuntingFlight.Geometry recordedWall(){return new HuntingFlight.Geometry(){
        public boolean clear(Vec3 a,Vec3 b){return a.x()>-2&&b.x()>-2&&a.y()>70&&b.y()>70;}
        public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}
        public boolean clearEnvelope(HuntingFlight.Box b){return b.minX()>-2&&b.minY()>70;}
    };}
    private static Vec3 recordedLook(){float radians=.017453292f,yaw=84.13114f,pitch=2.9229248f;
        float x=-(float)Math.sin(yaw*radians)*(float)Math.cos(pitch*radians),y=-(float)Math.sin(pitch*radians),z=(float)Math.cos(yaw*radians)*(float)Math.cos(pitch*radians);return new Vec3(x,y,z);}
    private static HuntingFlight.Box recordedBody(){return new HuntingFlight.Box(-1.7640129544549226,71.04643721207941,-.8836024179198012,-1.2640129544549226,71.44643721803988,-.3836024179198012);}
    @Test void reconstructsDNativeWallImpactAndRejectsItsActualServerBodyTrajectory(){
        var origin=new Vec3(-.01433456170084634,71.52000000327826,-.4999435886021046);
        var initial=new Vec3(-1.4880072023890971,-.059158749030110015,.15357410389259255);
        var first=HuntingFlight.advance(initial,HuntingFlight.Medium.AIR);var at=HuntingFlight.add(origin,first);var second=HuntingFlight.advance(first,HuntingFlight.Medium.WATER);
        var hit=HuntingFlight.lerp(at,HuntingFlight.add(at,second),(-2-at.x())/second.x());
        assertEquals(-2,hit.x(),1e-14);assertEquals(71.39058469570512,hit.y(),1e-13);assertEquals(-.2950072214745318,hit.z(),1e-14);
        var eye=new Vec3(origin.x(),origin.y()+HuntingFlight.EYE_OFFSET,origin.z());
        assertFalse(HuntingFlight.check(FLIGHT,eye,recordedLook(),ZERO,recordedBody(),new Vec3(.000055070344317678664,-.025820457217230586,-.02891747907886292),3,recordedWall()).feasible());
    }
    @Test void syntheticDelayedRenderBodyCannotReplaceReceivedNativePacketBody(){
        var eye=new Vec3(-.01433456170084634,71.62000000476837,-.4999435886021046);var received=recordedBody();
        // D did not record its client render box. This explicit delayed-pose example demonstrates
        // the observation bug without pretending these offsets were measured in that run.
        var render=received.move(new Vec3(0,.08,.18));
        assertTrue(HuntingFlight.check(FLIGHT,eye,recordedLook(),ZERO,render,ZERO,3,recordedWall()).feasible());
        assertFalse(HuntingFlight.check(FLIGHT,eye,recordedLook(),ZERO,received,new Vec3(.000055070344317678664,-.025820457217230586,-.02891747907886292),3,recordedWall()).feasible());
    }
    @Test void nominalTargetHitAndEmptySpreadSpaceDoNotEstablishActualSpreadContact(){
        var body=new HuntingFlight.Box(2.1,1.15,-.495,2.6,1.55,.005);
        var check=HuntingFlight.check(FLIGHT,new Vec3(0,1.4,0),new Vec3(1,0,0),ZERO,body,ZERO,3,floor(0));
        assertFalse(check.feasible(),"A positive-Z draw misses this exact body even though the nominal zero-Z ray enters it");
    }
    @Test void aColliderBetweenCornerRaysStillBlocksTheBoundedSpreadCorridor(){
        var narrow=new HuntingFlight.Geometry(){
            public boolean clear(Vec3 a,Vec3 b){return true;} // Outer sampled rays alone would miss a narrow internal obstruction.
            public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}
            public boolean clearEnvelope(HuntingFlight.Box b){return !(b.minX()<1&&b.maxX()>1&&b.minZ()<.002&&b.maxZ()>.002);}
        };
        var check=HuntingFlight.check(FLIGHT,new Vec3(0,1.4,0),new Vec3(1,0,0),ZERO,box(2.1,1.15),ZERO,3,narrow);
        assertFalse(check.feasible());assertEquals("Native spread corridor meets obstruction",check.reason());
    }
    private static Vec3 nativeLook(float yaw,float pitch){float r=.017453292f;return new Vec3(-(float)Math.sin(yaw*r)*(float)Math.cos(pitch*r),-(float)Math.sin(pitch*r),(float)Math.cos(yaw*r)*(float)Math.cos(pitch*r));}
    @Test void literalIFirstReceivedPoseRemainsFeasibleAcrossDeclaredTrackingLag(){
        var eye=new Vec3(2.7030785532722863,71.67473859307083,-1.0912749889433497);
        var body=new HuntingFlight.Box(.01416015625,70.967041015625,-1.803955078125,.51416015625,71.36704102158546,-1.303955078125);
        var velocity=new Vec3(-.048953183177684156,0,0);var acceptedPacketMovement=new Vec3(-.013699917632600922,-.02400659238438152,.003658335254642431);
        var bound=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,3);
        var check=HuntingFlight.check(bound,eye,nativeLook(-263.37845f,7.7500763f),acceptedPacketMovement,body,velocity,3,floor(70));
        assertTrue(check.feasible(),check.reason());assertEquals(2,check.contactTick());
    }
    @Test void literalISecondFreshPacketStillNeedsObservationAgeWindow(){
        var eye=new Vec3(.6883611742974729,70.40000000596046,3.2913163298814);
        var body=new HuntingFlight.Box(-1.448974609375,70,3.421875,-.948974609375,70.40000000596046,3.921875);
        var velocity=new Vec3(-.019532442165659525,-.0050051883049502255,-.06805835317096987);
        var acceptedPacketMovement=new Vec3(-.022983441525794346,0,-.0010614316414936376);
        var look=nativeLook(-282.92078f,7.1284957f);
        assertTrue(HuntingFlight.check(FLIGHT,eye,look,acceptedPacketMovement,body,velocity,3,floor(70)).feasible(),"The old zero-age assumption admits the recorded stale endpoint");
        var bounded=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,3);
        assertFalse(HuntingFlight.check(bounded,eye,look,acceptedPacketMovement,body,velocity,3,floor(70)).feasible(),"A fresh-client packet did not represent current native fish position in I");
    }
    @Test void missingObservationTimingBudgetCannotBecomeZeroAgeByDefault(){
        var legacy=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3);
        assertEquals(-1,legacy.maxObservationLagTicks());
        var check=HuntingFlight.check(legacy,new Vec3(0,1.4,0),new Vec3(1,0,0),ZERO,box(2.1,1.15),ZERO,3,floor(0));
        assertFalse(check.feasible());assertTrue(check.reason().contains("observation-lag"));
        assertThrows(IllegalArgumentException.class,()->new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,9));
    }
    @Test void nativeAabbInsideOriginDoesNotManufactureZeroDistanceContact(){
        var body=new HuntingFlight.Box(0,0,0,1,1,1);
        assertNull(body.clip(new Vec3(.5,.5,.5),new Vec3(2,.5,.5)));
        assertNotNull(body.clip(new Vec3(-1,.5,.5),new Vec3(.5,.5,.5)));
    }
    @Test void recordedSurfaceCodBodyCanHavePhysicalContactWithoutInventingWetFlag(){
        double x=6.3721923828125,y=71.9178466796875,z=-2.5323486328125;
        var body=new HuntingFlight.Box(x-.25,y,z-.25,x+.25,y+(double).3f,z+.25);
        double waterSurface=71+(double)(8f/9f);
        assertTrue(y>waterSurface,"Recorded Cod is genuinely dry above the actual native source-water height");
        var eye=new Vec3(x+.75,71.62,z);double aimY=y+HuntingTactics.aimHeight((double).3f,eye.y()-y,.81,.22);
        var medium=new HuntingFlight.Geometry(){
            public boolean clear(Vec3 a,Vec3 b){return a.y()>70&&b.y()>70;}
            public HuntingFlight.Medium medium(Vec3 at){return at.y()+.001<=waterSurface?HuntingFlight.Medium.WATER:HuntingFlight.Medium.AIR;}
            public boolean clearEnvelope(HuntingFlight.Box b){return b.minY()>70;}
        };
        assertTrue(HuntingFlight.check(FLIGHT,eye,new Vec3(x-eye.x(),aimY-eye.y(),0),ZERO,body,ZERO,3,medium).feasible());
    }
    @Test void fullyAirborneLiveCodUsesDeclaredNativeAirFlightWithoutWaterAssumption(){
        var body=new HuntingFlight.Box(6.1,72.1,-.25,6.6,72.4,.25);var eye=new Vec3(7.1,72.6,0);
        var air=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return a.y()>72&&b.y()>72;}public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.AIR;}public boolean clearEnvelope(HuntingFlight.Box b){return b.minY()>72;}};
        var direction=new Vec3(6.35-eye.x(),72.3253-eye.y(),0);
        assertTrue(HuntingFlight.check(FLIGHT,eye,direction,ZERO,body,ZERO,3,air).feasible());
    }
    @Test void literalNCurrentNativeInterceptPrecedesWallBeyondSourcePrediction(){
        var eye=new Vec3(.5350845677106938,70.40000000596046,3.1088882074939796);
        var direction=new Vec3(-.9335454702377319,-.035274237394332886,.3567192554473877);
        var predicted=new Vec3(-2.0359090177362305,70.30221503338488,4.204457597453256);
        var terrain=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return a.x()>-2&&b.x()>-2&&a.y()>70&&b.y()>70;}public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}public boolean clearEnvelope(HuntingFlight.Box b){return b.minX()>-2&&b.minY()>70;}};
        assertFalse(terrain.clear(eye,predicted),"The source lead ray crosses the actual wall after the fish");
        var contract=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,3);
        var render=new HuntingFlight.Box(-1.9491373707423918,70,4.001057939633029,-1.4491373707423918,70.40000000596046,4.501057939633029);
        var raw=new HuntingFlight.Box(-1.965087890625,70,3.949462890625,-1.465087890625,70.40000000596046,4.449462890625);
        var a=HuntingFlight.check(contract,eye,direction,ZERO,render,new Vec3(-.015950521308695897,0,-.05159505362098571),3,terrain);
        var b=HuntingFlight.check(contract,eye,direction,ZERO,raw,new Vec3(-.012329854117072547,-.0050051883049502255,-.0495635719953611),3,terrain);
        assertTrue(a.feasible(),a.reason());assertTrue(b.feasible(),b.reason());assertEquals(.834157971956768,a.contactFraction(),1e-12);assertEquals(.7112678756983652,b.contactFraction(),1e-12);
    }
    @Test void KnownAirWaterSpreadHistoriesAreIntegratedRatherThanInventedUnsupported(){
        var both=new HuntingFlight.Geometry(){public boolean clear(Vec3 a,Vec3 b){return a.y()>0&&b.y()>0;}public HuntingFlight.Medium medium(Vec3 at){return at.x()<1.485?HuntingFlight.Medium.AIR:HuntingFlight.Medium.WATER;}public HuntingFlight.Medium media(HuntingFlight.Box b){return HuntingFlight.Medium.BOTH;}public boolean clearEnvelope(HuntingFlight.Box b){return b.minY()>0;}};
        var body=new HuntingFlight.Box(2.1,1.1,-.25,2.6,1.5,.25);
        var check=HuntingFlight.check(FLIGHT,new Vec3(0,1.4,0),new Vec3(1,0,0),ZERO,body,ZERO,3,both);
        assertTrue(check.feasible(),check.reason());assertEquals(2,check.contactTick());
    }
    @Test void literalS2SourceFeasiblePhysicalPoseNeedsRotationRatherThanReapproach(){
        var eye=new Vec3(13.042477408853179,70.40000000596046,-.9389127152463874);
        var actual=new Vec3(.38325226306915283,-.07901343703269958,-.9202578663825989);
        var source=new Vec3(13.819691975913884-eye.x(),70.30663386308957-eye.y(),-2.913584390615142-eye.z());
        float yaw=net.minecraft.util.Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-source.x(),source.z()))),pitch=net.minecraft.util.Mth.wrapDegrees((float)-Math.toDegrees(Math.atan2(source.y(),Math.hypot(source.x(),source.z())))),rad=.017453292f;
        var nativeSource=new Vec3(-net.minecraft.util.Mth.sin(yaw*rad)*net.minecraft.util.Mth.cos(pitch*rad),-net.minecraft.util.Mth.sin(pitch*rad),net.minecraft.util.Mth.cos(yaw*rad)*net.minecraft.util.Mth.cos(pitch*rad));
        double angle=Math.toDegrees(Math.acos((actual.x()*source.x()+actual.y()*source.y()+actual.z()*source.z())/Math.sqrt((actual.x()*actual.x()+actual.y()*actual.y()+actual.z()*actual.z())*(source.x()*source.x()+source.y()*source.y()+source.z()*source.z()))));
        assertEquals(2.304999981283224,angle,1e-10);assertTrue(angle<3,"Source3degree gate remains unchanged; actual trajectory still matters");
        var terrain=new HuntingFlight.Geometry(){
            public boolean clear(Vec3 a,Vec3 b){return a.x()>-2&&b.x()>-2&&a.x()<15&&b.x()<15&&a.y()>70&&b.y()>70&&a.y()<77&&b.y()<77&&a.z()>-3&&b.z()>-3&&a.z()<5&&b.z()<5;}
            public HuntingFlight.Medium medium(Vec3 at){return HuntingFlight.Medium.WATER;}
            public boolean clearEnvelope(HuntingFlight.Box b){return b.minX()>-2&&b.maxX()<15&&b.minY()>70&&b.maxY()<77&&b.minZ()>-3&&b.maxZ()<5;}
        };
        var contract=new HuntingFlight.Contract(HuntingFlight.Model.VANILLA_SNOWBALL_26_3,15,3.3,3);
        var bodies=new HuntingFlight.Box[]{new HuntingFlight.Box(13.553076177835464,70,-2.9935546815395355,14.253076165914536,70.40000000596046,-2.2935546934604645),new HuntingFlight.Box(13.337744146585464,70,-2.9918456971645355,14.037744134664536,70.40000000596046,-2.2918457090854645)};
        var velocities=new Vec3[]{new Vec3(-.0642089805478463,0,.0005696614243788645),new Vec3(-.06653238112677773,-.0050051883049502255,.0005493499359092713)};
        double[] fractions={.9998410129991053,.9985796371550226};
        boolean movingBoth=true;
        for(int i=0;i<2;i++){
            var now=HuntingFlight.check(contract,eye,actual,ZERO,bodies[i],velocities[i],3,terrain);
            assertFalse(now.feasible());assertTrue(now.reason().contains("blocked"),"Never cast using the unconverged actual camera");
            var tracked=HuntingFlight.check(contract,eye,nativeSource,ZERO,bodies[i],velocities[i],3,terrain);
            assertTrue(tracked.feasible(),tracked.reason());assertEquals(1,tracked.contactTick());assertEquals(fractions[i],tracked.contactFraction(),1e-12);
            movingBoth&=HuntingFlight.check(contract,eye,nativeSource,new Vec3(.2,0,0),bodies[i],velocities[i],3,terrain).feasible();
        }
        assertFalse(movingBoth,"A stationary source plan cannot establish both moving native body views");
    }

}
