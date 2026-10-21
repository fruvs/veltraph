package dev.veltraph.client.foraging.scheduling;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.foraging.module.*;
import dev.veltraph.client.foraging.replant.ForagingReplantModule;
import dev.veltraph.client.galatea.GalateaForagingModule;
import dev.veltraph.client.scheduling.ScheduledPausePort;
import dev.veltraph.client.inventory.NativeStackIdentity;
import java.util.*;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/** Explicit four-family scheduler adapter. Root must supply its ticket-bound matched-arrival and
 * saved-return proof; this port additionally verifies fresh native objects and exact saved tool/pose.
 * It never disconnects, joins, moves the player, clears a cursor, or restarts an enabled engine. */
public final class ForagingScheduledPausePort implements ScheduledPausePort {
    private final Minecraft client;
    private final Module module;
    private final ForagingRestOwner family;
    private final BooleanSupplier arrivalProof;
    private record Capture(Object ticket,Object player,Object world,Object connection,UUID id,Object dimension,
                           double x,double y,double z,float yaw,float pitch,Pose posture,int slot,ItemStack tool,NativeStackIdentity.Snapshot identity) {}
    private Capture captured;
    public ForagingScheduledPausePort(Minecraft client,TreeChopModule owner,BooleanSupplier arrivalProof){this(client,owner,owner,arrivalProof);}
    public ForagingScheduledPausePort(Minecraft client,AutoForagingModule owner,BooleanSupplier arrivalProof){this(client,owner,owner,arrivalProof);}
    public ForagingScheduledPausePort(Minecraft client,ForagingReplantModule owner,BooleanSupplier arrivalProof){this(client,owner,owner,arrivalProof);}
    public ForagingScheduledPausePort(Minecraft client,GalateaForagingModule owner,BooleanSupplier arrivalProof){this(client,owner,owner,arrivalProof);}
    private ForagingScheduledPausePort(Minecraft client,Module owner,ForagingRestOwner family,BooleanSupplier arrivalProof){this.client=Objects.requireNonNull(client);this.module=Objects.requireNonNull(owner);this.family=family;this.arrivalProof=Objects.requireNonNull(arrivalProof);}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Foraging scheduler port requires client thread");}
    private boolean healthy(){return client.player!=null&&client.level!=null&&client.getConnection()!=null&&client.player.isAlive()&&client.player.onGround()
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
            &&client.level.getChunkSource().hasChunk(client.player.blockPosition().getX()>>4,client.player.blockPosition().getZ()>>4);}
    public Optional<String> readiness(){thread();if(!healthy())return Optional.of("Foraging rest requires a loaded supported pose and closed native menu");var boundary=family.scheduledRestReadiness();if(boundary.isPresent())return boundary;
        try{NativeStackIdentity.capture(client.player.getMainHandItem(),client.level.registryAccess());return Optional.empty();}
        catch(RuntimeException unsupported){return Optional.of("Foraging rest cannot capture the complete native tool identity");}}
    public boolean atBoundary(){return readiness().isEmpty();}
    @Override public boolean pause(Object ticket){
        thread();if(captured!=null&&!owns(captured.ticket()))captured=null;
        if(ticket==null||captured!=null||readiness().isPresent())return false;var p=client.player;
        var pin=new Capture(ticket,p,client.level,client.getConnection(),p.getUUID(),client.level.dimension(),p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),p.getPose(),p.getInventory().getSelectedSlot(),p.getMainHandItem().copy(),NativeStackIdentity.capture(p.getMainHandItem(),client.level.registryAccess()));
        if(!family.pauseForScheduledRest(ticket))return false;captured=pin;return true;
    }
    @Override public boolean owns(Object ticket){return captured!=null&&captured.ticket()==ticket&&ticket!=null&&module.isEnabled()&&family.scheduledRestOwnedBy(ticket);}
    private static float angle(float a,float b){float d=(a-b)%360;return Math.abs(d>180?d-360:d< -180?d+360:d);}
    private boolean freshSavedProof(){
        if(captured==null||!healthy())return false;var p=client.player;
        return p!=captured.player()&&client.level!=captured.world()&&client.getConnection()!=captured.connection()
                &&p.getUUID().equals(captured.id())&&client.level.dimension().equals(captured.dimension())
                &&Math.abs(p.getY()-captured.y())<.15&&Math.hypot(p.getX()-captured.x(),p.getZ()-captured.z())<.25
                &&angle(p.getYRot(),captured.yaw())<1.5&&Math.abs(p.getXRot()-captured.pitch())<1.5&&p.getPose()==captured.posture()
                &&p.getInventory().getSelectedSlot()==captured.slot()&&captured.identity().matches(p.getMainHandItem(),client.level.registryAccess());
    }
    public String proofDiagnostics(){
        if(captured==null)return "no capture";if(!healthy())return "native pose/menu not healthy";var p=client.player;
        return "fresh="+(p!=captured.player()&&client.level!=captured.world()&&client.getConnection()!=captured.connection())+" uuid="+p.getUUID().equals(captured.id())+" dimension="+client.level.dimension().equals(captured.dimension())
                +" distance="+Math.hypot(p.getX()-captured.x(),p.getZ()-captured.z())+","+Math.abs(p.getY()-captured.y())+" view="+angle(p.getYRot(),captured.yaw())+","+Math.abs(p.getXRot()-captured.pitch())
                +" posture="+(p.getPose()==captured.posture())+" slot="+(p.getInventory().getSelectedSlot()==captured.slot())+" tool="+captured.identity().matches(p.getMainHandItem(),client.level.registryAccess())+" nativeReferenceEquality="+ItemStack.matches(p.getMainHandItem(),captured.tool())
                +" item="+(p.getMainHandItem().getItem()==captured.tool().getItem())+" count="+p.getMainHandItem().getCount()+"/"+captured.tool().getCount()+" damage="+p.getMainHandItem().getDamageValue()+"/"+captured.tool().getDamageValue();
    }
    @Override public boolean rebindAfterOwnedArrival(Object ticket){thread();return owns(ticket)&&arrivalProof.getAsBoolean()&&freshSavedProof()&&family.rebindForScheduledRest(ticket);}
    @Override public boolean resume(Object ticket){thread();if(!owns(ticket)||!arrivalProof.getAsBoolean()||!freshSavedProof()||!family.resumeFromScheduledRest(ticket))return false;captured=null;return true;}
    @Override public void stop(Object ticket){thread();if(owns(ticket))module.setEnabled(false);if(captured!=null&&captured.ticket()==ticket)captured=null;}
}
