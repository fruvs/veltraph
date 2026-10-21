package dev.veltraph.client.navigation;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CertifiedDropLandingAcknowledgementTest {
    private static final Vec3 ORIGIN=new Vec3(.5,71,3.5),TARGET=new Vec3(.5,70,4.5),LANDED=new Vec3(.5,70,4.97);
    private static final GroundPathfinder.Terrain TERRAIN=new GroundPathfinder.Terrain(){public boolean floor(BlockPos p){return p.y()==(p.z()<=3?70:69);}public boolean clear(BlockPos p){return p.y()>(p.z()<=3?70:69);}};
    private static GroundDropBrakeForecast.Episode receipt(){return new GroundDropBrakeForecast.Episode(105,5,ORIGIN,TARGET,LANDED,new Vec3(0,-.0784,.08),List.of(),new Vec3(0,-.0784,.08),true,LANDED,new Vec3(.5,70,5.147));}
    @Test void onlyCompletedObservedReceiptCanAcknowledgeOriginalNonterminalDropCell(){var walker=new WalkController(1);walker.startCourse(List.of(TARGET,new Vec3(.5,70,6)),ORIGIN,100,.14,TERRAIN);assertEquals(ORIGIN,walker.locomotionDropOrigin());assertFalse(walker.acknowledgeDropLanding(null,200,TERRAIN,()->true));assertTrue(walker.acknowledgeDropLanding(receipt(),200,TERRAIN,()->true));assertEquals(new Vec3(.5,70,6),walker.locomotionTarget());}
    @Test void terminalLandingKeepsOriginalTargetAndStrictArrivalRadius(){var walker=new WalkController(1);walker.startCourse(List.of(TARGET),ORIGIN,100,.14,TERRAIN);assertTrue(walker.acknowledgeDropLanding(receipt(),200,TERRAIN,()->true));assertEquals(TARGET,walker.locomotionTarget());assertNull(walker.locomotionDropOrigin());assertEquals(WalkController.State.WALKING,walker.tick(new WalkController.Input(LANDED,180,true,201),TERRAIN,4000).state());}
    @Test void foreignScopeOrDifferentLandingCellCannotAdvanceOrClearOriginalCertificate(){var walker=new WalkController(1);walker.startCourse(List.of(TARGET,new Vec3(.5,70,6)),ORIGIN,100,.14,TERRAIN);assertFalse(walker.acknowledgeDropLanding(receipt(),200,TERRAIN,()->false));assertEquals(TARGET,walker.locomotionTarget());assertEquals(ORIGIN,walker.locomotionDropOrigin());var r=receipt();var foreign=new GroundDropBrakeForecast.Episode(r.tick(),r.gravityStage(),r.origin(),r.target(),new Vec3(.5,70,5.01),r.velocity(),r.remaining(),r.nextVelocity(),r.nextGround(),r.landing(),r.coast());assertFalse(walker.acknowledgeDropLanding(foreign,200,TERRAIN,()->true));}
}
