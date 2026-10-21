package dev.veltraph.client.navigation;

import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact original S71 search reference, renamed; reference Terrain extends production Terrain
 * only for the original TravelPath method signature. All original default method bodies remain. No native terrain/current/budget/timing proof is supplied. */
class GroundHeightAllocationParityTest {
    private static final class Reads {
        final List<String> trace=new ArrayList<>();final int refuseAt,throwAt;final Error failure;
        Reads(int refuseAt,int throwAt,Error failure){this.refuseAt=refuseAt;this.throwAt=throwAt;this.failure=failure;}
        boolean read(String value,boolean result){trace.add(value);if(trace.size()==throwAt)throw failure;return trace.size()==refuseAt?false:result;}
        Vec3 position(BlockPos p){trace.add("position:"+p);if(trace.size()==throwAt)throw failure;return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
    }
    private static GroundPathfinder.Terrain actual(Reads r){return new GroundPathfinder.Terrain(){
        public boolean clear(BlockPos p){return r.read("clear:"+p,p.y()>=70);}
        public boolean floor(BlockPos p){return r.read("floor:"+p,p.y()==69);}
        public Vec3 position(BlockPos p){return r.position(p);}
        public boolean walkClear(Vec3 a,Vec3 b){return r.read("walk:"+a+":"+b,true);}
        public boolean stepClear(Vec3 a,Vec3 b){return r.read("step:"+a+":"+b,true);}
    };}
    private static OriginalHeightAllocationGroundPathfinder.Terrain original(Reads r){return new OriginalHeightAllocationGroundPathfinder.Terrain(){
        public boolean clear(BlockPos p){return r.read("clear:"+p,p.y()>=70);}
        public boolean floor(BlockPos p){return r.read("floor:"+p,p.y()==69);}
        public Vec3 position(BlockPos p){return r.position(p);}
        public boolean walkClear(Vec3 a,Vec3 b){return r.read("walk:"+a+":"+b,true);}
        public boolean stepClear(Vec3 a,Vec3 b){return r.read("step:"+a+":"+b,true);}
    };}
    private static void same(OriginalHeightAllocationGroundPathfinder.Result a,GroundPathfinder.Result b){
        assertEquals(a.steps(),b.steps());assertEquals(a.reason(),b.reason());assertEquals(a.visited(),b.visited());assertEquals(a.found(),b.found());
    }
    @Test void exactSearchAndLevelFirstKeepAllDirectionsBudgetsResultsAndQueryOrder(){
        BlockPos start=new BlockPos(0,70,0);
        for(var goal:List.of(new BlockPos(2,70,2),new BlockPos(-2,70,-2),new BlockPos(2,70,0),new BlockPos(0,70,2),new BlockPos(2,71,2)))
            for(int budget:new int[]{0,1,2,5,16,40})for(int radius:new int[]{0,1,3})for(boolean flat:new boolean[]{false,true}){
                var a=new Reads(-1,-1,null);var b=new Reads(-1,-1,null);
                same(flat?OriginalHeightAllocationGroundPathfinder.findLevelFirst(original(a),start,goal,budget,radius):OriginalHeightAllocationGroundPathfinder.find(original(a),start,goal,budget,radius),
                     flat?GroundPathfinder.findLevelFirst(actual(b),start,goal,budget,radius):GroundPathfinder.find(actual(b),start,goal,budget,radius));assertEquals(a.trace,b.trace);
            }
    }
    @Test void everyOriginalBooleanRefusalRetainsSearchOutputAndFollowingQuerySequence(){
        BlockPos start=new BlockPos(0,70,0),goal=new BlockPos(2,70,2);var all=new Reads(-1,-1,null);
        OriginalHeightAllocationGroundPathfinder.find(original(all),start,goal,16,3);
        for(int stop=1;stop<=all.trace.size();stop++)if(!all.trace.get(stop-1).startsWith("position:")){
            var a=new Reads(stop,-1,null);var b=new Reads(stop,-1,null);
            same(OriginalHeightAllocationGroundPathfinder.find(original(a),start,goal,16,3),GroundPathfinder.find(actual(b),start,goal,16,3));assertEquals(a.trace,b.trace);
        }
    }
    @Test void everyOriginalThrowRetainsExactErrorIdentityAndNoAdditionalTerrainCalls(){
        BlockPos start=new BlockPos(0,70,0),goal=new BlockPos(1,70,1);var all=new Reads(-1,-1,null);
        OriginalHeightAllocationGroundPathfinder.find(original(all),start,goal,8,2);
        for(int stop=1;stop<=all.trace.size();stop++){
            Error failure=new AssertionError("terrain callback "+stop);var a=new Reads(-1,stop,failure);var b=new Reads(-1,stop,failure);
            assertSame(failure,assertThrows(Error.class,()->OriginalHeightAllocationGroundPathfinder.find(original(a),start,goal,8,2)));
            assertSame(failure,assertThrows(Error.class,()->GroundPathfinder.find(actual(b),start,goal,8,2)));assertEquals(a.trace,b.trace);assertEquals(stop,a.trace.size());
        }
    }
    @Test void diagonalStepEndpointsAndHeightRefusalsKeepOriginalCallbackOrder(){
        BlockPos start=new BlockPos(0,70,0);
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=-2;dy<=2;dy++){
            BlockPos goal=start.add(dx,dy,dz);var a=new Reads(-1,-1,null);var b=new Reads(-1,-1,null);
            assertEquals(OriginalHeightAllocationGroundPathfinder.canStep(original(a),start,goal),GroundPathfinder.canStep(actual(b),start,goal));assertEquals(a.trace,b.trace);
        }
    }
}
