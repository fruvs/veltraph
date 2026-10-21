package dev.veltraph.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ContainerInput;
import java.util.*;

/** Client-thread container reads/clicks, guarded against stale IDs, changed labels and player slots. */
public final class McMenuAccess {
    private final Minecraft client;
    private record Binding(Object player,Object world,Object menu) {}
    private final IdentityHashMap<MenuSnapshot,Binding> bindings=new IdentityHashMap<>();
    private final ArrayDeque<MenuSnapshot> observed=new ArrayDeque<>();
    private Object observedPlayer, observedWorld, observedMenu;
    public McMenuAccess(Minecraft client) { this.client=client; }
    /** Stable exact identity of ALL effective persistent native components; unsupported types refuse. */
    public static String componentIdentity(net.minecraft.world.item.ItemStack item,net.minecraft.core.HolderLookup.Provider registry) {
        Objects.requireNonNull(item);Objects.requireNonNull(registry);
        if(item.isEmpty())return "{}";
        for(var value:item.getComponents())if(value.type().isTransient())throw new IllegalArgumentException("Transient native component has no exact persistent identity");
        var ops=registry.createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE);
        return canonicalComponents(net.minecraft.core.component.DataComponentMap.CODEC.encodeStart(ops,item.getComponents()).getOrThrow());
    }
    static String canonicalComponents(com.google.gson.JsonElement value) {
        return canonicalValue(Objects.requireNonNull(value)).toString();
    }
    private static com.google.gson.JsonElement canonicalValue(com.google.gson.JsonElement value) {
        if(value.isJsonObject()){
            var result=new com.google.gson.JsonObject();
            value.getAsJsonObject().keySet().stream().sorted().forEach(key->result.add(key,canonicalValue(value.getAsJsonObject().get(key))));
            return result;
        }
        if(value.isJsonArray()){var result=new com.google.gson.JsonArray();for(var element:value.getAsJsonArray())result.add(canonicalValue(element));return result;}
        return value.deepCopy();
    }
    public void clear() {
        bindings.clear();observed.clear();
        observedPlayer=null;observedWorld=null;observedMenu=null;
    }
    public MenuSnapshot read() {
        if(!client.isSameThread())throw new IllegalStateException("Menu read requires client thread");
        if(client.player==null||client.level==null||!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) { clear();return null; }
        var menu=screen.getMenu();if(menu!=client.player.containerMenu) { clear();return null; }
        if(observedPlayer!=client.player||observedWorld!=client.level||observedMenu!=menu) {
            clear();observedPlayer=client.player;observedWorld=client.level;observedMenu=menu;
        }
        List<MenuSnapshot.Slot> slots=new ArrayList<>();
        try { for(var slot:menu.slots) {
            var item=slot.getItem();var lore=item.get(DataComponents.LORE);
            slots.add(new MenuSnapshot.Slot(slot.index,item.isEmpty()?"":MenuSnapshot.clean(item.getHoverName().getString()),
                    lore==null?List.of():lore.lines().stream().map(line->MenuSnapshot.clean(line.getString())).toList(),slot.container==client.player.getInventory(),net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),item.getCount(),item.hasFoil(),componentIdentity(item,client.level.registryAccess())));
        }
        } catch(RuntimeException unsupportedIdentity){clear();return null;}
        var snapshot=new MenuSnapshot(menu.containerId,MenuSnapshot.clean(screen.getTitle().getString()),slots,menu.getStateId());
        bindings.put(snapshot,new Binding(client.player,client.level,menu));observed.addLast(snapshot);
        while(observed.size()>64)bindings.remove(observed.removeFirst());
        return snapshot;
    }
    public boolean click(MenuSnapshot expected,int slotId) {
        if(!client.isSameThread())throw new IllegalStateException("Menu click requires client thread");
        var binding=bindings.get(expected);
        if(expected==null||binding==null||client.player==null||!client.player.isAlive()||client.gameMode==null
                ||binding.player()!=client.player||binding.world()!=client.level||binding.menu()!=client.player.containerMenu
                ||!client.player.containerMenu.getCarried().isEmpty())return false;
        var current=read();
        if(current==null||current.id()!=expected.id()||current.revision()!=expected.revision()||!current.title().equals(expected.title()))return false;
        var old=expected.slots().stream().filter(s->s.id()==slotId&&!s.playerInventory()&&!s.locked()&&s.count()>0).findFirst();
        var fresh=current.slots().stream().filter(s->s.id()==slotId).findFirst();
        if(old.isEmpty()||fresh.isEmpty()||!old.get().equals(fresh.get()))return false;
        client.gameMode.handleContainerInput(current.id(),slotId,0,ContainerInput.PICKUP,client.player);return true;
    }
    public boolean click(int menuId,int slotId,String expectedName) {
        MenuSnapshot snapshot=read();
        if(snapshot==null||snapshot.id()!=menuId||client.gameMode==null) return false;
        var slot=snapshot.slots().stream().filter(s->s.id()==slotId&&!s.playerInventory()&&s.name().equals(expectedName)).findFirst();
        if(slot.isEmpty()) return false;
        client.gameMode.handleContainerInput(menuId,slotId,0,ContainerInput.PICKUP,client.player);return true;
    }
    public boolean close(int menuId) {
        var snapshot=read();if(snapshot==null||snapshot.id()!=menuId) return false;
        client.player.closeContainer();return true;
    }
}
