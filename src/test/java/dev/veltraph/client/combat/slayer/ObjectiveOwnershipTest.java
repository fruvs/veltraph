package dev.veltraph.client.combat.slayer;

import dev.veltraph.client.world.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ObjectiveOwnershipTest {
    @Test void uncertaintyCanRejectAnApproachingSecondBossButCannotExpandPositiveOwnership() {
        var c=new ObjectiveOwnership();
        assertTrue(c.associateWithMargin(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),3,1.5,List.of(boss(own,1),boss(other,4))).isEmpty());
        c.reset();assertTrue(c.associateWithMargin(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),3,1.5,List.of(boss(own,4))).isEmpty());
        c.reset();assertEquals(own,c.associateWithMargin(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),3,1.5,List.of(boss(own,2))).orElseThrow());
    }
    private final UUID own=UUID.randomUUID(),other=UUID.randomUUID(),objective=UUID.randomUUID();
    private ObjectiveOwnership.Boss boss(UUID id,double x) {return new ObjectiveOwnership.Boss(id,SlayerSignals.Family.BLAZE,new Vec3(x,70,0));}
    @Test void overlapCannotAuthorizeEvenTheNearestBoss() {
        var c=new ObjectiveOwnership();assertTrue(c.associate(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),22,List.of(boss(own,1),boss(other,4))).isEmpty());
        assertTrue(c.associate(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),22,List.of(boss(own,1))).isEmpty());
    }
    @Test void establishedOwnershipSurvivesBossMotionAndOverlap() {
        var c=new ObjectiveOwnership();assertEquals(own,c.associate(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),22,List.of(boss(own,1))).orElseThrow());
        assertEquals(own,c.associate(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),22,List.of(boss(other,0),boss(own,25))).orElseThrow());
    }
    @Test void familiesRemovalAndResetCannotLeakBindings() {
        var c=new ObjectiveOwnership();assertTrue(c.associate(objective,SlayerSignals.Family.SPIDER,new Vec3(0,70,0),5,List.of(boss(own,1))).isEmpty());
        c.retain(Set.of());assertEquals(own,c.associate(objective,SlayerSignals.Family.BLAZE,new Vec3(0,70,0),22,List.of(boss(own,1))).orElseThrow());
        c.reset();assertTrue(c.owner(objective).isEmpty());
    }
}
