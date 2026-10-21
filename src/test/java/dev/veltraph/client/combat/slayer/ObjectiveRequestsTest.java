package dev.veltraph.client.combat.slayer;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ObjectiveRequestsTest {
    @Test void aRefusedProtocolExpiresInsteadOfInventingRemoval() {
        var c=new ObjectiveRequests();var id=UUID.randomUUID();
        for(int i=0;i<12;i++)c.tick(id,8,true,true,false,i*200);
        assertEquals(ObjectiveRequests.State.EXPIRED,c.tick(id,8,true,true,false,20000).state());
        assertEquals(ObjectiveRequests.State.ACKNOWLEDGED,c.tick(id,-1,false,false,false,true,21000).state());
    }
    @Test void mandatoryAlignmentAndUnknownTrackingHaveABoundedLifetime() {
        var c=new ObjectiveRequests();var id=UUID.randomUUID();
        assertEquals(ObjectiveRequests.State.PAUSED,c.tick(id,-1,true,false,true,0).state());
        assertEquals(ObjectiveRequests.State.PAUSED,c.tick(id,-1,false,false,true,89999).state());
        assertEquals(ObjectiveRequests.State.EXPIRED,c.tick(id,-1,false,false,true,90000).state());
    }
    @Test void ignoredPacketsRemainUnconfirmedAndEventuallyStop() {
        var c=new ObjectiveRequests();var id=UUID.randomUUID();
        for(int i=0;i<12;i++)assertEquals(ObjectiveRequests.State.REQUEST,c.tick(id,8,true,true,false,i*200).state());
        assertEquals(ObjectiveRequests.State.STALLED,c.tick(id,8,true,true,false,3000).state());
    }
    @Test void actualProgressResetsBudgetButZeroIsNotRemoval() {
        var c=new ObjectiveRequests();var id=UUID.randomUUID();
        for(int i=0;i<12;i++)c.tick(id,8,true,true,false,i*200);
        assertEquals(ObjectiveRequests.State.REQUEST,c.tick(id,7,true,true,false,3000).state());
        assertEquals(ObjectiveRequests.State.WAIT,c.tick(id,0,true,true,false,3300).state());
        assertEquals(ObjectiveRequests.State.ACKNOWLEDGED,c.tick(id,-1,false,false,false,true,3400).state());
        assertEquals(ObjectiveRequests.State.NONE,c.tick(null,-1,false,false,false,3500).state());
    }
    @Test void lethalPausesAndMissingPlatesDoNotCompleteOrSpendRequests() {
        var c=new ObjectiveRequests();var id=UUID.randomUUID();
        assertEquals(ObjectiveRequests.State.PAUSED,c.tick(id,8,true,true,true,0).state());
        assertEquals(ObjectiveRequests.State.PAUSED,c.tick(id,8,true,false,false,200).state());
        assertEquals(1,c.tick(id,8,true,true,false,500).requests());
        assertEquals(ObjectiveRequests.State.PAUSED,c.tick(id,8,false,false,false,700).state());
        assertEquals(ObjectiveRequests.State.NONE,c.tick(null,-1,false,false,false,800).state());
    }
    @Test void receiptsExpireAreBoundedAndWorldScoped() {
        Object first=new Object(),second=new Object();
        for(int i=0;i<100;i++)SlayerParticleSignal.receive(first,new dev.veltraph.client.world.Vec3(i,70,0),i);
        assertEquals(64,SlayerParticleSignal.read(first,100).size());assertTrue(SlayerParticleSignal.read(second,100).isEmpty());
        assertTrue(SlayerParticleSignal.read(first,700).isEmpty());
        SlayerParticleSignal.receive(second,new dev.veltraph.client.world.Vec3(0,70,0),800);assertTrue(SlayerParticleSignal.read(first,800).isEmpty());
    }
}
