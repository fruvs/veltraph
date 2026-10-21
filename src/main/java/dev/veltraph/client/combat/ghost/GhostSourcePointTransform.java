package dev.veltraph.client.combat.ghost;

/** Exact source point helpers; use the mode proven by the caller, never a universal center offset. */
public final class GhostSourcePointTransform {
    public record Point(double x,double y,double z) {}
    private GhostSourcePointTransform() {}
    public static Point blockCenter(Point value) { return transform(value,true); }
    public static Point standingCenter(Point value) { return transform(value,false); }
    private static Point transform(Point value,boolean fullCenter) {
        if (value==null || !Double.isFinite(value.x()) || !Double.isFinite(value.y()) || !Double.isFinite(value.z()))
            throw new IllegalArgumentException("Finite source point required");
        return new Point(Math.floor(value.x())+.5,Math.floor(value.y())+(fullCenter?.5:0),Math.floor(value.z())+.5);
    }
}
