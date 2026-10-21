package dev.veltraph.client.mixin;
import dev.veltraph.client.rotation.NormalIdleInputVersion;
import dev.veltraph.client.service.mc.*;
import net.minecraft.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyMapping.class)
public abstract class NormalIdleKeyMappingHistoryMixin implements NormalIdleKeyCarrier {
 @Unique private final NormalIdleInputVersion veltraph$normalIdleVersion=new NormalIdleInputVersion();
 public String cachedNormalIdleKeyDiagnostic(){return veltraph$normalIdleVersion.cachedCurrentDiagnostic();}
 public void watchNormalIdleAttemptOrigin(Object exact){if(exact instanceof NormalIdleInputVersion.Snapshot s)veltraph$normalIdleVersion.watchAttemptOrigin(s);}
 public Object normalIdleUnownedSnapshot(){return veltraph$normalIdleVersion.unownedSnapshot();}
 public boolean normalIdleUnownedCurrent(Object exact){return exact instanceof NormalIdleInputVersion.UnownedSnapshot s&&veltraph$normalIdleVersion.unownedCurrent(s);}
 public Object normalIdleKeySnapshot(){return veltraph$normalIdleVersion.snapshot();}
 public boolean normalIdleKeyCurrent(Object exact){return exact instanceof NormalIdleInputVersion.Snapshot s&&veltraph$normalIdleVersion.current(s);}
 public boolean normalIdleUnscopedAdvanced(Object before,Object after){return before instanceof NormalIdleInputVersion.Snapshot a&&after instanceof NormalIdleInputVersion.Snapshot b&&veltraph$normalIdleVersion.unscopedAdvancedOnce(a,b);}
 public boolean normalIdleCanonicalToggleAdvanced(Object before,Object after,McHeldKeys.CanonicalTogglePublication actual){return actual!=null&&actual.matchesNativeHistory((KeyMapping)(Object)this,before,after)
  &&before instanceof NormalIdleInputVersion.Snapshot a&&after instanceof NormalIdleInputVersion.Snapshot b&&veltraph$normalIdleVersion.canonicalUnscopedAdvancedOnce(a,b,actual.nativeEventIdentity());}
 public boolean normalIdleKeyAdvanced(Object before,Object after,java.util.List<?> calls){return before instanceof NormalIdleInputVersion.Snapshot a&&after instanceof NormalIdleInputVersion.Snapshot b&&veltraph$normalIdleVersion.advancedOnly(a,b,calls);}
 public boolean normalIdleKeyInFlightCurrent(Object original,java.util.List<?> returned,Object actualPending,int exactDepth){return original instanceof NormalIdleInputVersion.Snapshot snapshot&&veltraph$normalIdleVersion.inFlightOnly(snapshot,returned,actualPending,exactDepth);}
 public void normalIdleNativeBegin(boolean down,boolean restore){var c=Minecraft.getInstance();var invocation=McHeldKeys.claimNativeInput((KeyMapping)(Object)this,down,restore);boolean nativeThread=c!=null&&c.isSameThread();veltraph$normalIdleVersion.begin(invocation,McHeldKeys.canonicalToggleHistoryEvent((KeyMapping)(Object)this,down,restore),nativeThread);if(nativeThread)veltraph$normalIdleVersion.observeAttemptOrigin(down,restore);}
 public void normalIdleNativeReturned(){var c=Minecraft.getInstance();if(c==null||!c.isSameThread()){veltraph$normalIdleVersion.invalidate();return;}veltraph$normalIdleVersion.end((Object)this instanceof ToggleKeyMapping);}
 @Inject(method="setDown(Z)V",at=@At("HEAD"),require=1)
 private void veltraph$normalIdleSetBegin(boolean down,CallbackInfo ci){McHeldKeys.canonicalToggleBaseBegin((KeyMapping)(Object)this,down);normalIdleNativeBegin(down,false);}
 @Inject(method="setDown(Z)V",at=@At("RETURN"),require=1)
 private void veltraph$normalIdleSetReturned(boolean down,CallbackInfo ci){normalIdleNativeReturned();McHeldKeys.canonicalToggleBaseReturned((KeyMapping)(Object)this,down);}
}
