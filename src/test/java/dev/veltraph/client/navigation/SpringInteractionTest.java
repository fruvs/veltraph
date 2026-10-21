package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.SpringInteraction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.slayer.SpringInteraction.Action.*;
class SpringInteractionTest {
    @Test void alternatingActionsRespectAimAndCadence() {
        var c=new SpringInteraction();assertEquals(NONE,c.tick(1,false,0));
        assertEquals(NONE,c.tick(1,true,50));assertEquals(ATTACK,c.tick(1,true,150));
        assertEquals(NONE,c.tick(1,false,200));assertEquals(NONE,c.tick(1,true,250));assertEquals(INTERACT,c.tick(1,true,350));
        assertEquals(ATTACK,c.tick(1,true,450));
    }
    @Test void nextSpringStartsItsOwnSequenceAndClicksDoNotImplyCompletion() {
        var c=new SpringInteraction();
        c.tick(1,true,0);
        for(int i=0;i<50;i++)assertEquals(i%2==0?ATTACK:INTERACT,c.tick(1,true,(i+1)*100));
        assertEquals(NONE,c.tick(2,true,5100));assertEquals(ATTACK,c.tick(2,true,5200));c.reset();assertEquals(NONE,c.tick(2,true,5300));
    }
}
