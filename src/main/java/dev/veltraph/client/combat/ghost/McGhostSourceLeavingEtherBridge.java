package dev.veltraph.client.combat.ghost;

import net.minecraft.client.Minecraft;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import net.minecraft.world.item.ItemStack;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.navigation.mc.McEtherwarpNavigator;
import dev.veltraph.client.navigation.EtherwarpController;
import dev.veltraph.client.navigation.LegacyTravelRetirement;
import dev.veltraph.client.navigation.LegacyTravelPublicationIdentity;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;

/** Qualified guarded modern Ether Transmission actuator for the privately issued leaving endpoint. */
public final class McGhostSourceLeavingEtherBridge {
    public enum State { NEW, ACTIVE, ARRIVED, UNKNOWN_USE, REVOKED }
    private final Minecraft client;private final McGhostSourceLeavingChild child;private final McEtherwarpNavigator nativeEther;
    private State state=State.NEW;
    private final FarmServices services;
    private final LegacyTravelPublicationLease publications;private LegacyTravelPublicationLease.Leg etherLeg;private long etherJourney;
    private LegacyTravelRetirement.RetirementOutcome etherRetirement;private boolean retirementBlocked,handoffRefused;private String retirementDiagnostic="";
    private LegacyTravelPublicationIdentity.State capturedParentContext;
    private java.util.function.Supplier<LegacyTravelPublicationIdentity.State> retiredCleanupProof=()->null;
    private int admittedSlot=-1;private ItemStack admittedAbility=ItemStack.EMPTY;
    McGhostSourceLeavingEtherBridge(Minecraft client,McGhostSourceLeavingChild child,FarmServices services,ConfigService config){this.client=client;this.child=child;this.services=services;nativeEther=new McEtherwarpNavigator(client,services,config);publications=new LegacyTravelPublicationLease(client,services,config,()->config.revision(GhostGrindConfig.GHOST_SOURCE_MOVEMENT_WORKFLOW).orElse(-1));}
    public State state(){return state;}
    /** Parent invokes only under actual child control; no arbitrary BlockPos/landing can be supplied. */
    public boolean begin(){if(!freshJourneyAllowed()||state!=State.NEW||!child.current()||child.state()!=McGhostSourceLeavingChild.State.ETHERWARP_READY)return false;
        int slot=child.findActualEtherSlot();if(slot<0||services.inventory().selectedSlot()!=slot||nativeEther.findTool()!=slot){state=State.REVOKED;return false;}
        if(!stopEther())return false;etherLeg=publications.begin(++etherJourney);
        if(etherLeg!=null&&capturedParentContext==null)capturedParentContext=etherLeg.commandContext();
        if(etherLeg==null||!child.bindOwnedEtherLeg(etherLeg)){revoke();return false;}
        admittedSlot=slot;admittedAbility=client.player.getInventory().getItem(slot).copy();
        if(!current()){revoke();return false;}
        var endpoint=child.actualLeavingEndpoint();var block=new net.minecraft.core.BlockPos(endpoint.x(),endpoint.y()-1,endpoint.z());
        if(!client.level.isLoaded(block)||client.level.getBlockState(block).getCollisionShape(client.level,block).isEmpty()){revoke();return false;}
        var terrain=McGhostSourceCoordinateTerrainSnapshot.observe(client,this::current,null);double top=terrain.block(new GhostSourceWalkFrontier.Cell(block.getX(),block.getY(),block.getZ())).supportTop();
        if(!current()){revoke();return false;}
        boolean started=nativeEther.start(new BlockPos(block.getX(),block.getY(),block.getZ()),Direction.UP,new Vec3(endpoint.x()+.5,top,endpoint.z()+.5),etherLeg);
        if(!started||!current()){revoke();return false;}state=State.ACTIVE;return true;
    }
    public State tick(){if(state!=State.ACTIVE)return state;if(!current()){revoke();return state;}
        int actualSlot=child.findActualEtherSlot();if(actualSlot!=admittedSlot||services.inventory().selectedSlot()!=actualSlot){revoke();return state;}
        long beforeEpoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        var authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        var observed=nativeEther.tick();
        if(observed==EtherwarpController.State.ARRIVED){
            boolean ownedTerminal=child.contextCurrentExceptCameraEpoch()&&nativeEther.ownedArrivalRelease()
                    .filter(receipt->receipt.current(client,authority,beforeEpoch)).isPresent();
            if(ownedTerminal){state=State.ARRIVED;return state;}revoke();return state;
        }
        if(observed==EtherwarpController.State.FAILED){revoke();return state;}
        if(!child.acceptOwnedEtherPublication(etherLeg)||!current()){revoke();return state;}
        return state;
    }
    /** Only the navigator's actual issued normal-use expectation can acknowledge this exact position once. */
    public boolean consumeExpectedTeleport(Vec3 actualPosition,long now){return nativeEther.useDispatched()
            &&(state==State.ACTIVE&&current()||state==State.ARRIVED&&etherLeg!=null&&etherLeg.current()&&child.contextCurrentExceptCameraEpoch())
            &&nativeEther.consumeExpectedTeleport(actualPosition,now);}
    /** Original source AIM event uses this same concrete helper leg while it is installed. */
    public boolean trackSourceAim(float yaw,float pitch){return state==State.ACTIVE&&current()&&etherLeg.publishTracked(yaw,pitch)>0
            &&child.acceptOwnedEtherPublication(etherLeg)&&current();}
    private boolean current(){return !retirementBlocked&&etherLeg!=null&&etherLeg.current()&&child.current()&&admittedSlot>=0
            &&services.inventory().selectedSlot()==admittedSlot&&ItemStack.matches(admittedAbility,client.player.getInventory().getItem(admittedSlot));}
    private boolean stopEther(){return stopEther(true);}
    private boolean stopEther(boolean handoff){var old=etherLeg;RuntimeException stopFailure=null;boolean retireFailure=false;
        try{nativeEther.stop();}catch(RuntimeException failed){stopFailure=failed;}
        finally{try{if(old!=null){try{etherRetirement=old.retireOwned();}catch(RuntimeException failed){retireFailure=true;etherRetirement=old.retirementOutcome();
                    if(etherRetirement==null)etherRetirement=LegacyTravelRetirement.notRetired(true,true);retirementBlocked=true;}}}
            finally{if(old!=null)retiredCleanupProof=old::retiredCleanupParentContext;if(etherLeg==old)etherLeg=null;}}
        if(stopFailure!=null||retireFailure||etherRetirement!=null&&!etherRetirement.cleanSuccess()){
            retirementBlocked=true;retirementDiagnostic="MISMATCH: ghost Ether retirement "+etherRetirement;if(state!=State.UNKNOWN_USE)state=State.REVOKED;
            if(old!=null&&old.postRetirementOwnParentContext()!=null)throw new IllegalStateException(retirementDiagnostic);
        }
        if(!retirementBlocked&&handoff&&old!=null&&old.retirementContinuationContext()==null){
            handoffRefused=true;retirementDiagnostic="REVOKED: CLEAN ghost Ether retirement cannot authorize this handoff";if(state!=State.UNKNOWN_USE)state=State.REVOKED;
            if(retiredCleanupProof.get()==null){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed ghost Ether Parent scope superseded";if(state!=State.UNKNOWN_USE)state=State.REVOKED;}
        }
        return !retirementBlocked&&(!handoff||!handoffRefused);}
    private boolean freshJourneyAllowed(){if(retirementBlocked)return false;
        if(!sameCapturedParentCurrent()){retirementBlocked=true;retirementDiagnostic="MISMATCH: closed ghost Ether Parent changed before fresh journey";if(state!=State.UNKNOWN_USE)state=State.REVOKED;return false;}
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
    public void revoke(){if(nativeEther.useDispatched())state=State.UNKNOWN_USE;else state=State.REVOKED;
        try{if(!stopEther(false)&&state!=State.UNKNOWN_USE)state=State.REVOKED;}
        finally{if(etherLeg==null){admittedSlot=-1;admittedAbility=ItemStack.EMPTY;}}}
    public String status(){return state+" | "+retirementDiagnostic;}
}
