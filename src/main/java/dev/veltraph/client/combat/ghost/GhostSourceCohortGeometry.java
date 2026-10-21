package dev.veltraph.client.combat.ghost;

/** Modern input validation before observing native entities; source eligibility remains a separate predicate. */
public final class GhostSourceCohortGeometry {
    private GhostSourceCohortGeometry() {}
    public static boolean finitePoint(double x,double y,double z){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    public static boolean finiteBox(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        return finitePoint(minX,minY,minZ)&&finitePoint(maxX,maxY,maxZ)&&minX<=maxX&&minY<=maxY&&minZ<=maxZ;
    }
    public static boolean sourcePositionEligible(GhostSourceDestination destination,double x,double y,double z){
        return destination!=null&&finitePoint(x,y,z)&&Math.abs(y-76)<2.5&&destination.contains(x,y,z);
    }
}
