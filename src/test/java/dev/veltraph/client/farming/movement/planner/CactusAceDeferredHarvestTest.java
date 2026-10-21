package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CactusAceDeferredHarvestTest {
    private static final BlockPos TARGET=new BlockPos(361,75,4);
    private static final class Slab implements WorldService {
        boolean loaded=true,live=true,clipped;
        public boolean isLoaded(BlockPos p){return loaded;}
        public boolean isPassable(BlockPos p){return !blockId(p).equals("minecraft:cactus");}
        public boolean isStandable(BlockPos p){return p.y()==73;}
        public String blockId(BlockPos p){if((p.equals(TARGET)&&live)||p.equals(TARGET.down()))return "minecraft:cactus";return p.y()==73&&p.z()==6?"minecraft:quartz_slab":"minecraft:air";}
        public Optional<String> blockProperty(BlockPos p,String name){return name.equals("type")?Optional.of("bottom"):Optional.empty();}
        public boolean bodyClear(Vec3 p,double w,double h){return loaded&&!clipped;}
        public boolean hasGroundSupport(Vec3 p,double w){return loaded&&Math.abs(p.y()-73.5)<.025;}
    }
    private static Vec3 eye(Vec3 p){return new Vec3(p.x(),p.y()+1.62,p.z());}
    @Test void actualInterruptedColumnApproachedThenHarvestedAndAcknowledged(){
        var w=new Slab();var c=new CactusAceDeferredHarvest();var p=new Vec3(360.1483,73.5,6.30876);
        assertTrue(c.begin(w,p,Direction.NORTH,List.of(TARGET),0));var key=c.tick(w,p,eye(p),4.5,1,Set.of(),Optional.empty());
        assertTrue(key.right());assertTrue(key.sneak());assertFalse(key.attack());
        var arrived=new Vec3(361.14,73.5,6.30876);
        c.tick(w,arrived,eye(arrived),4.5,2,Set.of(),Optional.of(TARGET));
        var harvest=c.tick(w,arrived,eye(arrived),4.5,3,Set.of(),Optional.of(TARGET));assertTrue(harvest.attack());assertFalse(harvest.right());
        w.live=false;assertTrue(c.tick(w,arrived,eye(arrived),4.5,4,Set.of(TARGET),Optional.empty()).attack());
        assertNotEquals(CactusAceDeferredHarvest.State.COMPLETE,c.state());
        assertEquals(CactusAceDeferredHarvest.Keys.NONE,c.tick(w,arrived,eye(arrived),4.5,5,Set.of(),Optional.empty()));assertEquals(CactusAceDeferredHarvest.State.COMPLETE,c.state());
    }
    @Test void wrongCorridorClipUnknownAndUnchangedReachDenyBacktrack(){
        for(int mode=0;mode<4;mode++){
            var w=new Slab();var c=new CactusAceDeferredHarvest();var p=new Vec3(360.1483,73.5,6.30876);assertTrue(c.begin(w,p,Direction.NORTH,List.of(TARGET),0));
            if(mode==0)w.loaded=false;if(mode==1)w.clipped=true;
            var pose=mode==2?new Vec3(p.x(),p.y(),7.1):p;
            assertEquals(CactusAceDeferredHarvest.Keys.NONE,c.tick(w,pose,eye(pose),mode==3?1:4.5,1,Set.of(),Optional.empty()));assertEquals(CactusAceDeferredHarvest.State.CANCELLED,c.state());
        }
    }
}
