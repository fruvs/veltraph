package dev.veltraph.client.garden.pests;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PestBoxGeometryTest {
    PestBoxGeometry.Plot plot(float yaw){return new PestBoxGeometry.Plot(1,new Vec3(.5,70,-96.5),yaw,0);}
    @Test void sourceSpawnMetricIncludesVerticalDistanceAndExactOneBoundary(){assertTrue(PestBoxGeometry.atSpawn(plot(0),new Vec3(.5,71,-96.5)));assertFalse(PestBoxGeometry.atSpawn(plot(0),new Vec3(.5,71.01,-96.5)));}
    @Test void sourceBoxIsContinuousCardinalShapeAndMaximumExclusive(){var b=PestBoxGeometry.bounds(plot(0));assertEquals(new PestBoxGeometry.Bounds(-11.5,66,-97.5,12.5,76,-81.5),b);
        assertTrue(b.contains(new Vec3(-11.5,66,-97.5)));assertFalse(b.contains(new Vec3(12.5,70,-90)));assertFalse(b.contains(new Vec3(0,76,-90)));}
    @Test void quarterTurnsFollowNativeDirectionRatherThanContinuousTrig(){assertEquals(PestBoxGeometry.bounds(plot(0)),PestBoxGeometry.bounds(plot(44)));
        assertEquals(new PestBoxGeometry.Bounds(-14.5,66,-108.5,1.5,76,-84.5),PestBoxGeometry.bounds(plot(90)));assertEquals(PestBoxGeometry.bounds(plot(90)),PestBoxGeometry.bounds(plot(45)));}
    @Test void firstEnabledInFlattenOrderWinsAndMissingResolutionNeverFallsBack(){var rows=List.of(List.of(new PestBoxGeometry.Entry(2,PestBoxGeometry.Status.UNDETERMINED)),List.of(new PestBoxGeometry.Entry(1,PestBoxGeometry.Status.ENABLED),new PestBoxGeometry.Entry(2,PestBoxGeometry.Status.ENABLED)));
        assertEquals(plot(0),PestBoxGeometry.assigned(rows,List.of(plot(0))).orElseThrow());assertTrue(PestBoxGeometry.assigned(rows,List.of()).isEmpty());}
}
