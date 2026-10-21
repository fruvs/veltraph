package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.util.Mth;
import dev.veltraph.client.combat.ghost.GhostSourcePathResultScoring.*;

/** Actual native M2 result metrics/context, with source HashMap encounter order and fresh per-entry RNG. */
public final class McGhostSourcePathResultSelection {
    private record RemotePoint(RemotePlayer player,net.minecraft.world.phys.Vec3 position) {}
    private McGhostSourcePathResultSelection() {}
    private record RayCell(GhostSourceSampledRay.Cell cell,net.minecraft.world.level.block.state.BlockState state,GhostSourceSampledRay.Block value,McGhostSourceRaySnapshot ray) {}
    public static final class Observation {
        private final Minecraft client;private final GhostSourceWalkJob job;private final Object player,world,connection;private final int tick;
        private final net.minecraft.world.phys.Vec3 position,eye;private final float yaw;
        private final GhostSourceCrowdPressureCounter.Observation pressure;private final List<RemotePoint> remotes;
        private final List<McGhostSourceLookRayIndexSnapshot> looks;private final List<McGhostSourceRaySnapshot> rays;
        private final List<Entity> actors;private final List<RayCell> blocks;private final Selection selection;
        private Observation(Minecraft client,GhostSourceWalkJob job,Selection selection,Object player,Object world,Object connection,int tick,
                net.minecraft.world.phys.Vec3 position,net.minecraft.world.phys.Vec3 eye,float yaw,GhostSourceCrowdPressureCounter.Observation pressure,
                List<RemotePoint> remotes,List<McGhostSourceLookRayIndexSnapshot> looks,List<McGhostSourceRaySnapshot> rays,List<Entity> actors,List<RayCell> blocks){
            this.client=client;this.job=job;this.selection=selection;this.player=player;this.world=world;this.connection=connection;this.tick=tick;
            this.position=position;this.eye=eye;this.yaw=yaw;this.pressure=pressure;this.remotes=List.copyOf(remotes);this.looks=List.copyOf(looks);
            this.rays=List.copyOf(rays);this.actors=List.copyOf(actors);this.blocks=List.copyOf(blocks);
        }
        public Selection selection(){if(!current())throw new IllegalStateException("Native source selection observation stale");return selection;}
        public boolean current(){
            if(!McGhostSourcePathResultSelection.current(client,job,player,world,connection,tick,position,eye,yaw,pressure,remotes,looks,rays))return false;
            var rendered=new ArrayList<Entity>();client.level.entitiesForRendering().forEach(rendered::add);
            if(rendered.size()!=actors.size()||rendered.stream().anyMatch(e->actors.stream().noneMatch(a->a==e)))return false;
            for(var b:blocks){var pos=new net.minecraft.core.BlockPos(b.cell().x(),b.cell().y(),b.cell().z());
                if(!client.level.isLoaded(pos)||client.level.getBlockState(pos)!=b.state()||!b.ray().block(b.cell()).equals(b.value()))return false;}
            return true;
        }
    }
    public static Observation observe(Minecraft client,GhostSourceWalkJob job){
        if(client==null||!client.isSameThread()||job==null||job.state()!=GhostSourceWalkJob.State.RESULTS_READY
                ||!job.issuedReceipt().scopeCurrent())throw new IllegalStateException("Actual current READY source results required");
        var generated=job.generatedPaths();
        // Empty-map source branch consumes no score RNG. We still retain an actual callback frame below.
        var parent=job.parent();var pressure=parent.sourcePressureObservation();
        if(!generated.isEmpty()&&!pressure.observed())throw new IllegalStateException("Actual source pressure observation is uncertain");
        var player=client.player;var world=client.level;var connection=client.getConnection();int tick=player.tickCount;
        var position=player.position();var eye=player.getEyePosition();float yaw=player.getYRot();
        var remotes=world.players().stream().filter(p->p instanceof RemotePlayer).map(p->new RemotePoint((RemotePlayer)p,p.position())).toList();
        boolean nearby=remotes.stream().anyMatch(p->p.position().distanceTo(position)<1.5);
        var actors=new ArrayList<Entity>();world.entitiesForRendering().forEach(actors::add);
        // Original coroutine builds an ordinary HashMap, not a list preserving candidate rank order.
        var map=new HashMap<Creeper,McGhostSourceWalkPipeline.ProducedPath>();for(var path:generated)map.put(path.target(),path);
        var candidates=new ArrayList<Candidate>();var looks=new ArrayList<McGhostSourceLookRayIndexSnapshot>();
        var rays=new ArrayList<McGhostSourceRaySnapshot>();var blocks=new ArrayList<RayCell>();McGhostSourceRaySnapshot prior=null;
        for(var entry:map.entrySet()){
            var target=entry.getKey();var path=entry.getValue();
            if(!job.issuedReceipt().contains(target)||target.level()!=world||world.getEntity(target.getId())!=target)
                throw new IllegalStateException("Actual produced target identity changed");
            var look=McGhostSourceLookRayIndexSnapshot.observe(client,target);looks.add(look);
            var ray=prior==null?McGhostSourceRaySnapshot.observe(client,target,actors):McGhostSourceRaySnapshot.reobserveRegistry(client,target,actors,prior);
            rays.add(ray);prior=ray;var targetEye=target.getEyePosition();
            var from=new GhostSourceSampledRay.Point(eye.x,eye.y,eye.z);var to=new GhostSourceSampledRay.Point(targetEye.x,targetEye.y,targetEye.z);
            boolean preferred=GhostSourceSampledRay.hits(from,to,ray.targetBox(),GhostSourceFloatDirection.between(from,to,yaw),
                    new GhostSourceSampledRay.Mode(.2,true,false,true),new GhostSourceSampledRay.Snapshot(){
                        public boolean passableRegistryObserved(){return ray.passableRegistryObserved();}
                        public List<GhostSourceSampledRay.Actor> actors(){return ray.actors();}
                        public GhostSourceSampledRay.Block block(GhostSourceSampledRay.Cell cell){
                            var value=ray.block(cell);if(value.available())blocks.add(new RayCell(cell,world.getBlockState(new net.minecraft.core.BlockPos(cell.x(),cell.y(),cell.z())),value,ray));return value;}
                    });
            var box5=target.getBoundingBox().inflate(5,5,5);var box10=target.getBoundingBox().inflate(10,10,10);
            long five=remotes.stream().filter(p->box5.contains(p.position())).count();long ten=remotes.stream().filter(p->box10.contains(p.position())).count();
            double relative=Mth.wrapDegrees(Math.atan2(target.getZ()-position.z,target.getX()-position.x)*180.0/Math.PI-90.0-yaw);
            candidates.add(new Candidate(target,target.getUUID(),path,true,path.path().edges(),target.position().distanceTo(position),
                    player.distanceTo(target),relative,five,ten,look.sampleIndex(),preferred?Observed.YES:Observed.NO));
        }
        if(!current(client,job,player,world,connection,tick,position,eye,yaw,pressure,remotes,looks,rays))
            throw new IllegalStateException("Native source result frame changed before scoring");
        var selection=generated.isEmpty()?new Selection(true,List.of(),null):GhostSourcePathResultScoring.choose(candidates,new Context(nearby?Observed.YES:Observed.NO,pressure,true),
                upper->java.util.concurrent.ThreadLocalRandom.current().nextDouble(upper));
        if(!selection.observed()||!current(client,job,player,world,connection,tick,position,eye,yaw,pressure,remotes,looks,rays))
            throw new IllegalStateException("Native source result frame changed while scoring");
        var observation=new Observation(client,job,selection,player,world,connection,tick,position,eye,yaw,pressure,remotes,looks,rays,actors,blocks);
        if(!observation.current())throw new IllegalStateException("Native source ray/context changed after scoring");return observation;
    }
    private static boolean current(Minecraft client,GhostSourceWalkJob job,Object player,Object world,Object connection,int tick,
            net.minecraft.world.phys.Vec3 position,net.minecraft.world.phys.Vec3 eye,float yaw,
            GhostSourceCrowdPressureCounter.Observation pressure,List<RemotePoint> remotes,List<McGhostSourceLookRayIndexSnapshot> looks,List<McGhostSourceRaySnapshot> rays){
        return job.issuedReceipt().scopeCurrent()&&client.player==player&&client.level==world&&client.getConnection()==connection
                &&client.player.tickCount==tick&&client.player.position().equals(position)&&client.player.getEyePosition().equals(eye)
                &&Float.floatToIntBits(client.player.getYRot())==Float.floatToIntBits(yaw)&&(looks.isEmpty()||pressure.observed())
                &&remotes.stream().allMatch(p->p.player().level()==client.level&&client.level.getEntity(p.player().getId())==p.player()&&p.player().position().equals(p.position()))
                &&client.level.players().stream().filter(p->p instanceof RemotePlayer).count()==remotes.size()
                &&looks.stream().allMatch(McGhostSourceLookRayIndexSnapshot::current)&&rays.stream().allMatch(McGhostSourceRaySnapshot::passableRegistryObserved);
    }
}
