package dev.veltraph.client.combat.ghost;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Local, explicitly supplied and hash-verified source field bytes. No file discovery or acquisition. */
public final class GhostSourceParentFieldCodec {
    private GhostSourceParentFieldCodec() {}
    public static GhostSourceParentField decodeObservedResource(byte[] bytes,GhostSourceParentAreaResource observedArea,int maximumValues){
        if(observedArea==null)throw new IllegalArgumentException("Explicit observed source area required");
        return decode(bytes,observedArea.sha256(),observedArea.origin(),maximumValues);
    }
    public static GhostSourceParentField decode(byte[] observedBytes,String expectedSha256,
            GhostSourceOrderedStandingProvider.Cell observedOrigin,int maximumValues){
        if(observedBytes==null||observedOrigin==null||maximumValues<1||expectedSha256==null
                ||!expectedSha256.matches("[0-9a-f]{64}"))throw new IllegalArgumentException("Verified local field inputs required");
        byte[] bytes=observedBytes.clone();
        if(!sha256(bytes).equals(expectedSha256))throw new IllegalArgumentException("Source field hash changed");
        if(bytes.length<12)throw new IllegalArgumentException("Truncated source field dimensions");
        var buffer=ByteBuffer.wrap(bytes); // Source ByteBuffer default: big-endian.
        int x=buffer.getInt(),y=buffer.getInt(),z=buffer.getInt();
        if(x<1||y<1||z<1)throw new IllegalArgumentException("Positive complete source field dimensions required");
        long xy=(long)x*y;
        if(xy>maximumValues||xy*z>maximumValues)throw new IllegalArgumentException("Source field exceeds explicit local memory bound");
        long count=xy*z;
        if(count>(bytes.length-12L)/8)throw new IllegalArgumentException("Truncated source field values");
        double[][][] values=new double[x][y][z];
        for(int i=0;i<x;i++)for(int j=0;j<y;j++)for(int k=0;k<z;k++){
            double value=buffer.getDouble();
            if(!Double.isFinite(value))throw new IllegalArgumentException("Finite source field values required");
            values[i][j][k]=value;
        }
        // Source reader ignores remaining bytes; no undocumented trailing-byte rejection.
        return new GhostSourceParentField(observedOrigin.x(),observedOrigin.y(),observedOrigin.z(),values);
    }
    private static String sha256(byte[] value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
        catch(NoSuchAlgorithmException unavailable){throw new IllegalStateException(unavailable);}
    }
}
