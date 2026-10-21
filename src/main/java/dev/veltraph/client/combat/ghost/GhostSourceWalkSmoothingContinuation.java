package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import dev.veltraph.client.combat.ghost.GhostSourcePathSmoothing.*;

/** Same seven source transforms in the original order. Completed stages are retained;
 * a pending cell retries only the current pure transform over the same immutable input.
 * This is a scheduling adapter, never a native publication or geometry permission. */
final class GhostSourceWalkSmoothingContinuation {
    private final Context context;
    private final World world;
    private final List<String> stages=new ArrayList<>();
    private List<Cell> cells;
    private int next;
    private Result terminal;
    GhostSourceWalkSmoothingContinuation(List<Cell> path,Context context,World world){
        cells=List.copyOf(Objects.requireNonNull(path));this.context=Objects.requireNonNull(context);this.world=Objects.requireNonNull(world);
        if(cells.isEmpty())throw new IllegalArgumentException("Source worker must supply a nonempty path");
    }
    Result advance(){
        if(terminal!=null)return terminal;
        if(!known(context.smoothingEnabled(),"developer toggle"))return finish();
        while(true){
            boolean volume=context.firstVolume()||context.secondVolume();
            switch(next){
                case 0: commit(GhostSourcePathSmoothing.a(cells,world),"A");break;
                case 1: commit(GhostSourcePathSmoothing.b(cells,world),"B");break;
                case 2: if(volume)commit(GhostSourcePathSmoothing.c(cells,world),"C");else next++;break;
                case 3:
                    boolean parent=known(context.parentPresent(),"parent presence");
                    if(parent&&known(context.parentBypass(),"parent bypass"))return finish();
                    commit(GhostSourcePathSmoothing.d(cells,world,context.firstVolume()),"D");break;
                case 4: if(context.secondVolume())commit(GhostSourcePathSmoothing.e(cells,world),"E");else next++;break;
                case 5: if(!volume)commit(GhostSourcePathSmoothing.f(cells,world),"F");else next++;break;
                case 6:
                    if(!volume&&(!known(context.parentPresent(),"parent presence")||!known(context.parentTailVeto(),"parent tail veto"))){
                        if(context.roughStrength()==null)throw new MissingObservation("rough ray strength");
                        commit(GhostSourcePathSmoothing.g(cells,world,context.roughStrength()),"G");
                    }else next++;
                    break;
                default:return finish();
            }
        }
    }
    private void commit(List<Cell> actual,String label){cells=actual;stages.add(label);next++;}
    private Result finish(){return terminal=new Result(cells,stages);}
    private static boolean known(Known value,String label){
        if(value==null||value==Known.UNKNOWN)throw new MissingObservation(label);return value==Known.YES;
    }
}
