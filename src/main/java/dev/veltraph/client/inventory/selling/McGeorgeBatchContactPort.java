package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.garden.pests.philip.SourceContactCommand;
import dev.veltraph.client.inventory.abiphone.AbiphoneCall;
import dev.veltraph.client.metrics.FinancialActionReceipt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Committed source command ingress and exact single-pet packet adapter under one frozen batch owner. */
public final class McGeorgeBatchContactPort implements GeorgeBatchRuntime.Port {
    private final Map<UUID,GeorgeSaleIdentity> acknowledgedIdentities=new HashMap<>();
    private final Minecraft client;private final SellingMenuAccess access;private final SourceContactCommand contact;private final SourceContactCommand.Settings contactSettings;
    private final String pursePrefix;private final BooleanSupplier authorized;private final Object player,world,connection;private final int selected;
    private final Map<EquipmentSlot,ItemStack> gear=new EnumMap<>(EquipmentSlot.class);private final SellingRuntime.Settings saleSettings;
    private SourceContactCommand.Observation origin;private Object menuOwner;private GeorgeBatchRuntime runtime;
    public McGeorgeBatchContactPort(Minecraft client,SourceContactCommand.Settings contactSettings,SellingRuntime.Settings saleSettings,String pursePrefix,BooleanSupplier authorized){
        this.client=Objects.requireNonNull(client);this.contactSettings=Objects.requireNonNull(contactSettings);this.saleSettings=Objects.requireNonNull(saleSettings);this.pursePrefix=Objects.requireNonNull(pursePrefix);this.authorized=Objects.requireNonNull(authorized);
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||pursePrefix.isBlank()||!contactSettings.title().equals(saleSettings.menuTitle()))throw new IllegalArgumentException("Exact committed George owner/title/purse required");
        player=client.player;world=client.level;connection=client.getConnection();selected=client.player.getInventory().getSelectedSlot();
        for(var slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND))gear.put(slot,client.player.getItemBySlot(slot).copy());
        access=new SellingMenuAccess(client);
        contact=new SourceContactCommand(new SourceContactCommand.Port(){public SourceContactCommand.Observation read(){return contactObservation();}public boolean send(SourceContactCommand.Observation expected,String command){
            if(!valid()||!expected.equals(contactObservation())||!command.equals(contactSettings.command()))return false;client.getConnection().sendCommand(command);return true;}});
    }
    public void bind(GeorgeBatchRuntime runtime){if(this.runtime!=null)throw new IllegalStateException("George port already bound");this.runtime=Objects.requireNonNull(runtime);}
    @Override public boolean owner(){return client.isSameThread()&&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player.isAlive()
            &&client.player.getInventory().getSelectedSlot()==selected&&gear.entrySet().stream().allMatch(e->ItemStack.matches(e.getValue(),client.player.getItemBySlot(e.getKey())));}
    @Override public boolean valid(){return owner()&&authorized.getAsBoolean();}
    @Override public boolean closed(){return owner()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    @Override public SellingObservation read(){return access.read(pursePrefix);}
    private SourceContactCommand.Observation contactObservation(){
        if(!owner())return new SourceContactCommand.Observation(false,null,null,null,null,"",0,List.of(),false,List.of(),-1);
        var o=read();var inventory=new ArrayList<AbiphoneCall.Item>();for(int i=0;i<36;i++){var s=o.inventoryStack(i).orElseThrow();inventory.add(new AbiphoneCall.Item(s.registryId(),s.name(),s.count(),s.components()));}
        var rows=new ArrayList<AbiphoneCall.Row>();for(var s:o.slots().values())rows.add(new AbiphoneCall.Row(s.slot(),s.inventoryIndex()>=0,s.registryId().equals("minecraft:barrier")||s.registryId().equals("minecraft:bedrock"),new AbiphoneCall.Item(s.registryId(),s.name(),s.count(),s.components())));rows.sort(Comparator.comparingInt(AbiphoneCall.Row::slot));
        return new SourceContactCommand.Observation(true,player,world,connection,o.menu()==null?null:client.player.containerMenu,o.menu()==null?"":o.menu().title(),o.menu()==null?0:o.menu().revision(),rows,o.cursorEmpty(),inventory,selected);
    }
    @Override public boolean beginContact(long tick){if(!valid()||!closed())return false;menuOwner=null;origin=contactObservation();return contact.start(contactSettings,tick);}
    @Override public FarmAutoSellRuntime.Progress contact(long tick){contact.tick(tick);if(contact.state()==SourceContactCommand.State.HANDOFF){if(!valid()||!contact.handoffValid())return FarmAutoSellRuntime.Progress.FAILED;menuOwner=client.player.containerMenu;runtime.bindMenu(menuOwner);return FarmAutoSellRuntime.Progress.COMPLETE;}return contact.state()==SourceContactCommand.State.WAIT_MENU?FarmAutoSellRuntime.Progress.ACTIVE:FarmAutoSellRuntime.Progress.FAILED;}
    @Override public boolean releaseContact(){return closed()&&contact.release();}
    @Override public boolean act(SellingRuntime.Action action,SellingRule rule){
        boolean restoring=action.kind()==SellingRuntime.Kind.RETURN_DEPOSIT||action.kind()==SellingRuntime.Kind.RETURN_ORIGIN;
        if(!owner()||!restoring&&!valid())return false;
        boolean confirmation=runtime.saleState()==SellingRuntime.State.READY_CONFIRM&&action.observation().menu()!=null&&action.observation().menu().title().equals(saleSettings.confirmTitle());
        if(client.player.containerMenu!=menuOwner){if(!confirmation)return false;menuOwner=client.player.containerMenu;runtime.bindMenu(menuOwner);}
        return access.act(action,rule,pursePrefix);
    }
    @Override public void closeContact(){
        if(!owner()||origin==null||runtime.cleaning()||runtime.escrowHeld())return;var current=contactObservation();var o=read();
        if(current.menu()!=null&&(current.menu()==contact.ownedMenu()&&current.title().equals(contactSettings.title())||current.menu()==menuOwner&&runtime.saleState()==SellingRuntime.State.CANCELLED&&current.title().equals(saleSettings.confirmTitle()))&&current.cursorEmpty()&&current.inventory().equals(origin.inventory())&&current.selectedSlot()==origin.selectedSlot()&&o.menu()!=null
                )access.closeOwned(o.menu().id(),o.menu().title());
        contact.cancel();
    }
    public void message(String line){if(runtime.state()==GeorgeBatchRuntime.State.CONTACT)contact.message(line);else runtime.message(line);}
    public List<SellingAcknowledgment> acknowledgments(String outcomeId,SellingAcknowledgment.Denomination denomination){
        if(!client.isSameThread())throw new IllegalStateException("George evidence requires client thread");
        return runtime.receipts().stream().map(r->{var p=r.proof();var identity=acknowledgedIdentity(outcomeId,r.petUuid());return new SellingAcknowledgment(identity.outcomeId(),p.channel(),p.itemIdentity(),p.inventoryIndex(),p.quantity(),p.proceeds(),p.purseBefore(),p.purseAfter(),pursePrefix,denomination,player,world,connection,new FinancialActionReceipt.Evidence(r.menu(),p.menuId(),p.requestRevision(),p.receiptRevision(),p.matchedReceiptSerial(),p.channel().name()));}).toList();
    }
    private GeorgeSaleIdentity acknowledgedIdentity(String activation,UUID petUuid){
        var identity=acknowledgedIdentities.computeIfAbsent(petUuid,id->GeorgeSaleIdentity.acknowledged(activation,id));
        if(!identity.activationUuid().toString().equals(activation))throw new IllegalStateException("Acknowledged George activation changed");
        return identity;
    }
    /** Raw source attribution is retained separately from the canonical financial outcome ID. */
    public List<GeorgeSaleIdentity> acknowledgedSourceIdentities(String activation){
        if(!client.isSameThread())throw new IllegalStateException("Native George source attribution requires client thread");
        return runtime.receipts().stream().map(r->acknowledgedIdentity(activation,r.petUuid())).toList();
    }

}
