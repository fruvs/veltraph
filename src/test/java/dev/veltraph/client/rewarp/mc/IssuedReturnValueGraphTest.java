package dev.veltraph.client.rewarp.mc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class IssuedReturnValueGraphTest {
    @Test void mutableNbtArrayProducesDetachedExactValues(){var source=new byte[]{1,2};var value=new IssuedReturnValueGraph.Writer().bytes(source,0);source[0]=9;assertEquals("1",value.children().get(0).scalar());assertNotEquals(value,new IssuedReturnValueGraph.Writer().bytes(source,0));}
    @Test void rawFloatingBitsDistinguishSignedZero(){var first=new IssuedReturnValueGraph.Writer().atom("float-bits",Integer.toString(Float.floatToRawIntBits(0f)),0);var second=new IssuedReturnValueGraph.Writer().atom("float-bits",Integer.toString(Float.floatToRawIntBits(-0f)),0);assertNotEquals(first,second);}
    @Test void oversizedNativeGraphRefusesRatherThanTruncating(){var source=new int[4096];assertThrows(IssuedReturnValueGraph.Unsupported.class,()->new IssuedReturnValueGraph.Writer().ints(source,0));}
    @Test void oversizedCustomNbtTextRefusesRatherThanDroppingSuffix(){assertThrows(IssuedReturnValueGraph.Unsupported.class,()->new IssuedReturnValueGraph.Writer().atom("nbt-string","a".repeat(8193),0));}
}
