package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.grind.CombatMovement;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Declared native stone-ground friction model and exact T narrow-melee starting operands. */
class GhostConstrainedMeleeStandoffTest {
    final Object owner=new Object(),player=new Object(),world=new Object(),connection=new Object(),job=new Object(),targetIdentity=new Object();
    final GhostConstrainedMeleeStandoff.Scope scope=new GhostConstrainedMeleeStandoff.Scope(owner,player,world,connection,job,targetIdentity,0);
    static final Vec3 TARGET=new Vec3(158.5,76,78.5);
    GhostConstrainedMeleeStandoff.Frame frame(long tick,double radius,boolean hit,boolean standing,boolean left,boolean right,boolean hazard,Vec3 target,Vec3 velocity){return new GhostConstrainedMeleeStandoff.Frame(scope,tick,new Vec3(target.x(),target.y(),target.z()-radius),target,velocity,2.25,hit,true,standing,left,right,hazard);}
    GhostConstrainedMeleeStandoff prepared(){var policy=new GhostConstrainedMeleeStandoff();for(int tick=0;tick<3;tick++)policy.observe(frame(tick,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0)));assertEquals(GhostConstrainedMeleeStandoff.Outcome.HOLD,policy.cachedEvidence().outcome());return policy;}
    record Replay(int reversals,int heldTicks,double finalRadius,double finalSpeed,int validHitTicks){}
    Replay replay(boolean modern){
        var movement=new CombatMovement();movement.reset();var policy=new GhostConstrainedMeleeStandoff();
        Vec3 position=new Vec3(158.4999752206199,76,76.50987361686701);double velocity=.11785906504049293;int prior=0,reversals=0,held=0,hits=0;
        for(int tick=0;tick<50;tick++){
            double radius=position.horizontalDistanceTo(TARGET);boolean exactHit=radius<3;
            boolean hold=modern&&policy.observe(new GhostConstrainedMeleeStandoff.Frame(scope,tick,position,TARGET,new Vec3(0,0,0),2.25,exactHit,true,true,false,false,false)).outcome()==GhostConstrainedMeleeStandoff.Outcome.HOLD;
            var keys=movement.choose(position,TARGET,0,2.25,false,hold,p->Math.abs(p.x()-158.5)<.205,List.of());
            int axis=(keys.forward()?1:0)-(keys.back()?1:0);if(axis!=0&&prior!=0&&axis!=prior)reversals++;if(axis!=0)prior=axis;if(hold)held++;if(exactHit)hits++;
            // Native private stone ground: .098 acceleration (.1*.98); .546 retained velocity (.6*.91).
            double displacement=velocity+axis*.098;position=new Vec3(position.x(),position.y(),position.z()+displacement);velocity=displacement*.546;
        }return new Replay(reversals,held,position.horizontalDistanceTo(TARGET),Math.abs(velocity),hits);
    }
    @Test void actualSharedLegacyScorerForcesRepeatedReversalsInTheMeasuredNarrowGeometry(){var old=replay(false);assertTrue(old.reversals()>=4,old.toString());assertEquals(0,old.heldTicks());}
    @Test void modernSafeStationaryChoiceSettlesWithoutReversingOrLosingNativeHitReach(){var next=replay(true);assertTrue(next.reversals()<=1,next.toString());assertTrue(next.heldTicks()>=40,next.toString());assertTrue(next.finalSpeed()<.002);assertTrue(next.finalRadius()>1.65&&next.finalRadius()<2.85);assertEquals(50,next.validHitTicks());}
    @Test void openSpaceKeepsExistingOrbitMotion(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,true,true,false,TARGET,new Vec3(0,0,0))).outcome());}
    @Test void observedTargetPositionChangeAndActualVelocityImmediatelyReengage(){
        var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,false,false,new Vec3(TARGET.x(),76,TARGET.z()+.03),new Vec3(0,0,0))).outcome());
        p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,.03))).outcome());
    }
    @Test void positiveAndNegativeVerticalNativeVelocityImmediatelyReengage(){for(double dy:new double[]{.03,-.03}){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,false,false,TARGET,new Vec3(0,dy,0))).outcome());assertEquals(0,p.cachedEvidence().stationaryTargetTicks());}}
    @Test void repeatedSubPointZeroFiveVerticalDisplacementCannotAccumulateStationaryTicks(){var p=prepared();for(int tick=3;tick<10;tick++){var next=new Vec3(TARGET.x(),TARGET.y()+.03*(tick-2),TARGET.z());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(tick,2.25,true,true,false,false,false,next,new Vec3(0,0,0))).outcome());assertEquals(0,p.cachedEvidence().stationaryTargetTicks());}}
    @Test void threeDimensionalDiagonalDisplacementUsesCombinedMagnitude(){var p=prepared();var diagonal=new Vec3(TARGET.x()+.011,TARGET.y()+.011,TARGET.z());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,false,false,diagonal,new Vec3(0,0,0))).outcome());}
    @Test void downwardTargetTeleportAndLateralJumpRevokeHeldStateOnTheSameObservation(){for(Vec3 next:List.of(new Vec3(TARGET.x(),TARGET.y()-2,TARGET.z()),new Vec3(TARGET.x()+2,TARGET.y(),TARGET.z()))){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,false,false,next,new Vec3(0,0,0))).outcome());}}
    @Test void freshHazardHitLossOrProtectedUnsafeStandingNeverHolds(){for(int condition=0;condition<3;condition++){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,condition!=0,condition!=1,false,false,condition==2,TARGET,new Vec3(0,0,0))).outcome());}}
    @Test void nativeSupportLossOrAirborneStateRevokesHold(){var p=prepared();var f=frame(3,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0));assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(new GhostConstrainedMeleeStandoff.Frame(f.scope(),f.tick(),f.position(),f.target(),f.targetVelocity(),f.desiredRadius(),true,false,true,false,false,false)).outcome());}
    @Test void boundedExitHysteresisLetsMomentumSettleButNeverExtendsAttackReach(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.HOLD,p.observe(frame(3,1.89,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(4,1.64,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.HOLD,p.observe(frame(3,2.84,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(4,2.86,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());}
    @Test void foreignOwnerNativeContextJobTargetOrEpochCannotInheritHeldState(){for(int changed=0;changed<7;changed++){var p=prepared();var s=new GhostConstrainedMeleeStandoff.Scope(changed==0?new Object():owner,changed==1?new Object():player,changed==2?new Object():world,changed==3?new Object():connection,changed==4?new Object():job,changed==5?new Object():targetIdentity,changed==6?1:0);var f=frame(3,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0));assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(new GhostConstrainedMeleeStandoff.Frame(s,f.tick(),f.position(),f.target(),f.targetVelocity(),f.desiredRadius(),true,true,true,false,false,false)).outcome());}}
    @Test void oldDuplicateTicksRejectWithoutAdvancingThePriorContext(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.REJECTED,p.observe(frame(2,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());assertEquals(GhostConstrainedMeleeStandoff.Outcome.REJECTED,p.observe(frame(1,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());}
    @Test void lostCadenceResetsStationaryObservationAndExplicitResetClearsEvidence(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(7,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0))).outcome());p.reset();assertEquals(Long.MIN_VALUE,p.cachedEvidence().tick());assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.cachedEvidence().outcome());}
    @Test void oneAvailableTangentPreservesSituationalMovementInsteadOfFreezing(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.MOVE,p.observe(frame(3,2.25,true,true,false,true,false,TARGET,new Vec3(0,0,0))).outcome());}
    @Test void unavailableOrNonfiniteNativeFrameCannotAuthorizeHold(){var p=prepared();assertEquals(GhostConstrainedMeleeStandoff.Outcome.REJECTED,p.observe(null).outcome());var f=frame(4,2.25,true,true,false,false,false,TARGET,new Vec3(0,0,0));assertEquals(GhostConstrainedMeleeStandoff.Outcome.REJECTED,p.observe(new GhostConstrainedMeleeStandoff.Frame(scope,4,new Vec3(Double.NaN,76,0),f.target(),f.targetVelocity(),2.25,true,true,true,false,false,false)).outcome());}
}
