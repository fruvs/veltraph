package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.farming.movement.TurnDirection;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.stub.NoOpInventoryService;
import dev.veltraph.client.testutil.*;
import java.util.Random;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PumpkinSourceRowPlannerTest {
    private final FakePlayerService player=new FakePlayerService();
    private final RecordingInputService input=new RecordingInputService();
    private final RecordingBlockBreaking breaking=new RecordingBlockBreaking();
    private boolean end,aligned=true,corner=true;
    private double target=Double.NaN;
    private final PumpkinSourceRowPlanner planner=new PumpkinSourceRowPlanner(
            new FarmServices(new FakeClock(),player,new FakeWorldService(),input,new FakeRotationService(),breaking,new NoOpInventoryService()),
            new PumpkinSourceRowPlanner.World(){
                public boolean atRowEnd(TurnDirection side){return end;}
                public boolean atTurnCorner(dev.veltraph.client.world.Vec3 current,dev.veltraph.client.world.Vec3 previous){return corner;}
            },()->aligned,yaw->target=yaw,new Random(0));
    @Test void literalAlternatingSideAndOriginalYawTurnAreDecodedWithoutCardinalFlip(){
        player.onGround=true;planner.start(17.3f);planner.tick();
        assertTrue(input.forward&&input.left&&breaking.breaking);assertFalse(input.right);
        end=true;for(int tick=0;tick<19;tick++)planner.tick();planner.tick();
        assertEquals(PumpkinSourceRowPlanner.State.ON_END_ROW,planner.state());
        assertTrue(target>=125.3&&target<129.3,"LEFT is original yaw plus108..112");
        assertEquals(TurnDirection.RIGHT,planner.side());
        assertFalse(input.forward||input.left||input.right||breaking.breaking);
        aligned=false;planner.tick();assertEquals(PumpkinSourceRowPlanner.State.ON_END_ROW,planner.state());
        aligned=true;end=false;planner.tick();assertTrue(input.forward&&input.right);
        end=true;for(int tick=0;tick<19;tick++)planner.tick();planner.tick();
        assertEquals(17.3,target,.001,"RIGHT returns original yaw rather than adding180");
        assertEquals(TurnDirection.LEFT,planner.side());
    }
    @Test void minimumCounterAndActualCornerGatePrecedeTurn(){
        player.onGround=true;end=true;corner=false;planner.start(0);
        for(int tick=0;tick<19;tick++)planner.tick();
        assertEquals(PumpkinSourceRowPlanner.State.FARMING,planner.state());assertTrue(Double.isNaN(target));
        planner.tick();assertEquals(PumpkinSourceRowPlanner.State.ON_END_ROW,planner.state());
        for(int tick=0;tick<10;tick++){
            player.position=new dev.veltraph.client.world.Vec3(tick*.1,64,.5);planner.tick();
            assertTrue(input.left);assertFalse(input.forward||breaking.breaking);
        }
        assertTrue(Double.isNaN(target),"side pressure must reach the recovered corner condition before aiming");
        corner=true;planner.tick();assertTrue(target>=108&&target<112);
        assertFalse(input.forward||input.left||input.right||breaking.breaking);
        planner.stop();assertFalse(input.forward||input.left||input.right||breaking.breaking);
    }
}
