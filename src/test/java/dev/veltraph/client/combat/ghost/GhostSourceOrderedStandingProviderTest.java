package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceOrderedStandingProvider.*;
final class GhostSourceOrderedStandingProviderTest {
    @Test void equalScoresKeepFirstXThenYThenZCandidate(){
        var left=new Cell(-2,76,0);var right=new Cell(2,76,0);
        var chosen=GhostSourceOrderedStandingProvider.choose(new Inputs(){
            public boolean sourceCellStandable(Cell c){return c.equals(left)||c.equals(right);}
            public Point targetStandingPoint(){return new Point(.5,76,.5);}
            public Point actualPlayerPosition(){return new Point(.5,76,.5);}
        });assertEquals(left,chosen);
    }
    @Test void emptyCandidatesFallbackOnlyForGroundedTarget(){
        assertEquals(new Cell(0,76,0),GhostSourceOrderedStandingProvider.choose(new Inputs(){public boolean sourceCellStandable(Cell c){return false;}}));
        assertNull(GhostSourceOrderedStandingProvider.choose(new Inputs(){public boolean sourceCellStandable(Cell c){return false;}public boolean targetOnGround(){return false;}}));
    }
    @Test void unknownContextOrAreaCostCannotSelectAStandingPoint(){
        assertNull(GhostSourceOrderedStandingProvider.choose(new Inputs(){public boolean current(){return false;}}));
        assertNull(GhostSourceOrderedStandingProvider.choose(new Inputs(){public double parentFieldValue(Cell c){return Double.NaN;}}));
    }
    @Test void nullOriginTargetOrPlayerDeniesBeforeAnyNativePredicate(){
        for(int missing=0;missing<3;missing++){
            final int operand=missing;
            var input=new Inputs(){
                public Cell targetBlock(){return operand==0?null:super.targetBlock();}
                public Point targetStandingPoint(){return operand==1?null:super.targetStandingPoint();}
                public Point actualPlayerPosition(){return operand==2?null:super.actualPlayerPosition();}
                public boolean sourceCellStandable(Cell c){throw new AssertionError("Bad geometry reached stand predicate");}
                public boolean sourceProtectedTargetRay(Cell c){throw new AssertionError("Bad geometry reached ray predicate");}
                public double parentFieldValue(Cell c){throw new AssertionError("Bad geometry reached area predicate");}
                public boolean lavaAtOrBelow(Cell c){throw new AssertionError("Bad geometry reached lava predicate");}
                public double customPenalty(Cell c){throw new AssertionError("Bad geometry reached custom predicate");}
                public boolean targetOnGround(){throw new AssertionError("Bad geometry reached grounded fallback");}
            };
            assertNull(GhostSourceOrderedStandingProvider.choose(input));
        }
    }
    @Test void nonfinitePointFailsBeforeNativePredicatesOrGroundedFallback(){
        assertThrows(IllegalArgumentException.class,()->new Point(Double.NaN,76,0));
        assertThrows(IllegalArgumentException.class,()->new Point(0,Double.POSITIVE_INFINITY,0));
        assertNull(GhostSourceOrderedStandingProvider.choose(new Inputs(){
            public Point targetStandingPoint(){return new Point(0,76,Double.NEGATIVE_INFINITY);}
            public boolean sourceCellStandable(Cell c){throw new AssertionError("Nonfinite geometry reached native predicate");}
            public boolean targetOnGround(){throw new AssertionError("Nonfinite geometry reached fallback");}
        }));
    }
    @Test void signedCustomPenaltyIsPreserved(){
        var preferred=new Cell(1,76,0);
        assertEquals(preferred,GhostSourceOrderedStandingProvider.choose(new Inputs(){
            public double customPenalty(Cell c){return c.equals(preferred)?-100:0;}
        }));
    }
    private static class Inputs implements NativeInputs {
        public ParentAreaObservation parentAreaObservation(){return ParentAreaObservation.PRESENT;}
        public boolean current(){return true;}public Cell targetBlock(){return new Cell(0,76,0);}
        public Point targetStandingPoint(){return new Point(.5,76,.5);}public Point actualPlayerPosition(){return new Point(.5,76,.5);}
        public boolean targetOnGround(){return true;}public boolean sourceCellStandable(Cell c){return c.y()==76;}
        public boolean sourceProtectedTargetRay(Cell c){return true;}public double parentFieldValue(Cell c){return 0;}
        public boolean lavaAtOrBelow(Cell c){return false;}public double customPenalty(Cell c){return 0;}
    }
}
