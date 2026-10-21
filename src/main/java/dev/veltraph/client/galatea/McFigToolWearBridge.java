package dev.veltraph.client.galatea;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.service.BlockBreakingService;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.BlockPos;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.*;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import dev.veltraph.client.galatea.FigHarvestPickupLedger.Scope;
import static dev.veltraph.client.galatea.FigToolWearLedger.*;

/** Correlated native mining cost, not proof of the remote server's exact mineBlock callback. */
public final class McFigToolWearBridge implements AutoCloseable {
    public record Binding(Scope scope,FigToolWearLedger ledger,long originalDeadlineMillis,BlockBreakingService breaking,
            IntSupplier axeSlot,Supplier<String> currentSettings,LongSupplier attemptGeneration,BooleanSupplier allOtherGuards) {}
    public record Event(long serial,String kind,long millis,String detail) {}
    private record Allowed(Binding binding,long generation,long epoch,String settings,int slot,Cell before,
            Object guard,BlockPos target,BlockState original,long at) {}
    private static final class Operation {Action action;Ack ack;Operation(Action action){this.action=action;}}
    private record Before(Binding binding,List<Cell> cells,long at) {}
    private static final class Applied {final long serial,at;final Before before;final List<Cell> after;boolean staged;
        Applied(long serial,long at,Before before,List<Cell> after){this.serial=serial;this.at=at;this.before=before;this.after=after;}}
    private static McFigToolWearBridge installed;
    private final Minecraft client;private final Supplier<Binding> binding;private final Supplier<?> rootOwner;
    private Binding active;private long activeGeneration=-1,serial,lastNow;
    private final Map<BlockPos,Allowed> allowed=new HashMap<>();private final List<Operation> operations=new ArrayList<>();
    private final Map<Object,Before> before=new IdentityHashMap<>();private final ArrayDeque<Event> events=new ArrayDeque<>();
    private final List<Applied> applied=new ArrayList<>();
    private final ArrayDeque<Receipt> completed=new ArrayDeque<>();
    private McFigToolWearBridge(Minecraft c,Supplier<Binding> b,Supplier<?> owner){client=Objects.requireNonNull(c);binding=Objects.requireNonNull(b);rootOwner=Objects.requireNonNull(owner);}
    public static McFigToolWearBridge install(Minecraft c,Supplier<Binding> b,Supplier<?> actualSoleRootInputOwner){
        if(installed!=null)throw new IllegalStateException("Wear bridge already installed");return installed=new McFigToolWearBridge(c,b,actualSoleRootInputOwner);}
    public static void observeSafely(Runnable observation){try{observation.run();}catch(RuntimeException|LinkageError unsupported){var b=installed;if(b!=null&&b.client.isSameThread())b.clear();}}
    private Binding current(){if(!client.isSameThread())return null;
        try{var b=binding.get();long now=System.currentTimeMillis();
            if(b==null||b.scope()==null||b.ledger()==null||b.breaking()==null||b.attemptGeneration().getAsLong()<0||!b.allOtherGuards().getAsBoolean()
                    ||client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.player.level()!=client.level
                    ||b.scope().player()!=client.player||b.scope().world()!=client.level||b.scope().connection()!=client.getConnection()
                    ||b.scope().owner()!=rootOwner.get()||!McOwnedRotation.installed(client)||McOwnedRotation.authority(client)!=b.scope().owner()
                    ||now<lastNow||now>b.originalDeadlineMillis()||b.originalDeadlineMillis()==Long.MAX_VALUE){clear();return null;}
            long generation=b.attemptGeneration().getAsLong();
            if(active==null||!active.scope().same(b.scope())||generation!=activeGeneration){clear();active=b;activeGeneration=generation;}
            if(active.originalDeadlineMillis()!=b.originalDeadlineMillis()||active.ledger()!=b.ledger()){clear();return null;}
            lastNow=now;return active;
        }catch(RuntimeException unsupported){clear();return null;}}
    private void clear(){if(active!=null){event("WEAR_SCOPE_RETIRED","");active.ledger().clear();}active=null;activeGeneration=-1;allowed.clear();operations.clear();before.clear();applied.clear();}
    private long event(String kind,String detail){long n=++serial;events.addLast(new Event(n,kind,System.currentTimeMillis(),detail));while(events.size()>512)events.removeFirst();return n;}
    public List<Event> events(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");return List.copyOf(events);}
    public List<Receipt> receipts(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");return List.copyOf(completed);}
    private Cell cell(int index){var stack=client.player.getInventory().getItem(index);var normalized=stack.isEmpty()?ItemStack.EMPTY:stack.copy();if(!normalized.isEmpty())normalized.remove(DataComponents.DAMAGE);
        var tool=stack.get(DataComponents.TOOL);return new Cell(index,stack.getCount(),stack.getDamageValue(),NativeStackIdentity.capture(stack,client.level.registryAccess()),
                NativeStackIdentity.capture(normalized,client.level.registryAccess()),tool,tool==null?0:tool.damagePerBlock());}
    private List<Cell> cells(){var result=new ArrayList<Cell>();for(int i=0;i<36;i++)result.add(cell(i));return List.copyOf(result);}
    private static BlockPos pos(net.minecraft.core.BlockPos p){return new BlockPos(p.getX(),p.getY(),p.getZ());}
    /** Actual existing target predicate result; never evaluates it again. */
    public static void guardReturned(MultiPlayerGameMode gameMode,net.minecraft.core.BlockPos target,boolean permitted){var self=installed;if(self==null||!permitted)return;var b=self.current();if(b==null)return;
        int slot=b.axeSlot().getAsInt();var guard=b.breaking().targetGuardBinding(b.scope().owner()).orElse(null);
        if(gameMode!=self.client.gameMode||slot<0||slot>8||self.client.player.getInventory().getSelectedSlot()!=slot||!b.breaking().isBreaking()
                ||!self.client.options.keyAttack.isDown()||guard==null||!b.breaking().targetGuardBoundToCurrentContext(b.scope().owner(),guard)||!self.client.level.hasChunkAt(target))return;
        var state=self.client.level.getBlockState(target);if(state.isAir()||!state.is(BlockTags.LOGS))return;
        var snapshot=self.cell(slot);if(snapshot.nativeTool()==null||snapshot.damagePerBlock()<=0||snapshot.count()<=0)return;
        self.allowed.put(pos(target),new Allowed(b,b.attemptGeneration().getAsLong(),McOwnedRotation.epoch(self.client),b.currentSettings().get(),slot,snapshot,guard,pos(target),state,System.currentTimeMillis()));
    }
    public static void submitted(Connection transport,Packet<?> packet){var self=installed;if(self==null||!(packet instanceof ServerboundPlayerActionPacket p))return;var b=self.current();
        if(b==null||transport!=self.client.getConnection().getConnection())return;var target=pos(p.getPos());
        if(p.getAction()==ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK){self.operations.removeIf(o->o.action.target().equals(target));self.allowed.remove(target);return;}
        if(p.getAction()!=ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK&&p.getAction()!=ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK)return;
        var arm=self.allowed.get(target);if(arm==null||!b.scope().same(arm.binding().scope())||arm.generation()!=b.attemptGeneration().getAsLong()
                ||arm.epoch()!=McOwnedRotation.epoch(self.client)||!Objects.equals(arm.settings(),b.currentSettings().get())
                ||!b.breaking().targetGuardBoundToCurrentContext(b.scope().owner(),(BlockBreakingService.TargetGuardBinding)arm.guard()))return;
        long n=self.event("WEAR_"+p.getAction().name(),target+" seq="+p.getSequence()+" slot="+arm.slot()+" damage="+arm.before().damage());long now=System.currentTimeMillis();
        if(p.getAction()==ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK){self.operations.add(new Operation(new Action(b.scope(),arm.generation(),n,target,arm.original(),arm.guard(),arm.slot(),arm.before(),
                arm.epoch(),arm.settings(),b.originalDeadlineMillis(),now,p.getSequence(),0,-1)));}
        else{var matches=self.operations.stream().filter(o->o.action.target().equals(target)&&o.action.stopSerial()==0).toList();if(matches.size()!=1)return;
            var old=matches.getFirst().action;matches.getFirst().action=new Action(old.scope(),old.attemptGeneration(),old.issueSerial(),old.target(),old.originalLogState(),old.targetGuard(),old.slot(),old.before(),old.epoch(),old.settings(),old.originalDeadline(),old.issued(),old.startSequence(),n,p.getSequence());}
    }
    public static void airApplied(Object listener,net.minecraft.core.BlockPos target,BlockState state,int sequence){var self=installed;if(self==null||!state.isAir())return;var b=self.current();
        if(b==null||listener!=self.client.getConnection())return;long n=self.event("WEAR_AUTHORITATIVE_AIR",target+" upTo="+sequence);long now=System.currentTimeMillis();
        for(var op:self.operations)if(op.action.target().equals(pos(target))&&op.ack==null){int required=op.action.stopSerial()>0?op.action.stopSequence():op.action.startSequence();
            if(sequence==-1||sequence>=required)op.ack=new Ack(n,now,sequence,true);}
        self.join();
    }
    /** Called by the existing scoped authoritative prediction observer; no duplicate sync redirect. */
    public static void verifiedApplied(Object world,net.minecraft.core.BlockPos target,BlockState state,Object listener,int sequence){var self=installed;
        if(self!=null&&world==self.client.level)airApplied(listener,target,state,sequence);}
    public static void inventoryStarted(Object listener,Object packet){var self=installed;if(self==null)return;var b=self.current();
        if(b!=null&&listener==self.client.getConnection())self.before.put(packet,new Before(b,self.cells(),System.currentTimeMillis()));}
    public static void inventoryApplied(Object listener,Object packet){var self=installed;if(self==null)return;var b=self.current();var start=self.before.remove(packet);
        if(b==null||start==null||listener!=self.client.getConnection()||!start.binding().scope().same(b.scope()))return;
        var after=self.cells();long now=System.currentTimeMillis();long n=self.event("WEAR_INVENTORY_APPLIED",packet.getClass().getSimpleName());
        self.applied.add(new Applied(n,now,start,after));self.join();
    }
    private void join(){var b=active;if(b==null)return;for(var delivery:applied){if(delivery.staged)continue;
        int slot=b.axeSlot().getAsInt();if(slot<0||slot>=36)continue;var before=delivery.before.cells().get(slot);var changed=delivery.after.get(slot);
        if(Objects.equals(before.full(),changed.full()))continue;
        var matches=operations.stream().filter(o->o.ack!=null&&o.action.slot()==changed.index()&&o.action.attemptGeneration()==b.attemptGeneration().getAsLong()
                &&o.action.epoch()==McOwnedRotation.epoch(client)&&Objects.equals(o.action.settings(),b.currentSettings().get())&&o.action.before().equals(before)).toList();
        if(matches.size()!=1){event("WEAR_UNCORRELATED_DELTA","slot="+slot+" operationMatches="+matches.size());continue;}
        var op=matches.getFirst();var receipt=new Receipt(Evidence.CORRELATED_NATIVE_ACTION,op.action,op.ack,delivery.serial,delivery.at,before,changed);
        boolean accepted=b.ledger().stage(receipt);if(accepted){delivery.staged=true;completed.addLast(receipt);while(completed.size()>64)completed.removeFirst();}
        event(accepted?"WEAR_CORRELATED_NATIVE_ACTION":"WEAR_DELTA_REJECT","slot="+slot+" damage="+before.damage()+"->"+changed.damage()+" requestedCost="+before.damagePerBlock());
    }}
    @Override public void close(){if(!client.isSameThread())throw new IllegalStateException("Client thread required");clear();if(installed==this)installed=null;}
}
