package dev.veltraph.client.combat.ghost;

import java.util.*;

/** Pure ordered translation of stable PATH_WRAPPER and its seven distinct transforms.
 * Geometry inputs are explicit source observations, never inferred from an ordinary line simplifier.
 * This output is not a native worker/publication receipt; typed edge/corner validation remains required. */
public final class GhostSourcePathSmoothing {
 private GhostSourcePathSmoothing(){}
 public record Cell(int x,int y,int z){public Cell below(){return add(0,-1,0);}public Cell add(int dx,int dy,int dz){return new Cell(x+dx,y+dy,z+dz);}}
 public record Point(double x,double y,double z){
  Point add(Point p){return new Point(x+p.x,y+p.y,z+p.z);}Point scale(double n){return new Point(x*n,y*n,z*n);}
  Point minus(Point p){return new Point(x-p.x,y-p.y,z-p.z);}Cell floor(){return new Cell((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));}
  double distance(Point p){return Math.sqrt(Math.pow(x-p.x,2)+Math.pow(y-p.y,2)+Math.pow(z-p.z,2));}
 }
 public enum Known { YES,NO,UNKNOWN }
 public record Stair(boolean stair,String facing){}
 public record Context(Known smoothingEnabled,boolean firstVolume,boolean secondVolume,Known parentPresent,Known parentBypass,Known parentTailVeto,Integer roughStrength){}
 /** Nullable values mean missing native observations. Implementations must not supply guessed defaults.
  * supportRelative: raw union maxY-cellY, empty shape=0. supportAbsolute: cellY+relative.
  * blocked: source G danger predicate OR actual WebBlock. support/foot/head retain source predicates. */
 public interface World {
  Boolean loaded(Cell p);Boolean passable(Cell p);Boolean blocked(Cell p);
  Double supportRelative(Cell below);Double supportAbsolute(Cell below);
  /** null means missing state; a known non-stair is Stair(false,null). */ Stair stair(Cell below);
  Boolean cocoa(Cell p);Boolean support(Cell p,boolean volume);Boolean foot(Cell p);Boolean headClearOne(Cell p);
 }
 public static final class MissingObservation extends IllegalStateException {public MissingObservation(String field){super("Missing source smoothing observation: "+field);}}
 public record Result(List<Cell> cells,List<String> transforms){public Result{cells=List.copyOf(cells);transforms=List.copyOf(transforms);}}
 private static boolean known(Known k,String field){if(k==null||k==Known.UNKNOWN)throw new MissingObservation(field);return k==Known.YES;}
 private static boolean b(Boolean v,String field){if(v==null)throw new MissingObservation(field);return v;}
 private static double number(Double v,String field){if(v==null||!Double.isFinite(v))throw new MissingObservation(field);return v;}
 public static Result apply(List<Cell> path,Context c,World w){
  Objects.requireNonNull(path);Objects.requireNonNull(c);Objects.requireNonNull(w);if(path.isEmpty())throw new IllegalArgumentException("Source worker must supply a nonempty path");
  if(!known(c.smoothingEnabled,"developer toggle"))return new Result(path,List.of());
  var stages=new ArrayList<String>();List<Cell> p=a(path,w);stages.add("A");p=b(p,w);stages.add("B");boolean volume=c.firstVolume||c.secondVolume;
  if(volume){p=c(p,w);stages.add("C");}
  boolean parent=known(c.parentPresent,"parent presence");if(parent&&known(c.parentBypass,"parent bypass"))return new Result(p,stages);
  p=d(p,w,c.firstVolume);stages.add("D");if(c.secondVolume){p=e(p,w);stages.add("E");}
  if(!volume){p=f(p,w);stages.add("F");if(!parent||!known(c.parentTailVeto,"parent tail veto")){if(c.roughStrength==null)throw new MissingObservation("rough ray strength");p=g(p,w,c.roughStrength);stages.add("G");}}
  return new Result(p,stages);
 }
 /** A: uniform xz delta OR duplicate leg; same y and exactly equal relative below-shape heights. */
 public static List<Cell> a(List<Cell> p,World w){if(p.size()<3)return p;var out=new ArrayList<Cell>();out.add(p.getFirst());for(int i=1;i<p.size()-1;i++){
  Cell before=p.get(i-1),mid=p.get(i),after=p.get(i+1);boolean remove=false;
  if(before.y==mid.y&&mid.y==after.y){double h=number(w.supportRelative(before.below()),"relative support"),hm=number(w.supportRelative(mid.below()),"relative support"),hz=number(w.supportRelative(after.below()),"relative support");if(h==hm&&h==hz){int dx=mid.x-before.x,dz=mid.z-before.z,ex=after.x-mid.x,ez=after.z-mid.z;remove=dx==ex&&dz==ez||dx==0&&dz==0||ex==0&&ez==0;}}
  if(!remove)out.add(mid);
 }out.add(p.getLast());return out;}
 /** B only removes axis-aligned, one-y-per-leg triples with identical stair facings underneath. */
 public static List<Cell> b(List<Cell> p,World w){if(p.size()<3)return p;var out=new ArrayList<Cell>();out.add(p.getFirst());for(int i=1;i<p.size()-1;i++){
  Cell a=p.get(i-1),m=p.get(i),z=p.get(i+1);boolean sameX=a.x==m.x&&m.x==z.x,sameZ=a.z==m.z&&m.z==z.z;boolean remove=false;
  if(sameX!=sameZ&&a.y-m.y==m.y-z.y&&Math.abs(a.y-m.y)==1){Stair fa=w.stair(a.below()),fm=w.stair(m.below()),fz=w.stair(z.below());if(fa==null||fm==null||fz==null)throw new MissingObservation("stair state");if(fa.stair&&fm.stair&&fz.stair){if(fa.facing==null||fm.facing==null||fz.facing==null)throw new MissingObservation("stair facing");remove=fa.facing.equals(fm.facing)&&fm.facing.equals(fz.facing);}}
  if(!remove)out.add(m);
 }out.add(p.getLast());return out;}
 public static List<Cell> c(List<Cell> p,World w){if(p.size()<3)return p;var out=new ArrayList<Cell>();out.add(p.getFirst());for(int i=1;i<p.size()-1;i++){Cell a=p.get(i-1),m=p.get(i),z=p.get(i+1);if(!(a.x==m.x&&m.x==z.x&&a.z==m.z&&m.z==z.z&&b(w.cocoa(m),"cocoa")&&b(w.cocoa(a),"cocoa")&&b(w.cocoa(z),"cocoa")))out.add(m);}out.add(p.getLast());return out;}
 /** D/E retain the source lagged cursor: on failure append input[i], not input[i+1]. */
 public static List<Cell> d(List<Cell> p,World w,boolean volume){Cell anchor=p.getFirst();var out=new ArrayList<Cell>();out.add(anchor);for(int i=0;i<p.size()-1;i++){Cell next=p.get(i+1);if(!((volume||anchor.y==next.y)&&dEdge(anchor,next,w,volume))){anchor=p.get(i);out.add(anchor);}}out.add(p.getLast());return out;}
 public static List<Cell> e(List<Cell> p,World w){var out=new ArrayList<Cell>();out.add(p.getFirst());for(int i=0;i<p.size()-1;i++)if(!eEdge(out.getLast(),p.get(i+1),w))out.add(p.get(i));out.add(p.getLast());return out;}
 private static final List<Point> BASE=List.of(new Point(.5,.5,.5),new Point(-.75,.5,-.75),new Point(.75,.5,-.75),new Point(-.75,.5,.75),new Point(.75,.5,.75),new Point(-.75,1.5,-.75),new Point(.75,1.5,-.75),new Point(-.75,1.5,.75),new Point(.75,1.5,.75));
 private static final List<Point> EXTENDED;
 static {var offsets=new ArrayList<>(BASE);offsets.addAll(List.of(new Point(-.75,-.5,-.75),new Point(.75,-.5,-.75),new Point(-.75,-.5,.75),new Point(.75,-.5,.75),new Point(-.75,2.5,-.75),new Point(.75,2.5,-.75),new Point(-.75,2.5,.75),new Point(.75,2.5,.75)));EXTENDED=List.copyOf(offsets);}
 private static Point origin(Cell p){return new Point(p.x,p.y,p.z);}private static Point center(Cell p){return new Point(p.x+.5,p.y+.5,p.z+.5);}private static Point standing(Cell p){return new Point(p.x+.5,p.y,p.z+.5);}
 public static boolean dEdge(Cell a,Cell z,World w,boolean volume){if(!b(w.loaded(a),"loaded")||!b(w.loaded(z),"loaded"))return false;boolean same=a.y==z.y;for(Point offset:volume&&!same?EXTENDED:BASE)if(!ray(origin(a).add(offset),origin(z).add(offset),offset,w,volume,true))return false;return !volume||same||dLevels(a,z,w);}
 public static boolean eEdge(Cell a,Cell z,World w){boolean same=a.y==z.y;for(Point offset:same?BASE:EXTENDED)if(!ray(origin(a).add(offset),origin(z).add(offset),offset,w,true,false))return false;return same||eLevels(a,z,w);}
 /** Exact integer samples 0..ceil(distance)-1: target endpoint is excluded. */
 private static boolean ray(Point a,Point z,Point offset,World w,boolean volume,boolean hazard){double length=z.distance(a);Point delta=z.minus(a);for(int i=0;i<(int)Math.ceil(length);i++){Cell cell=a.add(delta.scale(i/length)).floor();if(hazard&&b(w.blocked(cell),"blocked"))return false;if(!b(w.loaded(cell),"loaded")||!b(w.passable(cell),"passable"))return false;if(offset.y<=1&&!b(w.support(cell,volume),"source support"))return false;}return true;}
 private static boolean dLevels(Cell a,Cell z,World w){Point from=center(a),to=center(z),delta=to.minus(from);double length=to.distance(from);Point unit=delta.scale(1/length);Set<Integer> seen=new HashSet<>();int last=-1;
  for(int i=0;i<(int)Math.ceil(length);i++){Point p=from.add(delta.scale(i/length));int castY=(int)p.y;if(last==castY)continue;last=castY;Cell cell=p.floor();if(!seen.add(cell.y))continue;if(!b(w.passable(cell),"passable"))return false;double reach=from.distance(origin(cell))+.5,forward=(length-reach)/3+1.5;Point lo=origin(cell).minus(unit.scale(reach)),hi=origin(cell).add(unit.scale(forward));lo=new Point(lo.x+.5,castY+.5,lo.z+.5);hi=new Point(hi.x+.5,castY+.5,hi.z+.5);if(!ray(lo,hi,new Point(0,0,0),w,true,true))return false;}return true;}
 private static boolean eLevels(Cell a,Cell z,World w){Point from=center(a),to=center(z),delta=to.minus(from);Point unit=delta.scale(1/to.distance(from));for(int y=Math.min(a.y,z.y)-1;y<=Math.max(a.y,z.y)+1;y++){Point lo=new Point(from.x-unit.x*2,y+.5,from.z-unit.z*2),hi=new Point(to.x+unit.x*2,y+.5,to.z+unit.z*2);if(!ray(lo,hi,new Point(0,0,0),w,true,false))return false;}return true;}
 public static List<Cell> f(List<Cell> p,World w){var out=new ArrayList<Cell>();Cell anchor=p.getFirst();out.add(anchor);for(int i=0;i<p.size()-1;i++){Cell prev=p.get(i),next=p.get(i+1);double height=number(w.supportAbsolute(next.below()),"absolute support")-number(w.supportAbsolute(prev.below()),"absolute support");if(Math.abs(height)>.5){out.add(prev);out.add(next);anchor=next;continue;}if(!groundRay(standing(anchor),standing(next),w,false)){out.add(prev);anchor=prev;}}out.add(p.getLast());return out;}
 public static List<Cell> g(List<Cell> p,World w,int strength){if(strength<=0)return p;var out=new ArrayList<Cell>();Cell anchor=p.getFirst();out.add(anchor);for(int i=1;i<p.size()-1;i++){Cell prev=p.get(i),next=p.get(i+1);double height=number(w.supportAbsolute(next.below()),"absolute support")-number(w.supportAbsolute(prev.below()),"absolute support");if(height>1){out.add(prev);out.add(next);anchor=next;continue;}if(center(anchor).distance(center(next))>strength||!gEdge(anchor,next,w)){out.add(prev);anchor=prev;}}out.add(p.getLast());return out;}
 public static boolean gEdge(Cell a,Cell z,World w){if(!groundRay(standing(a),standing(z),w,true))return false;for(double x:new double[]{.05,.95})for(double q:new double[]{.05,.95})if(!groundRay(new Point(a.x+x,a.y,a.z+q),new Point(z.x+x,z.y,z.z+q),w,true))return false;return true;}
 /** F/G use repeated double +=.1, a changing support y, and deduplicate only sampled cells.
  * Candidate order is exactly -1,0,+1. A blocked lower candidate rejects the entire ray immediately. */
 private static boolean groundRay(Point from,Point to,World w,boolean rough){double length=to.distance(from),dx=to.x-from.x,dz=to.z-from.z;Cell support=from.floor(),last=null;
  for(double t=0;t<Math.ceil(length);t+=.1){double x=from.x+dx/length*t,z=from.z+dz/length*t;int y=support.y;Cell sample=new Point(x,y,z).floor();if(sample.equals(last))continue;Cell previous=last;last=sample;boolean found=false;
   for(int dy=-1;dy<2;dy++){Cell candidate=new Point(x,y+dy,z).floor();if(b(w.blocked(candidate),"blocked"))return false;double change=number(w.supportAbsolute(candidate.below()),"absolute support")-number(w.supportAbsolute(support.below()),"absolute support");if(!b(w.foot(candidate),"source foot")||!(rough?change<=1:Math.abs(change)<=.5))continue;
    if(rough){Cell other=previous==null?from.floor():previous;if(candidate.y!=other.y){Cell lower=candidate.y>other.y?other:candidate;if(!b(w.passable(lower.add(0,2,0)),"transition overhead"))continue;}}
    found=true;support=candidate;break;
   }
   if(!found)return false;if(!rough&&!b(w.headClearOne(support),"one head clearance"))return false;
  }return true;
 }
}
