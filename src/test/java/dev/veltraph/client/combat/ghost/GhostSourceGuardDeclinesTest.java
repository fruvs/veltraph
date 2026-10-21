package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.combat.ghost.GhostSourceParentGuardDispatcher.*;
class GhostSourceGuardDeclinesTest {
    @Test void explicitlyDisabledFlagsDeclineButEnabledUnboundHelpersStayUnknown(){
        for(var role:new Role[]{Role.AUTO_SELL,Role.PESTS,Role.VISITORS,Role.PEST_TRAPS}){
            assertEquals(Result.UNHANDLED,GhostSourceGuardDeclines.ordinary(role,false,false,false,false,GameArea.DWARVEN_MINES));
            assertEquals(Result.UNKNOWN,GhostSourceGuardDeclines.ordinary(role,true,true,true,true,GameArea.DWARVEN_MINES));
        }
    }
    @Test void onlyProvedGhostConstructorAndCompleteHierarchyRolesAreStaticallyInapplicable(){
        for(var role:new Role[]{Role.FARM_GARDEN_PAUSE,Role.FARM_PEST_SPRAY,Role.MARKER_WORK})
            assertEquals(Result.UNHANDLED,GhostSourceGuardDeclines.ordinary(role,true,true,true,true,GameArea.UNKNOWN));
        assertEquals(Result.UNKNOWN,GhostSourceGuardDeclines.ordinary(Role.SCHEDULED_REST,false,false,false,false,GameArea.DWARVEN_MINES));
    }
    @Test void actualDwarvenObservationExcludesMoongladeWithoutInventingMissingAreaNames(){
        assertEquals(Result.UNHANDLED,GhostSourceGuardDeclines.ordinary(Role.MOONGLADE,false,false,false,false,GameArea.DWARVEN_MINES));
        assertEquals(Result.UNKNOWN,GhostSourceGuardDeclines.ordinary(Role.MOONGLADE,false,false,false,false,GameArea.UNKNOWN));
    }
}
