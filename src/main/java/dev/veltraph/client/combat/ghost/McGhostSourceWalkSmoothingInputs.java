package dev.veltraph.client.combat.ghost;

/** Exact ordinary-Ghost native primitive bindings for V2's ordered seven-transform pipeline. */
public final class McGhostSourceWalkSmoothingInputs implements GhostSourcePathSmoothing.World {
    private final McGhostSourceWalkTerrainSnapshot terrain;
    public McGhostSourceWalkSmoothingInputs(McGhostSourceWalkTerrainSnapshot actualTerrain){terrain=java.util.Objects.requireNonNull(actualTerrain);}
    private static GhostSourceWalkFrontier.Cell c(GhostSourcePathSmoothing.Cell p){return new GhostSourceWalkFrontier.Cell(p.x(),p.y(),p.z());}
    public Boolean loaded(GhostSourcePathSmoothing.Cell p){return terrain.loaded(c(p));}
    public Boolean passable(GhostSourcePathSmoothing.Cell p){return terrain.block(c(p)).sourcePassable();}
    public Boolean blocked(GhostSourcePathSmoothing.Cell p){var b=terrain.block(c(p));return b.fluid()||b.kind()==GhostSourceOrdinaryWalkTerrain.Kind.WEB;}
    /** Default synthetic mask2 enables empty-fluid full-block bounds; only empty DRY shape returns0. */
    public Double supportRelative(GhostSourcePathSmoothing.Cell p){var cell=c(p);return terrain.block(cell).supportTop()-cell.y();}
    public Double supportAbsolute(GhostSourcePathSmoothing.Cell p){return terrain.block(c(p)).supportTop();}
    public GhostSourcePathSmoothing.Stair stair(GhostSourcePathSmoothing.Cell p){var cell=c(p);boolean stair=terrain.block(cell).kind()==GhostSourceOrdinaryWalkTerrain.Kind.STAIR;
        return new GhostSourcePathSmoothing.Stair(stair,stair?terrain.observedStairFacing(cell):null);}
    public Boolean cocoa(GhostSourcePathSmoothing.Cell p){return terrain.block(c(p)).kind()==GhostSourceOrdinaryWalkTerrain.Kind.COCOA;}
    public Boolean support(GhostSourcePathSmoothing.Cell p,boolean volume){
        if(volume)throw new GhostSourcePathSmoothing.MissingObservation("Other-family volume terrain is outside ordinary Ghost");
        return GhostSourceOrdinaryWalkTerrain.sourceFoot(terrain,c(p));
    }
    public Boolean foot(GhostSourcePathSmoothing.Cell p){return GhostSourceOrdinaryWalkTerrain.sourceFoot(terrain,c(p));}
    /** V legacy name: source GRAPH1886 is full radius1 flat-neighborhood eligibility, not one overhead voxel. */
    public Boolean headClearOne(GhostSourcePathSmoothing.Cell p){return GhostSourceOrdinaryWalkTerrain.flatNeighborhood(terrain,c(p));}
}
