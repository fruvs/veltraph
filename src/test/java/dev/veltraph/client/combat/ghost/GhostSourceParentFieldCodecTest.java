package dev.veltraph.client.combat.ghost;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostSourceParentFieldCodecTest {
    private static final GhostSourceOrderedStandingProvider.Cell ORIGIN=new GhostSourceOrderedStandingProvider.Cell(-5,70,3);
    static String hash(byte[] b){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));}catch(Exception e){throw new AssertionError(e);}}
    static byte[] data(){var b=ByteBuffer.allocate(12+64);b.putInt(2).putInt(2).putInt(2);for(int i=0;i<8;i++)b.putDouble(i-4);return b.array();}
    @Test void verifiedBigEndianDimensionsAndXyzValuesKeepObservedOrigin(){
        var bytes=data();var field=GhostSourceParentFieldCodec.decode(bytes,hash(bytes),ORIGIN,8);
        assertEquals(-4,field.at(-5,70,3));assertEquals(-3,field.at(-5,70,4));
        assertEquals(-2,field.at(-5,71,3));assertEquals(0,field.at(-4,70,3));assertEquals(3,field.at(-4,71,4));
        assertEquals(1,field.at(-6,70,3));assertEquals(1,field.at(-5,72,3));
        Arrays.fill(bytes,(byte)0);assertEquals(-4,field.at(-5,70,3));
    }
    @Test void hashDriftTruncatedHeaderOrPayloadDeniesBeforeAllocation(){
        var original=data();var changed=original.clone();changed[20]^=1;
        assertThrows(IllegalArgumentException.class,()->GhostSourceParentFieldCodec.decode(changed,hash(original),ORIGIN,8));
        for(var b:new byte[][]{new byte[11],Arrays.copyOf(original,original.length-1)})
            assertThrows(IllegalArgumentException.class,()->GhostSourceParentFieldCodec.decode(b,hash(b),ORIGIN,8));
    }
    @Test void explicitMemoryBoundInvalidDimensionsAndNonfiniteValuesDeny(){
        var b=data();assertThrows(IllegalArgumentException.class,()->GhostSourceParentFieldCodec.decode(b,hash(b),ORIGIN,7));
        for(int dimension:new int[]{0,-1,Integer.MAX_VALUE}){
            var bad=ByteBuffer.allocate(12).putInt(dimension).putInt(Integer.MAX_VALUE).putInt(Integer.MAX_VALUE).array();
            assertThrows(IllegalArgumentException.class,()->GhostSourceParentFieldCodec.decode(bad,hash(bad),ORIGIN,8));
        }
        var nan=data();ByteBuffer.wrap(nan).putDouble(12,Double.NaN);
        assertThrows(IllegalArgumentException.class,()->GhostSourceParentFieldCodec.decode(nan,hash(nan),ORIGIN,8));
    }
    @Test void sourceReaderIgnoresTrailingBytesWithoutChangingField(){
        var b=Arrays.copyOf(data(),80);b[79]=12;
        assertEquals(3,GhostSourceParentFieldCodec.decode(b,hash(b),ORIGIN,8).at(-4,71,4));
    }
}
