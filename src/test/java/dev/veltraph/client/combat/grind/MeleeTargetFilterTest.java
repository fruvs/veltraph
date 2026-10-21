package dev.veltraph.client.combat.grind;

import dev.veltraph.client.world.Vec3;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeTargetFilterTest {
    private final Vec3 player=new Vec3(0,70,0);
    private final Predicate<CombatTarget> bounds=t->t.position().x()>=2&&t.position().x()<=5
            &&t.position().y()>=69&&t.position().y()<=72&&t.position().z()>=-1&&t.position().z()<=1;
    private CombatTarget body(int id,double x){return new CombatTarget(id,"Wolf",new Vec3(x,70,0),true,false);}
    @Test void closerExteriorBodyCannotDisplaceValidInteriorTarget(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);var outside=body(1,1);var inside=body(2,3);
        var loop=new CombatLoop();var selected=loop.tick(filter.candidates(List.of(outside,inside)),player,"Wolf",3,16,true,true,1000,5000);
        assertEquals(inside,selected.target());assertEquals(CombatLoop.Action.AIM,selected.action());
        assertEquals(CombatLoop.Action.ATTACK,loop.tick(filter.candidates(List.of(outside,inside)),player,"Wolf",3,16,true,true,1100,5000).action());
    }
    @Test void exteriorOnlyWaitsWithoutAnAttackOrKill(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);var loop=new CombatLoop();
        var decision=loop.tick(filter.candidates(List.of(body(1,1),body(2,6))),player,"Wolf",3,16,true,true,1000,5000);
        assertEquals(CombatLoop.Action.WAIT,decision.action());assertNull(decision.target());assertEquals(0,loop.kills());
    }
    @Test void freshBodyPositionRevokesPreviouslyAcceptedObservation(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);var initial=body(1,3);var fresh=body(1,1.99);
        assertTrue(filter.accepts(initial));assertFalse(filter.accepts(fresh));
        var loop=new CombatLoop();loop.tick(filter.candidates(List.of(initial)),player,"Wolf",3,16,false,true,1000,5000);
        var decision=loop.tick(filter.candidates(List.of(fresh,body(2,2.5))),player,"Wolf",3,16,true,true,1100,5000);
        assertEquals(2,decision.target().id());assertEquals(CombatLoop.Action.AIM,decision.action());assertEquals(0,loop.kills());
    }
    @Test void exactEdgesAndVerticalAndDepthBoundsUseTheSuppliedPredicate(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);
        assertTrue(filter.accepts(new CombatTarget(1,"Wolf",new Vec3(2,69,-1),true,false)));
        assertTrue(filter.accepts(new CombatTarget(1,"Wolf",new Vec3(5,72,1),true,false)));
        for(var p:List.of(new Vec3(1.999,70,0),new Vec3(5.001,70,0),new Vec3(3,68.999,0),new Vec3(3,72.001,0),new Vec3(3,70,-1.001),new Vec3(3,70,1.001)))
            assertFalse(filter.accepts(new CombatTarget(1,"Wolf",p,true,false)));
    }
    @Test void genericDefaultStillSelectsTheNearestBody(){
        var filter=new MeleeGrindModule.TargetFilter(null);var outside=body(1,1);var inside=body(2,3);
        assertEquals(List.of(outside,inside),filter.candidates(List.of(outside,inside)));
        var selected=new CombatLoop().tick(filter.candidates(List.of(outside,inside)),player,"Wolf",3,16,true,true,1000,5000);
        assertEquals(outside,selected.target());
    }
    @Test void parentPolicyDoesNotOverrideProtectionNameRangeOrAttackReadiness(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);
        var protectedBody=new CombatTarget(1,"Wolf",new Vec3(2,70,0),true,true);
        var foreign=new CombatTarget(2,"Cow",new Vec3(2,70,0),true,false);
        var loop=new CombatLoop();assertNull(loop.tick(filter.candidates(List.of(protectedBody,foreign,body(3,5))),player,"Wolf",3,4,true,true,1000,5000).target());
        var selected=List.of(body(4,2));loop.tick(filter.candidates(selected),player,"Wolf",3,16,true,true,1100,5000);
        assertEquals(CombatLoop.Action.AIM,loop.tick(filter.candidates(selected),player,"Wolf",3,16,true,false,1200,5000).action());
    }
    @Test void cancellationClearsTheSelectedBodyAndRequiresFreshAimOnRestart(){
        var filter=new MeleeGrindModule.TargetFilter(bounds);var loop=new CombatLoop();var bodies=filter.candidates(List.of(body(1,2)));
        loop.tick(bodies,player,"Wolf",3,16,false,true,1000,5000);loop.reset();assertEquals(-1,loop.targetId());
        assertEquals(CombatLoop.Action.AIM,loop.tick(bodies,player,"Wolf",3,16,true,true,1100,5000).action());
    }
}
