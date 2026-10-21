package dev.veltraph.client.combat.ghost;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.combat.ghost.GhostSourceOrderedStandingProvider.*;

class GhostSourceOptionalParentAreaTest {
    static final class Input implements NativeInputs {
        ParentAreaObservation observation;int bodyCalls,fieldCalls,geometryCalls;
        Input(ParentAreaObservation observation){this.observation=observation;}
        public boolean current(){return true;}
        public ParentAreaObservation parentAreaObservation(){return observation;}
        public Cell targetBlock(){geometryCalls++;return new Cell(0,0,0);}
        public Point targetStandingPoint(){geometryCalls++;return new Point(1.5,0,.5);}
        public Point actualPlayerPosition(){geometryCalls++;return new Point(.5,0,.5);}
        public boolean targetOnGround(){return true;}
        public boolean sourceCellStandable(Cell c){bodyCalls++;return c.y()==0&&c.z()==0&&(c.x()==0||c.x()==1);}
        public boolean sourceProtectedTargetRay(Cell c){return true;}
        public double parentFieldValue(Cell c){fieldCalls++;return c.x()==0?-3:0;}
        public boolean lavaAtOrBelow(Cell c){return false;}
        public double customPenalty(Cell c){return 0;}
    }
    @Test void explicitlyAbsentSkipsFieldReadsAndItsScoreTerm(){
        var input=new Input(ParentAreaObservation.ABSENT);
        assertEquals(new Cell(0,0,0),GhostSourceOrderedStandingProvider.choose(input));
        assertEquals(0,input.fieldCalls);assertTrue(input.bodyCalls>0);
    }
    @Test void presentSamplesSignedFieldAndSquaresContribution(){
        var input=new Input(ParentAreaObservation.PRESENT);
        assertEquals(new Cell(1,0,0),GhostSourceOrderedStandingProvider.choose(input));
        assertEquals(2,input.fieldCalls);
    }
    @Test void unknownOrUnobservedAreaDeniesBeforeGeometryAndWorldPredicates(){
        for(var observation:new ParentAreaObservation[]{ParentAreaObservation.UNKNOWN,null}){
            var input=new Input(observation);assertNull(GhostSourceOrderedStandingProvider.choose(input));
            assertEquals(0,input.geometryCalls);assertEquals(0,input.bodyCalls);assertEquals(0,input.fieldCalls);
        }
    }
}
