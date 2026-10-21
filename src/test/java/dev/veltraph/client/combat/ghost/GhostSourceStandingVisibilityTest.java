package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceStandingVisibilityTest {
    @Test void identityIsFilenameInsideDirectoryRatherThanDirectoryNamedJson() {
        assertEquals("ghost-route/private_fixture.json",GhostSourceCatalogLocation.relativeFile("private_fixture","json"));
        assertThrows(IllegalArgumentException.class,()->GhostSourceCatalogLocation.relativeFile("../other","json"));
    }
    @Test void standingScoreRetainsTargetDistanceLavaAndAreaPenalty() {
        assertEquals(404,GhostSourceStandingScore.score(2,0,2,true,0));
        assertEquals(1.25,GhostSourceStandingScore.score(2.5,1,0,false,0));
        assertThrows(IllegalArgumentException.class,()->GhostSourceStandingScore.score(2.50001,0,0,false,0));
    }
    @Test void signedSourceFieldIsSquaredWithoutChangingOtherScoreOperands() {
        assertEquals(GhostSourceStandingScore.score(2,0,3,false,0),GhostSourceStandingScore.score(2,0,-3,false,0));
        assertEquals(6,GhostSourceStandingScore.score(2,0,-3,false,-3));
        assertThrows(IllegalArgumentException.class,()->GhostSourceStandingScore.score(2,0,Double.NaN,false,0));
    }
    @Test void eightVisibilityEyeSamplesPreserveNegativePositiveAxisOrder() {
        var samples=GhostSourceVisibilitySamples.around(new GhostSourceVisibilitySamples.Eye(10,77,20));
        assertEquals(8,samples.size());
        assertEquals(new GhostSourceVisibilitySamples.Eye(9.5,76.5,19.5),samples.getFirst());
        assertEquals(new GhostSourceVisibilitySamples.Eye(10.5,77.5,20.5),samples.getLast());
    }
}
