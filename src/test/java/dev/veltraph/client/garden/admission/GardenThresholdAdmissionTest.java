package dev.veltraph.client.garden.admission;
import org.junit.jupiter.api.Test;
import dev.veltraph.client.config.GardenAdmissionConfig;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.garden.admission.GardenThresholdAdmission.Result.*;
class GardenThresholdAdmissionTest {
    @Test void sourceDefaultsRemainIndependent(){assertFalse(GardenAdmissionConfig.AUTOSTART_VISITOR_DURING_FARMING.defaultValue());assertTrue(GardenAdmissionConfig.AUTOSTART_PESTS_DURING_FARMING.defaultValue());assertEquals("3.0:4.0",GardenAdmissionConfig.ACTIVATE_VISITOR_THRESHOLD.defaultValue());assertEquals("3.0:4.0",GardenAdmissionConfig.ACTIVATE_PEST_THRESHOLD.defaultValue());assertTrue(GardenAdmissionConfig.DO_VISITORS_IN_CONTEST_2.defaultValue());assertTrue(GardenAdmissionConfig.DO_PESTS_IN_CONTEST_2.defaultValue());assertFalse(GardenAdmissionConfig.ACTIVATE_VISITOR_INSTANTLY.defaultValue());assertFalse(GardenAdmissionConfig.ACTIVATE_PESTS_INSTANTLY.defaultValue());assertTrue(GardenAdmissionConfig.GARDEN_ADMISSION_BINDINGS.defaultValue().isEmpty());}
    @Test void visitorThresholdIsInclusiveAndRoundsUpAtHalf(){assertEquals(ADMIT,visitor(3,true,3.49,true,true,false,false));assertEquals(BELOW_THRESHOLD,visitor(3,true,3.5,true,true,false,false));assertEquals(ADMIT,visitor(4,true,3.99,true,true,false,false));}
    @Test void visitorPreservesSourceFailureOrder(){assertEquals(COUNT_UNKNOWN,visitor(null,false,3,false,false,true,false));assertEquals(COUNT_UNKNOWN,visitor(-1,true,3,true,true,false,true));assertEquals(NOT_READY,visitor(4,false,3,true,true,false,true));assertEquals(BELOW_THRESHOLD,visitor(2,true,3,false,false,true,false));assertEquals(DISABLED,visitor(4,true,3,false,false,true,false));assertEquals(CAPABILITY_DENIED,visitor(4,true,3,true,false,true,false));assertEquals(CONTEST_DENIED,visitor(4,true,3,true,true,true,false));}
    @Test void pestOptionalThresholdStillRequiresPositiveSourceCount(){assertEquals(ADMIT,pest(1,Double.NaN,false,true,true,false,false));assertEquals(NOT_READY,pest(0,Double.NaN,false,true,true,false,false));assertEquals(NOT_READY,pest(-2,3,false,true,true,false,false));assertEquals(COUNT_UNKNOWN,pest(-1,3,false,true,true,false,false));}
    @Test void pestThresholdInclusiveAndPriority(){assertEquals(BELOW_THRESHOLD,pest(3,3.5,true,true,true,false,true));assertEquals(ADMIT,pest(4,3.5,true,true,true,false,true));assertEquals(DISABLED,pest(1,3,true,false,false,true,false));assertEquals(CAPABILITY_DENIED,pest(4,3,true,true,false,true,false));assertEquals(CONTEST_DENIED,pest(4,3,true,true,true,true,false));}
    @Test void unknownCapabilitiesAndThresholdsNeverAdmit(){assertEquals(CAPABILITY_UNKNOWN,visitor(4,true,3,true,null,false,true));assertEquals(CAPABILITY_UNKNOWN,pest(4,3,true,true,null,false,true));assertEquals(THRESHOLD_UNKNOWN,visitor(4,true,Double.NaN,true,true,false,true));assertEquals(THRESHOLD_UNKNOWN,pest(4,Double.POSITIVE_INFINITY,true,true,true,false,true));}
    @Test void kotlinRoundingTiesTowardsPositiveInfinity(){assertEquals(-1,GardenThresholdAdmission.roundToInt(-1.5));assertEquals(0,GardenThresholdAdmission.roundToInt(-.5));assertEquals(Integer.MAX_VALUE,GardenThresholdAdmission.roundToInt(Double.MAX_VALUE));assertEquals(Integer.MIN_VALUE,GardenThresholdAdmission.roundToInt(-Double.MAX_VALUE));}
    @Test void originalRandomDrawOrderIsLazyAndOptionalPestCheckStillSamples(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();java.util.function.DoubleSupplier sample=()->{calls.incrementAndGet();return 3;};
        assertEquals(COUNT_UNKNOWN,GardenThresholdAdmission.visitor(null,true,sample,true,true,false,true));assertEquals(0,calls.get());
        assertEquals(NOT_READY,GardenThresholdAdmission.visitor(4,false,sample,true,true,false,true));assertEquals(0,calls.get());
        assertEquals(DISABLED,GardenThresholdAdmission.visitor(4,true,sample,false,true,false,true));assertEquals(1,calls.get());
        assertEquals(DISABLED,GardenThresholdAdmission.pest(4,sample,true,false,true,false,true));assertEquals(1,calls.get());
        assertEquals(ADMIT,GardenThresholdAdmission.pest(1,sample,false,true,true,false,true));assertEquals(2,calls.get());
    }
    @Test void sourceCapabilityQueryOccursOnlyAfterEarlierGatesPass(){
        var calls=new java.util.concurrent.atomic.AtomicInteger();java.util.function.Supplier<Boolean> capability=()->{calls.incrementAndGet();return true;};
        assertEquals(BELOW_THRESHOLD,GardenThresholdAdmission.visitorLazy(2,true,()->3,true,capability,false,true));assertEquals(0,calls.get());
        assertEquals(DISABLED,GardenThresholdAdmission.pestLazy(4,()->3,true,false,capability,false,true));assertEquals(0,calls.get());
        assertEquals(ADMIT,GardenThresholdAdmission.visitorLazy(4,true,()->3,true,capability,false,true));assertEquals(1,calls.get());
    }
    private GardenThresholdAdmission.Result visitor(Integer c,Boolean r,double t,boolean a,Boolean g,boolean j,boolean allow){return GardenThresholdAdmission.visitor(c,r,t,a,g,j,allow);}
    private GardenThresholdAdmission.Result pest(Integer c,double t,boolean check,boolean a,Boolean g,boolean j,boolean allow){return GardenThresholdAdmission.pest(c,t,check,a,g,j,allow);}
}
