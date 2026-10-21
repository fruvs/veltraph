package dev.veltraph.client.mixin;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import dev.veltraph.client.movement.device.OwnedUseMappedKeyCarrier;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(KeyMapping.class)
public abstract class OwnedDeviceMappedKeyHistoryMixin implements OwnedUseMappedKeyCarrier {
 @Shadow private InputConstants.Key key;
 @Unique private long ownedUse$mappingVersion;@Unique private boolean ownedUse$mappingExhausted;
 @Unique private final Object ownedUse$mappingLock=new Object();@Unique private int ownedUse$mappingPending;
 private static final class MappingSnapshot {
  final Object owner;final InputConstants.Key captured;final long version;
  private MappingSnapshot(Object owner,InputConstants.Key captured,long version){this.owner=owner;this.captured=captured;this.version=version;}
 }
 public Object ownedUseMappingSnapshot(){synchronized(ownedUse$mappingLock){return ownedUse$mappingExhausted||ownedUse$mappingPending!=0||key==null?null:new MappingSnapshot(this,key,ownedUse$mappingVersion);}}
 public boolean ownedUseMappingCurrent(Object exact){synchronized(ownedUse$mappingLock){return exact instanceof MappingSnapshot snapshot
   &&snapshot.owner==this&&!ownedUse$mappingExhausted&&ownedUse$mappingPending==0&&snapshot.captured==key&&snapshot.version==ownedUse$mappingVersion;}}
 @Inject(method="setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",at=@At("HEAD"),require=1)
 private void ownedUse$mappingChanged(InputConstants.Key key,CallbackInfo ci){synchronized(ownedUse$mappingLock){ownedUse$mappingPending++;if(ownedUse$mappingVersion==Long.MAX_VALUE)ownedUse$mappingExhausted=true;else ownedUse$mappingVersion++;}}
 @Inject(method="setKey(Lcom/mojang/blaze3d/platform/InputConstants$Key;)V",at=@At("RETURN"),require=1)
 private void ownedUse$mappingReturned(InputConstants.Key key,CallbackInfo ci){synchronized(ownedUse$mappingLock){if(ownedUse$mappingPending<=0)ownedUse$mappingExhausted=true;else ownedUse$mappingPending--;}}
}
