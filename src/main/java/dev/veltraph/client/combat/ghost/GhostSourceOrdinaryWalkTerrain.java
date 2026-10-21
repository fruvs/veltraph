package dev.veltraph.client.combat.ghost;

import java.util.Objects;
import java.util.Set;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.*;

/** GRAPH ordinary-ground terrain arithmetic. Every cell/property is observed through the native job frame. */
public final class GhostSourceOrdinaryWalkTerrain {
    public enum Kind { OTHER,FENCE,FENCE_GATE,WALL,IRON_BARS,SLIME,WEB,LILY_PAD,TRAP_DOOR,COCOA,STAIR,LAVA,DOOR,FLOWER_POT,LANTERN,CARPET,MOSSY_CARPET,SNOW }
    public record Block(Kind kind,boolean sourcePassable,boolean fluid,boolean supportAllowed,boolean open,int snowLayers,double supportTop){
        public Block {Objects.requireNonNull(kind);if(!Double.isFinite(supportTop))throw new IllegalArgumentException("Actual finite collision/fluid top required");}
    }
    /** Fully observed additional active-parent graph policies; UNKNOWN must never be converted to empty or zero. */
    public interface World {
        boolean current();Block block(Cell cell);
        boolean directionalPassable(Cell cell,Offset direction);
        boolean sameStairFacing(Cell fromSupport,Cell toSupport);
        boolean destinationBottomStairFacingMovementOpposite(Cell destination,Cell origin);
    }
    private GhostSourceOrdinaryWalkTerrain() {}
    private static Cell offset(Cell c,int x,int y,int z){return new Cell(Math.addExact(c.x(),x),Math.addExact(c.y(),y),Math.addExact(c.z(),z));}
    public static boolean preflightAir(Block b){return b.sourcePassable||switch(b.kind){
        case FENCE,WALL,IRON_BARS,TRAP_DOOR,DOOR,FLOWER_POT,LANTERN,CARPET,MOSSY_CARPET->true;
        case SNOW->b.snowLayers<=4;default->false;};}
    public static boolean body(World w,Cell c,Offset direction){return w.directionalPassable(c,direction)&&w.directionalPassable(offset(c,0,1,0),direction);}
    public static boolean standable(World w,Cell c,Offset direction){
        if(!body(w,c,direction))return false;var under=w.block(offset(c,0,-1,0));if(under.supportAllowed)return true;
        if(under.kind==Kind.FENCE||under.kind==Kind.WALL||under.kind==Kind.FENCE_GATE&&!under.open)return false;
        return w.block(c).fluid&&under.fluid;
    }
    public static double standingHeight(World w,Cell c){return w.block(c).fluid?c.y()+.5:w.block(offset(c,0,-1,0)).supportTop;}
    public static boolean admit(World w,Cell from,Offset move,Set<Cell> actualForbiddenCells,boolean observedNativeJumpBoost){
        Objects.requireNonNull(w);Objects.requireNonNull(from);Objects.requireNonNull(move);Objects.requireNonNull(actualForbiddenCells);
        if(!w.current()||!GhostSourceWalkFrontier.structuralMove(move))return false;
        var to=offset(from,move.x(),move.y(),move.z());
        if(actualForbiddenCells.contains(to)||actualForbiddenCells.contains(offset(to,0,-1,0)))return false;
        if(!standable(w,to,move))return false;
        if(standingHeight(w,to)-standingHeight(w,from)>1+.1)return false;
        if(move.x()*move.z()!=0&&(!body(w,offset(from,move.x(),0,0),null)||!body(w,offset(from,0,0,move.z()),null)))return false;
        if(move.y()==1&&!w.directionalPassable(offset(from,0,2,0),move))return false;
        if(move.y()<=-1&&!w.directionalPassable(offset(to,0,2,0),move))return false;
        if(move.y()<-1)for(int y=0;y<from.y()-to.y()+2;y++)if(!w.block(offset(to,0,y,0)).sourcePassable)return false;
        if(observedNativeJumpBoost&&w.block(offset(to,0,-1,0)).kind==Kind.SLIME)return false;
        return w.current();
    }
    /** Ordinary Ghost manager/region modifiers must be added by the separately observed options policy. */
    public static double terrainCost(World w,Cell from,Offset move,boolean observedNativeJumpBoost,int visitLimit){
        if(!w.current())throw new IllegalStateException("Actual terrain frame changed");var to=offset(from,move.x(),move.y(),move.z());
        var body=w.block(to);var below=w.block(offset(to,0,-1,0));double height=standingHeight(w,to)-standingHeight(w,from);
        double cost=GhostSourceWalkBaseCost.observedBase(move.x(),move.y(),move.z(),height,from.y(),to.y());
        int truncatedRise=height<0?0:(int)height;
        boolean needsJump=move.y()>=1&&!w.sameStairFacing(offset(from,0,-1,0),offset(to,0,-1,0))
                &&!w.destinationBottomStairFacingMovementOpposite(to,from)&&truncatedRise>.5;
        if(needsJump&&!body.fluid){cost+=3;if(observedNativeJumpBoost&&height>.6)cost+=visitLimit>=1000?3:1;}
        if(body.kind==Kind.LAVA||below.kind==Kind.LAVA)cost+=10;
        if(body.kind==Kind.WEB)cost+=10;
        if(nearHazard(w,to,!w.block(from).fluid))cost+=4;
        if(nearFourClearColumn(w,to))cost+=5;
        if(flatNeighborhood(w,from)&&!flatNeighborhood(w,to))cost+=2;
        if(!body.fluid&&!w.block(offset(to,0,2,0)).sourcePassable)cost+=.8;
        if(nearBodyObstacle(w,to))cost+=2;
        if(body.fluid){int count=0;for(int y=1;y<10;y++){if(!w.block(offset(to,0,y,0)).fluid)break;count++;}cost+=1+Math.pow(count,2)*5.5;}
        if(!w.current())throw new IllegalStateException("Terrain changed during source cost");return cost;
    }
    public static boolean sourceFoot(World w,Cell c){return !w.block(offset(c,0,-1,0)).fluid&&standable(w,c,null);}
    private static boolean nearHazard(World w,Cell c,boolean includeFluids){
        for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++){
            var b=w.block(offset(c,x,y,z));if(includeFluids&&b.fluid||b.kind==Kind.FENCE||b.kind==Kind.SLIME||b.kind==Kind.WEB||b.kind==Kind.LILY_PAD)return true;
        }return false;
    }
    private static boolean nearFourClearColumn(World w,Cell c){
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){if(x==0&&z==0)continue;boolean all=true;
            for(int y=-1;y<3;y++)if(!w.block(offset(c,x,-y,z)).sourcePassable){all=false;break;}
            if(all)return true;
        }return false;
    }
    public static boolean flatNeighborhood(World w,Cell c){
        double top=w.block(offset(c,0,-1,0)).supportTop;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){if(x==0&&z==0)continue;boolean eligible=false;
            for(int y=-1;y<=1;y++){var n=offset(c,x,y,z);
                if(Math.abs(w.block(offset(n,0,-1,0)).supportTop-top)<=.5&&standable(w,n,null)){eligible=true;break;}}
            if(!eligible)return false;
        }return true;
    }
    private static boolean nearBodyObstacle(World w,Cell c){
        for(int x=-1;x<=1;x++)for(int y=0;y<=1;y++)for(int z=-1;z<=1;z++)if(!w.block(offset(c,x,y,z)).sourcePassable)return true;
        return false;
    }
}
