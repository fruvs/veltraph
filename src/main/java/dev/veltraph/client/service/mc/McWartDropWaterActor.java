package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.navigation.WaterPublicationActor;
import dev.veltraph.client.navigation.WaterPublicationIdentity;
import dev.veltraph.client.navigation.WaterPublicationInputs;
import dev.veltraph.client.navigation.WaterPublicationRevokedException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;

/** Exact original Root Wart WATER actor. All effects use the canonical native farm services. */
public final class McWartDropWaterActor implements WaterPublicationActor,WaterPublicationInputs {
    private final Minecraft client;
    private final VeltraphBootstrap.RootWartDropPublication source;
    private final McInputService input;
    private final McRotationService rotation;
    private final McFarmOwnedCleanup.NativeScope nativeScope;
    private final long revision;
    private Object plan;
    private WaterPublicationIdentity.Scope expected;
    private McRotationService.SourceAimOutcome outcome;
    private McRotationService.DirectPublicationReceipt publication;
    private long ticket;
    private boolean retired,publishing,cleaning,cleanupAttempted;

    public McWartDropWaterActor(Minecraft client,VeltraphBootstrap.RootWartDropPublication source){
        this.client=java.util.Objects.requireNonNull(client);this.source=java.util.Objects.requireNonNull(source);
        if(!source.current()||!(source.farmServicesIdentity().input() instanceof McInputService actualInput)
                ||!(source.farmServicesIdentity().rotation() instanceof McRotationService actualRotation))throw new WaterPublicationRevokedException();
        input=actualInput;rotation=actualRotation;
        nativeScope=McFarmOwnedCleanup.NativeScope.capture(client,source.farmIdentity());
        revision=source.configIdentity().revision(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER).orElse(-1L);
        if(nativeScope==null||revision<0||!source.current())throw new WaterPublicationRevokedException();
        expected=scope();if(expected==null||!current())throw new WaterPublicationRevokedException();
    }
    boolean beforeMetadataCurrent(){return publishing&&!cleaning&&!retired&&expected!=null&&expected.exact(scope())&&source.sourceCurrent();}
    boolean inputPublicationCurrent(VeltraphBootstrap.RootWartDropPublication exact){return exact==source&&!publishing&&!cleaning&&!retired
        &&plan!=null&&expected!=null&&expected.exact(scope())&&source.sourceCurrent();}
    boolean publishingFor(VeltraphBootstrap.RootWartDropPublication exact){return publishing&&!cleaning&&!retired&&source==exact;}
    void retainSourceAimOutcome(McRotationService exact,McRotationService.SourceAimOutcome actual){
        if(exact==rotation&&publishingFor(source)&&actual!=null&&actual.after().parent()==source.farmIdentity()
                &&actual.after().service()==rotation&&actual.after().configStore()==source.configIdentity())outcome=actual;
    }
    public WaterPublicationActor actor(){return this;}
    /** The generation is the real canonical request generation, updated only by our actual receipt. */
    public WaterPublicationIdentity.Scope scope(){
        if(!client.isSameThread()||nativeScope==null)return null;
        return new WaterPublicationIdentity.Scope(this,source.attemptIdentity(),source.farmIdentity(),nativeScope.player,nativeScope.world,
                nativeScope.connection,source.configIdentity(),revision,source.issueGeneration(),rotation.requestGeneration(),
                McOwnedRotation.epoch(client),McOwnedRotation.publicationSerial(client),ticket);
    }
    public boolean current(){
        return !retired&&source.current()&&nativeScope.activeCurrent()&&expected!=null&&expected.exact(scope())
                &&(publication==null||publication.current())&&source.sourceCurrent();
    }
    public boolean bindPlan(Object value){if(value==null||plan!=null||!current())return false;plan=value;return current();}
    public boolean planCurrent(Object value){return value!=null&&value==plan&&current();}
    public void retirePlan(Object value){if(value!=null&&value==plan){plan=null;retired=true;}}
    private long publish(float yaw,float pitch){
        if(publishing||cleaning||plan==null||!current())return -1;publishing=true;
        try{
            var before=expected;
            // The native service seals its SourceAimOutcome before the final callback, preserving partial cleanup custody.
            var actual=rotation.aimWartDropWithOutcome(source,this,yaw,pitch);if(actual==null)return -1;
            outcome=actual;var receipt=actual.gameplayPublication();
            if(receipt==null||!receipt.current()||!source.current())return -1;
            var prior=receipt.before();var after=receipt.after();
            if(prior.parent()!=source.farmIdentity()||prior.service()!=rotation||prior.configStore()!=source.configIdentity()
                    ||prior.player()!=before.player()||prior.world()!=before.world()||prior.connection()!=before.connection()
                    ||prior.generation()!=before.generation()||prior.epoch()!=before.epoch()||prior.publicationSerial()!=before.serial()
                    ||after.generation()!=before.generation()+1||after.player()!=before.player()||after.world()!=before.world()
                    ||after.connection()!=before.connection()||after.parent()!=source.farmIdentity()||after.service()!=rotation
                    ||after.configStore()!=source.configIdentity()||receipt.actualTicket()<=0||after.publicationSerial()!=receipt.actualTicket())return -1;
            publication=receipt;ticket=receipt.actualTicket();expected=scope();
            return current()?ticket:-1;
        }finally{publishing=false;}
    }
    public long publishYaw(float yaw){return publish(yaw,McOwnedRotation.requestedPitch(client));}
    // Drop-only actor deliberately exposes no retained terminal hold in the farm driver.
    public long publishTrackedPose(float yaw,float pitch){return publish(yaw,pitch);}
    private void key(KeyMapping key,boolean held){
        if(publishing||cleaning||!current()||key==null||!input.publishWartDropMovement(source,this,key,held)||!current())throw new WaterPublicationRevokedException();
    }
    public void setForwardHeld(boolean held){key(client.options.keyUp,held);}
    public void setBackHeld(boolean held){key(client.options.keyDown,held);}
    public void setLeftHeld(boolean held){key(client.options.keyLeft,held);}
    public void setRightHeld(boolean held){key(client.options.keyRight,held);}
    public void setJumpHeld(boolean held){key(client.options.keyJump,held);}
    public void setSneakHeld(boolean held){key(client.options.keyShift,held);}
    public void setSprintHeld(boolean held){key(client.options.keySprint,held);}
    public void releaseAllMovement(){setForwardHeld(false);setBackHeld(false);setLeftHeld(false);setRightHeld(false);setJumpHeld(false);setSneakHeld(false);setSprintHeld(false);}
    /** Original canonical FarmHeld and SourceAimOutcome own cleanup. A stale helper publishes no release or cancellation. */
    public void cleanupOwned(){
        if(cleaning||cleanupAttempted||!source.cleanupCurrent())return;cleaning=true;cleanupAttempted=true;
        try{
            input.releaseAllMovement();
            if(outcome!=null)rotation.cancelSourceAimOwned(outcome,source::cleanupCurrent,!source.farmIdentity().isEnabled(),source::cleanupCurrent);
        }finally{retired=true;cleaning=false;}
    }
}
