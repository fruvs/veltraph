package dev.veltraph.client.combat.ghost;

/** Exact source GHOST_DESTINATION enum cuboids, BlockPos max inclusive converted to AABB max exclusive. */
public enum GhostSourceDestination {
    MAIN_AREA(114,74,34,183,100,101),
    SECONDARY_AREA(-73,66,92,6,96,162),
    SECONDARY_AREA_EXTENDED(-73,66,30,61,96,162),
    WHOLE_MIST(-73,74,34,183,96,162);
    private final GhostSourceSampledRay.Box bounds;
    GhostSourceDestination(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){bounds=new GhostSourceSampledRay.Box(minX,minY,minZ,maxX,maxY,maxZ);}
    public GhostSourceSampledRay.Box bounds(){return bounds;}
    public boolean contains(double x,double y,double z){return bounds.contains(new GhostSourceSampledRay.Point(x,y,z),0);}
}
