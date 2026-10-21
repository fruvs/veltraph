package dev.veltraph.client.builder;
import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Local ordered process schema from FarmBuilderLayout.md; replaces the excluded native transform. */
public record BuildLayout(String name,List<Entry> entries) {
    public enum Operation { PLACE, DESTROY }
    public record Entry(Operation operation,BlockPos pos,String block) {
        public Entry {
            Objects.requireNonNull(operation);Objects.requireNonNull(pos);
            if(block==null||!block.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||block.equals("minecraft:air"))
                throw new IllegalArgumentException("Every entry needs a non-air block id (expected block for destroy)");
        }
    }
    public BuildLayout {
        if(name==null||name.isBlank()) throw new IllegalArgumentException("Layout name required");
        entries=List.copyOf(entries);
        if(entries.isEmpty()||entries.size()>50_000) throw new IllegalArgumentException("Layout requires 1-50000 entries");
        int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
        for(var e:entries) {
            if(Math.abs((long)e.pos().x())>480||Math.abs((long)e.pos().z())>480||Math.abs((long)e.pos().y())>256)
                throw new IllegalArgumentException("Relative layout coordinates exceed bounds");
            minX=Math.min(minX,e.pos().x());maxX=Math.max(maxX,e.pos().x());minZ=Math.min(minZ,e.pos().z());maxZ=Math.max(maxZ,e.pos().z());
        }
        if(maxX-minX+1>481||maxZ-minZ+1>481) throw new IllegalArgumentException("Layout span exceeds 481 blocks");
    }
    /** Conservative operation material budget; DESTROY entries never count as inventory requirements. */
    public Map<String,Long> requiredMaterials() {
        var counts=new LinkedHashMap<String,Long>();
        for(var e:entries)if(e.operation()==Operation.PLACE)counts.merge(e.block(),1L,Long::sum);
        return Collections.unmodifiableMap(counts);
    }
    public BuildLayout transformed(BlockPos anchor,int quarterTurns) {
        int turns=Math.floorMod(quarterTurns,4);
        List<Entry> out=new ArrayList<>();
        for(var e:entries) {
            int x=e.pos().x(),z=e.pos().z();
            for(int i=0;i<turns;i++) { int previous=x;x=-z;z=previous; }
            BlockPos world=anchor.add(x,e.pos().y(),z);
            if(world.x()< -240||world.x()>239||world.z()< -240||world.z()>239||world.y()<0||world.y()>255)
                throw new IllegalArgumentException("Transformed entry outside Garden bounds: "+world);
            out.add(new Entry(e.operation(),world,e.block()));
        }
        return new BuildLayout(name,out);
    }
}
