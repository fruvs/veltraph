package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;

/** Native client-tick pose stability and newly observed received endpoint, never a server clock claim. */
public final class HuntingDispatchFreshness {
    public record Gate(boolean ready,String reason) {}
    private long tick=Long.MIN_VALUE;private String pose;private double eyeHeight;private Vec3 endpoint;
    public Gate observe(long nativeTick,String nativePose,double nativeEyeHeight,Vec3 receivedEndpoint){
        if(nativePose==null||receivedEndpoint==null||!Double.isFinite(nativeEyeHeight)
            ||!Double.isFinite(receivedEndpoint.x())||!Double.isFinite(receivedEndpoint.y())||!Double.isFinite(receivedEndpoint.z()))return new Gate(false,"Invalid native dispatch observation");
        if(nativeTick<=tick)return new Gate(false,"No new native player tick for dispatch");
        boolean stable=pose!=null&&pose.equals(nativePose)&&eyeHeight==nativeEyeHeight;
        boolean fresh=endpoint!=null&&!endpoint.equals(receivedEndpoint);
        tick=nativeTick;pose=nativePose;eyeHeight=nativeEyeHeight;endpoint=receivedEndpoint;
        if(!stable)return new Gate(false,"Native launch pose/eye height is still changing");
        if(!fresh)return new Gate(false,"Waiting for a newly received native target endpoint");
        return new Gate(true,"Stable native pose with newly observed packet endpoint");
    }
    public void reset(){tick=Long.MIN_VALUE;pose=null;endpoint=null;}
}
