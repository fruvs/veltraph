package dev.veltraph.client.skills;

import dev.veltraph.client.game.GameArea;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class FairySoulCatalogTest {
    @TempDir Path directory;
    @Test void localCatalogAndClaimsPreserveAreaDimensionAndCoordinates()throws Exception{
        var souls=FairySoulCatalog.parse("{\"HUB\":[[1,70,-2]],\"ISLAND\":[[1,70,-2]]}");
        var file=FairySoulCatalog.file(directory,"fixture");
        var claim=new FairySoulCatalog.Claim("minecraft:overworld",souls.getFirst());FairySoulCatalog.save(file,Set.of(claim));
        assertEquals(Set.of(claim),FairySoulCatalog.load(file));
        assertFalse(FairySoulCatalog.load(file).contains(new FairySoulCatalog.Claim("minecraft:overworld",souls.getLast())));
        assertFalse(FairySoulCatalog.load(file).contains(new FairySoulCatalog.Claim("minecraft:the_nether",souls.getFirst())));
        assertEquals(GameArea.HUB,souls.getFirst().area());
    }
    @Test void refusesDuplicateUnknownAreaAndTraversal(){
        assertThrows(IllegalArgumentException.class,()->FairySoulCatalog.parse("{\"HUB\":[[1,70,0],[1,70,0]]}"));
        assertThrows(IllegalArgumentException.class,()->FairySoulCatalog.parse("{\"UNKNOWN\":[[1,70,0]]}"));
        assertThrows(IllegalArgumentException.class,()->FairySoulCatalog.file(directory,"../outside"));
        assertThrows(RuntimeException.class,()->FairySoulCatalog.parse("{\"HUB\":[[1.5,70,0]]}"));
    }
    @Test void corruptPersistenceFailsClosedAndNeverMarksUnacknowledgedSouls()throws Exception{
        var file=FairySoulCatalog.file(directory,"fixture");assertTrue(FairySoulCatalog.load(file).isEmpty());
        Files.writeString(file,"not JSON");assertThrows(java.io.IOException.class,()->FairySoulCatalog.load(file));
    }
}
