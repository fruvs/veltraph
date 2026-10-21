package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourcePathSmoothing.*;

class GhostSourceWalkSmoothingContinuationTest {
    private static final World FLAT=new World(){
        public Boolean loaded(Cell c){return true;}public Boolean passable(Cell c){return c.y()>=76;}
        public Boolean blocked(Cell c){return false;}public Double supportRelative(Cell c){return 1.0;}
        public Double supportAbsolute(Cell c){return c.y()+1.0;}public Stair stair(Cell c){return new Stair(false,null);}
        public Boolean cocoa(Cell c){return false;}public Boolean support(Cell c,boolean volume){return true;}
        public Boolean foot(Cell c){return true;}public Boolean headClearOne(Cell c){return true;}
    };
    private static final class PendingWorld implements World {
        final World actual;final Set<String> completed=new HashSet<>();int suspensions;
        PendingWorld(World actual){this.actual=actual;}
        private <T> T read(String method,Cell c,Supplier<T> value){
            String key=method+":"+c;if(!completed.contains(key)){
                var ready=new CompletableFuture<>();ready.thenRun(()->completed.add(key));suspensions++;
                throw new GhostSourceWalkPendingCell(ready);
            }return value.get();
        }
        public Boolean loaded(Cell c){return read("loaded",c,()->actual.loaded(c));}
        public Boolean passable(Cell c){return read("passable",c,()->actual.passable(c));}
        public Boolean blocked(Cell c){return read("blocked",c,()->actual.blocked(c));}
        public Double supportRelative(Cell c){return read("relative",c,()->actual.supportRelative(c));}
        public Double supportAbsolute(Cell c){return read("absolute",c,()->actual.supportAbsolute(c));}
        public Stair stair(Cell c){return read("stair",c,()->actual.stair(c));}
        public Boolean cocoa(Cell c){return read("cocoa",c,()->actual.cocoa(c));}
        public Boolean support(Cell c,boolean volume){return read("support"+volume,c,()->actual.support(c,volume));}
        public Boolean foot(Cell c){return read("foot",c,()->actual.foot(c));}
        public Boolean headClearOne(Cell c){return read("head",c,()->actual.headClearOne(c));}
    }
    private static Result complete(GhostSourceWalkSmoothingContinuation continuation){
        for(int i=0;i<10000;i++)try{return continuation.advance();}
        catch(GhostSourceWalkPendingCell pending){pending.availability().complete(null);}
        throw new AssertionError("Finite fixture did not finish");
    }
    @Test void ordinaryOrderedTransformsMatchExistingPipelineAfterEachExactWorldDependencySuspends(){
        var path=List.of(new Cell(0,76,0),new Cell(1,76,0),new Cell(2,76,0),new Cell(2,76,1),new Cell(3,76,1));
        var context=new Context(Known.YES,false,false,Known.YES,Known.NO,Known.NO,4);
        var expected=GhostSourcePathSmoothing.apply(path,context,FLAT);var world=new PendingWorld(FLAT);
        var continuation=new GhostSourceWalkSmoothingContinuation(path,context,world);
        var result=complete(continuation);assertEquals(expected,result);assertEquals(List.of("A","B","D","F","G"),result.transforms());
        assertTrue(world.suspensions>0);assertSame(result,continuation.advance());
    }
    @Test void bothVolumeTransformsRetainOriginalSevenStageOrderingAndSkipOrdinaryTail(){
        var path=List.of(new Cell(0,76,0),new Cell(1,76,0),new Cell(1,77,0),new Cell(2,77,0));
        var context=new Context(Known.YES,true,true,Known.YES,Known.NO,Known.UNKNOWN,null);
        var world=new PendingWorld(FLAT);
        assertEquals(GhostSourcePathSmoothing.apply(path,context,FLAT),complete(new GhostSourceWalkSmoothingContinuation(path,context,world)));
    }
    @Test void parentBypassAndDisabledToggleDoNotDemandUnappliedWorldOrTailFacts(){
        var path=List.of(new Cell(0,76,0),new Cell(1,76,0));
        var disabled=new Context(Known.NO,false,false,Known.UNKNOWN,Known.UNKNOWN,Known.UNKNOWN,null);
        var disabledWorld=new PendingWorld(FLAT);
        assertEquals(GhostSourcePathSmoothing.apply(path,disabled,FLAT),complete(new GhostSourceWalkSmoothingContinuation(path,disabled,disabledWorld)));
        assertEquals(0,disabledWorld.suspensions);
        var bypass=new Context(Known.YES,false,false,Known.YES,Known.YES,Known.UNKNOWN,null);
        assertEquals(GhostSourcePathSmoothing.apply(path,bypass,FLAT),complete(new GhostSourceWalkSmoothingContinuation(path,bypass,new PendingWorld(FLAT))));
    }
    @Test void unknownRequiredTailRemainsUnavailableRatherThanBecomingReadyDefault(){
        var path=List.of(new Cell(0,76,0),new Cell(1,76,0));
        var context=new Context(Known.YES,false,false,Known.YES,Known.NO,Known.UNKNOWN,null);
        assertThrows(MissingObservation.class,()->GhostSourcePathSmoothing.apply(path,context,FLAT));
        assertThrows(MissingObservation.class,()->new GhostSourceWalkSmoothingContinuation(path,context,FLAT).advance());
    }
}
