package dev.veltraph.client.greenhouse;

import java.util.*;
import static dev.veltraph.client.greenhouse.GreenhouseModel.*;

/** Native observations of a user-selected finite area; never asserts that the area is a Greenhouse footprint. */
public final class GreenhouseEnvironmentCensus {
    private GreenhouseEnvironmentCensus() {}
    public static final int MAX_CELLS=100,MAX_BODIES=128;
    public record Request(Cell first,int width,int depth) {
        public Request {Objects.requireNonNull(first);if(width<1||width>10||depth<1||depth>10)throw new IllegalArgumentException("Select an area from 1x1 to 10x10");Math.addExact(first.x(),width-1);Math.addExact(first.z(),depth-1);}
        public Set<Cell> cells(){var result=new TreeSet<Cell>();for(int x=0;x<width;x++)for(int z=0;z<depth;z++)
            result.add(new Cell(Math.addExact(first.x(),x),first.y(),Math.addExact(first.z(),z)));return Set.copyOf(result);}
    }
    public record Body(int localId,String nativeType,Position position,double minX,double minY,double minZ,double maxX,double maxY,double maxZ) {}
    /** AIR is a block observation, not a server empty-plant/absence assertion. Entity labels never identify species. */
    public record NativeCell(Cell cell,boolean readable,String blockId,String blockState,String soilId,String soilState,
                             List<Body> bodies,boolean bodiesComplete) {
        public NativeCell {bodies=List.copyOf(bodies);}
        public boolean authorizesPlantAbsence(){return false;}
    }
    public record Sample(Request request,String actualDimension,long observedAt,Map<Cell,NativeCell> cells,boolean stable) {
        public Sample {cells=Map.copyOf(cells);}
        public boolean completeNativeRead(){return stable&&cells.keySet().equals(request.cells())&&cells.entrySet().stream().allMatch(e->{var c=e.getValue();return e.getKey().equals(c.cell())&&c.readable()&&c.bodiesComplete()&&c.blockId()!=null&&!c.blockId().isBlank()&&c.blockState()!=null&&!c.blockState().isBlank()&&c.soilId()!=null&&!c.soilId().isBlank()&&c.soilState()!=null&&!c.soilState().isBlank();});}
        public boolean authorizesGeometry(){return false;}
        public boolean authorizesCare(){return false;}
        public String summary(){long readable=cells.values().stream().filter(NativeCell::readable).count();return "Native census: "+readable+"/"+cells.size()+" readable cells, stable="+stable+", complete native read="+completeNativeRead()
            +", dimension="+actualDimension+"; requested coordinates do not identify a Greenhouse plot, recipe neighborhood, empty plant, directed effect or care target";}
    }
}
