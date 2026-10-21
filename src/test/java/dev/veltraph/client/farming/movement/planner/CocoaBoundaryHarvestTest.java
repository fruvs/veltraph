package dev.veltraph.client.farming.movement.planner;
import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CocoaBoundaryHarvestTest {
    static final Vec3 FEET=new Vec3(3.7,.375,93.5125),EYE=new Vec3(3.7,1.995,93.5125);
    static final BlockPos CROP=new BlockPos(3,4,93),GATE=new BlockPos(4,1,93);
    static class World implements WorldService {
        boolean gate=true,loaded=true,visible=true;String age="2",facing="east";Vec3 point=new Vec3(3.7,4.3,93.5);
        public boolean isLoaded(BlockPos p){return loaded;}
        public boolean isPassable(BlockPos p){return true;}
        public boolean isStandable(BlockPos p){return false;}
        public String blockId(BlockPos p){return p.equals(CROP)?"minecraft:cocoa":gate&&p.equals(GATE)?"minecraft:oak_trapdoor":"minecraft:air";}
        public Optional<String> blockProperty(BlockPos p,String key){return p.equals(CROP)?Optional.ofNullable(key.equals("age")?age:key.equals("facing")?facing:null):p.equals(GATE)&&key.equals("open")?Optional.of("true"):Optional.empty();}
        public Optional<Vec3> interactionPoint(BlockPos p,Vec3 eye,double range){return p.equals(CROP)&&visible?Optional.ofNullable(point):Optional.empty();}
    }
    @Test void loadedMatureNativeOutlineFocusRestoresAfterRealTargetDisappears(){
        var p=new CocoaBoundaryHarvest();var w=new World();
        assertEquals(CROP,p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,0).orElseThrow().target());
        w.age="0";assertTrue(p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,50).isEmpty());
        assertEquals(CocoaBoundaryHarvest.State.RESTORING,p.state());p.restored();assertEquals(CocoaBoundaryHarvest.State.IDLE,p.state());
    }
    @Test void blockedUnloadedUnknownAndOutOfRangeWitnessesNeverGrantFocus(){
        for(int caseId=0;caseId<5;caseId++){
            var w=new World();switch(caseId){case 0->w.visible=false;case 1->w.loaded=false;case 2->w.age=null;case 3->w.point=new Vec3(3.7,6.6,93.5);case 4->w.point=new Vec3(Double.NaN,4.3,93.5);}
            assertTrue(new CocoaBoundaryHarvest().observe(w,FEET,EYE,4.5,Direction.NORTH,true,0).isEmpty());
        }
    }
    @Test void ordinaryLaneCocoaDoesNotActivateButObservedCrosswiseAttachmentDoes(){
        var w=new World();w.gate=false;assertTrue(new CocoaBoundaryHarvest().observe(w,FEET,EYE,4.5,Direction.NORTH,true,0).isEmpty());
        // Crosswise detection uses the lower native plane, so supply its native coordinate explicitly.
        var lower=new BlockPos(3,2,93);
        var cross=new World(){public String blockId(BlockPos p){return p.equals(lower)?"minecraft:cocoa":"minecraft:air";}public Optional<String> blockProperty(BlockPos p,String key){return p.equals(lower)?Optional.of(key.equals("age")?"2":key.equals("facing")?"north":""):Optional.empty();}public Optional<Vec3> interactionPoint(BlockPos p,Vec3 eye,double range){return p.equals(lower)?Optional.of(new Vec3(3.7,2.4,93.5)):Optional.empty();}};
        assertEquals(lower,new CocoaBoundaryHarvest().observe(cross,FEET,EYE,4.5,Direction.NORTH,true,0).orElseThrow().target());
    }
    @Test void lostSupportAndUnclearedNativeTargetStopTheCleanup(){
        var p=new CocoaBoundaryHarvest();var w=new World();p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,0);
        assertTrue(p.observe(w,FEET,EYE,4.5,Direction.NORTH,false,50).isEmpty());assertEquals(CocoaBoundaryHarvest.State.BLOCKED,p.state());
        p.reset();p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,0);
        assertTrue(p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,8050).isEmpty());assertEquals(CocoaBoundaryHarvest.State.BLOCKED,p.state());
    }
    @Test void restorationStillRequiresSupportedContext(){
        var p=new CocoaBoundaryHarvest();var w=new World();p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,0);
        w.age="0";p.observe(w,FEET,EYE,4.5,Direction.NORTH,true,50);assertEquals(CocoaBoundaryHarvest.State.RESTORING,p.state());
        p.observe(w,FEET,EYE,4.5,Direction.NORTH,false,100);assertEquals(CocoaBoundaryHarvest.State.BLOCKED,p.state());
    }
}
