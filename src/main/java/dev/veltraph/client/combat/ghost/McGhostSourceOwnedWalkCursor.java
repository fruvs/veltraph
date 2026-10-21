package dev.veltraph.client.combat.ghost;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.monster.Creeper;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import dev.veltraph.client.combat.ghost.GhostSourceWalkFrontier.Cell;

/** Actual privately produced WALK cursor. Returns steering/callback intent; native input emission remains the canonical parent. */
public final class McGhostSourceOwnedWalkCursor {
    public enum Outcome { CONTINUE, AT_DESTINATION, RESEARCH, STALE }
    public record Steering(Outcome outcome,Point point,boolean jump,boolean sourceCompleted) {}
    private final GhostSourceWalkJob job;private final McGhostSourceWalkPipeline.ProducedPath path;private final Creeper target;
    private final ArrayList<GhostSourceWalkEdgeGeometry.Edge> remaining;
    private final java.util.IdentityHashMap<GhostSourceWalkEdgeGeometry.Edge,ArrayList<GhostSourceWalkEdgeGeometry.TravelPoint>> points=new java.util.IdentityHashMap<>();
    private McGhostSourceWalkTerrainSnapshot terrain;private boolean terminal;
    private McGhostSourceOwnedWalkCursor(GhostSourceWalkJob job,McGhostSourceWalkPipeline.ProducedPath path){this.job=job;this.path=path;target=path.target();remaining=new ArrayList<>(path.edges());remaining.forEach(e->points.put(e,new ArrayList<>(e.travelPoints())));}
    static McGhostSourceOwnedWalkCursor pending(GhostSourceWalkJob job){var path=job.completedPath();if(path==null||!job.ownsCompletedResult(path.target(),path.path()))throw new IllegalStateException("Actual private terminal WALK required");return new McGhostSourceOwnedWalkCursor(job,path);}
    public Object sourcePathIdentity(){return path;}
    /** Passive immutable current-edge facts only. This neither establishes current ownership nor advances the cursor. */
    public record EdgeEvidence(Object identity,Point from,Point to,boolean sameEndpointY) {}
    public java.util.Optional<EdgeEvidence> cachedCurrentEdgeEvidence(){
        if(remaining.isEmpty())return java.util.Optional.empty();var edge=remaining.getFirst();
        return java.util.Optional.of(new EdgeEvidence(edge,edge.from(),edge.to(),edge.from().y()==edge.to().y()));
    }
    public boolean current(Minecraft client){return client!=null&&client.isSameThread()&&!terminal&&job.remainingGeometryCurrent(client,target,path.path())
        &&job.parent().sourceNativeInstalledWalkCursor()==this&&job.parent().sourceNativeSelectedTarget(job.parent())==target&&job.parent().sourceNativeSearchReceipt(job.parent()).map(r->r.current(job.issuedReceipt().scope().parent(),target)).orElse(false);}
    /** Actual parent invokes only for its installed cursor after atomic commit. No setPos/setRot/packet/key calls. */
    public Steering observe(Minecraft client){
        if(!current(client))return new Steering(Outcome.STALE,null,false,false);
        if(terrain==null)terrain=McGhostSourceWalkTerrainSnapshot.observeCommittedJob(client,job.issuedReceipt(),target);
        else terrain=McGhostSourceWalkTerrainSnapshot.observeContinuation(client,job.issuedReceipt(),target,terrain);
        var p=client.player;var position=new Point(p.getX(),p.getY(),p.getZ());Double commonHeight=commonSupport(position,p.onGround(),p.getBbWidth());
        var observations=new ArrayList<GhostSourceWalkCursor.EdgeObservation>();
        for(int i=0;i<remaining.size();i++){
            var e=remaining.get(i);var dest=e.to();var c=new Cell((int)Math.floor(dest.x()),(int)Math.floor(dest.y()),(int)Math.floor(dest.z()));
            double threshold=path.sourceRadius(e); // Immutable original NONE-mode endpoint predicate, protected by terrain mutation receipt.
            boolean corner=false;if(commonHeight!=null&&i<5&&i+1<remaining.size())corner=points.get(remaining.get(i+1)).stream().anyMatch(t->GhostSourceWalkEdgeGeometry.cornerHit(t,position,commonHeight));
            observations.add(new GhostSourceWalkCursor.EdgeObservation(dest,threshold,corner?GhostSourceWalkCursor.Observed.YES:GhostSourceWalkCursor.Observed.NO));
        }
        var advance=GhostSourceWalkCursor.observeAdvance(observations,position,GhostSourceWalkCursor.Observed.YES); // Actual parent control/ownership was revalidated above.
        for(int i=0;i<advance.pollCount();i++){var edge=remaining.removeFirst();points.remove(edge);}
        if(!current(client))return new Steering(Outcome.STALE,null,false,false);
        if(advance.status()==GhostSourceWalkCursor.Status.STOP_SUCCESS){boolean visible=visibleTarget(client);if(!current(client))return new Steering(Outcome.STALE,null,false,false);terminal=true;return new Steering(visible?Outcome.AT_DESTINATION:Outcome.RESEARCH,null,false,true);}
        if(remaining.isEmpty())return new Steering(Outcome.STALE,null,false,false);
        var edge=remaining.getFirst();var list=points.get(edge);Point steering=edge.to();
        // Source951–988 lagged mutable sample cursor: compare actual horizontal distances, poll first on reverse progress.
        for(int i=0;i<list.size()&&i<list.size()-2;i++){
            var a=list.get(i).point();var b=list.get(i+1).point();if(horizontal(position,b)>horizontal(position,a)){steering=b;break;}list.removeFirst();
        }
        if(!protectedStepClear(client,steering))return new Steering(Outcome.STALE,null,false,false);
        boolean jump=p.onGround()&&steering.y()>position.y()+.35; // Modern actuator jump intent; original key-controller details remain qualified.
        return current(client)?new Steering(Outcome.CONTINUE,steering,jump,false):new Steering(Outcome.STALE,null,false,false);
    }
    private Double commonSupport(Point player,boolean onGround,float observedWidth){if(onGround)return player.y();
        var heights=new ArrayList<Double>();double half=(double)observedWidth/2;
        for(double x:new double[]{-half,half})for(double z:new double[]{-half,half}){
            var origin=new Cell((int)Math.floor(player.x()+x),(int)Math.floor(player.y()),(int)Math.floor(player.z()+z));boolean found=false;
            for(int y=0;y<11;y++){var cell=new Cell(origin.x(),origin.y()-y,origin.z());if(GhostSourceOrdinaryWalkTerrain.sourceFoot(terrain,cell)){heights.add(terrain.block(new Cell(cell.x(),cell.y()-1,cell.z())).supportTop());found=true;break;}}
            if(!found)return null;
        }double h=heights.getFirst();return heights.stream().allMatch(v->v==h)?h:null;
    }
    private boolean visibleTarget(Minecraft client){var eye=client.player.getEyePosition();var targetEye=target.getEyePosition();
        var origin=new Point(eye.x,eye.y,eye.z);var aim=new Point(targetEye.x,targetEye.y,targetEye.z);
        return terrain.visibleRay(origin,aim,client.player.getYRot());
    }
    /** User-requested situational safety: actual other living actors entering the next body sweep force re-observation. */
    private boolean protectedStepClear(Minecraft client,Point steering){var p=client.player;double dx=steering.x()-p.getX(),dz=steering.z()-p.getZ(),length=Math.sqrt(dx*dx+dz*dz);
        if(length<=.001)return true;var box=p.getBoundingBox();var moved=box.move(dx/length*.25,0,dz/length*.25);
        var sweep=new net.minecraft.world.phys.AABB(Math.min(box.minX,moved.minX),Math.min(box.minY,moved.minY),Math.min(box.minZ,moved.minZ),Math.max(box.maxX,moved.maxX),Math.max(box.maxY,moved.maxY),Math.max(box.maxZ,moved.maxZ));
        for(var a:terrain.capturedActors())if(a.living()&&!a.invisible()&&!a.localPlayer()&&!a.selectedTarget()){
            var b=a.box();if(sweep.intersects(new net.minecraft.world.phys.AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ())))return false;
        }return current(client);
    }
    private static double horizontal(Point a,Point b){return Math.sqrt(Math.pow(a.x()-b.x(),2)+Math.pow(a.z()-b.z(),2));}
}
