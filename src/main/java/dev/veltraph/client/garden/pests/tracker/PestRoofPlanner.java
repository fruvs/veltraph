package dev.veltraph.client.garden.pests.tracker;

import dev.veltraph.client.garden.pests.GardenPlotBounds;
import dev.veltraph.client.world.*;
import java.util.*;
import java.util.random.RandomGenerator;

/** Readable stable-source roof selection: scan Y < 77, three calls, six 10..15 forward samples. */
public final class PestRoofPlanner {
    public static final int MAX_ATTEMPTS=3,SCAN_CEILING=77;
    public record Hop(BlockPos support,Direction face,Vec3 landing) {}
    public enum Outcome { HOP, NO_ROOF, BLOCKED, EXHAUSTED, REFUSED }
    public record Plan(Outcome outcome,Hop hop,List<BlockPos> candidates,int attempt) {
        public Plan {candidates=List.copyOf(candidates);}
    }
    public interface World {
        boolean loaded(BlockPos block);
        boolean passable(BlockPos block);
        /** Exact observed, safe, unprotected support plus actual standing body clearance. */
        boolean validRoof(int plot,BlockPos block);
        /** Exact unobstructed acquisition ray and server-supported landing. */
        Optional<Hop> ray(Vec3 eye,BlockPos support);
    }
    private int attempts;private boolean dispatched;
    public Plan plan(World world,int plot,Vec3 feet,Vec3 eye,Vec3 look,boolean itemReady,boolean evacuation,RandomGenerator random) {
        if(world==null||random==null||!finite(feet)||!finite(eye)||!finite(look)||!itemReady||(dispatched&&!evacuation)
                ||feet.y()<60||feet.y()>=92||Math.sqrt(look.x()*look.x()+look.y()*look.y()+look.z()*look.z())>1.001
                ||!GardenPlotBounds.contains(plot,feet.x(),feet.z()))return result(Outcome.REFUSED,null,List.of());
        if(attempts>=MAX_ATTEMPTS)return result(Outcome.EXHAUSTED,null,List.of());
        attempts++;
        BlockPos roof=null;var origin=feet.toBlockPos();boolean blocked=false;
        for(int y=origin.y();y<SCAN_CEILING;y++) {
            var block=new BlockPos(origin.x(),y,origin.z());
            if(!world.loaded(block)){blocked=true;break;}
            if(world.validRoof(plot,block)){roof=block;break;}
            if(!world.passable(block)){blocked=true;break;}
        }
        if(roof==null)return result(blocked?Outcome.BLOCKED:Outcome.NO_ROOF,null,List.of());
        var candidates=new ArrayList<BlockPos>();var hops=new ArrayList<Hop>();
        for(int step=10;step<16;step++) {
            var candidate=new Vec3(roof.x()+.5+look.x()*step+random.nextDouble(-1,1),roof.y(),roof.z()+.5+look.z()*step+random.nextDouble(-1,1)).toBlockPos();
            if(!GardenPlotBounds.contains(plot,candidate.x()+.5,candidate.z()+.5)||!world.loaded(candidate)||!world.validRoof(plot,candidate))continue;
            var hop=world.ray(eye,candidate).filter(h->validHop(plot,candidate,h));
            if(hop.isPresent()){candidates.add(candidate);hops.add(hop.get());}
        }
        if(!hops.isEmpty())return result(Outcome.HOP,hops.get(random.nextInt(hops.size())),candidates);
        var vertical=roof;var fallback=world.ray(eye,roof).filter(h->validHop(plot,vertical,h));
        return fallback.isPresent()?result(Outcome.HOP,fallback.get(),List.of()):result(Outcome.BLOCKED,null,List.of());
    }
    private static boolean validHop(int plot,BlockPos support,Hop hop){return hop!=null&&support.equals(hop.support())&&hop.face()!=null&&finite(hop.landing())
        &&GardenPlotBounds.contains(plot,hop.landing().x(),hop.landing().z())&&Math.abs(hop.landing().x()-(support.x()+.5))<.01
        &&Math.abs(hop.landing().z()-(support.z()+.5))<.01&&hop.landing().y()>support.y()&&hop.landing().y()<=support.y()+1;}
    private Plan result(Outcome outcome,Hop hop,List<BlockPos> candidates){return new Plan(outcome,hop,candidates,attempts);}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x())&&Double.isFinite(v.y())&&Double.isFinite(v.z());}
    /** Source flag becomes true at the selected-hop callback; only explicit evacuation can override. */
    public void dispatched(){dispatched=true;}
    public int attempts(){return attempts;}public void reset(){attempts=0;dispatched=false;}
}
