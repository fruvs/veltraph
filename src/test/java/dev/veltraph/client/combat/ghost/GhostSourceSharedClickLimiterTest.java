package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GhostSourceSharedClickLimiterTest {
    @Test void spacingEqualityAdmitsAndEarlierRefuses(){var g=new GhostSourceSharedClickLimiter(n->0);assertEquals(GhostSourceSharedClickLimiter.Result.ADMITTED,g.admit(1000));assertEquals(GhostSourceSharedClickLimiter.Result.SPACING,g.admit(1049));assertEquals(GhostSourceSharedClickLimiter.Result.ADMITTED,g.admit(1050));}
    @Test void cleanupStrictEqualityRetainsAndCapIsConstructorLifetime(){int[] draws={0};var g=new GhostSourceSharedClickLimiter(n->{draws[0]++;return 0;});for(int i=0;i<10;i++)assertEquals(GhostSourceSharedClickLimiter.Result.ADMITTED,g.admit(1000+i*50));assertEquals(GhostSourceSharedClickLimiter.Result.COUNT_LIMIT,g.admit(2000));assertEquals(GhostSourceSharedClickLimiter.Result.ADMITTED,g.admit(2001));assertEquals(1,draws[0]);}
    @Test void sharedInstanceDoesNotResetAcrossConsumerLifetime(){var g=new GhostSourceSharedClickLimiter(n->4);for(int i=0;i<14;i++)assertEquals(GhostSourceSharedClickLimiter.Result.ADMITTED,g.admit(1000+i*50));assertEquals(GhostSourceSharedClickLimiter.Result.COUNT_LIMIT,g.admit(1700));assertEquals(14,g.recordedCount());}
}
