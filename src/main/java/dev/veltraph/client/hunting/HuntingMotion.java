package dev.veltraph.client.hunting;

import dev.veltraph.client.world.Vec3;
import java.util.ArrayDeque;

/** Stable helper: FIFO of 40 native per-tick displacements, then current aim plus mean ×5. */
public final class HuntingMotion {
    private final ArrayDeque<Vec3> samples=new ArrayDeque<>();
    public void sample(Vec3 displacement){
        if(displacement==null||!Double.isFinite(displacement.x())||!Double.isFinite(displacement.y())||!Double.isFinite(displacement.z()))throw new IllegalArgumentException("Finite native target displacement required");
        samples.addLast(displacement);if(samples.size()>40)samples.removeFirst();
    }
    public Vec3 predict(Vec3 current){double x=0,y=0,z=0;for(var p:samples){x+=p.x();y+=p.y();z+=p.z();}int n=samples.size();return n==0?current:new Vec3(current.x()+x/n*5,current.y()+y/n*5,current.z()+z/n*5);}
    public int sampleCount(){return samples.size();}
    public void reset(){samples.clear();}
}
