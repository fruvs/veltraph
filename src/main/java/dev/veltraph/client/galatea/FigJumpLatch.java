package dev.veltraph.client.galatea;

/** Recovered Fig input latch. Operand is source cached 3D displacement, never pulse age. */
public final class FigJumpLatch {
    private boolean held;
    public boolean observe(double cachedDx,double cachedDy,double cachedDz) {
        double magnitude=Math.sqrt(cachedDx*cachedDx+cachedDy*cachedDy+cachedDz*cachedDz);
        if(magnitude<.1)held=true;
        return held;
    }
    /** Only the source global input-release equivalent clears this request. */
    public void release(){held=false;}
    public boolean held(){return held;}
}
