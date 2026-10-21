package dev.veltraph.client.navigation;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class MultiModeRouteTest {
    @Test void roundTripsEachModeWithoutSilentlyChangingItsMeaning() {
        var route=new LocalRoute(GameArea.GARDEN,"minecraft:overworld",true,List.of(
                new LocalRoute.Node(new Vec3(1,70,1),0,0),
                new LocalRoute.Node(LocalRoute.Kind.FLY,new Vec3(4,75,1),0,0),
                new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,new Vec3(10.5,71,.5),90,20,new BlockPos(10,70,0),Direction.UP)));
        String json=LocalRouteStore.encode(route);assertTrue(json.contains("\"version\": 2"));assertEquals(route,LocalRouteStore.decode(json));
        assertThrows(IllegalArgumentException.class,()->LocalRouteStore.decode(json.replace("etherwarp","soulflow_unknown")));
    }
    @Test void etherwarpWithoutAnExplicitFaceAndBlockIsRejected() {
        assertThrows(IllegalArgumentException.class,()->new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,new Vec3(10,71,0),0,0));
    }
}
