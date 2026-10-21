package dev.veltraph.client.navigation.mc;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CollectionSceneFrozenTraitsTest {
    private static CollectionSceneFrozenTraits.Scalars values(float friction,float speed,float jump) {
        return new CollectionSceneFrozenTraits.Scalars(Float.floatToRawIntBits(friction),Float.floatToRawIntBits(speed),
            Float.floatToRawIntBits(jump),Float.floatToRawIntBits(0F),Float.floatToRawIntBits(1F));
    }
    private static final CollectionSceneFrozenTraits.Scalars AIR=values(.6F,1F,1F),ICE=values(.98F,1F,1F);
    private static List<CollectionSceneFrozenTraits.Cell> cells(CollectionSceneFrozenTraits.Scalars floor,boolean floorHazard,
                                                              boolean bodyHazard,boolean bodyClimbable) {
        return List.of(new CollectionSceneFrozenTraits.Cell(0,-1,0,floorHazard,false,false,floor),
            new CollectionSceneFrozenTraits.Cell(0,0,0,bodyHazard,bodyClimbable,false,AIR),
            new CollectionSceneFrozenTraits.Cell(0,1,0,false,false,false,AIR));
    }
    private static final CollectionSceneFrozenBoxes.Box BODY=new CollectionSceneFrozenBoxes.Box(.2,0,.2,.8,1.8,.8);
    @Test void capturedActualIceSupportRetainsItsOwnFrictionInsteadOfUniversalDefault() {
        assertTrue(CollectionSceneFrozenTraits.clear(BODY,0,cells(ICE,false,false,false),AIR,ICE));
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(ICE,false,false,false),AIR,AIR));
    }
    @Test void fullSupportMagmaHazardRefusesEvenWithOrdinaryScalarValues() {
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(AIR,true,false,false),AIR,AIR));
    }
    @Test void emptyCollisionFluidFireAndClimbableCellsStillRefuse() {
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(AIR,false,true,false),AIR,AIR));
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(AIR,false,false,true),AIR,AIR));
    }
    @Test void shiftedSupportSpeedOrJumpTraitRefusesTheCapturedProfileValues() {
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(values(.6F,.4F,1F),false,false,false),AIR,AIR));
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,cells(values(.6F,1F,.5F),false,false,false),AIR,AIR));
    }
    @Test void staleFenceSupportYCannotSelectTheOldSafeCellAfterPlayerYMoves() {
        // For the fixed .500001F movement offset FENCES is skipped by native code.
        // The stale fence Y is safe, but the actual movement support Y has changed traits.
        var entries=List.of(
            new CollectionSceneFrozenTraits.Cell(0,95,0,false,false,false,AIR),
            new CollectionSceneFrozenTraits.Cell(0,96,0,true,false,false,AIR));
        int selected=CollectionSceneFrozenTraits.movementSupportY(97D,95,false);
        assertEquals(96,selected);
        assertFalse(CollectionSceneFrozenTraits.at(entries,0,95,0).hazard());
        assertTrue(CollectionSceneFrozenTraits.at(entries,0,selected,0).hazard());
    }
    @Test void wallsAndFenceGateRetainHistoricalYAtTheExactMovementOffset() {
        assertEquals(95,CollectionSceneFrozenTraits.movementSupportY(97D,95,true));
        assertEquals(96,CollectionSceneFrozenTraits.movementSupportY(97.5D,95,false));
    }
    @Test void missingSupportBandCellCannotBeSilentlySkipped() {
        var missing=new ArrayList<>(cells(AIR,false,false,false));missing.remove(0);
        assertFalse(CollectionSceneFrozenTraits.clear(BODY,0,missing,AIR,AIR));
        assertTrue(CollectionSceneFrozenTraits.clear(BODY,0,cells(AIR,false,false,false),AIR,AIR));
    }
}
