package dev.veltraph.client.rewarp;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarmReturnPointTest {
    @Test void livePoseRetainsSubBlockCoordinatesInsteadOfChoosingPersistedMarker() {
        var pose=new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(.79,70.125,.12),73,-12);
        assertEquals(.79,pose.position().x());assertEquals(70.125,pose.position().y());assertEquals(.12,pose.position().z());
        var marker=new RewarpPoint("minecraft:overworld",GameArea.GARDEN,new BlockPos(20,70,10),0,0,0);
        assertNotEquals(FarmReturnPoint.fromSavedMarker(marker).position(),pose.position());
    }
    @Test void nonFiniteOrUnreachableCameraPoseIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(Double.NaN,70,0),0,0));
        assertThrows(IllegalArgumentException.class,()->new FarmReturnPoint("minecraft:overworld",GameArea.GARDEN,new Vec3(0,70,0),0,91));
    }
}
