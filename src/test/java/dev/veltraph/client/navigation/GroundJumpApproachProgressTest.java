package dev.veltraph.client.navigation;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GroundJumpApproachProgressTest {
    @Test void actualStoppedQNeutralPrefixHasARealFiniteSuffixInsteadOfRepeatedImaginaryProgress(){var actual=new Vec3(.45069806424166753,70,7.3264942235206885);var velocity=new Vec3(0,-.0784000015258789,0);var target=new Vec3(.5,71,8.5);var plans=GroundJumpApproach.candidates(actual,velocity,-2.3684716f,target);var neutral=plans.stream().filter(p->p.key()==GroundJumpApproach.Key.NEUTRAL).findFirst().orElseThrow();assertEquals(actual,neutral.next());assertEquals(3,neutral.sequence().size());assertTrue(neutral.sequence().subList(1,3).stream().anyMatch(k->k!=GroundJumpApproach.Key.NEUTRAL));var next=GroundJumpApproach.remaining(actual,velocity,-2.3684716f,neutral.sequence().subList(1,3));assertEquals(2,next.sequence().size());assertEquals(neutral.sequence().get(1),next.key());}
    @Test void boundedSequenceConsumesOnlyActuallyPublishedStepAndCannotGrowOrResetDeadline(){var p=new Vec3(.45,70,7.3);var v=new Vec3(0,-.0784,0);var first=GroundJumpApproach.remaining(p,v,0,List.of(GroundJumpApproach.Key.NEUTRAL,GroundJumpApproach.Key.BACK_LEFT,GroundJumpApproach.Key.RIGHT));var second=GroundJumpApproach.remaining(first.next(),first.nextVelocity(),0,first.sequence().subList(1,3));var third=GroundJumpApproach.remaining(second.next(),second.nextVelocity(),0,second.sequence().subList(1,2));assertEquals(3,first.sequence().size());assertEquals(2,second.sequence().size());assertEquals(1,third.sequence().size());assertFalse(GroundJumpApproach.currentClock(100,1250,1301));}
}
