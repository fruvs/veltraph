package dev.veltraph.client.garden.pests;

import java.util.List;
import java.util.OptionalInt;

/** Horizontal rectangles recovered from PlotRegistry static map, plot zero is the barn. */
public final class GardenPlotBounds {
    public record Bounds(int id,int minX,int maxX,int minZ,int maxZ) {
        public boolean contains(double x,double z){return Double.isFinite(x)&&Double.isFinite(z)&&x>=minX&&x<maxX&&z>=minZ&&z<maxZ;}
    }
    private GardenPlotBounds() {}
    public static final List<Bounds> ALL=List.of(
        new Bounds(0,-48,48,-48,48),
        new Bounds(1,-48,48,-144,-48),
        new Bounds(2,-144,-48,-48,48),
        new Bounds(3,48,144,-48,48),
        new Bounds(4,-48,48,48,144),
        new Bounds(5,-144,-48,-144,-48),
        new Bounds(6,48,144,-144,-48),
        new Bounds(7,-144,-48,48,144),
        new Bounds(8,48,144,48,144),
        new Bounds(9,-48,48,-240,-144),
        new Bounds(10,-240,-144,-48,48),
        new Bounds(11,144,240,-48,48),
        new Bounds(12,-48,48,144,240),
        new Bounds(13,-144,-48,-240,-144),
        new Bounds(14,48,144,-240,-144),
        new Bounds(15,-240,-144,-144,-48),
        new Bounds(16,144,240,-144,-48),
        new Bounds(17,-240,-144,48,144),
        new Bounds(18,144,240,48,144),
        new Bounds(19,-144,-48,144,240),
        new Bounds(20,48,144,144,240),
        new Bounds(21,-240,-144,-240,-144),
        new Bounds(22,144,240,-240,-144),
        new Bounds(23,-240,-144,144,240),
        new Bounds(24,144,240,144,240));
    public static boolean contains(int plot,double x,double z){return ALL.stream().anyMatch(b->b.id()==plot&&b.contains(x,z));}
    /** Source plot ctor Y=60..91, BoxUtil max+1, containsPosition horizontal expansion by 2. */
    public static boolean sourceSprayContains(int plot,double x,double y,double z){
        return Double.isFinite(y)&&y>=60&&y<92&&ALL.stream().anyMatch(b->b.id()==plot&&Double.isFinite(x)&&Double.isFinite(z)
                &&x>=b.minX()-2&&x<b.maxX()+2&&z>=b.minZ()-2&&z<b.maxZ()+2);
    }
    public static OptionalInt at(double x,double z){return ALL.stream().filter(b->b.contains(x,z)).mapToInt(Bounds::id).findFirst();}
}
