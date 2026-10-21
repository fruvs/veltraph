package dev.veltraph.client.farming.design;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class ClassicalGourdAuthoredGeometryTest {
 @Test void bodySupportAllElevenCorners(){ClassicalGourdAuthoredGeometry.validate();assertEquals(11,ClassicalGourdAuthoredGeometry.corners().size());}
 @Test void allRecoveredTurnAnglesTraverseEveryRow(){for(double delta:new double[]{108,109,110,111,112}){var r=ClassicalGourdAuthoredGeometry.simulate(delta,15000);assertTrue(r.completed(),"delta="+delta+" "+r);assertEquals(11,r.turns().size());for(int i=0;i<11;i++){var expected=ClassicalGourdAuthoredGeometry.corners().get(i);var actual=r.turns().get(i).pose();assertEquals(expected.x(),actual.x(),1e-7);assertEquals(expected.z(),actual.z(),1e-7);assertTrue(ClassicalGourdAuthoredGeometry.corner(r.turns().get(i).pose()));}}}
 @Test void originalYawVectorsHaveOppositeLateralAndSharedNorthProgress(){for(double d:new double[]{108,110,112}){var left=ClassicalGourdAuthoredGeometry.vector(-60,true,true);var right=ClassicalGourdAuthoredGeometry.vector(-60+d,true,false);assertTrue(left[0]>0&&right[0]<0);assertTrue(left[1]<0&&right[1]<0);}}
 @Test void wallsClipBodyWithoutCrossingAndEndProbesAreAir(){var c=ClassicalGourdAuthoredGeometry.corners().getFirst();assertTrue(ClassicalGourdAuthoredGeometry.bodyClear(c));assertFalse(ClassicalGourdAuthoredGeometry.bodyClear(new ClassicalGourdAuthoredGeometry.Pose(c.x()+.02,c.y(),c.z())));assertFalse(ClassicalGourdAuthoredGeometry.corner(new ClassicalGourdAuthoredGeometry.Pose(c.x()-.2,c.y(),c.z())));}
}
