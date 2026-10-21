package dev.veltraph.client.garden.admission;
import dev.veltraph.client.garden.pests.PestAutoAdmissionSignals;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GardenAdmissionBindingsTest {
    private final GardenAdmissionBindings b=new GardenAdmissionBindings(new GardenAdmissionBindings.Visitor("Visitors","Visitors:","(","Ready visitor","Garden capability yes","Garden capability no"),new PestAutoAdmissionSignals.Bindings("Pests:","Pests:"));
    @Test void exactFirstContainingRowAndParenthesizedCount(){assertEquals(4,b.visitorCount(List.of("  Visitors: (4)  ")));assertNull(b.visitorCount(List.of("Else Visitors: (4)","Visitors: (5)")));}
    @Test void malformedFirstRowCannotBorrowLaterCount(){assertNull(b.visitorCount(List.of("Visitors: (bad)","Visitors: (4)")));assertNull(b.visitorCount(List.of("Visitors: (4")));assertNull(b.visitorCount(List.of("visitors: (4)")));assertNull(b.visitorCount(List.of("Visitors: ( 4)")));}
    @Test void readinessIsIndependentOfCount(){assertFalse(b.visitorReady(List.of("Visitors: (4)")));assertTrue(b.visitorReady(List.of("Ready visitor Alice")));}
    @Test void capabilityIsUnknownWhenAbsentOrContradictory(){assertNull(b.visitorCapability(List.of(),List.of()));assertEquals(true,b.visitorCapability(List.of("Garden capability yes"),List.of()));assertEquals(false,b.visitorCapability(List.of(),List.of("Garden capability no")));assertNull(b.visitorCapability(List.of("Garden capability yes"),List.of("Garden capability no")));}
    @Test void missingDuplicateUnknownFieldsAndWrongTypesAreDenied(){for(String v:List.of("","{}","{\"visitorContains\":\"a\",\"visitorContains\":\"b\"}","{\"visitorContains\":true}","[]"))assertThrows(IllegalArgumentException.class,()->GardenAdmissionBindings.read(v));}
    @Test void pestOnlyMappingDoesNotRequireVisitorLabels(){var p=GardenAdmissionBindings.read("{\"pest\":{\"tabPrefix\":\"Pests:\",\"sidebarPrefix\":\"Pests:\"}}");assertNull(p.visitor());assertNotNull(p.pests());assertNull(p.visitorCount(List.of("Visitors: (4)")));assertNull(p.visitorReady(List.of("Ready visitor")));}
    @Test void visitorOnlyMappingDoesNotRequirePestLabels(){var v=new GardenAdmissionBindings(b.visitor(),null);assertNull(v.pests());assertEquals(4,v.visitorCount(List.of("Visitors: (4)")));}
    @Test void pestNativeGrammarCannotBorrowSidebarAfterMalformedTab(){assertTrue(PestAutoAdmissionSignals.read(b.pests(),List.of("Pests: xbad"),List.of("Pests: 4")).isEmpty());assertEquals(4,PestAutoAdmissionSignals.read(b.pests(),List.of("Pests: x4"),List.of("Pests: 1")).orElseThrow());}
}
