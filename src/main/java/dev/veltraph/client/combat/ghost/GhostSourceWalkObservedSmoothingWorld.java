package dev.veltraph.client.combat.ghost;
/** Off-thread V2 primitive adapter; all values originate in actual native demand capture. */
public final class GhostSourceWalkObservedSmoothingWorld implements GhostSourcePathSmoothing.World {
    private final GhostSourceWalkObservedWorld world;private final java.util.function.Function<GhostSourceWalkFrontier.Cell,GhostSourceWalkObservedWorld.CellValue> cells;
    public GhostSourceWalkObservedSmoothingWorld(GhostSourceWalkObservedWorld world,java.util.function.Function<GhostSourceWalkFrontier.Cell,GhostSourceWalkObservedWorld.CellValue> cells){this.world=java.util.Objects.requireNonNull(world);this.cells=java.util.Objects.requireNonNull(cells);}
    private static GhostSourceWalkFrontier.Cell c(GhostSourcePathSmoothing.Cell p){return new GhostSourceWalkFrontier.Cell(p.x(),p.y(),p.z());}
    public Boolean loaded(GhostSourcePathSmoothing.Cell p){cells.apply(c(p));return true;} // CaptureValue exists only after actual native isLoaded succeeds.
    public Boolean passable(GhostSourcePathSmoothing.Cell p){return world.sourcePassable(c(p));}
    public Boolean blocked(GhostSourcePathSmoothing.Cell p){var b=world.block(c(p));return b.fluid()||b.kind()==GhostSourceOrdinaryWalkTerrain.Kind.WEB;}
    public Double supportRelative(GhostSourcePathSmoothing.Cell p){return world.supportTop(c(p))-p.y();}
    public Double supportAbsolute(GhostSourcePathSmoothing.Cell p){return world.supportTop(c(p));}
    public GhostSourcePathSmoothing.Stair stair(GhostSourcePathSmoothing.Cell p){var v=cells.apply(c(p));boolean stair=v.block().kind()==GhostSourceOrdinaryWalkTerrain.Kind.STAIR;return new GhostSourcePathSmoothing.Stair(stair,stair?v.facing():null);}
    public Boolean cocoa(GhostSourcePathSmoothing.Cell p){return world.block(c(p)).kind()==GhostSourceOrdinaryWalkTerrain.Kind.COCOA;}
    public Boolean support(GhostSourcePathSmoothing.Cell p,boolean volume){if(volume)throw new GhostSourcePathSmoothing.MissingObservation("Other-family volume branch");return foot(p);}
    public Boolean foot(GhostSourcePathSmoothing.Cell p){return GhostSourceOrdinaryWalkTerrain.sourceFoot(world,c(p));}
    public Boolean headClearOne(GhostSourcePathSmoothing.Cell p){return GhostSourceOrdinaryWalkTerrain.flatNeighborhood(world,c(p));}
}
