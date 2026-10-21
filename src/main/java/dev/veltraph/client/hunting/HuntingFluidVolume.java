package dev.veltraph.client.hunting;

/** Native fluid interaction uses any overlap of the deflated body, not homogeneous corner samples. */
public final class HuntingFluidVolume {
    private HuntingFluidVolume() {}
    public record Cell(boolean loaded,boolean supported,boolean water,double surface,String description) {}
    public interface Cells { Cell at(int x,int y,int z); }
    public record Observation(boolean supported,boolean water,String detail) {}
    public static Observation inspect(HuntingFlight.Box b,Cells cells){
        boolean water=false;String sample="";
        for(int x=(int)Math.floor(b.minX());x<=(int)Math.ceil(b.maxX())-1;x++)for(int y=(int)Math.floor(b.minY());y<=(int)Math.ceil(b.maxY())-1;y++)for(int z=(int)Math.floor(b.minZ());z<=(int)Math.ceil(b.maxZ())-1;z++){
            var c=cells.at(x,y,z);String at="cell="+x+","+y+","+z+" "+c.description()+" surface="+c.surface()+" body="+b;
            if(!c.loaded()||!c.supported())return new Observation(false,false,at);
            if(c.water()&&c.surface()>=b.minY()){water=true;if(sample.isEmpty())sample=at;}
        }return new Observation(true,water,sample.isEmpty()?"Native body has no water overlap "+b:sample);
    }
}
