package dev.veltraph.client.greenhouse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

class GreenhouseNativeLoadedCellTest {
    @Test void capturedNativeHeightUsesInclusiveMinimumAndExclusiveMaximum(){assertTrue(McGreenhouseLoadedCell.withinHeight(new Cell(-97,-64,-2),-64,320));assertTrue(McGreenhouseLoadedCell.withinHeight(new Cell(-97,319,-2),-64,320));assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(-97,-65,-2),-64,320));assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(-97,320,-2),-64,320));}
    @Test void extremeHeightsDoNotBecomeAirOrSoilFacts(){assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(0,Integer.MIN_VALUE,0),-64,320));assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(0,Integer.MAX_VALUE,0),-64,320));assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(0,70,0),320,-64));assertFalse(McGreenhouseLoadedCell.withinHeight(new Cell(0,70,0),70,70));}
    @Test void absentNativeWorldAndCellRemainUnreadable(){assertFalse(McGreenhouseLoadedCell.readable(null,new Cell(-97,70,-2)));assertFalse(McGreenhouseLoadedCell.withinHeight(null,-64,320));}
}
