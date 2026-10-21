package dev.veltraph.client.navigation;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LocalRouteStoreTest {
    @TempDir Path folder;
    LocalRoute route() { return new LocalRoute(GameArea.GARDEN,"minecraft:overworld",true,List.of(new LocalRoute.Node(new Vec3(1,70,2),90,20))); }
    @Test void roundTripsTypedRouteThroughLocalFile() throws Exception {
        var store=new LocalRouteStore(folder);store.save("garden",route());assertEquals(route(),store.load("garden"));
    }
    @Test void rejectsPathTraversalUnsupportedWarpAndTrailingData() {
        var store=new LocalRouteStore(folder);
        assertThrows(IllegalArgumentException.class,()->store.path("../other"));
        String json=LocalRouteStore.encode(route());
        assertThrows(IllegalArgumentException.class,()->LocalRouteStore.decode(json.replace("walk","etherwarp")));
        assertThrows(IllegalArgumentException.class,()->LocalRouteStore.decode(json+"false"));
        assertThrows(IllegalArgumentException.class,()->LocalRouteStore.decode("{}"));
    }
    @Test void rejectsEmptyRoutesAndNonFinitePositions() {
        assertThrows(IllegalArgumentException.class,()->new LocalRoute(GameArea.GARDEN,"minecraft:overworld",false,List.of()));
        assertThrows(IllegalArgumentException.class,()->new LocalRoute.Node(new Vec3(Double.NaN,1,1),0,0));
        assertThrows(IllegalArgumentException.class,()->new LocalRoute(GameArea.UNKNOWN,"minecraft:overworld",false,route().nodes()));
    }
}
