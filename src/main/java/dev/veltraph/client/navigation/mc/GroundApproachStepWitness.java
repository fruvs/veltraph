package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.world.Vec3;
import dev.veltraph.client.world.BlockPos;
/** Cached one native step evaluation; no permission, timers, queries or live formatting reads. */
public final class GroundApproachStepWitness {
    private String operand="NOT_EVALUATED";private boolean passed;private Vec3 step,coast;private double dx=Double.NaN,dz=Double.NaN;
    public boolean observe(String name,boolean value){operand=name;passed=value;return value;}
    public void endpoints(Vec3 step,Vec3 coast,double dx,double dz){this.step=step;this.coast=coast;this.dx=dx;this.dz=dz;}
    public Snapshot snapshot(Vec3 actual,Vec3 next,Vec3 coast,BlockPos cell){return new Snapshot(operand,passed,actual,next,coast,step,this.coast,dx,dz,cell);}
    public record Snapshot(String operand,boolean passed,Vec3 actual,Vec3 nominalNext,Vec3 nominalCoast,Vec3 paddedNext,Vec3 paddedCoast,double dx,double dz,BlockPos sourceCell){}
}
