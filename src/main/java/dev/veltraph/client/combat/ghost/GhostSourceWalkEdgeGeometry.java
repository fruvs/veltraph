package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.ArrayList;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** EDGE ordinary-ground branch and CORNER height-band math; no pose/input mutation or vanilla LOS. */
public final class GhostSourceWalkEdgeGeometry {
    public interface World {boolean current();boolean sourcePassable(Cell cell);boolean standable(Cell cell);boolean fluid(Cell cell);double supportTop(Cell cell);}
    public record Band(double minY,double maxY,double radius) {}
    public record TravelPoint(Point point,List<Band> bands){public TravelPoint {bands=List.copyOf(bands);}}
    public record Edge(Point from,Point to,double geometricLength,List<Point> rawSamples,List<TravelPoint> travelPoints,boolean zeroLength){
        public Edge {rawSamples=List.copyOf(rawSamples);travelPoints=List.copyOf(travelPoints);}
    }
    private GhostSourceWalkEdgeGeometry() {}
    private static Cell floor(Point p){return new Cell((int)Math.floor(p.x()),(int)Math.floor(p.y()),(int)Math.floor(p.z()));}
    private static Point add(Point p,double x,double y,double z){return new Point(p.x()+x,p.y()+y,p.z()+z);}
    private static double distance(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2));}
    public static Edge ground(Cell fromCell,Cell toCell,World world){
        Point from=new Point(fromCell.x()+.5,fromCell.y(),fromCell.z()+.5),to=new Point(toCell.x()+.5,toCell.y(),toCell.z()+.5);
        double length=distance(from,to);int steps=(int)Math.ceil(length);var raw=new ArrayList<Point>();var travel=new ArrayList<TravelPoint>();
        if(steps==0){ // Source's zero*NaN raw adornment is excluded as a modern finite-geometry safeguard; endpoint/control behavior retained.
            Point endpoint=withSupport(to,world);travel.add(profile(endpoint,world));return new Edge(from,to,0,List.of(),travel,true);
        }
        double dx=(to.x()-from.x())/steps,dy=(to.y()-from.y())/steps,dz=(to.z()-from.z())/steps;
        for(int i=0;i<=steps;i++)raw.add(add(from,dx*i,dy*i,dz*i));raw.add(to); // Original duplicate endpoint retained.
        var adjusted=new ArrayList<TravelPoint>();Cell previous=floor(from);boolean supported=true;
        for(int i=0;i<steps;i++){
            Point p=new Point(from.x()+dx*i,previous.y(),from.z()+dz*i);Cell c=floor(p);
            // EDGE bytecode245/248 equality test has no skip body; repeated sampled cells are retained.
            if(!world.standable(c)){Cell below=new Cell(c.x(),c.y()-1,c.z());
                if(world.standable(below))c=below;else {c=new Cell(c.x(),c.y()+1,c.z());if(!world.standable(c)){supported=false;break;}}
            }
            if(!world.fluid(c))p=new Point(p.x(),world.supportTop(new Cell(c.x(),c.y()-1,c.z())),p.z());
            adjusted.add(profile(p,world));previous=c;
        }
        if(supported)travel=adjusted;
        else { // Exact source linear fallback; complete remaining native geometry still validates before control.
            travel.clear();for(int i=0;i<steps;i++)travel.add(profile(add(from,dx*i,dy*i,dz*i),world));
        }
        travel.add(profile(withSupport(to,world),world));
        if(!world.current())throw new IllegalStateException("Actual source edge geometry changed");return new Edge(from,to,length,raw,travel,false);
    }
    private static Point withSupport(Point p,World w){var c=floor(p);return new Point(p.x(),w.supportTop(new Cell(c.x(),c.y()-1,c.z())),p.z());}
    private static TravelPoint profile(Point point,World w){var bands=new ArrayList<Band>();double prior=0,minY=0;
        for(int y=0;y<8;y++){
            var c=floor(point);if(!w.sourcePassable(c))c=new Cell(c.x(),c.y()+1,c.z());var at=new Cell(c.x(),c.y()+y,c.z());
            double radius=clear(w,at,1)?.75:clear(w,at,0)?.25:0;
            if(y==0){prior=radius;minY=point.y();continue;}
            if(radius<prior){if(prior>0)bands.add(new Band(minY,point.y()+y-1,prior));prior=radius;minY=point.y()+y;}
            if(radius<=0)return new TravelPoint(point,bands);
        }
        if(prior>0)bands.add(new Band(minY,point.y()+7,prior));return new TravelPoint(point,bands);
    }
    private static boolean clear(World w,Cell c,int radius){
        for(int x=-radius;x<=radius;x++)for(int z=-radius;z<=radius;z++)if(!w.sourcePassable(new Cell(c.x()+x,c.y(),c.z()+z))||!w.sourcePassable(new Cell(c.x()+x,c.y()+1,c.z()+z)))return false;
        return true;
    }
    public static boolean cornerHit(TravelPoint travel,Point actualOrPredictedPlayer,double actualCommonSupportHeight){
        if(actualCommonSupportHeight!=travel.point.y())return false;
        double horizontal=Math.sqrt(Math.pow(actualOrPredictedPlayer.x()-travel.point.x(),2)+Math.pow(actualOrPredictedPlayer.z()-travel.point.z(),2));
        return travel.bands.stream().anyMatch(b->actualOrPredictedPlayer.y()>=b.minY&&actualOrPredictedPlayer.y()<=b.maxY&&horizontal<=b.radius);
    }
}
