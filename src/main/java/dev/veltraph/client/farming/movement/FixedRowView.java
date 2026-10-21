package dev.veltraph.client.farming.movement;

/** Normal S profile selected once against an observed mature outline. Native guards stay independent. */
public record FixedRowView(float yaw,float pitch) {
    public FixedRowView {
        if(!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch<-90||pitch>90)
            throw new IllegalArgumentException("Nonfinite fixed row view");
    }
    public static FixedRowView forMatureWheat(float cardinalYaw,double eyeY,int cropY,int furthest,double settlingDegrees) {
        if(!Float.isFinite(cardinalYaw)||!Double.isFinite(eyeY)||furthest<2||furthest>4
                ||!Double.isFinite(settlingDegrees)||settlingDegrees<0||settlingDegrees>2)return null;
        return forNormalCrop(cardinalYaw,eyeY,cropY,furthest,1.0,settlingDegrees);
    }
    public static FixedRowView forNormalCrop(float cardinalYaw,double eyeY,int cropY,int furthest,
            double outlineHeight,double settlingDegrees) {
        return forNormalCrop(cardinalYaw,eyeY,cropY,1,furthest,outlineHeight,settlingDegrees);
    }
    public static FixedRowView forNormalCrop(float cardinalYaw,double eyeY,int cropY,int nearest,int furthest,
            double outlineHeight,double settlingDegrees) {
        return forNormalCrop(cardinalYaw,eyeY,cropY,nearest,furthest,outlineHeight,settlingDegrees,0);
    }
    public static FixedRowView forNormalCrop(float cardinalYaw,double eyeY,int cropY,int nearest,int furthest,
            double outlineHeight,double settlingDegrees,double forwardOffset) {
        if(!Float.isFinite(cardinalYaw)||!Double.isFinite(eyeY)||nearest<1||furthest<nearest||furthest>4
                ||!Double.isFinite(forwardOffset)||forwardOffset<-.5||forwardOffset>=.5
                ||!Double.isFinite(outlineHeight)||outlineHeight<=0||outlineHeight>1
                ||!Double.isFinite(settlingDegrees)||settlingDegrees<0||settlingDegrees>2)return null;
        // FarmHelper's normal S profile selects cardinal yaw and a shallow pitch once on enable.
        // Keep that behavior; modern crop outlines validate the candidate, never force a hidden crop.
        double lower=-90,upper=90;
        for(int d=nearest;d<=furthest;d++) {
            // A descending ray may enter the TOP before leaving the block, rather than its front face.
            // Immature/foreign front occluders are never removed by this geometry calculation.
            double entry=d-.5-forwardOffset,exit=d+.5-forwardOffset;
            lower=Math.max(lower,Math.toDegrees(Math.atan((eyeY-(cropY+outlineHeight))/exit)));
            upper=Math.min(upper,Math.toDegrees(Math.atan((eyeY-cropY)/entry)));
        }
        lower+=settlingDegrees;upper-=settlingDegrees;
        if(!Double.isFinite(lower)||!Double.isFinite(upper)||lower>=upper)return null;
        // Deterministic representative of the archived 2.8–3.3 degree normal-S startup range.
        // Unsupported fields refuse this profile; they never select a different crop-facing pitch.
        double pitch=3.0;
        if(pitch<lower||pitch>upper)return null;
        return new FixedRowView(cardinalYaw,(float)pitch);
    }
}
