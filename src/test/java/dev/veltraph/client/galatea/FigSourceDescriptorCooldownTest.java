package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigSourceDescriptorCooldownTest {
    @Test void sameValuesDifferentDescriptorDoesNotAlias(){var c=new FigSourceDescriptorCooldown();Object a=new String("same"),b=new String("same");c.cannotDamage(a,100);assertTrue(c.contains(a));assertFalse(c.contains(b));}
    @Test void exactFiveSecondBoundaryRemainsCooling(){var c=new FigSourceDescriptorCooldown();var d=new Object();c.cannotDamage(d,100);c.expire(5100);assertTrue(c.contains(d));c.expire(5101);assertFalse(c.contains(d));}
    @Test void latestFeedbackRenewsOnlyItsOwnDescriptor(){var c=new FigSourceDescriptorCooldown();var a=new Object();var b=new Object();c.cannotDamage(a,0);c.cannotDamage(b,0);c.cannotDamage(a,1000);c.expire(5001);assertTrue(c.contains(a));assertFalse(c.contains(b));c.reset();assertEquals(0,c.size());}
}
