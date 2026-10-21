package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealSourceBufferPhasesTest {
    @Test void zeroDelaysStillKeepPostSwitchAndEndOnSeparateInvocations(){
        var p=new HealSourceBufferPhases();p.emitted();
        assertEquals(HealSourceBufferPhases.Step.WAIT,p.tick(true,0));
        assertEquals(HealSourceBufferPhases.Step.SWITCH_DECISION,p.tick(true,0));p.decisionMade(0);
        assertEquals(HealSourceBufferPhases.Step.END,p.tick(true,0));
    }
    @Test void oneDelaysDecrementThenSkipEachPhaseForOneFullInvocation(){
        var p=new HealSourceBufferPhases();p.emitted();p.tick(true,1);
        assertEquals(HealSourceBufferPhases.Step.WAIT,p.tick(true,0));
        assertEquals(HealSourceBufferPhases.Step.SWITCH_DECISION,p.tick(true,0));p.decisionMade(1);
        assertEquals(HealSourceBufferPhases.Step.WAIT,p.tick(true,0));assertEquals(HealSourceBufferPhases.Step.END,p.tick(true,0));
    }
    @Test void packetTransitionSkipsBufferSamplingButNotSeparateEnd(){
        var p=new HealSourceBufferPhases();p.emitted();p.posePacket();
        assertEquals(HealSourceBufferPhases.Step.SWITCH_DECISION,p.tick(false,1));p.decisionMade(0);
        assertEquals(HealSourceBufferPhases.Step.END,p.tick(false,1));
    }
}
