package dev.veltraph.client.navigation;

import dev.veltraph.client.config.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicRouteQueryAndConfigFenceTest {
    @Test void individualNativeQueryReturningAfterBudgetCannotGrantRoutePermission(){
        var now=new AtomicLong(100);var fence=new DynamicRouteQueryFence(now::get,()->true,50);
        assertFalse(fence.query(()->{now.set(151);return true;}));assertFalse(fence.available());
        var called=new AtomicBoolean();assertFalse(fence.query(()->{called.set(true);return true;}));assertFalse(called.get());
    }
    @Test void scopeRevocationInsideSuccessfulNativeQueryCannotPublishIt(){
        var current=new AtomicBoolean(true);var fence=new DynamicRouteQueryFence(()->100,current::get,50);
        assertFalse(fence.query(()->{current.set(false);return true;}));
    }
    @Test void configurationChangedAndRevertedAndReplacementStoreRevokeTheOriginalJourney(){
        var cfg=new MapConfigService();var stamp=DynamicRouteConfigStamp.capture(cfg).orElseThrow();assertTrue(stamp.current(cfg));
        double old=cfg.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);cfg.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old+.1);cfg.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old);
        assertFalse(stamp.current(cfg));assertFalse(stamp.current(new MapConfigService()));
        var next=DynamicRouteConfigStamp.capture(cfg).orElseThrow();var approach=cfg.get(TravelConfig.TRAVEL_APPROACH_MODE);
        cfg.set(TravelConfig.TRAVEL_APPROACH_MODE,ApproachMode.WALK_ONLY);cfg.set(TravelConfig.TRAVEL_APPROACH_MODE,approach);assertFalse(next.current(cfg));
    }
    @Test void oldJourneyCannotCancelNewerSameOwnerEpochOrReplacementNativeContext(){
        Object p=new Object(),w=new Object(),c=new Object(),a=new Object();var custody=new DynamicRouteCustody(p,w,c,a,9);
        var cancels=new AtomicInteger();
        assertFalse(custody.cancelIfMatching(p,w,c,a,10,cancels::incrementAndGet));assertEquals(0,cancels.get());
        assertTrue(custody.cancelIfMatching(p,w,c,a,9,cancels::incrementAndGet));assertEquals(1,cancels.get());
        assertTrue(custody.matches(p,w,c,a,9));assertFalse(custody.matches(p,w,c,a,10));
        assertFalse(custody.matches(new Object(),w,c,a,9));assertFalse(custody.matches(p,new Object(),c,a,9));
        assertFalse(custody.matches(p,w,new Object(),a,9));assertFalse(custody.matches(p,w,c,new Object(),9));
    }
    @Test void revokedContextOrChangedAndRevertedConfigurationAfterGeometryPublishesNoEffects(){
        var cfg=new MapConfigService();var stamp=DynamicRouteConfigStamp.capture(cfg).orElseThrow();
        var current=new AtomicBoolean(true);var effects=new AtomicInteger();Runnable camera=effects::incrementAndGet,keys=effects::incrementAndGet;
        double old=cfg.get(RotationConfig.ROTATION_SPEED_MULTIPLIER);cfg.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old+.1);cfg.set(RotationConfig.ROTATION_SPEED_MULTIPLIER,old);
        assertFalse(DynamicRouteCustody.publish(()->current.get()&&stamp.current(cfg),camera,keys));assertEquals(0,effects.get());
        var fresh=DynamicRouteConfigStamp.capture(cfg).orElseThrow();current.set(false);
        assertFalse(DynamicRouteCustody.publish(()->current.get()&&fresh.current(cfg),camera,keys));assertEquals(0,effects.get());
    }

    @Test void scopeChangeDuringCameraPublicationCannotPublishKeys(){
        var current=new AtomicBoolean(true);var keys=new AtomicInteger();
        assertFalse(DynamicRouteCustody.publish(current::get,()->current.set(false),keys::incrementAndGet));assertEquals(0,keys.get());
    }

}
