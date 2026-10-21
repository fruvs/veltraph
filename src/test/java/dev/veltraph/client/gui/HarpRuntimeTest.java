package dev.veltraph.client.gui;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class HarpRuntimeTest {
    // These explicit fixture contracts are not recovered source numeric constants.
    final HarpRuntime.TimingLimits fixtureLimits=new HarpRuntime.TimingLimits(OptionalLong.of(1000),OptionalLong.of(0));
    List<String> blank() { return new ArrayList<>(Collections.nCopies(54,"minecraft:air")); }
    void warm(HarpRuntime runtime,List<String> grid) {
        for(int i=0;i<10;i++) assertTrue(runtime.nativeTick(1,grid,i*50L,OptionalLong.of(0),true,10,fixtureLimits).isEmpty());
    }
    OptionalInt nativeTick(HarpRuntime runtime,List<String> grid,long millis,long latency,boolean automatic,int delay) {
        return runtime.nativeTick(1,grid,millis,OptionalLong.of(latency),automatic,delay,fixtureLimits);
    }
    @Test void clicksRecoveredOffsetOnceAfterConfiguredDelay() {
        var runtime=new HarpRuntime();var grid=blank();assertTrue(runtime.tick(1,grid,0,2).isEmpty());
        grid.set(28,"minecraft:green_wool");assertTrue(runtime.tick(1,grid,1,2).isEmpty());
        assertTrue(runtime.tick(1,grid,2,2).isEmpty());assertEquals(37,runtime.tick(1,grid,3,2).orElseThrow());
        assertTrue(runtime.tick(1,grid,4,2).isEmpty());
    }
    @Test void dropsQueuedClickWhenScreenChangesAndIgnoresShortInventory() {
        var runtime=new HarpRuntime();var grid=blank();runtime.tick(1,grid,0,2);grid.set(30,"minecraft:red_wool");runtime.tick(1,grid,1,2);
        assertTrue(runtime.tick(2,grid,3,2).isEmpty());
        assertTrue(runtime.tick(2,List.of("minecraft:red_wool"),4,0).isEmpty());
    }
    @Test void firstChangeSetsClockAndRetainsSnapshotUntilTheFollowingTick() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:green_wool");assertTrue(nativeTick(runtime,grid,500,0,true,10).isEmpty());
        assertEquals(HarpRuntime.TimingState.FIRST_CHANGE,runtime.timingState());assertEquals(0,runtime.sampleCount());
        assertEquals(37,nativeTick(runtime,grid,550,0,true,10).orElseThrow());
        assertEquals(1.0,runtime.averageTicks(550,1000));assertEquals(1,runtime.sampleCount());
        assertTrue(nativeTick(runtime,grid,600,0,true,10).isEmpty());assertEquals(1,runtime.sampleCount());
    }
    @Test void adaptiveDelayUsesFullNativeLatencyAndRoundedCurrentMean() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:green_wool");nativeTick(runtime,grid,500,50,true,10);
        assertEquals(37,nativeTick(runtime,grid,550,50,true,10).orElseThrow());
        grid.set(28,"minecraft:blue_wool");assertTrue(nativeTick(runtime,grid,1000,50,true,10).isEmpty());
        assertEquals(4.0,runtime.averageTicks(1000,1000)); // Mean of 0 and (450-50)/50=8.
        assertTrue(nativeTick(runtime,grid,1050,50,true,10).isEmpty());
        assertTrue(nativeTick(runtime,grid,1100,50,true,10).isEmpty());
        assertEquals(37,nativeTick(runtime,grid,1150,50,true,10).orElseThrow());
    }
    @Test void strictSampleExpiryReturnsZeroAndDoesNotClampNegativeSourceSamples() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");nativeTick(runtime,grid,500,100,true,10);
        assertEquals(37,nativeTick(runtime,grid,550,100,true,10).orElseThrow());
        assertEquals(-1.0,runtime.averageTicks(1550,1000));
        assertEquals(0.0,runtime.averageTicks(1551,1000));assertEquals(0,runtime.sampleCount());
    }
    @Test void onlyHeadAgesAndAtMostOneNoteIsDispatchedPerNativeTick() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");nativeTick(runtime,grid,500,0,false,4);nativeTick(runtime,grid,550,0,false,4);
        grid.set(28,"minecraft:air");grid.set(29,"minecraft:blue_wool");nativeTick(runtime,grid,600,0,false,4);
        assertEquals(2,runtime.queuedSourceNotes());assertTrue(nativeTick(runtime,grid,650,0,false,4).isEmpty());
        assertEquals(37,nativeTick(runtime,grid,700,0,false,4).orElseThrow());
        assertTrue(nativeTick(runtime,grid,750,0,false,4).isEmpty());assertTrue(nativeTick(runtime,grid,800,0,false,4).isEmpty());
        assertTrue(nativeTick(runtime,grid,850,0,false,4).isEmpty());assertEquals(38,nativeTick(runtime,grid,900,0,false,4).orElseThrow());
    }
    @Test void ownDispatchCooldownDefersEligibleHeadUntilItsStrictBoundary() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        var cooldown=new HarpRuntime.TimingLimits(OptionalLong.of(1000),OptionalLong.of(200));
        grid.set(28,"minecraft:red_wool");runtime.nativeTick(1,grid,500,OptionalLong.of(0),true,10,cooldown);
        assertEquals(37,runtime.nativeTick(1,grid,550,OptionalLong.of(0),true,10,cooldown).orElseThrow());
        grid.set(28,"minecraft:blue_wool");assertTrue(runtime.nativeTick(1,grid,600,OptionalLong.of(0),true,10,cooldown).isEmpty());
        assertTrue(runtime.nativeTick(1,grid,749,OptionalLong.of(0),true,10,cooldown).isEmpty());
        assertEquals(37,runtime.nativeTick(1,grid,750,OptionalLong.of(0),true,10,cooldown).orElseThrow());
    }
    @Test void unreadableLimitsOrMissingLatencyNeverBecomeSyntheticTiming() {
        var runtime=new HarpRuntime();var grid=blank();
        assertTrue(runtime.nativeTick(1,grid,0,OptionalLong.of(0),true,10,HarpRuntime.TimingLimits.unresolvedSource()).isEmpty());
        assertEquals(HarpRuntime.TimingState.UNSUPPORTED_SOURCE_LIMITS,runtime.timingState());
        assertTrue(runtime.nativeTick(1,grid,0,OptionalLong.empty(),true,10,fixtureLimits).isEmpty());
        assertEquals(HarpRuntime.TimingState.MISSING_NATIVE_LATENCY,runtime.timingState());assertEquals(0,runtime.sampleCount());
    }
    @Test void actualOtherMenuInputClockSurvivesHarpResetAndDefersClick() {
        var runtime=new HarpRuntime();var grid=blank();
        runtime.onNativeContainerInput(500);runtime.reset();warm(runtime,grid);
        var cooldown=new HarpRuntime.TimingLimits(OptionalLong.of(1000),OptionalLong.of(200));
        grid.set(28,"minecraft:red_wool");runtime.nativeTick(1,grid,500,OptionalLong.of(0),true,10,cooldown);
        assertTrue(runtime.nativeTick(1,grid,550,OptionalLong.of(0),true,10,cooldown).isEmpty());
        assertTrue(runtime.nativeTick(1,grid,699,OptionalLong.of(0),true,10,cooldown).isEmpty());
        assertEquals(37,runtime.nativeTick(1,grid,700,OptionalLong.of(0),true,10,cooldown).orElseThrow());
    }
    @Test void replacedMenuAndExplicitResetClearClockSamplesWarmupAndQueue() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:green_wool");nativeTick(runtime,grid,500,0,false,10);nativeTick(runtime,grid,550,0,false,10);
        assertEquals(1,runtime.queuedSourceNotes());assertEquals(1,runtime.sampleCount());
        assertTrue(runtime.nativeTick(2,grid,600,OptionalLong.of(0),true,10,fixtureLimits).isEmpty());
        assertEquals(0,runtime.sampleCount());assertEquals(0,runtime.queuedSourceNotes());runtime.reset();
        assertEquals(HarpRuntime.TimingState.WAITING_FOR_SNAPSHOT,runtime.timingState());
    }
    @Test void sourceDefaultsAndPersistedManualDelayRemainIndependent() {
        var config=new dev.veltraph.client.config.MapConfigService();
        assertTrue(config.get(HarpConfig.AUTOMATIC_HARP_TICK_RATE_DETECTION));
        assertEquals(10,config.get(dev.veltraph.client.config.MiscConfig.AUTO_HARP_TICK_DELAY));
        config.set(dev.veltraph.client.config.MiscConfig.AUTO_HARP_TICK_DELAY,2);
        config.set(HarpConfig.AUTOMATIC_HARP_TICK_RATE_DETECTION,false);
        assertEquals(2,config.get(dev.veltraph.client.config.MiscConfig.AUTO_HARP_TICK_DELAY));
    }
    @Test void recoveredSourceLimitsBindOneSecondRetentionAndOneHundredMillisCommonInputGate() {
        var limits=HarpRuntime.TimingLimits.recoveredSource();assertTrue(limits.complete());
        assertEquals(1000,limits.sampleRetentionMillis().orElseThrow());assertEquals(100,limits.clickCooldownMillis().orElseThrow());
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");runtime.nativeTick(1,grid,500,OptionalLong.of(0),true,10,limits);
        assertEquals(37,runtime.nativeTick(1,grid,550,OptionalLong.of(0),true,10,limits).orElseThrow());
        grid.set(28,"minecraft:air");grid.set(29,"minecraft:blue_wool");
        assertTrue(runtime.nativeTick(1,grid,600,OptionalLong.of(0),true,10,limits).isEmpty());
        assertTrue(runtime.nativeTick(1,grid,649,OptionalLong.of(0),true,10,limits).isEmpty());
        assertEquals(38,runtime.nativeTick(1,grid,650,OptionalLong.of(0),true,10,limits).orElseThrow());
    }
    @Test void recoveredEvictionIsByTimestampAndAverageSumIntMaxShareStrictBoundary() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");nativeTick(runtime,grid,500,25,true,10);nativeTick(runtime,grid,550,25,true,10);
        grid.set(28,"minecraft:blue_wool");nativeTick(runtime,grid,675,0,true,10);
        assertEquals(1.5,runtime.averageTicks(1550,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(3.0,runtime.sumTicks(1550,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(2,runtime.maxWholeTicks(1550,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(2.5,runtime.averageTicks(1551,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(2.5,runtime.sumTicks(1675,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(0,runtime.maxWholeTicks(1676,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(0.0,runtime.averageTicks(1676,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
        assertEquals(0.0,runtime.sumTicks(1676,HarpRuntime.SOURCE_SAMPLE_RETENTION_MILLIS));
    }
    @Test void sourceMaxCastsNegativeDoubleTowardZeroBeforeMax() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");nativeTick(runtime,grid,500,120,true,10);nativeTick(runtime,grid,550,120,true,10);
        assertEquals(-1.4,runtime.averageTicks(550,1000),1e-10);
        assertEquals(-1,runtime.maxWholeTicks(550,1000));assertEquals(-1.4,runtime.sumTicks(550,1000),1e-10);
    }
    @Test void diagnosticSampleReceiptDoesNotExtendTheActualOneSecondWindowAndClearsWithScope() {
        var runtime=new HarpRuntime();var grid=blank();warm(runtime,grid);
        grid.set(28,"minecraft:red_wool");nativeTick(runtime,grid,500,25,true,10);nativeTick(runtime,grid,550,25,true,10);
        var actual=runtime.latestSampleObservation().orElseThrow();
        assertEquals(1,actual.containerId());assertEquals(1,actual.serial());assertEquals(550,actual.observedMillis());
        assertEquals(50,actual.elapsedMillis());assertEquals(25,actual.nativeLatencyMillis());assertEquals(0.5,actual.ticks());
        assertEquals(0.0,runtime.averageTicks(1551,1000));assertEquals(0,runtime.sampleCount());
        assertEquals(actual,runtime.latestSampleObservation().orElseThrow());
        assertTrue(nativeTick(runtime,grid,1551,25,true,10).isEmpty());
        runtime.reset();assertTrue(runtime.latestSampleObservation().isEmpty());
    }
}
