package dev.veltraph.client.combat.ghost;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
final class GhostSourceParentFieldTest {
    @Test void samplesOffsetScalarRatherThanGeometricDistance(){
        var source=new double[][][]{{{.25,7}},{{-2}}};
        var field=new GhostSourceParentField(-3,74,9,source);
        assertEquals(.25,field.at(-3,74,9));assertEquals(7,field.at(-3,74,10));assertEquals(-2,field.at(-2,74,9));
        source[0][0][0]=123;assertEquals(.25,field.at(-3,74,9));
    }
    @Test void outsideObservedFieldReturnsSourceOneAndRejectsUnknownValues(){
        var field=new GhostSourceParentField(0,0,0,new double[][][]{{{4}}});
        assertEquals(1,field.at(-1,0,0));assertEquals(1,field.at(0,1,0));assertEquals(1,field.at(Integer.MAX_VALUE,0,0));
        assertThrows(IllegalArgumentException.class,()->new GhostSourceParentField(0,0,0,new double[][][]{{{Double.NaN}}}));
    }
}
