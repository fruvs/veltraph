package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PreparedBazaarStartBoundaryTest {
    private final Object screen=new Object(),container=new Object(),inventory=new Object();
    private MenuSnapshot menu(int id,int revision){return new MenuSnapshot(id,"Bazaar",List.of(new MenuSnapshot.Slot(11,"Sell",List.of("Selling: 2x Wheat","Total: 6 Coins"),false)),revision);}
    @Test void identicalClosedGameplayBoundaryRemainsEligible(){assertTrue(PreparedBazaarStartBoundary.current(true,null,inventory,null,null,inventory,inventory,null));}
    @Test void identicalOpenBoundaryAllowsEqualFreshSnapshot(){assertTrue(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),screen,container,inventory,menu(1,2)));}
    @Test void gameplayCannotAdoptAnOpenedMenu(){assertFalse(PreparedBazaarStartBoundary.current(true,null,inventory,null,screen,container,inventory,menu(1,2)));}
    @Test void closedPreparedOpenMenuCannotReopenFromGameplay(){assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),null,inventory,inventory,null));}
    @Test void identicalTitleAndSnapshotCannotAdoptAReplacementContainer(){assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),screen,new Object(),inventory,menu(1,2)));}
    @Test void sameContainerCannotAdoptReplacementScreen(){assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),new Object(),container,inventory,menu(1,2)));}
    @Test void sameObjectsCannotAdoptNewRevisionIdOrContents(){assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),screen,container,inventory,menu(1,3)));assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),screen,container,inventory,menu(2,2)));assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,menu(1,2),screen,container,inventory,new MenuSnapshot(1,"Bazaar",List.of(),2)));}
    @Test void missingOrInconsistentPreparedModeAlwaysRefuses(){assertFalse(PreparedBazaarStartBoundary.current(true,screen,inventory,null,null,inventory,inventory,null));assertFalse(PreparedBazaarStartBoundary.current(false,null,container,menu(1,2),null,container,inventory,menu(1,2)));assertFalse(PreparedBazaarStartBoundary.current(false,screen,container,null,screen,container,inventory,null));assertFalse(PreparedBazaarStartBoundary.current(true,null,null,null,null,null,null,null));}
}
