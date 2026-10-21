package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.combat.slayer.ImpelController.*;
class ImpelControllerTest {
    @Test void clicksOnceOnlyAfterFacingTheRequestedDirection() {
        var c=new ImpelController();var r=new Receipt(1,Command.CLICK_UP,0);
        assertEquals(Action.TURN_UP,c.tick(r,50,0,true,false));
        assertEquals(Action.CLICK,c.tick(r,100,-76,true,false));
        assertEquals(Action.NONE,c.tick(r,150,-76,true,false));
    }
    @Test void jumpCompletesOnActualAirborneObservationAndSneakReleases() {
        var c=new ImpelController();var r=new Receipt(1,Command.JUMP,0);
        assertEquals(Action.JUMP,c.tick(r,50,0,true,false));
        assertEquals(Action.NONE,c.tick(r,100,0,false,false));
        assertEquals(Action.NONE,c.tick(r,500,0,true,false));
        r=new Receipt(2,Command.SNEAK,600);
        assertEquals(Action.SNEAK,c.tick(r,650,0,true,false));
        assertEquals(Action.NONE,c.tick(r,800,0,true,true));
    }
    @Test void preexistingAirborneStateIsNotMistakenForARequestedJump() {
        var c=new ImpelController();var r=new Receipt(1,Command.JUMP,0);
        assertEquals(Action.NONE,c.tick(r,50,0,false,false));
        assertEquals(Action.JUMP,c.tick(r,100,0,true,false));
        assertEquals(Action.NONE,c.tick(r,150,0,false,false));
    }
    @Test void clearedOrOtherWorldReceiptCancelsAPendingTurn() {
        var c=new ImpelController();c.tick(new Receipt(1,Command.CLICK_UP,0),50,0,true,false);
        assertEquals(Action.NONE,c.tick(new Receipt(1,Command.NONE,0),100,0,true,false));
    }
    @Test void expiredAndPreexistingPromptsCannotTriggerActions() {
        var c=new ImpelController();var r=new Receipt(1,Command.CLICK_DOWN,0);
        assertEquals(Action.EXPIRED,c.tick(r,2001,0,true,false));
        assertEquals(Action.NONE,c.tick(r,2050,80,true,false));
        c.reset(2);assertEquals(Action.NONE,c.tick(new Receipt(2,Command.JUMP,2100),2150,0,true,false));
        assertEquals(Command.NONE,parse("run /warp hub"));assertEquals(Command.NONE,parse("please JUMP"));
    }
    @Test void duplicatePacketsDoNotRenewDeadlinesAndWorldsStayIsolated() {
        Object world=new Object();ImpelSignal.receive(world,"IMPEL: JUMP",0);var first=ImpelSignal.read(world);
        for(int t=100;t<=2500;t+=100)ImpelSignal.receive(world,"JUMP",t);assertEquals(first,ImpelSignal.read(world));
        assertEquals(Command.NONE,ImpelSignal.read(new Object()).command());
        ImpelSignal.clear(world);ImpelSignal.receive(world,"JUMP",2600);assertTrue(ImpelSignal.read(world).serial()>first.serial());
    }
}
