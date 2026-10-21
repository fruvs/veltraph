package dev.veltraph.client.farming.movement.planner;

import dev.veltraph.client.service.WorldService;
import dev.veltraph.client.world.*;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class CactusAceRecenteringTest {
    private static final class Slab implements WorldService {
        boolean loaded=true,clipPulse;
        public boolean isLoaded(BlockPos p){return loaded;}
        public boolean isPassable(BlockPos p){return p.y()>73;}
        public boolean isStandable(BlockPos p){return p.y()==73;}
        public String blockId(BlockPos p){return p.y()==73&&p.z()==6?"minecraft:quartz_slab":"minecraft:air";}
        public Optional<String> blockProperty(BlockPos p,String name){return name.equals("type")?Optional.of("bottom"):Optional.empty();}
        public boolean bodyClear(Vec3 p,double w,double h){return loaded&&p.z()>=6&&p.z()<7&&!(clipPulse&&p.y()==74&&p.z()>6.505);}
        public boolean hasGroundSupport(Vec3 p,double w){return loaded&&(Math.abs(p.y()-73.5)<.025||p.y()==74&&p.z()<6.3);}
    }
    @Test void measuredPrecisionPulseRestoresOriginalSlabThenResumes(){
        var c=new CactusAceRecentering();var w=new Slab();var lip=new Vec3(263.4,74,6.2995315);
        assertTrue(c.begin(w,lip,Direction.NORTH,100));assertEquals(CactusAceRecentering.State.SETTLING,c.state());
        var pulse=c.tick(w,lip,true,101);assertTrue(pulse.back());assertFalse(pulse.forward());assertTrue(pulse.sneak());
        assertEquals(CactusAceRecentering.Keys.NONE,c.tick(w,new Vec3(263.4,73.92,6.435),false,102));
        assertEquals(CactusAceRecentering.Keys.NONE,c.tick(w,new Vec3(263.4,73.5,6.51),true,103));
        c.tick(w,new Vec3(263.4,73.5,6.551),true,104);assertNotEquals(CactusAceRecentering.State.COMPLETE,c.state());
        c.tick(w,new Vec3(263.4,73.5,6.573),true,105);assertEquals(CactusAceRecentering.State.COMPLETE,c.state());assertEquals(1,c.pulses());
    }
    @Test void naturalMeasuredSettlingNeedsNoCorrectivePulse(){
        var c=new CactusAceRecentering();var w=new Slab();c.begin(w,new Vec3(5.5,74,6.2995),Direction.NORTH,0);
        c.tick(w,new Vec3(5.5,73.8,6.5),false,1);c.tick(w,new Vec3(5.5,73.5,6.5),true,2);
        c.tick(w,new Vec3(5.5,73.5,6.5),true,3);assertEquals(CactusAceRecentering.State.COMPLETE,c.state());assertEquals(0,c.pulses());
    }
    @Test void unknownClipOtherCorridorAndDeadlineCancelWithoutKeys(){
        var w=new Slab();var lip=new Vec3(5.5,74,6.2995);
        for(int mode=0;mode<4;mode++){
            w.loaded=true;w.clipPulse=false;var c=new CactusAceRecentering();assertTrue(c.begin(w,lip,Direction.NORTH,0));
            if(mode==0)w.loaded=false;if(mode==1)w.clipPulse=true;
            var position=mode==2?new Vec3(5.5,74,7.1):lip;
            assertEquals(CactusAceRecentering.Keys.NONE,c.tick(w,position,true,mode==3?40:1));assertEquals(CactusAceRecentering.State.CANCELLED,c.state());
        }
    }
    @Test void outstandingNativePredictionDefersOrdinaryCorrection(){
        var c=new CactusAceRecentering();var w=new Slab();var lip=new Vec3(5.5,74,6.2995);c.begin(w,lip,Direction.NORTH,0);
        assertEquals(CactusAceRecentering.Keys.NONE,c.tick(w,lip,true,1,false));assertEquals(0,c.pulses());
        assertTrue(c.tick(w,lip,true,2,true).back());assertEquals(1,c.pulses());
    }
    @Test void currentSourceBandIsPointTwentyOneNotWiderCollisionBranch(){
        assertEquals(.21,CactusAceRecentering.SOURCE_RADIUS);var w=new Slab();var c=new CactusAceRecentering();
        c.begin(w,new Vec3(5.5,74,6.2995),Direction.NORTH,0);
        c.tick(w,new Vec3(5.5,73.5,6.75),true,1);c.tick(w,new Vec3(5.5,73.5,6.75),true,2);
        assertNotEquals(CactusAceRecentering.State.COMPLETE,c.state());
    }
}
