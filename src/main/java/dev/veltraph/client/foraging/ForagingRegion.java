package dev.veltraph.client.foraging;
import dev.veltraph.client.world.BlockPos;
import java.util.List;
/** Recovered TreeType region bounds, density thresholds and search checkpoints (raw initializer 280–284).
 * ANY and OAK are local fixture/custom-world extensions; Park defaults to the recovered BIRCH region. */
public enum ForagingRegion {
    ANY("",1,1,1,0,0,0,0,0,0,List.of()),
    OAK("oak",3,3,3,0,0,0,0,0,0,List.of()),
    BIRCH("birch", 4,7,12, -335,110,-90,-255,50,20, List.of(new BlockPos(-281,81,-5),new BlockPos(-299,81,1),new BlockPos(-295,80,-20),new BlockPos(-304,81,-28),new BlockPos(-292,80,-35),new BlockPos(-278,81,-29))),
    SPRUCE("spruce", 4,9,16, -323,60,100,-385,110,-21, List.of(new BlockPos(-353,90,-7),new BlockPos(-346,89,11),new BlockPos(-342,89,41),new BlockPos(-370,90,22),new BlockPos(-367,90,-5))),
    DARK_OAK("dark_oak", 5,13,20, -340,70,-110,-415,120,-25, List.of(new BlockPos(-377,97,-67),new BlockPos(-386,98,-42),new BlockPos(-389,98,-82),new BlockPos(-355,101,-85))),
    ACACIA("acacia", 4,7,11, -460,130,90,-396,100,-25, List.of(new BlockPos(-430,108,9),new BlockPos(-425,108,35),new BlockPos(-416,109,49),new BlockPos(-447,110,19),new BlockPos(-414,109,0),new BlockPos(-406,109,20),new BlockPos(-414,109,49))),
    JUNGLE("jungle", 4,8,14, -412,149,-123,-495,110,-25, List.of(new BlockPos(-442,122,-91),new BlockPos(-453,120,-63),new BlockPos(-470,120,-40),new BlockPos(-469,127,-87)));
    public enum Density { ANY, LOW, MEDIUM, HIGH }
    private final String species; private final int low,medium,high;
    private final BlockPos a,b; private final List<BlockPos> checkpoints;
    ForagingRegion(String species,int low,int medium,int high,int x1,int y1,int z1,int x2,int y2,int z2,List<BlockPos> checkpoints) {
        this.species=species;this.low=low;this.medium=medium;this.high=high;
        a=new BlockPos(x1,y1,z1);b=new BlockPos(x2,y2,z2);this.checkpoints=checkpoints;
    }
    public String species() { return species; }
    public boolean matches(String id) { return this==ANY || speciesOf(id).equals(species); }
    public int minimumLogs(Density density) { return switch(density) { case ANY -> 1; case LOW -> low;case MEDIUM -> medium;case HIGH -> high;}; }
    public boolean contains(BlockPos p) {
        return checkpoints.isEmpty() || p.x()>=Math.min(a.x(),b.x())&&p.x()<=Math.max(a.x(),b.x())
            &&p.y()>=Math.min(a.y(),b.y())&&p.y()<=Math.max(a.y(),b.y())
            &&p.z()>=Math.min(a.z(),b.z())&&p.z()<=Math.max(a.z(),b.z());
    }
    public List<BlockPos> checkpoints() { return checkpoints; }
    public static String speciesOf(String id) {
        String path=id.substring(id.indexOf(':')+1).replaceFirst("^stripped_", "");
        for(String suffix:new String[]{"_log","_wood","_stem","_hyphae"}) if(path.endsWith(suffix)) return path.substring(0,path.length()-suffix.length());
        return "";
    }
}
