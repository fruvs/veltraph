package dev.veltraph.client.service.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.core.Module;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/** Private method-entry observation only. Does not authorize a setter or alter public input behavior. */
final class RootNativeInputEpisode {
    private static final ThreadLocal<RootNativeInputEpisode> ACTIVE=new ThreadLocal<>();
    private final RootNativeInputEpisode parent;private final Minecraft client;
    private final Object service;private final McInputService canonicalInput;private final KeyMapping key;private final boolean desired;
    McInputService.NegativeInputObservation negativeObservation;
    boolean negativeEntryCurrent(){return negativeCurrent(canonicalInput);}
    boolean negativeCurrent(McInputService input){return !desired&&!closed&&!revoked&&ACTIVE.get()==this
        &&Thread.currentThread()==thread&&canonicalInput==input&&input!=null&&client.isSameThread();}
    private Module original;private VeltraphBootstrap.RootOriginalNativeEnrollment source;
    private final Thread thread=Thread.currentThread();private boolean revoked,closed;
    private RootNativeInputEpisode(Object service,McInputService canonicalInput,Minecraft client,KeyMapping key,boolean desired){
        this.service=service;this.canonicalInput=canonicalInput;this.client=client;this.key=key;this.desired=desired;parent=ACTIVE.get();

    }
    static RootNativeInputEpisode enter(McInputService service,Minecraft client,KeyMapping key,boolean desired){return enter0(service,service,client,key,desired);}
    static RootNativeInputEpisode enter(McBlockBreakingService service,Minecraft client,KeyMapping key,boolean desired){
        return enter0(service,McInputService.canonicalFarmInput(client),client,key,desired);
    }
    private static RootNativeInputEpisode enter0(Object service,McInputService input,Minecraft client,KeyMapping key,boolean desired){
        RootNativeInputEpisode created=null;
        try{var previous=ACTIVE.get();if(previous!=null)previous.revoked=true;
            created=new RootNativeInputEpisode(service,input,client,key,desired);if(previous!=null)created.revoked=true;
            ACTIVE.set(created);
            created.negativeObservation=McInputService.observeActualNegativeEntry(created);
            created.original=input==null?null:VeltraphBootstrap.originalInputIssuingModule(input);
            created.source=created.original==null?null:RootNativeHistory.enrolledSourceFor(created.original,client);
            return created;
        }catch(RuntimeException|Error unavailable){if(created!=null){created.revoked=true;if(ACTIVE.get()==created)ACTIVE.set(created.parent);}return null;}
    }
    static RootNativeInputEpisode peek(){return ACTIVE.get();}
    boolean matches(Object exactService,McFarmOwnedCleanup.NativeScope scope,McHeldKeys.ScopedHeldReceipt receipt){
        try{return !closed&&!revoked&&ACTIVE.get()==this&&Thread.currentThread()==thread&&client.isSameThread()
            &&exactService==service&&source!=null&&original!=null&&scope.farm==original&&receipt!=null&&receipt.key()==key&&receipt.held()==desired
            &&canonicalInput!=null&&source.originalInputIssuingCurrent(original,canonicalInput)
            &&(service instanceof McInputService?McInputService.canonicalFarmRootCurrent(client)
                :McInputService.canonicalFarmBreakingCurrent((McBlockBreakingService)service,client));}
        catch(RuntimeException|Error unavailable){return false;}
    }
    void close(){closed=true;if(ACTIVE.get()==this){if(parent==null)ACTIVE.remove();else ACTIVE.set(parent);}else revoked=true;}
}
