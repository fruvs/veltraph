package dev.veltraph.client.skills;

import dev.veltraph.client.navigation.LocalRoute;
import dev.veltraph.client.world.*;
import java.util.*;

/** Readable stable Fairy compiler: both lists share a cursor; ETHER advances above its block. */
public final class FairySoulRouteCompiler {
    public enum Kind { ETHER, WALK }
    public record Step(Kind kind, BlockPos target, Direction face) {
        public Step { Objects.requireNonNull(kind); Objects.requireNonNull(target);
            if(kind==Kind.ETHER&&face==null||kind==Kind.WALK&&face!=null)throw new IllegalArgumentException("ETHER requires an explicit face; WALK has none");
            new LocalRoute.Node(feet(target),0,0);
        }
    }
    public record Hop(BlockPos from, LocalRoute.Node node, long timeoutMillis) {}
    public record Plan(List<Hop> outbound,List<Hop> returning) {
        public Plan { outbound=List.copyOf(outbound); returning=List.copyOf(returning); }
    }
    private FairySoulRouteCompiler() {}
    public static Vec3 feet(BlockPos p){return new Vec3(p.x()+.5,p.y(),p.z()+.5);}
    public static Plan compile(BlockPos start,List<Step> outbound,List<Step> returning){
        Objects.requireNonNull(start);if(outbound.isEmpty()||returning.isEmpty()||outbound.size()+returning.size()>256)throw new IllegalArgumentException("Explicit Fairy routes require both ordered lists, at most 256 steps");
        BlockPos[] cursor={start};return new Plan(compile(cursor,outbound),compile(cursor,returning));
    }
    private static List<Hop> compile(BlockPos[] cursor,List<Step> steps){
        var result=new ArrayList<Hop>();for(var step:steps){var previous=cursor[0];cursor[0]=step.kind()==Kind.ETHER?step.target().up():step.target();
            var node=step.kind()==Kind.ETHER?new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,feet(cursor[0]),0,0,step.target(),step.face()):new LocalRoute.Node(feet(cursor[0]),0,0);
            result.add(new Hop(previous,node,step.kind()==Kind.WALK?10000:30000));
        }return List.copyOf(result);
    }
}
