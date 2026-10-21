package dev.veltraph.client.greenhouse;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.greenhouse.GreenhouseEnvironmentCensus.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

class GreenhouseEnvironmentCensusTest {
    private static final Cell A=new Cell(0,70,0);
    private static NativeCell cell(Cell c,boolean readable,boolean complete){return new NativeCell(c,readable,"minecraft:air","Block{minecraft:air}","minecraft:farmland","Block{minecraft:farmland}",List.of(),complete);}
    @Test void fullSelectedBedIsExactly100WorldCellsAndNeverADefaultNeighborhood(){var r=new Request(A,10,10);assertEquals(100,r.cells().size());assertTrue(r.cells().contains(new Cell(9,70,9)));assertFalse(r.cells().contains(new Cell(-1,70,0)));}
    @Test void invalidBoundsAndCoordinateOverflowCannotBeginNativeReads(){for(int size:List.of(0,11,-1)){assertThrows(IllegalArgumentException.class,()->new Request(A,size,1));assertThrows(IllegalArgumentException.class,()->new Request(A,1,size));}assertThrows(ArithmeticException.class,()->new Request(new Cell(Integer.MAX_VALUE,70,0),2,1));assertThrows(ArithmeticException.class,()->new Request(new Cell(0,70,Integer.MAX_VALUE),1,2));}
    @Test void missingUnreadableAndTruncatedBodiesInvalidateCompleteness(){var r=new Request(A,2,1);var b=new Cell(1,70,0);assertFalse(new Sample(r,"dimension",10,Map.of(A,cell(A,true,true)),true).completeNativeRead());assertFalse(new Sample(r,"dimension",10,Map.of(A,cell(A,true,true),b,cell(b,false,true)),true).completeNativeRead());assertFalse(new Sample(r,"dimension",10,Map.of(A,cell(A,true,true),b,cell(b,true,false)),true).completeNativeRead());}
    @Test void nativeStateDriftNeverBecomesAStableRead(){var r=new Request(A,1,1);assertFalse(new Sample(r,"dimension",10,Map.of(A,cell(A,true,true)),false).completeNativeRead());}
    @Test void immutableFullNativeAirAndSoilReadDoesNotEstablishPlantAbsenceOrCare(){var r=new Request(A,1,1);var cells=new HashMap<Cell,NativeCell>();cells.put(A,cell(A,true,true));var sample=new Sample(r,"dimension",10,cells,true);cells.clear();assertTrue(sample.completeNativeRead());assertFalse(sample.authorizesCare());assertFalse(sample.authorizesGeometry());assertFalse(sample.cells().get(A).authorizesPlantAbsence());assertThrows(UnsupportedOperationException.class,()->sample.cells().clear());}
}
