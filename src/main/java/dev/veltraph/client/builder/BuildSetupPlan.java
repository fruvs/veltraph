package dev.veltraph.client.builder;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Ordered source process branch. The vanilla material adapter is explicitly a local equivalent. */
public record BuildSetupPlan(String name, List<Step> steps) {
    public enum Operation { CLEAR, WATER }
    public record Step(String stage, Operation operation, BlockPos pos, String expectedBlock) {
        public Step { Objects.requireNonNull(stage); Objects.requireNonNull(operation); Objects.requireNonNull(pos);
            if(expectedBlock==null||!expectedBlock.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+"))throw new IllegalArgumentException("Expected block required"); }
    }
    public BuildSetupPlan {
        steps=List.copyOf(steps);
        if(name==null||name.isBlank()||steps.isEmpty()||steps.size()>50000)throw new IllegalArgumentException("Invalid setup plan");
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        for(var s:steps){var p=s.pos();
            if(Math.abs((long)p.x())>480||Math.abs((long)p.z())>480||Math.abs((long)p.y())>256)throw new IllegalArgumentException("Setup coordinates exceed bounds");
            minX=Math.min(minX,p.x());maxX=Math.max(maxX,p.x());minZ=Math.min(minZ,p.z());maxZ=Math.max(maxZ,p.z());
        }
        if(maxX-minX+1>481||maxZ-minZ+1>481)throw new IllegalArgumentException("Setup span exceeds 481 blocks");
    }
    public Map<String,Long> requiredMaterials() { return Map.of("minecraft:water_bucket",steps.stream().filter(s->s.operation()==Operation.WATER).count()); }
    public BuildSetupPlan transformed(SourceBuilderTransform transform) {
        return new BuildSetupPlan(name,steps.stream().map(s->new Step(s.stage(),s.operation(),transform.apply(s.pos()),s.expectedBlock())).toList());
    }
    /** Source generator lines 519-704, preparation=true branch at line604. */
    public static BuildSetupPlan recovered95Irrigation(String clearingBlock) {
        var steps=new ArrayList<Step>();
        for(int x=93;x>=13;x-=10)addIrrigationSite(steps,new BlockPos(x,1,1),clearingBlock);
        for(int x=88;x>=8;x-=10)addIrrigationSite(steps,new BlockPos(x,1,286),clearingBlock);
        return new BuildSetupPlan("Veltraph generated 95x287 irrigation preparation (local water-bucket equivalent)",steps);
    }
    private static void addIrrigationSite(List<Step> steps,BlockPos anchor,String block) {
        String site=anchor.x()+","+anchor.z()+" ";
        for(int dx=0;dx>=-3;dx--)steps.add(new Step(site+"clear upper",Operation.CLEAR,anchor.add(dx,0,0),block));
        for(int dx=-1;dx>=-2;dx--)steps.add(new Step(site+"open water pocket",Operation.CLEAR,anchor.add(dx,-1,0),block));
        steps.add(new Step(site+"irrigate",Operation.WATER,anchor.add(-1,-1,0),"minecraft:water"));
        for(int dx=0;dx>=-3;dx--)steps.add(new Step(site+"clear remaining lower",Operation.CLEAR,anchor.add(dx,-1,0),block));
    }
}
