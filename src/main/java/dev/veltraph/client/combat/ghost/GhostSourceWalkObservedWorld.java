package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;
import dev.veltraph.client.combat.ghost.GhostSourceOrdinaryWalkTerrain.Kind;

/** Pure worker terrain over immutable actually captured cells. Missing cells demand native observation; never become AIR. */
public final class GhostSourceWalkObservedWorld implements GhostSourceOrdinaryWalkTerrain.World,GhostSourceWalkEdgeGeometry.World {
    public record CellValue(GhostSourceOrdinaryWalkTerrain.Block block,GhostSourceSampledRay.Box rawCollision,String facing,boolean bottomStair) {
        public CellValue {Objects.requireNonNull(block);}
    }
    private final Function<Cell,CellValue> captured;private final BooleanSupplier valid;
    public GhostSourceWalkObservedWorld(Function<Cell,CellValue> actualCapturedCells,BooleanSupplier actualCaptureValid){captured=Objects.requireNonNull(actualCapturedCells);valid=Objects.requireNonNull(actualCaptureValid);}
    public boolean current(){return valid.getAsBoolean();}
    private CellValue value(Cell cell){if(!current())throw new IllegalStateException("Actual source capture revoked");return Objects.requireNonNull(captured.apply(cell),"Required observed source cell");}
    public GhostSourceOrdinaryWalkTerrain.Block block(Cell cell){return value(cell).block();}
    public boolean sourcePassable(Cell cell){return block(cell).sourcePassable();}
    public boolean fluid(Cell cell){return block(cell).fluid();}
    public double supportTop(Cell cell){return block(cell).supportTop();}
    public boolean standable(Cell cell){return GhostSourceOrdinaryWalkTerrain.standable(this,cell,null);}
    public boolean directionalPassable(Cell cell,Offset direction){
        var v=value(cell);var b=v.block();if(b.sourcePassable())return true;
        if(direction==null||Math.abs(direction.x())==1&&Math.abs(direction.z())==1)return false;
        var d=Direction.getNearest(direction.x(),direction.y(),direction.z(),Direction.UP);
        if(b.kind()==Kind.TRAP_DOOR){if(!b.open())return false;var box=v.rawCollision();
            if(box==null&&b.fluid())box=new GhostSourceSampledRay.Box(cell.x(),cell.y(),cell.z(),cell.x()+1.0,cell.y()+1.0,cell.z()+1.0);
            if(box==null)return true;double x=(box.minX()+box.maxX())/2,y=(box.minY()+box.maxY())/2,z=(box.minZ()+box.maxZ())/2;
            double mx=cell.x()+.5-d.getStepX()/2.0,my=cell.y()+.5-d.getStepY()/2.0,mz=cell.z()+.5-d.getStepZ()/2.0;
            double distance=d==Direction.UP||d==Direction.DOWN?Math.abs(my-y):Math.sqrt(Math.pow(mx-x,2)+Math.pow(mz-z,2));return !(distance<.2);
        }
        if(b.kind()==Kind.COCOA){if(v.facing()==null)throw new IllegalStateException("Observed cocoa facing required");var second=relative(cell,Direction.valueOf(v.facing()).getOpposite(),1);
            if(block(second).kind()!=Kind.COCOA)return false;
            for(var c:List.of(relative(cell,d,1),relative(second,d,1),relative(cell,d,2),relative(second,d,2),add(cell,0,-1,0),add(second,0,-1,0),add(cell,0,-2,0),add(second,0,-2,0))){var other=block(c);if(!other.sourcePassable()&&other.kind()!=Kind.COCOA)return false;}return current();
        }return false;
    }
    public boolean sameStairFacing(Cell from,Cell to){var a=value(from);var b=value(to);return a.block().kind()==Kind.STAIR&&b.block().kind()==Kind.STAIR&&Objects.equals(a.facing(),b.facing());}
    public boolean destinationBottomStairFacingMovementOpposite(Cell destination,Cell origin){var v=value(destination);
        if(v.block().kind()!=Kind.STAIR||!v.bottomStair())return false;if(v.facing()==null)throw new IllegalStateException("Observed stair facing required");
        float yaw=(float)Math.atan2((double)origin.z()-destination.z(),(double)origin.x()-destination.x());yaw=(float)Mth.wrapDegrees((double)(yaw*180.0f)/Math.PI-90.0);
        float a=Mth.cos(-yaw*((float)Math.PI/180)-(float)Math.PI),b=Mth.sin(-yaw*((float)Math.PI/180)-(float)Math.PI);
        var vector=new Vec3((double)(b*-Mth.cos(0)),0,(double)(a*-Mth.cos(0))).normalize();var move=Direction.getNearest((int)vector.x,(int)vector.y,(int)vector.z,null);
        return Direction.valueOf(v.facing()).getOpposite()==move;
    }
    private static Cell add(Cell c,int x,int y,int z){return new Cell(Math.addExact(c.x(),x),Math.addExact(c.y(),y),Math.addExact(c.z(),z));}
    private static Cell relative(Cell c,Direction d,int n){return add(c,d.getStepX()*n,d.getStepY()*n,d.getStepZ()*n);}
}
