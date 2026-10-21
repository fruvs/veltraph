package dev.veltraph.client.garden.copy;

import dev.veltraph.client.world.BlockPos;
import java.util.*;

/** Read-only two-pass coherence check. Unknown chunks are never converted into AIR. */
public final class GardenScan {
    public enum Phase{SCAN,VERIFY,COMPLETE,FAILED,CANCELLED}
    public record Cell(boolean loaded,String state,boolean blockEntity){}
    public interface Reader{Object identity();Cell read(BlockPos p);}
    private final String name;private final GardenRegion region;private final Object identity;private final List<String> states=new ArrayList<>();private final Set<Integer> entities=new HashSet<>();private Phase phase=Phase.SCAN;private int index;private String reason="Scanning";
    public GardenScan(String name,GardenRegion region,int maxCells,Reader reader){if(name==null||!name.matches("[A-Za-z0-9_-]{1,64}")||maxCells<1||maxCells>1000000||region.cells()>maxCells)throw new IllegalArgumentException("Invalid scan name/budget");this.name=name;this.region=region;identity=Objects.requireNonNull(reader.identity());}
    public void tick(Reader reader,int batch){
        if(phase!=Phase.SCAN&&phase!=Phase.VERIFY)return;if(reader.identity()!=identity){fail("World identity changed");return;}if(batch<1||batch>4096){fail("Invalid scan batch");return;}
        for(int n=0;n<batch&&(phase==Phase.SCAN||phase==Phase.VERIFY);n++){
            var c=reader.read(region.at(index));if(!c.loaded()||c.state()==null){fail("Unloaded/unknown cell "+region.at(index));return;}
            if(phase==Phase.SCAN){states.add(c.state());if(c.blockEntity())entities.add(index);}else if(!states.get(index).equals(c.state())||entities.contains(index)!=c.blockEntity()){fail("Source changed during coherent snapshot at "+region.at(index));return;}
            if(++index==region.cells()){index=0;if(phase==Phase.SCAN){phase=Phase.VERIFY;reason="Verifying snapshot";}else{phase=Phase.COMPLETE;reason="Coherent dense mask complete";}}
        }
    }
    private void fail(String s){phase=Phase.FAILED;reason=s;states.clear();entities.clear();}
    public void cancel(){if(phase==Phase.SCAN||phase==Phase.VERIFY){phase=Phase.CANCELLED;states.clear();entities.clear();reason="Cancelled; no partial blueprint";}}
    public Optional<GardenBlueprint> result(){return phase==Phase.COMPLETE?Optional.of(new GardenBlueprint(name,region,states,entities)):Optional.empty();}
    public Phase phase(){return phase;}public String status(){return phase+": "+reason+" "+index+"/"+region.cells();}
}
