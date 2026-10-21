package dev.veltraph.client.farming.movement;

import dev.veltraph.client.world.Vec3;

/** Pure cardinal-corridor feedback for a held oblique view. Decisions never grant native inputs. */
public final class FixedViewKeySteering {
    private FixedViewKeySteering() {}
    public record Keys(boolean forward,boolean back,boolean left,boolean right) {}
    private static final Keys RELEASED=new Keys(false,false,false,false);
    public static Keys toward(Vec3 feet,Vec3 target,float yaw) {
        if(feet==null||target==null||!Float.isFinite(yaw))return RELEASED;
        double dx=target.x()-feet.x(),dz=target.z()-feet.z();
        if(!Double.isFinite(dx)||!Double.isFinite(dz))return RELEASED;
        double radians=Math.toRadians(yaw),sin=Math.sin(radians),cos=Math.cos(radians);
        double[] xs={-sin,sin,cos,-cos},zs={cos,-cos,sin,-sin}; // F,B,L,R native basis.
        boolean xAxis=Math.abs(dx)>=Math.abs(dz);
        double along=xAxis?dx:dz,cross=xAxis?dz:dx;
        double wanted=Math.signum(along);int primary=0;double score=-Double.MAX_VALUE;
        for(int i=0;i<4;i++){double projection=(xAxis?xs[i]:zs[i])*wanted;if(projection>score){score=projection;primary=i;}}
        boolean[] held=new boolean[4];if(Math.abs(along)>.06)held[primary]=true;
        if(Math.abs(cross)>.035) {
            int first=primary<2?2:0,last=primary<2?4:2,correction=first;score=-Double.MAX_VALUE;
            for(int i=first;i<last;i++){double projection=(xAxis?zs[i]:xs[i])*Math.signum(cross);if(projection>score){score=projection;correction=i;}}
            held[correction]=true;
        }
        return new Keys(held[0],held[1],held[2],held[3]);
    }
}
