package dev.veltraph.client.garden.visitor.history;

import java.util.*;

/** Recovered co-positioned STAL armor-stand marker. Equal densest groups are deliberately ambiguous. */
public final class VisitorLogbookMarker {
    private VisitorLogbookMarker() {}
    public record Stand(UUID id,boolean actualArmorStand,boolean loaded,boolean mainHandStal,double x,double y,double z) {}
    public record Anchor(int blockX,int blockY,int blockZ,int count) {
        public double x(){return blockX+.5;}public double y(){return blockY+1.1;}public double z(){return blockZ+.5;}
        public boolean withinThreeOfEye(double px,double py,double pz){return Double.isFinite(px)&&Double.isFinite(py)&&Double.isFinite(pz)
                && Math.pow(px-x(),2)+Math.pow(py-y(),2)+Math.pow(pz-z(),2)<=9;}
    }
    private record Cell(int x,int y,int z) {}
    public static Optional<Anchor> find(List<Stand> loadedEntities){
        if(loadedEntities==null||loadedEntities.size()>4096)return Optional.empty();
        var counts=new HashMap<Cell,Integer>();var ids=new HashSet<UUID>();
        for(var s:loadedEntities){
            if(s==null||s.id()==null||!ids.add(s.id()))return Optional.empty();
            if(!s.actualArmorStand()||!s.loaded()||!s.mainHandStal())continue;
            if(!Double.isFinite(s.x())||!Double.isFinite(s.y())||!Double.isFinite(s.z())
                    ||Math.abs(s.x())>30_000_000||Math.abs(s.z())>30_000_000||Math.abs(s.y())>4096)return Optional.empty();
            counts.merge(new Cell((int)Math.floor(s.x()),(int)Math.floor(s.y()),(int)Math.floor(s.z())),1,Integer::sum);
        }
        int max=0;Cell winner=null;boolean tie=false;
        for(var row:counts.entrySet()){
            if(row.getValue()>max){max=row.getValue();winner=row.getKey();tie=false;}
            else if(row.getValue()==max)tie=true;
        }
        if(max<3||tie||winner==null)return Optional.empty();
        return Optional.of(new Anchor(winner.x,winner.y,winner.z,max));
    }
}
