package dev.veltraph.client.chocolate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Self-contained actual-old-API red probe: compile/run against unchanged H before production stage. */
class ChocolateLegacyUnsafeReconcileRedTest {
    @Test void booleanWithoutOriginalInspectionCannotManufactureSpendOrClearUnknown(){var port=new ChocolateFactoryTest.Port();port.delivery=ChocolateFactoryRuntime.Delivery.UNKNOWN;var rt=new ChocolateFactoryRuntime(port);assertTrue(rt.select(ChocolateFactoryTest.profile(),ChocolateFactoryTest.observation(1,1,"200","old"),"employee",ChocolateFactoryTest.n("100"),ChocolateFactoryTest.n("0"),1000));rt.tick(ChocolateFactoryTest.profile(),1000);assertThrows(IllegalStateException.class,()->rt.reconcileUnknown(true));assertEquals(ChocolateFactoryRuntime.State.UNKNOWN,rt.state());assertEquals(0,rt.spent().signum());assertTrue(rt.acknowledgedUpgrades().isEmpty());assertEquals(1,port.clicks);}
}
