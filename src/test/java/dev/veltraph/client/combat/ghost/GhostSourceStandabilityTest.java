package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceStandabilityTest {
    @Test void loadedBodyChecksPrecedeSupportRegistryAndFluidFallback(){
        assertFalse(GhostSourceStandability.allowed(false,true,true,false,false,false,true,true));
        assertFalse(GhostSourceStandability.allowed(true,false,true,false,false,false,true,true));
        assertTrue(GhostSourceStandability.allowed(true,true,true,false,false,false,false,false));
    }
    @Test void excludedSupportCannotUseFluidFallbackAndBothFluidCellsRequired(){
        assertFalse(GhostSourceStandability.allowed(true,true,false,true,false,false,true,true));
        assertFalse(GhostSourceStandability.allowed(true,true,false,false,true,false,true,true));
        assertFalse(GhostSourceStandability.allowed(true,true,false,false,false,true,true,true));
        assertFalse(GhostSourceStandability.allowed(true,true,false,false,false,false,true,false));
        assertTrue(GhostSourceStandability.allowed(true,true,false,false,false,false,true,true));
    }
}
