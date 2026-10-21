package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Pose;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostHealDirectReturnTest {
    private static final Vec3 HOME=new Vec3(.5,70,.5);
    private static final GhostHealReturn.Pose POSE=new GhostHealReturn.Pose(HOME,-90,-13,0,1401,0);
    private static GhostHealDirectReturn.Scope scope(){return new GhostHealDirectReturn.Scope(new Object(),new Object(),new Object(),new Object(),POSE);}
    private static GhostHealDirectReturn.Frame frame(GhostHealDirectReturn.Scope s,long tick,Vec3 position,float yaw,double vx,double vz){return new GhostHealDirectReturn.Frame(s,tick,position,yaw,79.17236f,vx,vz,true);}
    private static final class Geometry implements GhostHealDirectReturn.Geometry {
        boolean current=true,clear=true;int checks;int failAt=-1;final List<Vec3> starts=new ArrayList<>(),endpoints=new ArrayList<>();
        public boolean current(){return current;}
        public GhostHealDirectReturn.Failure failure(){return GhostHealDirectReturn.Failure.BLOCKED;}
        public boolean clearEnvelope(Vec3 from,Vec3 to){starts.add(from);endpoints.add(to);checks++;return clear&&checks!=failAt;}
    }
    private static GhostHealDirectReturn driver(GhostHealDirectReturn.Scope s){var d=new GhostHealDirectReturn();d.start(s);return d;}

    @Test void recordedBIHalfTurnUsesCurrentYawBackwardKeysImmediately(){
        var s=scope();var d=driver(s);var geometry=new Geometry();
        var actual=new Vec3(2.156913715562566,70,.5);
        var original=new dev.veltraph.client.navigation.WalkController(1L);
        original.start(List.of(),HOME,0,GhostHealReturn.RETURN_POSITION_RADIUS);
        var floor=new dev.veltraph.client.navigation.GroundPathfinder.Terrain(){public boolean clear(dev.veltraph.client.world.BlockPos p){return p.y()>=70;}public boolean floor(dev.veltraph.client.world.BlockPos p){return p.y()==69;}};
        assertFalse(original.tick(new dev.veltraph.client.navigation.WalkController.Input(actual,-90,true,100),floor,4000).forward(),"Original native-current yaw needs its180-degree turn before forward input");
        var result=d.advance(frame(s,92,actual,-90,.19970707490831807,0),geometry);
        assertEquals(GhostHealDirectReturn.Outcome.DIRECT,result.outcome());
        assertTrue(result.intent().back());assertFalse(result.intent().forward());assertFalse(result.intent().left()||result.intent().right()||result.intent().jump());
        assertEquals(-90,s.pose().yaw());assertEquals(-13,s.pose().pitch());
        assertEquals(1401,s.pose().health(),"Movement never replaces the raw health basis");
    }
    @Test void directionComesFromActualYawNotFutureSteeringYaw(){
        for(float yaw:new float[]{-90,0,90,180}){
            var s=scope();var out=driver(s).advance(frame(s,1,new Vec3(2,70,.5),yaw,0,0),new Geometry()).intent();
            assertNotNull(out);assertFalse(out.forward()&&out.back());assertFalse(out.left()&&out.right());
            if(yaw==-90)assertTrue(out.back());if(yaw==0)assertTrue(out.right());if(yaw==90)assertTrue(out.forward());if(yaw==180)assertTrue(out.left());
        }
    }
    @Test void measuredApproachSpeedReleasesPropulsionBeforeTightGoalOvershoot(){
        var s=scope();var result=driver(s).advance(frame(s,130,new Vec3(.7,70,.5),90,-.11718165289804823,0),new Geometry());
        assertEquals(GhostSourceWalkSteering.Outcome.BRAKING,result.intent().outcome());assertFalse(result.intent().movingKeys());
        var fsm=new GhostHealReturn();fsm.start(POSE,0);fsm.tick(HOME,-90,80,1401,0,true,true,0,100,300,4000);
        assertEquals(GhostHealReturn.Action.WALK_RETURN,fsm.tick(new Vec3(.7,70,.5),90,79,1558,0,true,true,.11718165289804823,500,300,4000));
        assertEquals(GhostHealReturn.State.RETURN,fsm.state(),"Braking is not an arrival or health ACK");
    }
    @Test void initialUnsupportedEnvelopeRetainsOriginalPathWithoutLaterPromotion(){
        var s=scope();var d=driver(s);var g=new Geometry();g.clear=false;
        assertEquals(GhostHealDirectReturn.Outcome.PATH,d.advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),g).outcome());
        g.clear=true;assertEquals(GhostHealDirectReturn.Outcome.PATH,d.advance(frame(s,5,new Vec3(1,70,.5),-90,0,0),g).outcome());
        assertEquals(GhostHealDirectReturn.Mode.PATH,d.mode());assertEquals(1,g.checks);
    }
    @Test void activeDirectObstructionStopsRatherThanQuietlyChangingDrivers(){
        var s=scope();var d=driver(s);var g=new Geometry();
        assertEquals(GhostHealDirectReturn.Outcome.DIRECT,d.advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),g).outcome());
        g.clear=false;var result=d.advance(frame(s,2,new Vec3(1.9,70,.5),-90,-.1,0),g);
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome());assertNull(result.intent());assertEquals(GhostHealDirectReturn.Mode.DIRECT,d.mode());
    }
    @Test void everyNativeObjectAndExactCapturedPoseBelongsToSameRecovery(){
        var s=scope();var changed=List.of(
            new GhostHealDirectReturn.Scope(new Object(),s.player(),s.world(),s.connection(),s.pose()),
            new GhostHealDirectReturn.Scope(s.parent(),new Object(),s.world(),s.connection(),s.pose()),
            new GhostHealDirectReturn.Scope(s.parent(),s.player(),new Object(),s.connection(),s.pose()),
            new GhostHealDirectReturn.Scope(s.parent(),s.player(),s.world(),new Object(),s.pose()),
            new GhostHealDirectReturn.Scope(s.parent(),s.player(),s.world(),s.connection(),new GhostHealReturn.Pose(HOME,-90,-13,0,1401,0)));
        for(var foreign:changed){var result=driver(s).advance(frame(foreign,1,new Vec3(2,70,.5),-90,0,0),new Geometry());assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome());assertNull(result.intent());}
    }
    @Test void directFramesRejectDuplicateReversedAndSkippedNativeTicks(){
        for(long next:new long[]{1,0,3}){var s=scope();var d=driver(s);d.advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),new Geometry());assertEquals(GhostHealDirectReturn.Outcome.REJECTED,d.advance(frame(s,next,new Vec3(1.9,70,.5),-90,0,0),new Geometry()).outcome());}
    }
    @Test void contextChangeDuringNativeGeometryReadCannotEmitKeys(){
        for(boolean queryResult:new boolean[]{false,true}){
            var s=scope();var result=driver(s).advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),new GhostHealDirectReturn.Geometry(){boolean current=true;public boolean current(){return current;}public boolean clearEnvelope(Vec3 a,Vec3 b){current=false;return queryResult;}});
            assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome(),"Nested custody change cannot authorize direct or original-path keys, even when geometry returns false");
            assertNull(result.intent());
        }
    }
    @Test void actualKeyEnvelopeIncludesMeasuredMomentumAndCurrentYaw(){
        var s=scope();var g=new Geometry();var out=driver(s).advance(frame(s,1,new Vec3(2,70,.5),-90,.2,0),g);
        assertEquals(GhostHealDirectReturn.Outcome.DIRECT,out.outcome());assertEquals(1,g.checks);
        var union=GhostHealDirectReturn.envelope(frame(s,1,new Vec3(2,70,.5),-90,.2,0),HOME,out.intent());
        assertEquals(new Vec3(1.85,70,.5),union.keys());assertEquals(new Vec3(2.2,70,.5),union.coast());
        assertEquals(HOME,g.starts.getLast());assertEquals(union.maximum(),g.endpoints.getLast());assertEquals(2.2,union.maximum().x());
        var active=driver(s);active.advance(frame(s,1,new Vec3(2,70,.5),-90,.2,0),new Geometry());
        var blocked=new Geometry();blocked.failAt=1;var bad=active.advance(frame(s,2,new Vec3(2,70,.5),-90,.2,0),blocked);
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,bad.outcome());assertNull(bad.intent());assertEquals(1,blocked.checks);
    }
    @Test void airborneMalformedOrRevokedFrameCannotProduceMovement(){
        var s=scope();var g=new Geometry();g.current=false;
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,driver(s).advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),g).outcome());
        for(var f:List.of(new GhostHealDirectReturn.Frame(s,1,new Vec3(2,70,.5),-90,79,0,0,false),frame(s,1,new Vec3(Double.NaN,70,.5),-90,0,0),frame(s,1,new Vec3(2,70,.5),Float.NaN,0,0),frame(s,1,new Vec3(2,70,.5),-90,Double.POSITIVE_INFINITY,0)))assertEquals(GhostHealDirectReturn.Outcome.REJECTED,driver(s).advance(f,new Geometry()).outcome());
    }
    @Test void budgetPendingGrantsNoIntentAndNextOrdinaryFrameReprovesWholeUnion(){
        var s=scope();var d=driver(s);var calls=new int[1];
        var incomplete=new GhostHealDirectReturn.Geometry(){public boolean current(){return true;}public boolean clearEnvelope(Vec3 a,Vec3 b){calls[0]++;return false;}public GhostHealDirectReturn.Failure failure(){return GhostHealDirectReturn.Failure.INCOMPLETE;}};
        var pending=d.advance(frame(s,92,new Vec3(2,70,.5),-90,.2,0),incomplete);
        assertEquals(GhostHealDirectReturn.Outcome.PENDING,pending.outcome());assertNull(pending.intent());assertEquals(GhostHealDirectReturn.Mode.UNSELECTED,d.mode());assertEquals(1,calls[0]);
        var fresh=new Geometry();var complete=d.advance(frame(s,93,new Vec3(2.1,70,.5),-90,.1,0),fresh);
        assertEquals(GhostHealDirectReturn.Outcome.DIRECT,complete.outcome());assertEquals(1,fresh.checks);assertEquals(2.2,fresh.endpoints.getLast().x());
    }
    @Test void unknownFailedProofIsPendingNeverInitialPathPermission(){
        var s=scope();var d=driver(s);var unknown=new GhostHealDirectReturn.Geometry(){public boolean current(){return true;}public boolean clearEnvelope(Vec3 a,Vec3 b){return false;}};
        var result=d.advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),unknown);
        assertEquals(GhostHealDirectReturn.Outcome.PENDING,result.outcome());assertNull(result.intent());assertEquals(GhostHealDirectReturn.Mode.UNSELECTED,d.mode());
    }
    @Test void activeDirectBudgetDeferralRetainsNoOldGeometryGrantAndObstructionStillStops(){
        var s=scope();var d=driver(s);d.advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),new Geometry());
        var budget=new GhostHealDirectReturn.Geometry(){public boolean current(){return true;}public boolean clearEnvelope(Vec3 a,Vec3 b){return false;}public GhostHealDirectReturn.Failure failure(){return GhostHealDirectReturn.Failure.INCOMPLETE;}};
        var pending=d.advance(frame(s,2,new Vec3(1.9,70,.5),-90,-.1,0),budget);
        assertEquals(GhostHealDirectReturn.Outcome.PENDING,pending.outcome());assertNull(pending.intent());assertEquals(GhostHealDirectReturn.Mode.DIRECT,d.mode());
        var blocked=new Geometry();blocked.clear=false;assertEquals(GhostHealDirectReturn.Outcome.REJECTED,d.advance(frame(s,3,new Vec3(1.8,70,.5),-90,-.1,0),blocked).outcome());
    }
    @Test void lateContextLossDuringCachedClassificationRejectsBeforePendingOrFallback(){
        var s=scope();var g=new GhostHealDirectReturn.Geometry(){boolean current=true;public boolean current(){return current;}public boolean clearEnvelope(Vec3 a,Vec3 b){return false;}public GhostHealDirectReturn.Failure failure(){current=false;return GhostHealDirectReturn.Failure.INCOMPLETE;}};
        var result=driver(s).advance(frame(s,1,new Vec3(2,70,.5),-90,0,0),g);
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome());assertNull(result.intent());
    }
    @Test void unionContainsGoalCurrentCoastAndProjectedKeysForEveryNativeYawSector(){
        for(float yaw:new float[]{-180,-135,-90,-45,0,45,90,135}){
            var s=scope();var f=frame(s,1,new Vec3(2,70,.5),yaw,.2,-.13);var result=driver(s).advance(f,new Geometry());
            var envelope=GhostHealDirectReturn.envelope(f,HOME,result.intent());
            for(var point:List.of(HOME,f.position(),envelope.coast(),envelope.keys())){
                assertTrue(point.x()>=envelope.minimum().x()&&point.x()<=envelope.maximum().x());assertTrue(point.z()>=envelope.minimum().z()&&point.z()<=envelope.maximum().z());
            }
        }
    }
    @Test void knownMergedDomainExpansionRetainsOriginalInitialPathWithoutRaisingReadBound(){
        var goal=new Vec3(-32,70,0);var pose=new GhostHealReturn.Pose(goal,90,-13,0,1401,0);
        var s=new GhostHealDirectReturn.Scope(new Object(),new Object(),new Object(),new Object(),pose);
        var f=frame(s,1,new Vec3(0,70,0),90,.1,0);var g=new Geometry();var d=driver(s);var result=d.advance(f,g);
        assertEquals(32,f.position().distanceTo(goal));assertEquals(GhostHealDirectReturn.Outcome.PATH,result.outcome());assertNull(result.intent());assertEquals(0,g.checks);
        assertEquals(GhostHealDirectReturn.Mode.PATH,d.mode());
        // Individually allowed position→goal/coast/key rays cannot justify an
        // enlarged diagonal query. No native clearance is invented or cached.
        var active=driver(s);active.advance(frame(s,1,new Vec3(-31,70,0),90,0,0),new Geometry());var denied=new Geometry();
        assertEquals(GhostHealDirectReturn.Outcome.REJECTED,active.advance(frame(s,2,new Vec3(0,70,0),90,.1,0),denied).outcome());assertEquals(0,denied.checks);
    }
    @Test void pendingProofDoesNotResetOriginalFourSecondRecoveryDeadline(){
        var s=scope();var d=driver(s);var h=new GhostHealReturn();h.start(POSE,0);h.tick(HOME,-90,80,1401,0,true,true,0,100,300,4000);
        var budget=new GhostHealDirectReturn.Geometry(){public boolean current(){return true;}public boolean clearEnvelope(Vec3 a,Vec3 b){return false;}public GhostHealDirectReturn.Failure failure(){return GhostHealDirectReturn.Failure.INCOMPLETE;}};
        for(int tick=1;tick<=5;tick++){
            assertEquals(GhostHealDirectReturn.Outcome.PENDING,d.advance(frame(s,tick,new Vec3(2,70,.5),-90,0,0),budget).outcome());
            assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(new Vec3(2,70,.5),-90,79,1558,0,true,true,0,500+tick*100L,300,4000));
        }
        assertEquals(GhostHealReturn.Action.FAILED,h.tick(new Vec3(2,70,.5),-90,79,1558,0,true,true,0,4001,300,4000));
    }
    @Test void nativeTopFacesMustCoverEntireFootRectangleWithoutTinyGaps(){
        var full=new AABB(0,0,0,1,1,1);assertTrue(McGhostHealReturnClearance.covers(List.of(full),.2,.8,.2,.8));
        var gap=List.of(new AABB(0,0,0,.49,1,1),new AABB(.51,0,0,1,1,1));
        assertFalse(McGhostHealReturnClearance.covers(gap,.2,.8,.2,.8),"Corner-only support would miss the central.02 gap");
        assertTrue(McGhostHealReturnClearance.covers(List.of(new AABB(0,0,0,.5,1,1),new AABB(.5,0,0,1,1,1)),.2,.8,.2,.8));
        assertFalse(McGhostHealReturnClearance.covers(List.of(),.2,.8,.2,.8));
    }
    @Test void samePositionScaleOrActualPoseBodyChangeRevokesCertifiedNativeEnvelope(){
        var body=new AABB(.2,70,.2,.8,71.8,.8);
        assertTrue(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,new AABB(.2,70,.2,.8,71.8,.8),.6f,1.8f,Pose.STANDING));
        assertFalse(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,new AABB(.1,70,.1,.9,71.8,.9),.6f,1.8f,Pose.STANDING),"Same native center can acquire a wider actual body");
        assertFalse(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,body,.8f,1.8f,Pose.STANDING),"Standing scale changed before actual body rebuild");
        assertFalse(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,body,.6f,2f,Pose.STANDING));
        assertFalse(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,new AABB(.2,70,.2,.8,71.5,.8),.6f,1.8f,Pose.STANDING),"Native body height changed with standing dimensions unchanged");
        assertFalse(McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,body,.6f,1.8f,Pose.CROUCHING),"Native pose may change before its box rebuild");
        for(int mutation:new int[]{0,1,2}){
            var scope=scope();var changedDuringQuery=new GhostHealDirectReturn.Geometry(){
                float width=.6f;AABB actual=body;Pose pose=Pose.STANDING;
                public boolean current(){return McGhostHealReturnClearance.sameBody(body,.6f,1.8f,Pose.STANDING,actual,width,1.8f,pose);}
                public boolean clearEnvelope(Vec3 from,Vec3 to){if(mutation==0)width=.8f;else if(mutation==1)actual=new AABB(.1,70,.1,.9,71.8,.9);else pose=Pose.CROUCHING;return false;}
            };
            var result=driver(scope).advance(frame(scope,1,new Vec3(2,70,.5),-90,0,0),changedDuringQuery);
            assertEquals(GhostHealDirectReturn.Outcome.REJECTED,result.outcome(),"Body/scale/pose change inside failed native query is stale custody, never fallback permission");assertNull(result.intent());
        }
    }
    @Test void directPointStillNeedsOriginalNativeSpeedGroundViewAndTimeoutPredicates(){
        var h=new GhostHealReturn();h.start(POSE,0);h.tick(HOME,-90,80,1401,0,true,true,0,100,300,4000);
        assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(HOME,-90,-13,1558,0,true,true,.021,500,300,4000));
        assertEquals(GhostHealReturn.Action.WALK_RETURN,h.tick(HOME,-90,-13,1558,0,true,false,0,550,300,4000));
        assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(HOME,-90,79,1558,0,true,true,.02,600,300,4000));
        assertEquals(GhostHealReturn.Action.RESTORE_VIEW,h.tick(HOME,-90,79,1558,0,true,true,0,650,300,4000));
        assertEquals(GhostHealReturn.Action.COMPLETE,h.tick(HOME,-90,-13,1558,0,true,true,0,700,300,4000));
        var timed=new GhostHealReturn();timed.start(POSE,0);assertEquals(GhostHealReturn.Action.FAILED,timed.tick(HOME,-90,-13,1401,0,true,true,0,4001,300,4000));
    }
}
