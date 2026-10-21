package dev.veltraph.client.combat.ghost;

import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.GhostGrindConfig;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.combat.slayer.SlayerPickupSignal;
import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;

/** Read-only actual owned native ItemEntity coordinate producer and exact delivery session; no navigator/path/control emitter. */
public final class McGhostLootNativeProvider {
    /** Existing modern Ghost drop radius, explicitly distinct from generic-melee source route/kill-point radii. */
    public static final double MODERN_DROP_RADIUS=24;
    private McGhostLootNativeProvider() {}
    public static final class Observation {
        private final Minecraft client;private final GhostGrindModule parent;
        private final GhostSourceOwnedParentArea.Stamp scope;private final int tick;private final GhostSourceJobCameraCustody camera;
        private final Point playerPosition;private final List<GhostLootCandidatePlanner.Candidate> candidates;
        private Observation(Minecraft client,GhostGrindModule parent) {
            this.client=client;this.parent=parent;scope=requireScope(client,parent);tick=client.player.tickCount;playerPosition=point(client.player);
            camera=new GhostSourceJobCameraCustody(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));
            var items=new ArrayList<GhostLootCandidatePlanner.Candidate>();
            for(var entity:client.level.entitiesForRendering())if(entity instanceof ItemEntity item&&item.isAlive()&&!item.getItem().isEmpty()) {
                String id=serverId(item.getItem());if(!GhostLootCandidatePlanner.SOURCE_REGISTERED_DROP_IDS.contains(id)||item.distanceToSqr(client.player)>=MODERN_DROP_RADIUS*MODERN_DROP_RADIUS)continue;
                var key=NativeStackIdentity.capture(item.getItem().copyWithCount(1),client.level.registryAccess()).persistentCanonicalEncoding();
                items.add(new GhostLootCandidatePlanner.Candidate(item,item.getId(),item.getUUID(),id,key,item.getItem().getCount(),point(item),true));
            }
            candidates=List.copyOf(items);if(!current())throw new IllegalStateException("Native loot observation changed during capture");
        }
        public List<GhostLootCandidatePlanner.Candidate> candidates(){return candidates;}
        public GhostLootCandidatePlanner.Candidate nearest(){
            if(!current())throw new IllegalStateException("Current actual loot frame required");
            return GhostLootCandidatePlanner.nearest(GhostLootCandidatePlanner.Availability.OBSERVED,true,playerPosition,candidates,MODERN_DROP_RADIUS);
        }
        public boolean current(){
            if(!scopeCurrent(client,parent,scope)||!camera.current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client))||client.player.tickCount!=tick||!playerPosition.equals(point(client.player)))return false;
            for(var candidate:candidates) {
                if(!(candidate.nativeEntity() instanceof ItemEntity actual)||client.level.getEntity(candidate.entityId())!=actual
                        ||!actual.isAlive()||!candidate.uuid().equals(actual.getUUID())||!candidate.position().equals(point(actual))
                        ||actual.getItem().getCount()!=candidate.count()||!candidate.serverId().equals(serverId(actual.getItem()))
                        ||!candidate.unitIdentity().equals(unitIdentity(actual.getItem(),client)))return false;
            }
            return scopeCurrent(client,parent,scope);
        }
        /** A supplied candidate must be the exact private observation's chosen native record, in its current frame. */
        public Session begin(GhostLootCandidatePlanner.Candidate actualSelected,long nowMs,long explicitlyConfiguredTimeoutMs) {
            if(!current()||actualSelected==null||nearest()!=actualSelected)throw new IllegalStateException("Actual privately selected loot required");
            return new Session(this,actualSelected,nowMs,explicitlyConfiguredTimeoutMs);
        }
    }
    /** Opaque fresh coordinate intent. A coordinate is not a source graph path or permission to move. */
    public static final class NativeCoordinateGoal {
        private final Session producer;private final Point point,playerPosition;private final int tick;private final float yaw,pitch;private final net.minecraft.world.phys.Vec3 velocity;
        private NativeCoordinateGoal(Session producer,Point point){this.producer=producer;this.point=point;tick=producer.client.player.tickCount;
            playerPosition=point(producer.client.player);yaw=producer.client.player.getYRot();pitch=producer.client.player.getXRot();velocity=producer.client.player.getDeltaMovement();}
        public Point worldPosition(){return point;}
        public boolean current(){return producer.navigationPending()&&producer.scopeCurrent()&&producer.client.player.tickCount==tick
                &&producer.actualItemCurrent()&&point.equals(point(producer.item))&&playerPosition.equals(point(producer.client.player))
                &&Float.floatToIntBits(yaw)==Float.floatToIntBits(producer.client.player.getYRot())&&Float.floatToIntBits(pitch)==Float.floatToIntBits(producer.client.player.getXRot())
                &&velocity.equals(producer.client.player.getDeltaMovement());}
    }
    public static final class Session {
        private final Minecraft client;private final GhostGrindModule parent;
        private final GhostSourceOwnedParentArea.Stamp scope;private final GhostLootCandidatePlanner.Candidate selected;
        private final ItemEntity item;private final ItemStack unit;private final GhostLootPickupTransaction transaction;private final GhostSourceJobCameraCustody camera;
        private Session(Observation actual,GhostLootCandidatePlanner.Candidate selected,long nowMs,long timeoutMs){
            client=actual.client;parent=actual.parent;scope=actual.scope;camera=actual.camera;this.selected=selected;item=(ItemEntity)selected.nativeEntity();unit=item.getItem().copyWithCount(1);
            transaction=new GhostLootPickupTransaction(scope,selected,inventoryCount(unit,client),SlayerPickupSignal.serial(client.level,client.getConnection()),nowMs,timeoutMs);
            if(!actual.current()){transaction.revoke();throw new IllegalStateException("Loot frame changed before native inventory baseline");}
        }
        private boolean scopeCurrent(){return McGhostLootNativeProvider.scopeCurrent(client,parent,scope)
                &&camera.current(dev.veltraph.client.service.mc.McOwnedRotation.authority(client),dev.veltraph.client.service.mc.McOwnedRotation.epoch(client));}
        private boolean navigationPending(){return transaction.state()==GhostLootPickupTransaction.State.WAITING||transaction.state()==GhostLootPickupTransaction.State.PARTIAL;}
        private boolean actualItemCurrent(){return scopeCurrent()&&item.isAlive()&&client.level.getEntity(selected.entityId())==item
                &&selected.uuid().equals(item.getUUID())&&!item.getItem().isEmpty()&&ItemStack.isSameItemSameComponents(unit,item.getItem());}
        /** Persistent movement scope: issued tick/player pose may advance; actual item and original owner may not be rebound. */
        public boolean movementCurrent(){return navigationPending()&&actualItemCurrent();}
        /** Private native goal custody plus unchanged item coordinates; original frame freshness is separately checked at issuance. */
        public boolean ownsNativeGoal(NativeCoordinateGoal actualGoal){return actualGoal!=null&&actualGoal.producer==this&&movementCurrent()&&actualGoal.point.equals(point(item));}
        public int chosenId(){return selected.entityId();}
        public java.util.UUID chosenUuid(){return selected.uuid();}
        public String serverId(){return selected.serverId();}
        public int originalUnits(){return selected.count();}
        public GhostLootPickupTransaction.State state(){return transaction.state();}
        public NativeCoordinateGoal observeCoordinateGoal(){
            if(!actualItemCurrent()||transaction.state()==GhostLootPickupTransaction.State.COMPLETE
                    ||transaction.state()==GhostLootPickupTransaction.State.REVOKED||transaction.state()==GhostLootPickupTransaction.State.TIMED_OUT)return null;
            return new NativeCoordinateGoal(this,point(item));
        }
        /** Existing native packet hook supplies local-player receipts; full inventory delta is independently captured here. */
        public GhostLootPickupTransaction.State poll(long nowMs){
            if(!scopeCurrent()){transaction.revoke();return transaction.state();}
            for(var receipt:SlayerPickupSignal.read(client.level,client.getConnection(),client.player.getUUID(),transaction.afterSerial())) {
                transaction.observeActualLocalPickup(scope,new GhostLootPickupTransaction.Pickup(receipt.serial(),receipt.entityId(),receipt.item(),receipt.content().persistentCanonicalEncoding(),receipt.amount()));
            }
            int count=inventoryCount(unit,client);
            if(!scopeCurrent()){transaction.revoke();return transaction.state();}
            return transaction.observeInventory(scope,count,nowMs);
        }
        public int creditedUnits(){return transaction.creditedUnits();}
        public void revoke(){transaction.revoke();}
    }
    public static Observation observe(Minecraft actualClient,GhostGrindModule actualParent){return new Observation(actualClient,actualParent);}
    private static GhostSourceOwnedParentArea.Stamp requireScope(Minecraft client,GhostGrindModule parent){
        if(client==null||parent==null||!client.isSameThread())throw new IllegalStateException("Actual client-thread Ghost owner required");
        var stamp=parent.sourceParentAreaStamp(parent).orElseThrow(()->new IllegalStateException("Actual canonical Ghost authority required"));
        if(!scopeCurrent(client,parent,stamp))throw new IllegalStateException("Actual closed inventory/menu and drop config required");return stamp;
    }
    private static boolean scopeCurrent(Minecraft client,GhostGrindModule parent,GhostSourceOwnedParentArea.Stamp scope){
        if(client==null||parent==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null)return false;
        var actual=parent.sourceParentAreaStamp(parent).orElse(null);
        return scope.player()==client.player&&scope.world()==client.level&&scope.connection()==client.getConnection()&&scope.same(actual)&&VeltraphBootstrap.config().get(GhostGrindConfig.GHOST_GRIND_COLLECT_DROPS)
                &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();
    }
    private static String serverId(ItemStack stack){var data=stack.get(DataComponents.CUSTOM_DATA);if(data==null)return "";
        var tag=data.copyTag();return tag.getString("id").orElseGet(()->tag.getCompoundOrEmpty("ExtraAttributes").getStringOr("id",""));}
    private static String unitIdentity(ItemStack stack,Minecraft client){return NativeStackIdentity.capture(stack.copyWithCount(1),client.level.registryAccess()).persistentCanonicalEncoding();}
    private static int inventoryCount(ItemStack unit,Minecraft client){int result=0;for(int slot=0;slot<36;slot++){var stack=client.player.getInventory().getItem(slot);if(ItemStack.isSameItemSameComponents(stack,unit))result=Math.addExact(result,stack.getCount());}return result;}
    private static Point point(net.minecraft.world.entity.Entity entity){return new Point(entity.getX(),entity.getY(),entity.getZ());}
}
