package dev.veltraph.client.combat.ghost;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import dev.veltraph.client.combat.ghost.GhostSourceObservedSearchRanking.*;

/** Actual source-native cohort. Read-only observations/ledger history; no selection/path/control publication. */
public final class McGhostSourceNativeCohortSnapshot {
    public record Body(Creeper entity,McGhostSourceBodyEligibilitySnapshot eligibility,McGhostSourceLookRayIndexSnapshot metric,
                       Candidate candidate,boolean defensiveSafe) {}
    private final Minecraft client;private final GhostGrindModule parent;private final Object owner;
    private final GhostSourceOwnedParentArea.Stamp stamp;private final int tick;private final Vec3 playerPosition;
    private final AABB playerBounds,scan;private final List<Entity> scanned;
    private record PlayerPosition(net.minecraft.world.entity.player.Player player,Vec3 position) {}
    private final List<PlayerPosition> playerPositions;
    private record CreeperBounds(Creeper entity,AABB box) {}
    private final List<CreeperBounds> creeperBounds;
    private final List<McGhostSourceBodyEligibilitySnapshot> allCreeperStates;
    private final McGhostSourceGuardSnapshot guards;private final long cameraEpoch;
    private final GhostSourceDestination destination;private final long ignoreRevision,proximityRevision;
    private final Randomization randomization;private final List<Body> bodies;
    private McGhostSourceNativeCohortSnapshot(Minecraft client,GhostGrindModule parent,Object owner){
        this.client=client;this.parent=parent;this.owner=owner;
        stamp=parent.sourceParentAreaStamp(owner).orElseThrow(()->new IllegalStateException("Actual owned Ghost context required"));
        guards=McGhostSourceGuardSnapshot.observe(client,parent,owner);
        if(guards.beforeSearch()!=GhostSourceParentGuardDispatcher.Result.UNHANDLED
                ||parent.sourceJumpBoostBeforeSelection(owner)!=GhostSourceJumpBoostGate.Result.NOT_REQUIRED)throw new IllegalStateException("Actual source parent guard is not unhandled");
        cameraEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        tick=client.player.tickCount;playerPosition=client.player.position();
        playerBounds=client.player.getBoundingBox();
        if(!GhostSourceCohortGeometry.finitePoint(playerPosition.x,playerPosition.y,playerPosition.z)||!finiteBox(playerBounds))
            throw new IllegalStateException("Finite current native player/cohort bounds required");
        scan=playerBounds.inflate(100,6,100);
        if(!finiteBox(scan))throw new IllegalStateException("Finite inflated native cohort bounds required");
        scanned=List.copyOf(client.level.getEntities(client.player,scan));destination=parent.sourceDestination(owner);
        playerPositions=client.level.players().stream().map(p->new PlayerPosition(p,p.position())).toList();
        var proximity=parent.sourceObservePlayerProximity(owner);proximityRevision=proximity.revision();
        randomization=proximity.randomized()?Randomization.ON:Randomization.OFF;
        var ignores=GhostSourceIdentityIgnore.runtime();ignoreRevision=ignores.revision();
        var found=new ArrayList<Body>();var states=new ArrayList<McGhostSourceBodyEligibilitySnapshot>();var bounds=new ArrayList<CreeperBounds>();
        for(var entity:scanned){
            if(!(entity instanceof Creeper creeper))continue;
            if(!GhostSourceCohortGeometry.finitePoint(creeper.getX(),creeper.getY(),creeper.getZ())||!finiteBox(creeper.getBoundingBox()))
                throw new IllegalStateException("Finite current native Creeper geometry required");
            bounds.add(new CreeperBounds(creeper,creeper.getBoundingBox()));
            // Original nearby helper records grace for every Creeper before powered/Y/destination filters.
            var body=McGhostSourceBodyEligibilitySnapshot.observe(client,creeper,GhostSourceEntityGraceWindow.runtime(),GhostSourcePathFailureSuppression.runtime());
            states.add(body);
            if(!body.sourceEligible()||!GhostSourceCohortGeometry.sourcePositionEligible(destination,creeper.getX(),creeper.getY(),creeper.getZ())||ignores.contains(creeper))continue;
            var metric=McGhostSourceLookRayIndexSnapshot.observe(client,creeper);
            var c=new Candidate(creeper.getUUID(),creeper.getY(),client.player.distanceToSqr(creeper),metric.sampleIndex(),true,Observed.YES,Observed.YES,Observed.NO);
            found.add(new Body(creeper,body,metric,c,body.defensiveBodySafe()));
        }
        bodies=List.copyOf(found);allCreeperStates=List.copyOf(states);creeperBounds=List.copyOf(bounds);
    }
    public static McGhostSourceNativeCohortSnapshot observe(Minecraft client,GhostGrindModule parent,Object owner){
        if(client==null||!client.isSameThread()||parent==null)throw new IllegalStateException("Actual native cohort client-thread context required");
        return new McGhostSourceNativeCohortSnapshot(client,parent,owner);
    }
    public boolean current(){
        if(!client.isSameThread())return false;var actual=parent.sourceParentAreaStamp(owner).orElse(null);
        if(actual==null||!stamp.same(actual)||client.player.tickCount!=tick||!client.player.position().equals(playerPosition)
                ||!client.player.getBoundingBox().equals(playerBounds)||parent.sourceDestination(owner)!=destination||GhostSourceIdentityIgnore.runtime().revision()!=ignoreRevision
                ||parent.sourcePlayerProximityRevision(owner)!=proximityRevision||!guards.current()
                ||guards.beforeSearch()!=GhostSourceParentGuardDispatcher.Result.UNHANDLED
                ||parent.sourceJumpBoostBeforeSelection(owner)!=GhostSourceJumpBoostGate.Result.NOT_REQUIRED
                ||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=parent
                ||dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)!=cameraEpoch)return false;
        var players=client.level.players();
        if(players.size()!=playerPositions.size()||playerPositions.stream().anyMatch(p->players.stream().noneMatch(v->v==p.player())||!p.player().position().equals(p.position())))return false;
        var current=client.level.getEntities(client.player,scan);
        if(current.size()!=scanned.size()||current.stream().anyMatch(e->scanned.stream().noneMatch(v->v==e)))return false;
        return creeperBounds.stream().allMatch(b->b.entity().getBoundingBox().equals(b.box()))&&allCreeperStates.stream().allMatch(McGhostSourceBodyEligibilitySnapshot::current)&&bodies.stream().allMatch(b->b.eligibility().current()&&b.metric().current());
    }
    public List<Body> observedBodies(){requireCurrent();return bodies;}
    public List<Body> ranked(java.util.function.DoubleSupplier sourceComparatorMultiplier){
        requireCurrent();var ranked=GhostSourceObservedSearchRanking.choose(bodies.stream().map(Body::candidate).toList(),randomization,sourceComparatorMultiplier);
        if(!ranked.observed())throw new IllegalStateException("Source comparator observation unavailable");
        var byUuid=new LinkedHashMap<java.util.UUID,Body>();bodies.forEach(b->byUuid.put(b.entity().getUUID(),b));
        var result=ranked.candidates().stream().map(c->byUuid.get(c.id())).toList();requireCurrent();return result;
    }
    public Randomization randomization(){requireCurrent();return randomization;}
    public Object sourceChildIdentity(){requireCurrent();return parent.sourceNativeChildIdentity(owner);}
    private static boolean finiteBox(AABB b){return b!=null&&GhostSourceCohortGeometry.finiteBox(b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);}
    private void requireCurrent(){if(!current())throw new IllegalStateException("Actual source cohort/native inputs changed");}
}
