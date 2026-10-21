package dev.veltraph.client.navigation;
import java.util.*;
/** Exact represented-coordinate rectangle union, used only with actual native collision top faces. */
public final class FlightFootprintCoverage {
    public record Face(double minX,double maxX,double minZ,double maxZ){}
    private FlightFootprintCoverage(){}
    public static boolean covers(Face footprint,List<Face> faces){
        if(!valid(footprint)||faces==null||faces.size()>512)return false;
        var clipped=new ArrayList<Face>();var cuts=new TreeSet<Double>();cuts.add(footprint.minX());cuts.add(footprint.maxX());
        for(var f:faces){if(!valid(f))return false;var c=new Face(Math.max(footprint.minX(),f.minX()),Math.min(footprint.maxX(),f.maxX()),Math.max(footprint.minZ(),f.minZ()),Math.min(footprint.maxZ(),f.maxZ()));
            if(c.minX()<c.maxX()&&c.minZ()<c.maxZ()){clipped.add(c);cuts.add(c.minX());cuts.add(c.maxX());}}
        var x=new ArrayList<>(cuts);
        for(int n=1;n<x.size();n++){
            double a=x.get(n-1),b=x.get(n),covered=footprint.minZ();var spans=new ArrayList<Face>();
            for(var f:clipped)if(f.minX()<=a&&f.maxX()>=b)spans.add(f);
            spans.sort(Comparator.comparingDouble(Face::minZ).thenComparingDouble(Face::maxZ));
            for(var f:spans){if(f.minZ()>covered)return false;covered=Math.max(covered,f.maxZ());}
            if(covered<footprint.maxZ())return false;
        }
        return true;
    }
    private static boolean valid(Face f){return f!=null&&Double.isFinite(f.minX())&&Double.isFinite(f.maxX())&&Double.isFinite(f.minZ())&&Double.isFinite(f.maxZ())&&f.minX()<f.maxX()&&f.minZ()<f.maxZ();}
}
