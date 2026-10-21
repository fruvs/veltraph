package dev.veltraph.client.galatea;

import dev.veltraph.client.core.Module;
import dev.veltraph.client.mixin.FigScaffoldKeyMappingAccessor;
import dev.veltraph.client.mixin.FigOwnedUseKeyMappingRegistryAccessor;
import dev.veltraph.client.service.mc.McOwnedRotation;
import java.util.*;
import java.util.function.*;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** STAGED: one queued native event guarded at actual consumption and synchronous native submission. */
public final class McFigOwnedUseBridge implements AutoCloseable {
    public record Binding(Module owner,long generation,long originalDeadlineMillis,String frozenConfig) {}
    public record Submission(String packetType,long nativeMillis) {}
    public record SendPermit(OwnedNativeUseTicket ticket,String packetType,Minecraft client) {}
    private static McFigOwnedUseBridge installed;
    private final Minecraft client;
    private final Supplier<Binding> binding;
    private final Supplier<?> actualSoleRootInputOwner;
    private final Map<Module,Long> invoked=new IdentityHashMap<>();
    private final List<Submission> submissions=new ArrayList<>();
    private OwnedNativeUseTicket ticket,armed,inUse;
    private boolean normalConsumption;
    private McFigOwnedUseBridge(Minecraft client,Supplier<Binding> binding,Supplier<?> owner){
        this.client=Objects.requireNonNull(client);this.binding=Objects.requireNonNull(binding);
        actualSoleRootInputOwner=Objects.requireNonNull(owner);
    }
    public static McFigOwnedUseBridge install(Minecraft client,Supplier<Binding> binding,Supplier<?> actualSoleRootInputOwner){
        if(installed!=null)throw new IllegalStateException("An owned native-use bridge is already installed");
        // Bootstrap registration may precede the established client thread. No native observation here.
        var bridge=new McFigOwnedUseBridge(client,binding,actualSoleRootInputOwner);installed=bridge;return bridge;
    }
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Owned use callbacks require actual client thread");}
    private int pending(){return ((FigScaffoldKeyMappingAccessor)(Object)client.options.keyUse).veltraph$figScaffoldClickCount();}
    /** Full item/count/components copies, compared with native ItemStack.matches. */
    private static final class Loadout {
        final List<ItemStack> stacks;
        Loadout(Minecraft c){var values=new ArrayList<ItemStack>();for(int i=0;i<9;i++)values.add(c.player.getInventory().getItem(i).copy());
            for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))values.add(c.player.getItemBySlot(slot).copy());stacks=List.copyOf(values);}
        @Override public boolean equals(Object other){if(!(other instanceof Loadout that)||stacks.size()!=that.stacks.size())return false;
            for(int i=0;i<stacks.size();i++)if(!ItemStack.matches(stacks.get(i),that.stacks.get(i)))return false;return true;}
        @Override public int hashCode(){return 0;} // Equality proof only; never a hash-based item identity.
    }
    private OwnedNativeUseTicket.Frame frame(){
        try{thread();var b=binding.get();var p=client.player;
            if(b==null||p==null||client.level==null||client.getConnection()==null)return null;
            boolean healthy=b.owner()!=null&&b.owner().isEnabled()&&p.isAlive()&&!client.isPaused()
                    &&client.gui.screen()==null&&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty()
                    &&client.getCameraEntity()==p&&McOwnedRotation.installed(client);
            return new OwnedNativeUseTicket.Frame(b.owner(),b.generation(),p,client.level,client.getConnection(),
                    p.getInventory().getSelectedSlot(),new Loadout(client),b.frozenConfig(),actualSoleRootInputOwner.get(),
                    McOwnedRotation.authority(client),McOwnedRotation.epoch(client),b.originalDeadlineMillis(),System.currentTimeMillis(),healthy);
        }catch(RuntimeException unavailable){return null;}
    }
    public FigScaffoldStateMachine.Dispatch issueOnce(Module owner,long generation,int slot,long originalDeadlineMillis,
                                                     String frozenConfig,BooleanSupplier sourceThrottle){
        thread();
        if(owner==null||generation<0||generation<=invoked.getOrDefault(owner,-1L))return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        invoked.put(owner,generation); // Attempt identity is consumed even when source inner readiness refuses.
        var current=frame();Binding b;
        try{b=binding.get();}catch(RuntimeException unavailable){return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;}
        if(current==null||b==null||current.owner()!=owner||current.generation()!=generation||current.slot()!=slot
                ||b.originalDeadlineMillis()!=originalDeadlineMillis||!Objects.equals(current.config(),frozenConfig)
                ||!OwnedNativeUseTicket.eligible(current,originalDeadlineMillis))return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        var bound=KeyMappingHelper.getBoundKeyOf(client.options.keyUse);
        try{if(!OwnedNativeUseAdmission.ready(current,originalDeadlineMillis,sourceThrottle,this::pending,()->{
                if(ticket!=null&&ticket.headPending()||inUse!=null||slot<0||slot>8)return false;
                var aliases=FigOwnedUseKeyMappingRegistryAccessor.veltraph$figMappings().get(bound);
                return aliases!=null&&aliases.size()==1&&aliases.getFirst()==client.options.keyUse;
            }))return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
        }catch(RuntimeException unavailable){return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;}
        try{ticket=new OwnedNativeUseTicket(current,originalDeadlineMillis);}catch(IllegalArgumentException invalid){return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;}
        if(!ticket.matches(frame())||pending()!=0){ticket.revoke();return FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;}
        submissions.clear();KeyMapping.click(bound);
        return ticket.queued(0,pending())?FigScaffoldStateMachine.Dispatch.NATIVE_KEY_EVENT_QUEUED:FigScaffoldStateMachine.Dispatch.INVOKED_WITHOUT_QUEUED_EVENT;
    }
    public void revoke(Module owner,long generation){thread();if(ticket!=null&&ticket.owner()==owner&&ticket.generation()==generation)ticket.revoke();}
    public Optional<OwnedNativeUseTicket> ticket(Module owner,long generation){thread();return ticket!=null&&ticket.owner()==owner&&ticket.generation()==generation?Optional.of(ticket):Optional.empty();}
    public List<Submission> submissions(){thread();return List.copyOf(submissions);}
    /** Only vanilla's exact normal key-use loop opens this routing scope. */
    public static boolean consumeNormal(KeyMapping key){var b=installed;
        if(b==null||key!=b.client.options.keyUse)return key.consumeClick();b.thread();
        if(b.normalConsumption)throw new IllegalStateException("Reentrant owned key consumption");
        b.normalConsumption=true;try{return key.consumeClick();}finally{b.normalConsumption=false;}
    }
    /** Explicit inspected drain path; no owned invocation is armed. */
    public static boolean consumeDrain(KeyMapping key){return key.consumeClick();}
    public static boolean nativeConsumed(KeyMapping key,int before,int after,boolean vanillaResult){var b=installed;
        if(b==null||key!=b.client.options.keyUse||b.ticket==null||!b.ticket.headPending())return vanillaResult;
        b.thread();var result=b.ticket.consumed(b.frame(),before,after,vanillaResult,b.normalConsumption);
        if(result==OwnedNativeUseTicket.Consumption.ALLOW_OWNED){b.armed=b.ticket;return true;}
        return result==OwnedNativeUseTicket.Consumption.FOREIGN?vanillaResult:false;
    }
    public static void queueReleased(KeyMapping key){var b=installed;if(b!=null&&key==b.client.options.keyUse&&b.ticket!=null){b.thread();b.ticket.queueReleased();b.armed=null;}}
    /** No armed owned event means later manual/held use remains vanilla. */
    public static boolean beginUse(Minecraft client){var b=installed;if(b==null||b.client!=client||b.armed==null)return true;
        b.thread();var current=b.armed;b.armed=null;if(!current.beginUse(b.frame())){current.endUse();return false;}b.inUse=current;return true;}
    public static void endUse(Minecraft client){var b=installed;if(b!=null&&b.client==client){b.thread();if(b.inUse!=null)b.inUse.endUse();b.inUse=null;}}
    public static void endKeybinds(Minecraft client){var b=installed;if(b!=null&&b.client==client){b.thread();if(b.armed!=null){b.armed.revoke();b.armed=null;}endUse(client);}}
    /** Redirect wraps only the queued startUseItem call. Exceptions cannot leave a packet scope. */
    public static void runQueuedUse(Minecraft client,Runnable vanillaUse){
        if(!beginUse(client))return;
        try{vanillaUse.run();}finally{endUse(client);}
    }
    private static boolean usePacket(Packet<?> packet){return packet instanceof ServerboundUseItemPacket||packet instanceof ServerboundUseItemOnPacket
            ||packet instanceof ServerboundInteractPacket||packet instanceof ServerboundSetCarriedItemPacket;}
    /** null=unrelated vanilla packet, ticket=null=rejected owned submission. */
    public static SendPermit beforeSend(Object listener,Packet<?> packet){var b=installed;
        if(b==null||b.inUse==null||!usePacket(packet))return null;
        // The old captured listener must still be fenced after actual current connection changes.
        if(listener!=b.inUse.connection()&&listener!=b.client.getConnection())return null;
        b.thread();return new SendPermit(b.inUse.beforeOutgoing(b.frame())?b.inUse:null,packet.getClass().getName(),b.client);}
    public static void submitted(SendPermit permit){if(permit==null||permit.ticket()==null)return;
        if(!permit.client().isSameThread())throw new IllegalStateException("Owned submission receipt requires actual client thread");
        // Preserve the exact already-submitted ticket receipt even after a reentrant stop/end.
        permit.ticket().outgoingSubmitted();var b=installed;
        if(b!=null&&b.ticket==permit.ticket()){b.thread();b.submissions.add(new Submission(permit.packetType(),System.currentTimeMillis()));}}
    @Override public void close(){thread();if(installed==this){if(ticket!=null)ticket.revoke();
        // Keep revoked queued-head custody installed until vanilla consumes/releases it. No queue clearing.
        if(ticket!=null&&ticket.headPending())throw new IllegalStateException("Owned queued event must drain or be natively released before uninstall");
        if(inUse!=null)inUse.endUse();installed=null;}}
}
