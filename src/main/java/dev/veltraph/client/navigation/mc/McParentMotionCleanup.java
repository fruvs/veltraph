package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.rotation.RotationReceiptIdentity;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.mc.McRotationService;
import net.minecraft.client.Minecraft;
import java.util.function.Supplier;

/** Cancellation-only custody of a parent's own interaction request. Grants no action permission. */
public final class McParentMotionCleanup {
    private final Minecraft client;private final FarmServices services;private final Supplier<Module> owner;
    private Module captured;private RotationReceiptIdentity.Stamp receipt,before;private McRotationService.CleanupReceipt queuedReceipt;private McRotationService.DirectPublicationReceipt directReceipt;private int publications;private long beforeSerial;
    public McParentMotionCleanup(Minecraft client,FarmServices services,Supplier<Module> owner){this.client=client;this.services=services;this.owner=owner;}
    private RotationReceiptIdentity.Stamp snapshot(Module expected){
        var modules=VeltraphBootstrap.moduleManager();
        if(!client.isSameThread()||modules==null||expected==null||modules.get(expected.id()).orElse(null)!=expected
                ||modules.inputOwner().orElse(null)!=expected||McOwnedRotation.authority(client)!=expected
                ||client.player==null||client.level==null||client.getConnection()==null
                ||!(services.rotation() instanceof McRotationService rotation))return null;
        return new RotationReceiptIdentity.Stamp(rotation,rotation.requestGeneration(),expected,client.player,client.level,client.getConnection(),McOwnedRotation.epoch(client));
    }
    /** Exact expected request increments distinguish this publication from synchronous replacement. */
    public boolean beforePublication(int expectedPublications){
        publications=expectedPublications;before=null;beforeSerial=-1;
        if(expectedPublications<1||expectedPublications>2)return false;
        before=snapshot(owner.get());if(before==null)return false;
        beforeSerial=McOwnedRotation.publicationSerial(client);return true;
    }
    public void remember(){
        var expected=owner.get();var next=snapshot(expected);var prior=before;var serial=beforeSerial;before=null;beforeSerial=-1;
        if(prior==null||next==null||!(services.rotation() instanceof McRotationService rotation))return;
        var published=rotation.observedDirectPublication();
        boolean ownDirect=published!=null&&directMatches(published,prior,next,serial)
                &&(dev.veltraph.client.rotation.ParentMotionPublication.own(prior,next,publications)
                    ||publications==2&&published.sameTickRetained()
                        &&dev.veltraph.client.rotation.ParentMotionPublication.own(prior,next,1));
        if(ownDirect){captured=expected;receipt=next;directReceipt=published;queuedReceipt=null;return;}
        // A queue admission publishes no camera ticket. Only the service's exact queue identity may be captured.
        if(!dev.veltraph.client.rotation.ParentMotionPublication.own(prior,next,publications)
                ||McOwnedRotation.publicationSerial(client)!=serial)return;
        var queued=rotation.cleanupReceipt(expected);
        if(queued==null||!queueMatches(queued,next)||!rotation.currentRequest(queued))return;
        captured=expected;receipt=next;queuedReceipt=queued;directReceipt=null;
    }
    private boolean directMatches(McRotationService.DirectPublicationReceipt direct,RotationReceiptIdentity.Stamp prior,
            RotationReceiptIdentity.Stamp next,long serial){
        var a=direct.before();var b=direct.after();
        return direct.current()&&a!=null&&b!=null&&a.root()==VeltraphBootstrap.moduleManager()
                &&a.parent()==prior.authority()&&b.parent()==next.authority()&&a.service()==prior.service()&&b.service()==next.service()
                &&a.generation()==prior.generation()&&b.generation()==next.generation()
                &&a.player()==prior.player()&&a.world()==prior.world()&&a.connection()==prior.connection()
                &&b.player()==next.player()&&b.world()==next.world()&&b.connection()==next.connection()
                &&a.epoch()==prior.epoch()&&b.epoch()==next.epoch()&&a.publicationSerial()==serial
                &&direct.actualTicket()==McOwnedRotation.publicationSerial(client);
    }
    private static boolean queueMatches(McRotationService.CleanupReceipt queue,RotationReceiptIdentity.Stamp stamp){
        return queue.service()==stamp.service()&&queue.generation()==stamp.generation()&&queue.authority()==stamp.authority()
                &&queue.player()==stamp.player()&&queue.world()==stamp.world()&&queue.connection()==stamp.connection()
                &&queue.epoch()==stamp.epoch();
    }
    /** Exact active parent request freshness, including a proved own queued endpoint publication. */
    public boolean ownsOwnInteraction(){return ownsInteraction(false);}
    private boolean ownsInteraction(boolean allowAbsent){
        var modules=VeltraphBootstrap.moduleManager();
        if(!client.isSameThread()||modules==null||captured==null||owner.get()!=captured||receipt==null
                ||modules.get(captured.id()).orElse(null)!=captured
                ||!(services.rotation() instanceof McRotationService rotation))return false;
        var current=modules.inputOwner().orElse(null);
        boolean live=current==captured&&captured.isEnabled();
        boolean absent=allowAbsent&&current==null&&!captured.isEnabled()
                &&captured.id().equals(dev.veltraph.client.input.ScriptingInputLock.ownerModuleId());
        if(!live&&!absent)return false;
        var actual=new RotationReceiptIdentity.Stamp(rotation,rotation.requestGeneration(),captured,
                client.player,client.level,client.getConnection(),McOwnedRotation.epoch(client));
        // Raw request generation and epoch never prove an owned publication, including same-epoch plans.
        if(directReceipt!=null)return (allowAbsent?directReceipt.cleanupCurrent(absent):directReceipt.current())
                &&RotationReceiptIdentity.current(receipt,actual,client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),absent);
        if(queuedReceipt==null)return false;
        if(rotation.ownsCleanup(queuedReceipt,absent)
                &&RotationReceiptIdentity.current(receipt,actual,client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),absent))return true;
        // A new request/foreign context never reaches the service's narrow own-plan transition proof.
        if(!live||queuedReceipt==null||rotation.requestGeneration()!=receipt.generation()
                ||client.player!=receipt.player()||client.level!=receipt.world()||client.getConnection()!=receipt.connection()
                ||McOwnedRotation.authority(client)!=captured)return false;
        var refreshed=rotation.refreshOwnedReceipt(queuedReceipt);
        if(refreshed==null||refreshed.service()!=receipt.service()||refreshed.generation()!=receipt.generation()
                ||refreshed.authority()!=receipt.authority()||refreshed.player()!=receipt.player()
                ||refreshed.world()!=receipt.world()||refreshed.connection()!=receipt.connection())return false;
        var proven=new RotationReceiptIdentity.Stamp(refreshed.service(),refreshed.generation(),refreshed.authority(),
                refreshed.player(),refreshed.world(),refreshed.connection(),refreshed.epoch());
        if(!RotationReceiptIdentity.current(proven,actual,client.player,client.level,client.getConnection(),McOwnedRotation.authority(client),false))return false;
        if(!rotation.currentRequest(refreshed))return false;
        receipt=proven;queuedReceipt=refreshed;return true;
    }
    /** Cancellation only. Typed absent disabled ownership cannot grant gameplay freshness. */
    public boolean cancelOwnInteraction(){
        if(!ownsInteraction(true))return false;
        var rotation=(McRotationService)services.rotation();
        var modules=VeltraphBootstrap.moduleManager();boolean absent=modules.inputOwner().isEmpty()&&!captured.isEnabled();
        boolean cancelled=directReceipt!=null
                ?rotation.cancelDirectOwned(directReceipt,()->ownsInteraction(true),absent)!=null
                :queuedReceipt!=null&&rotation.cancelOwned(queuedReceipt,absent);
        if(!cancelled)return false;
        receipt=null;queuedReceipt=null;directReceipt=null;before=null;beforeSerial=-1;return true;
    }
    public void release(McSharedMovementNavigator movement){
        // Failed and arrived legs retain their private cleanup identity until stop.
        if(movement.hasLeg()){movement.stop();return;}
        movement.stop();releaseInteraction();
    }
    public void release(McTravelNavigator movement){
        if(movement.hasSharedMovementLeg()||movement.hasOwnedLegacyLeg()||movement.hasAutomaticLineage()){
            movement.stop();return;
        }
        // H's retirement resets only unbound logical state and does not touch native keys/camera/tools.
        movement.retireLogical();releaseInteraction();
    }
    private void releaseInteraction(){
        if(!cancelOwnInteraction())return;
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(client.options!=null)for(var key:java.util.List.of(client.options.keyUp,client.options.keyDown,
                client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,
                client.options.keySprint,client.options.keyAttack,client.options.keyUse))McHeldKeys.setHeld(key,false);
    }
}
