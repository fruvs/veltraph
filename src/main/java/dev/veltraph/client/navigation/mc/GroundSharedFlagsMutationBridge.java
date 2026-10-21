package dev.veltraph.client.navigation.mc;
import dev.veltraph.client.mixin.GroundSharedFlagsAccessor;
import dev.veltraph.client.mixin.GroundSharedFlagsItemInvoker;
import dev.veltraph.client.navigation.GroundPhysicsNativeRevisions;
import dev.veltraph.client.navigation.GroundNoGravityMutation;
import dev.veltraph.client.mixin.GroundNoGravityAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
/** Bounded to the actual player's exact flags data/item/accessor. No packet or value grants custody. */
public final class GroundSharedFlagsMutationBridge {
    private GroundSharedFlagsMutationBridge() {}
    public static void noGravityEntityAttempt(Object exactPlayer,boolean attemptedValue){
        var c=Minecraft.getInstance();var p=c==null?null:c.player;
        if(p==null||p.getClass()!=LocalPlayer.class||p!=exactPlayer)return;
        var accessor=GroundNoGravityAccessor.veltraph$nativeNoGravity();var data=p.getEntityData();
        var item=((GroundSharedFlagsItemInvoker)(Object)data).veltraph$nativeSharedItem(accessor);
        GroundNoGravityMutation.record(p,GroundNoGravityMutation.Source.ENTITY_METHOD,data,item,accessor,attemptedValue);
    }
    public static void attemptedData(Object exactData,Object accessor,Object value,int stage){
        var c=Minecraft.getInstance();var p=c==null?null:c.player;
        if(p==null||p.getClass()!=LocalPlayer.class||p.getEntityData()!=exactData)return;
        var noGravity=GroundNoGravityAccessor.veltraph$nativeNoGravity();
        if(accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> attempted&&attempted.id()==noGravity.id()){
            var actualItem=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(noGravity);
            GroundNoGravityMutation.record(p,stage==1?GroundNoGravityMutation.Source.RAW_SET2:
                stage==4?GroundNoGravityMutation.Source.RAW_SET3_FORCED:GroundNoGravityMutation.Source.RAW_SET3,
                exactData,actualItem,accessor,value);return;
        }
        var flags=GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
        // Same-id forged accessors still target the real native flags item, hence must deny.
        if(!(accessor instanceof net.minecraft.network.syncher.EntityDataAccessor<?> actual)||actual.id()!=flags.id())return;
        var item=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(flags);
        if((Object)p instanceof GroundSharedFlagsNativeCarrier carrier)
            carrier.observeSharedFlagsAttempt(exactData,item,accessor,value,stage);
        else GroundPhysicsNativeRevisions.mutation(p);
    }
    public static void attemptedItem(Object exactItem,Object value){
        var c=Minecraft.getInstance();var p=c==null?null:c.player;if(p==null||p.getClass()!=LocalPlayer.class)return;
        var noGravity=GroundNoGravityAccessor.veltraph$nativeNoGravity();
        var noGravityItem=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(noGravity);
        if(exactItem==noGravityItem){GroundNoGravityMutation.record(p,GroundNoGravityMutation.Source.RAW_ITEM,
            p.getEntityData(),noGravityItem,noGravity,value);return;}
        var flags=GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
        var item=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(flags);
        if(item!=exactItem)return;
        if((Object)p instanceof GroundSharedFlagsNativeCarrier carrier)
            carrier.observeSharedFlagsAttempt(p.getEntityData(),item,flags,value,3);
        else GroundPhysicsNativeRevisions.mutation(p);
    }
    public static void packetItemAttempt(Object exactData,Object exactItem,net.minecraft.network.syncher.SynchedEntityData.DataValue<?> attemptedPacket){
        var c=Minecraft.getInstance();var p=c==null?null:c.player;
        if(p==null||p.getClass()!=LocalPlayer.class||p.getEntityData()!=exactData)return;
        var noGravity=GroundNoGravityAccessor.veltraph$nativeNoGravity();
        var noGravityItem=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(noGravity);
        if(exactItem==noGravityItem){GroundNoGravityMutation.record(p,GroundNoGravityMutation.Source.PACKET_ASSIGN,
            exactData,noGravityItem,noGravity,attemptedPacket);return;}
        var flags=GroundSharedFlagsAccessor.veltraph$nativeSharedFlags();
        var item=((GroundSharedFlagsItemInvoker)(Object)p.getEntityData()).veltraph$nativeSharedItem(flags);
        if(item==exactItem)GroundPhysicsNativeRevisions.mutation(p,GroundPhysicsNativeRevisions.Kind.FOREIGN_FLAGS_PACKET,item);
    }
}
