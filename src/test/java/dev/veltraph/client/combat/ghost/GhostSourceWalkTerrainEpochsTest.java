package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
class GhostSourceWalkTerrainEpochsTest {
    @Test void absentNativeHookReceiptNeverPretendsStableGeometry(){var world=new Object();assertThrows(IllegalStateException.class,()->GhostSourceWalkTerrainEpochs.capture(world,GhostSourceWalkTerrainEpochs.footprint(Set.of())));}
    @Test void usedCellNeighborsAndChunkReplacementRevokeButUnrelatedCellsDoNot(){
        var world=new Object();GhostSourceWalkTerrainEpochs.observedLevelHook(world);GhostSourceWalkTerrainEpochs.observedChunkHook(world);
        var cell=new Cell(15,76,15);var s=GhostSourceWalkTerrainEpochs.capture(world,GhostSourceWalkTerrainEpochs.footprint(Set.of(cell)));
        GhostSourceWalkTerrainEpochs.changedCell(world,new Cell(100,76,100));assertTrue(GhostSourceWalkTerrainEpochs.current(s));
        GhostSourceWalkTerrainEpochs.changedCell(world,new Cell(16,76,15));assertFalse(GhostSourceWalkTerrainEpochs.current(s));
        var next=GhostSourceWalkTerrainEpochs.capture(world,GhostSourceWalkTerrainEpochs.footprint(Set.of(cell)));
        GhostSourceWalkTerrainEpochs.changedChunk(world,1,0);assertFalse(GhostSourceWalkTerrainEpochs.current(next));
    }
    @Test void separateWorldChangesNeverGrantOrRevokeAnotherWorldStamp(){
        var a=new Object();var b=new Object();for(var w:new Object[]{a,b}){GhostSourceWalkTerrainEpochs.observedLevelHook(w);GhostSourceWalkTerrainEpochs.observedChunkHook(w);}
        var s=GhostSourceWalkTerrainEpochs.capture(a,GhostSourceWalkTerrainEpochs.footprint(Set.of(new Cell(0,76,0))));
        GhostSourceWalkTerrainEpochs.changedCell(b,new Cell(0,76,0));assertTrue(GhostSourceWalkTerrainEpochs.current(s));
    }
}
