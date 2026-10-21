package dev.veltraph.client.garden.visitor.history;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VisitorLogbookMarkerTest {
    VisitorLogbookMarker.Stand stand(double x,double y,double z,boolean stal){return new VisitorLogbookMarker.Stand(UUID.randomUUID(),true,true,stal,x,y,z);}
    @Test void threeCopositionedActualStalStandsGiveCenterPlusPointSix(){var a=VisitorLogbookMarker.find(List.of(stand(1.1,2.1,3.1,true),stand(1.4,2.4,3.4,true),stand(1.9,2.9,3.9,true))).orElseThrow();assertEquals(1.5,a.x());assertEquals(3.1,a.y());assertEquals(3.5,a.z());assertTrue(a.withinThreeOfEye(1.5,6.1,3.5));assertFalse(a.withinThreeOfEye(1.5,6.10001,3.5));}
    @Test void fewerThanThreeIsUnknown(){assertTrue(VisitorLogbookMarker.find(List.of(stand(1,2,3,true),stand(1,2,3,true))).isEmpty());}
    @Test void equalDensestGroupsAreAmbiguous(){var s=new ArrayList<VisitorLogbookMarker.Stand>();for(int i=0;i<3;i++){s.add(stand(1,2,3,true));s.add(stand(5,2,3,true));}assertTrue(VisitorLogbookMarker.find(s).isEmpty());}
    @Test void wrongItemDoesNotContribute(){assertTrue(VisitorLogbookMarker.find(List.of(stand(1,2,3,true),stand(1,2,3,true),stand(1,2,3,false))).isEmpty());}
    @Test void negativeCoordinatesUseFloor(){var a=VisitorLogbookMarker.find(List.of(stand(-.1,2,-.1,true),stand(-.2,2,-.2,true),stand(-.3,2,-.3,true))).orElseThrow();assertEquals(-.5,a.x());assertEquals(-.5,a.z());}
    @Test void duplicateEntityCannotInflateDensity(){var s=stand(1,2,3,true);assertTrue(VisitorLogbookMarker.find(List.of(s,s,s)).isEmpty());}
}
