package dev.veltraph.client.fishing.trash;

import java.util.*;
import java.util.function.Predicate;

/** Readable automatic cliff scan/rank/sparsify from the original failed-CFR method's actual bytecode. */
public final class FishingTrashCliffSelector {
    public record Pos(int x,int y,int z) {
        public Pos offset(int dx,int dy,int dz){return new Pos(x+dx,y+dy,z+dz);}
        double distance(Pos other){return Math.sqrt((double)(x-other.x)*(x-other.x)+(double)(y-other.y)*(y-other.y)+(double)(z-other.z)*(z-other.z));}
    }
    /** Native Direction.get2DDataValue order. */
    public enum Direction { SOUTH(0,1),WEST(-1,0),NORTH(0,-1),EAST(1,0);
        final int x,z;Direction(int x,int z){this.x=x;this.z=z;}
        Pos step(Pos p){return p.offset(x,0,z);}
    }
    public enum Kind { OTHER,LAVA,SOUL_SAND }
    public interface Facts {
        boolean passable(Pos pos);
        boolean standable(Pos pos);
        Kind kind(Pos pos);
        double yawDifferenceToCenter(Pos pos);
    }
    public record Candidate(Pos position,List<Direction> clearDirections,double score) {
        public Candidate{clearDirections=List.copyOf(clearDirections);}
        /** Source chooses the second cardinal when at least three are clear; diagonal for adjacent pair. */
        public List<Direction> throwDirections() {
            if(clearDirections.size()>=3)return List.of(clearDirections.get(1));
            if(clearDirections.size()==2) {
                var a=clearDirections.get(0);var b=clearDirections.get(1);
                if(a.x+b.x!=0||a.z+b.z!=0)return clearDirections;
            }
            return List.of(clearDirections.getFirst());
        }
    }
    private FishingTrashCliffSelector(){}
    public static final class Scan {
        private final Pos origin;private final Random random;private final List<Candidate> all=new ArrayList<>();
        private int index;
        public Scan(Pos origin,Random random){this.origin=origin;this.random=random;}
        public boolean complete(){return index>=163216;}
        /** Client-thread incremental reads; candidate order and random calls equal the source's scan. */
        public void advance(Facts facts,int budget) {
            if(budget<1)throw new IllegalArgumentException("Positive scan budget required");
            int end=Math.min(163216,index+Math.min(163216,budget));
            while(index<end) {
                int n=index++;int x=n/(16*101)-50,y=(n/101)%16-10,z=n%101-50;
                var p=origin.offset(x,y,z);var candidate=evaluate(facts,origin,p,random);
                if(candidate!=null)all.add(candidate);
            }
        }
        public List<Candidate> ranked() {
            if(!complete())throw new IllegalStateException("Cliff scan is incomplete");
            all.sort(Comparator.comparingDouble(Candidate::score));
            var sparse=new ArrayList<Candidate>();for(int i=0;i<all.size()&&sparse.size()<5;i+=5)sparse.add(all.get(i));
            return List.copyOf(sparse);
        }
    }
    private static Candidate evaluate(Facts facts,Pos origin,Pos p,Random random) {
        if(!facts.passable(p)||!facts.standable(p))return null;
        var directions=new ArrayList<Direction>();
        for(var direction:Direction.values()) {
            var adjacent=direction.step(p);boolean clear=true;
            for(int dy=2;dy>-6;dy--) {
                var a=adjacent.offset(0,dy,0);
                if(!facts.passable(a)||!facts.passable(direction.step(a))){clear=false;break;}
            }
            if(clear)directions.add(direction);
        }
        if(directions.isEmpty())return null;
        double score=origin.distance(p)+(4-directions.size())*10;
        for(var block:List.of(p,p.offset(0,-1,0)))score+=switch(facts.kind(block)){case LAVA->10;case SOUL_SAND->20;default->0;};
        score+=Math.abs(p.y-origin.y)*10+Math.abs(facts.yawDifferenceToCenter(p))/30;
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if((dx!=0||dz!=0)&&!facts.passable(p.offset(dx,0,dz)))score+=10;
        var first=directions.getFirst();for(int depth=0;depth<20;depth++)if(!facts.passable(first.step(p).offset(0,-depth,0)))score+=10;
        score+=random.nextDouble(2);
        return new Candidate(p,directions,score);
    }
    public static List<Candidate> scan(Facts facts,Pos origin,Random random) {
        var scan=new Scan(origin,random);scan.advance(facts,163216);return scan.ranked();
    }
    /** The source path helper tries the retained candidates in this order, with its explicit5000 bound. */
    public static Optional<Candidate> choose(List<Candidate> candidates,Predicate<Candidate> pathAvailable) {
        return candidates.stream().filter(pathAvailable).findFirst();
    }
}
