package dev.veltraph.client.combat.grind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FinishingToolWearTest {
 @Test void unchangedNativeToolNeedsNoInventedAttack(){assertTrue(FinishingToolWear.allowed(1,1,5,5,0,true));}
 @Test void actualSubmittedAttackBoundsDamageOnly(){assertTrue(FinishingToolWear.allowed(1,1,5,7,2,true));assertFalse(FinishingToolWear.allowed(1,1,5,8,2,true));}
 @Test void decisionOrNormalApiWithoutWireSubmissionCannotAdoptWear(){assertFalse(FinishingToolWear.allowed(1,1,5,6,0,true));}
 @Test void countComponentRepairAndUnknownAttackCountersDeny(){assertFalse(FinishingToolWear.allowed(1,2,5,6,1,true));assertFalse(FinishingToolWear.allowed(1,1,5,6,1,false));assertFalse(FinishingToolWear.allowed(1,1,5,4,1,true));assertFalse(FinishingToolWear.allowed(1,1,5,5,-1,true));}
}
