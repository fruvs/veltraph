package dev.veltraph.client.skills;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Native full-content observations; ordinary pickup/place/shift requests never fabricate outcomes. */
final class AlchemyFlowMenus {
    record Slot(int id,boolean player,int inventorySlot,AlchemyFlowProfile.Item identity,int count,List<String> lore) {}
    record View(Object menu,int id,int revision,String title,AlchemyFlowProfile.Item cursor,int cursorCount,List<Slot> slots,int fuel,int brew) {
        Slot slot(int id){return slots.stream().filter(s->s.id()==id).findFirst().orElse(null);}
        boolean nativeStand(){return menu instanceof BrewingStandMenu;}
    }
    private final Minecraft client;
    private AlchemyFlowProfile profile;
    private dev.veltraph.client.inventory.bottle.BottleFillReceipts.Scope issuedScope;
    private long issuedSequence;
    private int issuedRevision;
    private Set<Integer> affected=Set.of();
    private List<Slot> issuedBefore=List.of();
    private final Map<Integer,ItemStack> protectedInventory=new HashMap<>();
    void resetReceipt(){issuedScope=null;affected=Set.of();issuedBefore=List.of();}
    /** A predicted click is never an acknowledgment. Only post-dispatch vanilla-applied payloads count. */
    boolean awaitingReceipt(View current){
        if(issuedScope==null)return false;
        var scope=dev.veltraph.client.inventory.bottle.BottleFillPacketObserver.scope(client);
        if(current==null||!issuedScope.same(scope))throw new IllegalStateException("Issued Alchemy menu/context changed before native receipt");
        var players=new HashMap<Integer,dev.veltraph.client.inventory.bottle.BottleFillReceipts.Cell>();
        var stand=new HashMap<Integer,dev.veltraph.client.inventory.bottle.BottleFillReceipts.Cell>();
        var observedAffected=new HashSet<>(affected);
        for(var row:current.slots()){var before=issuedBefore.stream().filter(old->old.id()==row.id()).findFirst().orElse(null);if(before==null||before.count()!=row.count()||!same(before.identity(),row.identity()))observedAffected.add(row.id());}
        for(int index:observedAffected){var row=client.player.containerMenu.slots.get(index);var cell=dev.veltraph.client.inventory.bottle.BottleFillPacketObserver.cell(client,row.getItem());
            if(row.container==client.player.getInventory())players.put(row.getContainerSlot(),cell);else stand.put(index,cell);}
        if(!dev.veltraph.client.inventory.bottle.BottleFillPacketObserver.journal().confirms(scope,issuedSequence,players,stand,issuedRevision))return true;
        resetReceipt();return false;
    }
    AlchemyFlowMenus(Minecraft client){this.client=client;}
    void configure(AlchemyFlowProfile profile){
        this.profile=profile;resetReceipt();protectedInventory.clear();
        var declared=new ArrayList<AlchemyFlowProfile.Item>();declared.add(profile.input().item());declared.add(profile.fuel().item());
        for(var stage:profile.steps()){declared.add(stage.reagent().item());declared.add(stage.output());}
        for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(!stack.isEmpty()&&declared.stream().noneMatch(item->same(identity(stack),item)))protectedInventory.put(i,stack.copy());}
    }
    void validateProtected(){for(var entry:protectedInventory.entrySet()){var actual=client.player.getInventory().getItem(entry.getKey());if(actual.getCount()!=entry.getValue().getCount()||!ItemStack.isSameItemSameComponents(actual,entry.getValue()))throw new IllegalStateException("Unrelated Alchemy inventory changed; preserve manual state");}}
    boolean stand(View view){return profile!=null&&view.title().equals(profile.standTitle())&&(profile.layout().mode().equals("native")?view.nativeStand():view.menu() instanceof ChestMenu chest&&chest.getRowCount()==profile.layout().rows());}
    static String clean(String value){return value.replaceAll("§.","").strip();}
    static AlchemyFlowProfile.Item identity(ItemStack item) {
        return new AlchemyFlowProfile.Item(BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.isEmpty()?"Air":clean(item.getHoverName().getString()),item.getComponents().toString(),1);
    }
    static boolean same(AlchemyFlowProfile.Item a,AlchemyFlowProfile.Item b){return a.id().equals(b.id())&&a.name().equals(b.name())&&a.components().equals(b.components());}
    View read(){
        if(!client.isSameThread()) throw new IllegalStateException("Alchemy menu observation requires client thread");
        if(client.player==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)||screen.getMenu()!=client.player.containerMenu)return null;
        var menu=screen.getMenu();var slots=new ArrayList<Slot>();
        for(var slot:menu.slots){var stack=slot.getItem();var lore=stack.get(DataComponents.LORE);
            slots.add(new Slot(slot.index,slot.container==client.player.getInventory(),slot.getContainerSlot(),identity(stack),stack.getCount(),lore==null?List.of():lore.lines().stream().map(c->clean(c.getString())).toList()));}
        String title=clean(screen.getTitle().getString());int fuel=menu instanceof BrewingStandMenu stand?stand.getFuel():-1,brew=menu instanceof BrewingStandMenu stand?stand.getBrewingTicks():-1;
        if(profile!=null&&profile.layout().mode().equals("source_13")&&title.equals(profile.standTitle())&&menu.getStateId()>0){
            var marker=slots.stream().filter(s->s.id()==profile.layout().fuelMarkerSlot()&&!s.player()).findFirst().orElseThrow();var pattern=java.util.regex.Pattern.compile(profile.layout().fuelLore());
            var values=marker.lore().stream().map(pattern::matcher).filter(java.util.regex.Matcher::matches).toList();if(values.size()!=1)throw new IllegalStateException("Ambiguous or missing configured fuel observation");fuel=Integer.parseInt(values.getFirst().group("fuel"));if(fuel<0||fuel>100000)throw new IllegalStateException("Invalid source fuel observation");
            brew=slots.stream().anyMatch(s->!s.player()&&s.identity().id().equals("minecraft:yellow_stained_glass_pane"))?1:0;
        }
        return new View(menu,menu.containerId,menu.getStateId(),title,identity(menu.getCarried()),menu.getCarried().getCount(),List.copyOf(slots),fuel,brew);
    }
    boolean click(View view,int slot,int button,ContainerInput action){
        if(view==null||client.gameMode==null||client.player==null||!client.player.isAlive()||!view.equals(read()))return false;
        var target=view.slot(slot);if(target==null||target.identity().id().equals("minecraft:barrier")||target.identity().id().equals("minecraft:bedrock"))return false;
        if(issuedScope!=null)return false;
        issuedScope=dev.veltraph.client.inventory.bottle.BottleFillPacketObserver.scope(client);
        issuedSequence=dev.veltraph.client.inventory.bottle.BottleFillPacketObserver.journal().sequence();issuedRevision=view.revision();issuedBefore=view.slots();
        client.gameMode.handleContainerInput(view.id(),slot,button,action,client.player);
        var predicted=read();if(predicted==null)throw new IllegalStateException("Alchemy menu changed during dispatch");
        var changed=new HashSet<Integer>();changed.add(slot);
        for(var row:predicted.slots()){var before=view.slot(row.id());if(before==null||before.count()!=row.count()||!same(before.identity(),row.identity()))changed.add(row.id());}
        affected=Set.copyOf(changed);return true;
    }
    boolean mayPlace(View view,int target,int source){
        if(!view.equals(read()))return false;
        var menu=client.player.containerMenu;
        return menu.slots.get(target).mayPlace(menu.slots.get(source).getItem());
    }
    int count(AlchemyFlowProfile.Item item) {
        int result=0;for(int i=0;i<36;i++){var stack=client.player.getInventory().getItem(i);if(stack.isEmpty())continue;var found=identity(stack);
            if(found.id().equals(item.id())&&found.name().equals(item.name())&&!found.components().equals(item.components()))throw new IllegalStateException("Ambiguous resource components: "+item.name());
            if(same(found,item))result=Math.addExact(result,stack.getCount());}return result;
    }
    int free(){int result=0;for(int i=0;i<36;i++)if(client.player.getInventory().getItem(i).isEmpty())result++;return result;}
    boolean emptyCursor(){return client.player!=null&&client.player.containerMenu.getCarried().isEmpty();}
}
