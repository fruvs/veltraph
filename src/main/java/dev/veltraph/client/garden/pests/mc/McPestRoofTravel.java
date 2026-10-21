package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.PestTrackerConfig;
import dev.veltraph.client.garden.pests.tracker.*;
import dev.veltraph.client.garden.pests.GardenPlotBounds;
import dev.veltraph.client.navigation.EtherwarpController;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.LegacyTravelPublicationIdentity;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McWorldService;
import dev.veltraph.client.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Pest-parent travel seam. The shared Etherwarp engine owns native use, slot custody and arrival. */
final class McPestRoofTravel {
    enum State { IDLE, ACTIVE, ARRIVED, UNNEEDED, FAILED }
    private final FarmServices services;private final Minecraft client;private final McEtherwarpNavigator warp;private final McNavigator terrain;private final McWorldService blocks;
    private final LegacyTravelPublicationLease publications;private LegacyTravelPublicationLease.Leg warpLeg;private long warpJourney;
    private LegacyTravelRetirement.RetirementOutcome warpRetirement;private boolean retirementBlocked,handoffRefused;private String retirementDiagnostic="";
    private LegacyTravelPublicationIdentity.State capturedParentContext;
    private java.util.function.Supplier<LegacyTravelPublicationIdentity.State> retiredCleanupProof=()->null;
    private final PestRoofPlanner planner=new PestRoofPlanner();private final BooleanSupplier lease;
    private Object player,world,connection;private int plot;private PestRoofBindings bindings;private PestRoofPlanner.Hop chosen;
    private int abilitySlot=-1;private ItemStack ability=ItemStack.EMPTY;
    private State state=State.IDLE;private String status="Idle";
    McPestRoofTravel(Minecraft client,FarmServices services,ConfigService config,BooleanSupplier lease){this.client=client;this.services=services;this.lease=lease;
        publications=new LegacyTravelPublicationLease(client,services,config,()->config.revision(PestTrackerConfig.PEST_ROOF_BINDINGS).orElse(-1));
        warp=new McEtherwarpNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);blocks=new McWorldService(client);}
    boolean toolReady(){return warp.findTool()>=0;}
    boolean reset(){if(!freshJourneyAllowed()||!cancel())return false;planner.reset();return true;}
    private static net.minecraft.core.BlockPos mc(BlockPos b){return new net.minecraft.core.BlockPos(b.x(),b.y(),b.z());}
    private static Vec3 vec(net.minecraft.world.phys.Vec3 p){return new Vec3(p.x,p.y,p.z);}
    private boolean scope(){return lease.getAsBoolean()&&client.player==player&&client.level==world&&client.getConnection()==connection
        &&client.player!=null&&client.player.isAlive()&&!client.player.isSpectator()&&client.gui.screen()==null
        &&GardenPlotBounds.contains(plot,client.player.getX(),client.player.getZ())
        &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean geometryCurrent(){return scope()&&warpLeg!=null&&warpLeg.current();}
    private boolean validRoof(int plot,BlockPos b){if(!geometryCurrent()||client.level==null||client.player==null||!blocks.isLoaded(b)||bindings.at(plot,b).filter(s->s.registryId().equals(blocks.blockId(b))).isEmpty()
        ||client.level.getBlockEntity(mc(b))!=null)return false;return terrain.standingClear(new Vec3(b.x()+.5,b.y()+1,b.z()+.5))&&geometryCurrent();}
    private Optional<PestRoofPlanner.Hop> ray(Vec3 eye,BlockPos support){
        if(!validRoof(plot,support))return Optional.empty();
        for(var face:Direction.values()) {
            var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(support)).add(face.dx()*.499,face.dy()*.499,face.dz()*.499);
            var start=new net.minecraft.world.phys.Vec3(eye.x(),eye.y(),eye.z());
            // Every touched cell must be loaded; a ray through an absent chunk is not an acquisition witness.
            int samples=(int)Math.ceil(start.distanceTo(point)*4);boolean loaded=true;
            for(int i=0;i<=samples;i++){var p=start.lerp(point,samples==0?0:(double)i/samples);if(!blocks.isLoaded(new Vec3(p.x,p.y,p.z).toBlockPos())){loaded=false;break;}}
            if(!geometryCurrent())return Optional.empty();
            if(!loaded||start.distanceTo(point)>100)continue;
            var hit=client.level.clip(new ClipContext(start,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,client.player));
            if(hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(mc(support))&&hit.getDirection().name().equals(face.name()))
                if(geometryCurrent())return Optional.of(new PestRoofPlanner.Hop(support,face,new Vec3(support.x()+.5,support.y()+1,support.z()+.5)));
        }
        return Optional.empty();
    }
    boolean start(PestRoofBindings bindings,int plot,boolean evacuation){
        if(!freshJourneyAllowed()||!cancel())return false;this.bindings=bindings;this.plot=plot;player=client.player;world=client.level;connection=client.getConnection();
        if(!scope()){fail("Roof travel lost concrete maintenance/native owner");return false;}
        warpLeg=publications.begin(++warpJourney);if(warpLeg==null){fail("Concrete roof publication admission refused");return false;}if(capturedParentContext==null)capturedParentContext=warpLeg.commandContext();
        var plan=planner.plan(new PestRoofPlanner.World(){public boolean loaded(BlockPos b){return geometryCurrent()&&blocks.isLoaded(b)&&geometryCurrent();}public boolean passable(BlockPos b){return geometryCurrent()&&terrain.clear(b)&&geometryCurrent();}
            public boolean validRoof(int p,BlockPos b){return McPestRoofTravel.this.validRoof(p,b);}public Optional<PestRoofPlanner.Hop> ray(Vec3 eye,BlockPos b){return McPestRoofTravel.this.ray(eye,b);}},
            plot,vec(client.player.position()),vec(client.player.getEyePosition()),vec(client.player.getLookAngle()),toolReady(),evacuation,java.util.random.RandomGenerator.getDefault());
        if(!geometryCurrent()){fail("Roof custody changed during geometry planning");return false;}
        status="Roof "+plan.outcome()+" attempt "+plan.attempt()+" candidates "+plan.candidates().size();
        if(plan.outcome()==PestRoofPlanner.Outcome.NO_ROOF){if(!stopWarp())return false;state=State.UNNEEDED;return true;}
        if(plan.outcome()!=PestRoofPlanner.Outcome.HOP){fail(status);return false;}
        chosen=plan.hop();abilitySlot=warp.findTool();if(abilitySlot<0||!geometryCurrent()){fail("Roof native ability disappeared after plan");return false;}ability=client.player.getInventory().getItem(abilitySlot).copy();
        if(ability.getCount()!=1||!warp.start(chosen.support(),chosen.face(),chosen.landing(),warpLeg)){fail(warp.status());return false;}if(!geometryCurrent()){fail("Roof custody changed during native helper admission");return false;}planner.dispatched();state=State.ACTIVE;return true;
    }
    State tick(){if(retirementBlocked||handoffRefused||state!=State.ACTIVE)return state;if(!geometryCurrent()||!validRoof(plot,chosen.support())
        ||!ItemStack.matches(ability,client.player.getInventory().getItem(abilitySlot))){fail("Roof owner, exact ability item, contract, support or body clearance changed");return state;}
        if(!geometryCurrent()){fail("Roof custody changed during native geometry observation");return state;}
        var result=warp.tick();status=warp.status();if(result==EtherwarpController.State.ARRIVED)state=State.ARRIVED;else if(result==EtherwarpController.State.FAILED)fail(status);return state;}
    boolean consumeExpectedTeleport(Vec3 p,long now){return (state==State.ACTIVE||state==State.ARRIVED)&&geometryCurrent()&&warp.consumeExpectedTeleport(p,now);}
    private boolean stopWarp(){return stopWarp(true);}
    private boolean stopWarp(boolean handoff){var old=warpLeg;RuntimeException stopFailure=null;boolean retireFailure=false;
        try{warp.stop();}catch(RuntimeException failed){stopFailure=failed;}
        finally{try{if(old!=null){try{warpRetirement=old.retireOwned();}catch(RuntimeException failed){retireFailure=true;warpRetirement=old.retirementOutcome();
                    if(warpRetirement==null)warpRetirement=LegacyTravelRetirement.notRetired(true,true);retirementBlocked=true;}}}
            finally{if(old!=null)retiredCleanupProof=old::retiredCleanupParentContext;if(warpLeg==old)warpLeg=null;}}
        if(stopFailure!=null||retireFailure||warpRetirement!=null&&!warpRetirement.cleanSuccess()){
            retirementBlocked=true;retirementDiagnostic="MISMATCH: roof Ether retirement "+warpRetirement;state=State.FAILED;status=retirementDiagnostic;
            if(old!=null&&old.postRetirementOwnParentContext()!=null)throw new IllegalStateException(retirementDiagnostic);
        }
        if(!retirementBlocked&&handoff&&old!=null&&old.retirementContinuationContext()==null){
            handoffRefused=true;retirementDiagnostic="REVOKED: CLEAN roof Ether retirement cannot authorize this handoff";state=State.FAILED;status=retirementDiagnostic;
            if(retiredCleanupProof.get()==null){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed roof Ether Parent scope superseded";state=State.FAILED;status=retirementDiagnostic;}
        }
        return !retirementBlocked&&(!handoff||!handoffRefused);}
    private boolean freshJourneyAllowed(){if(retirementBlocked)return false;
        if(!sameCapturedParentCurrent()){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed roof Ether Parent changed before fresh journey";state=State.FAILED;status=retirementDiagnostic;return false;}
        handoffRefused=false;return true;}
    /** Fresh workflow admission keeps the originally registered Parent and scene; a CLEAN receipt grants no new owner. */
    private boolean sameCapturedParentCurrent(){var captured=capturedParentContext;if(captured==null)return true;
        if(!client.isSameThread()||!(captured.parent() instanceof dev.veltraph.client.core.Module parent))return false;
        var root=dev.veltraph.client.VeltraphBootstrap.moduleManager();
        return root==captured.root()&&root.get(parent.id()).orElse(null)==parent&&root.inputOwner().orElse(null)==parent
            &&parent.isEnabled()&&parent.ownsGameplayInput()&&client.player==captured.player()&&client.level==captured.world()
            &&client.getConnection()==captured.connection()&&dev.veltraph.client.VeltraphBootstrap.config()==captured.config()
            &&services.rotation()==captured.service()&&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==parent
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==root&&root.inputOwner().orElse(null)==parent;
    }
    boolean cancelForHandoff(){return cancel(true);}
    boolean retirementMismatch(){return retirementBlocked;}
    boolean cancel(){return cancel(false);}
    private boolean cancel(boolean handoff){boolean clean=false;try{clean=stopWarp(handoff);return clean;}
        finally{if(warpLeg==null){player=world=connection=null;chosen=null;abilitySlot=-1;ability=ItemStack.EMPTY;state=clean?State.IDLE:State.FAILED;}}}
    private void fail(String reason){try{cancel();}finally{status=reason+" | "+retirementDiagnostic;state=State.FAILED;}}
    String status(){return state+": "+status+" | "+retirementDiagnostic;}int attempts(){return planner.attempts();}
}
