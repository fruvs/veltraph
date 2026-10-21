package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class GhostRouteCatalogTest {
    @TempDir java.nio.file.Path root;
    @Test void onlyLocalDwarvenWalkEtherRoutesAccepted()throws Exception{var store=new LocalRouteStore(root);store.save("walk",new LocalRoute(GameArea.DWARVEN_MINES,"minecraft:overworld",true,List.of(new LocalRoute.Node(new Vec3(.5,70,.5),0,0))));assertEquals(1,GhostRouteCatalog.load(store,"walk","minecraft:overworld").size());store.save("fly",new LocalRoute(GameArea.DWARVEN_MINES,"minecraft:overworld",true,List.of(new LocalRoute.Node(LocalRoute.Kind.FLY,new Vec3(.5,70,.5),0,0))));assertThrows(IllegalArgumentException.class,()->GhostRouteCatalog.load(store,"fly","minecraft:overworld"));assertThrows(IllegalArgumentException.class,()->GhostRouteCatalog.load(store,"walk","minecraft:the_end"));}
}
