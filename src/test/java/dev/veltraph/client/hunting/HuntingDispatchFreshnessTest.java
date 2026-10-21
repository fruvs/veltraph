package dev.veltraph.client.hunting;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HuntingDispatchFreshnessTest {
    @Test void renderedTicksWithoutNewReceivedEndpointCannotAuthorizeDispatch(){
        var f=new HuntingDispatchFreshness();var packet=new Vec3(1,71,0);
        assertFalse(f.observe(1,"STANDING",1.62,packet).ready());
        assertFalse(f.observe(2,"STANDING",1.62,packet).ready());
        assertTrue(f.observe(3,"STANDING",1.62,new Vec3(1,71,-.03)).ready());
        assertFalse(f.observe(4,"STANDING",1.62,new Vec3(1,71,-.03)).ready());
    }
    @Test void swimmingToStandingTransitionNeedsTwoActualPlayerTicks(){
        var f=new HuntingDispatchFreshness();assertFalse(f.observe(1,"SWIMMING",.4,new Vec3(1,71,0)).ready());
        assertFalse(f.observe(2,"STANDING",1.62,new Vec3(1,71,-.03)).ready());
        assertTrue(f.observe(3,"STANDING",1.62,new Vec3(1,71,-.06)).ready());
    }
    @Test void duplicateCallbacksAndOwnerResetNeverReuseTimingAuthority(){
        var f=new HuntingDispatchFreshness();f.observe(1,"STANDING",1.62,new Vec3(1,71,0));
        assertTrue(f.observe(2,"STANDING",1.62,new Vec3(1,71,-.03)).ready());
        assertFalse(f.observe(2,"STANDING",1.62,new Vec3(1,71,-.06)).ready());
        f.reset();assertFalse(f.observe(3,"STANDING",1.62,new Vec3(1,71,-.09)).ready());
    }
}
