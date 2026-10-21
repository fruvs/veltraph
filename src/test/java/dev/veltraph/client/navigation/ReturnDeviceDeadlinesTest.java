package dev.veltraph.client.navigation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ReturnDeviceDeadlinesTest {
    @Test void originalReturnDeadlineWinsOverFiveSecondDeviceAndSharedWindows(){
        assertEquals(1750,ReturnDeviceDeadlines.originalBound(1000,121000,1750));
        assertEquals(6000,ReturnDeviceDeadlines.originalBound(1000,121000,200000));
    }
    @Test void sharedDeadlineAlsoRemainsAnOriginalBound(){
        assertEquals(1700,ReturnDeviceDeadlines.originalBound(1000,1700,1800));
    }
    @Test void insufficientOrElapsedOriginalWindowRefusesInsteadOfExtending(){
        assertEquals(1500,ReturnDeviceDeadlines.originalBound(1000,121000,1500));
        for(long deadline:new long[]{-1,0,999,1000,1001,1499})assertEquals(-1,ReturnDeviceDeadlines.originalBound(1000,121000,deadline));
        assertEquals(-1,ReturnDeviceDeadlines.originalBound(1000,1499,121000));
    }
    @Test void invalidClockAndOverflowCannotMintAnEffectWindow(){
        assertEquals(-1,ReturnDeviceDeadlines.originalBound(-1,10000,10000));
        assertEquals(-1,ReturnDeviceDeadlines.originalBound(Long.MAX_VALUE-4999,Long.MAX_VALUE,Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE,ReturnDeviceDeadlines.originalBound(Long.MAX_VALUE-5000,Long.MAX_VALUE,Long.MAX_VALUE));
    }
    @Test void nativeCallbackCannotPublishAtOrAfterOriginalDeadline(){
        assertTrue(ReturnDeviceDeadlines.permitsOriginalWindow(1749,1000,1700,1700,1750));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1750,1000,1700,1700,1750));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1751,1000,1700,1700,1750));
    }
    @Test void rollbackBeforeStartPreviousObservationOrSharedFloorRefuses(){
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(999,1000,-1,1000,6000));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1400,1000,1401,1000,6000));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1400,1000,1300,1401,6000));
        assertTrue(ReturnDeviceDeadlines.permitsOriginalWindow(1401,1000,1401,1401,6000));
    }
    @Test void invalidOrExpiredSourceCannotCreateAWindow(){
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1000,-1,-1,0,6000));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1000,1000,-1,1000,1000));
        assertFalse(ReturnDeviceDeadlines.permitsOriginalWindow(1000,1000,-1,1000,-1));
    }
}
