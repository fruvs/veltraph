package dev.veltraph.client.chocolate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import dev.veltraph.client.mining.vein.VeinWatchNativeFields;
import dev.veltraph.client.mining.vein.VeinWatchGraphFields;
import dev.veltraph.client.rewarp.mc.IssuedReturnNativeFields;

/** Negative original precondition readback using installed raw fields, never a native formatter/codec/provider. */
final class ChocolateNativeClickPrerequisites {
    private final Minecraft client;private final LocalPlayer player;private final AbstractContainerMenu menu;private final Screen screen;
    private final int id,revision,slotCount;private final Object slots,nativeGui;private final ChocolateClickPrerequisiteRules.Title title;
    private ChocolateNativeClickPrerequisites(Minecraft c,LocalPlayer p,AbstractContainerMenu m,Screen s,int id,int revision,int slotCount,ChocolateClickPrerequisiteRules.Title title){client=c;player=p;menu=m;screen=s;this.id=id;this.revision=revision;this.slotCount=slotCount;slots=m.slots;nativeGui=c.gui;this.title=title;}
    static ChocolateNativeClickPrerequisites capture(Minecraft c,LocalPlayer p,AbstractContainerMenu m,Screen s,int id,int revision,int slotCount){
        if(!fieldBacked(c.gui,net.minecraft.client.gui.Gui.class,"chocolate$clickScreen")||!fieldBacked(s,Screen.class,"chocolate$clickTitle")||!fieldBacked(s,net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,"chocolate$clickMenu")||!fieldBacked(m,AbstractContainerMenu.class,"chocolate$clickRevision")||!fieldBacked(p,net.minecraft.world.entity.LivingEntity.class,"chocolate$clickHealthId")||!fieldBacked(p,net.minecraft.world.entity.Entity.class,"veltraph$watchRemoval")||!fieldBacked(p,net.minecraft.world.entity.Entity.class,"veltraph$watchData"))return null;
        if(!ownedRaw(net.minecraft.network.syncher.SynchedEntityData.class,"veltraph$watchItems")||!ownedRaw(net.minecraft.network.syncher.SynchedEntityData.DataItem.class,"veltraph$watchValue")||!ownedRaw(ItemStack.class,"veltraph$watchCount")||!ownedRaw(ItemStack.class,"veltraph$watchItem")||!ownedRaw(net.minecraft.core.Holder.Reference.class,"veltraph$sceneRawHolderValue")||!ownedRaw(MutableComponent.class,"veltraph$watchContents")||!ownedRaw(MutableComponent.class,"veltraph$watchSiblings")||!ownedRaw(PlainTextContents.LiteralContents.class,"veltraph$watchText"))return null;
        if(!fieldBacked(m,AbstractContainerMenu.class,"veltraph$returnCursor")||!fieldBacked(m,AbstractContainerMenu.class,"getCarried")||!fieldBacked(m.slots,net.minecraft.core.NonNullList.class,"veltraph$returnList")||!fieldBacked(m.slots,net.minecraft.core.NonNullList.class,"size"))return null;
        if(!fieldBacked(c.gui,net.minecraft.client.gui.Gui.class,"screen")||!fieldBacked(s,Screen.class,"getTitle")||!fieldBacked(s,net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class,"getMenu")||!fieldBacked(m,AbstractContainerMenu.class,"getStateId"))return null;
        if(!((Object)c.gui instanceof ChocolateClickNativeFields.GuiFields gui)||gui.chocolate$clickScreen()!=s||!((Object)s instanceof ChocolateClickNativeFields.ScreenFields raw))return null;
        var title=title(raw.chocolate$clickTitle(),new ArrayList<>(),0,new int[2]);if(title==null)return null;
        var captured=new ChocolateNativeClickPrerequisites(c,p,m,s,id,revision,slotCount,title);return captured.current()?captured:null;
    }
    /** Method metadata is inspected only before validation, never called to rebase final native facts. */
    private static boolean fieldBacked(Object value,Class<?> original,String method){try{return value!=null&&value.getClass().getMethod(method).getDeclaringClass()==original;}catch(ReflectiveOperationException|LinkageError unavailable){return false;}}
    /** Exact native classes protect subsequent fresh aliases; inspect their raw-reader ownership once. */
    private static boolean ownedRaw(Class<?> type,String method){try{return type.getMethod(method).getDeclaringClass()==type;}catch(ReflectiveOperationException|LinkageError unavailable){return false;}}
    boolean current(){
        if(client.player!=player||player==null||client.gui!=nativeGui||!((Object)client.gui instanceof ChocolateClickNativeFields.GuiFields gui)||gui.chocolate$clickScreen()!=screen||player.containerMenu!=menu||!((Object)screen instanceof ChocolateClickNativeFields.ContainerFields rawContainer)||rawContainer.chocolate$clickMenu()!=menu
            ||menu.containerId!=id||menu.slots!=slots||!((Object)menu instanceof ChocolateClickNativeFields.MenuFields rawMenu)||rawMenu.chocolate$clickRevision()!=revision
            ||!((Object)menu instanceof IssuedReturnNativeFields.Menu carried)||!empty(carried.veltraph$returnCursor())||!alive(player)
            ||!((Object)screen instanceof ChocolateClickNativeFields.ScreenFields rawScreen))return false;
        var rawList=(Object)menu.slots instanceof IssuedReturnNativeFields.ListFields list?list.veltraph$returnList():null;
        if(rawList==null||rawList.getClass()!=ArrayList.class||!ChocolateClickPrerequisiteRules.menu(menu.containerId,rawMenu.chocolate$clickRevision(),rawList.size(),id,revision,slotCount))return false;
        var now=title(rawScreen.chocolate$clickTitle(),new ArrayList<>(),0,new int[2]);return ChocolateClickPrerequisiteRules.title(title,now);
    }
    private static boolean empty(ItemStack stack){
        if(stack==ItemStack.EMPTY)return ChocolateClickPrerequisiteRules.empty(true,0,false);
        if(stack==null||stack.getClass()!=ItemStack.class||!((Object)stack instanceof VeinWatchNativeFields.StackFields raw))return false;
        int count=raw.veltraph$watchCount();if(count<=0)return ChocolateClickPrerequisiteRules.empty(false,count,false);
        var holder=raw.veltraph$watchItem();
        if(holder!=null&&holder.getClass()==net.minecraft.core.Holder.Reference.class&&(Object)holder instanceof dev.veltraph.client.mixin.CollectionSceneHolderAccessor fields)return ChocolateClickPrerequisiteRules.empty(false,count,fields.veltraph$sceneRawHolderValue()==net.minecraft.world.item.Items.AIR);
        return holder!=null&&holder.getClass()==net.minecraft.core.Holder.Direct.class&&ChocolateClickPrerequisiteRules.empty(false,count,((net.minecraft.core.Holder.Direct<?>)holder).value()==net.minecraft.world.item.Items.AIR);
    }
    private static boolean alive(LocalPlayer player){
        if(player.getClass()!=LocalPlayer.class||!((Object)player instanceof VeinWatchNativeFields.EntityFields entity)||entity.veltraph$watchRemoval()!=null||!((Object)player instanceof ChocolateClickNativeFields.LivingFields living))return false;
        var data=entity.veltraph$watchData();if(data==null||data.getClass()!=net.minecraft.network.syncher.SynchedEntityData.class||!((Object)data instanceof VeinWatchNativeFields.DataFields raw))return false;
        var id=living.chocolate$clickHealthId();if(id==null||id.getClass()!=net.minecraft.network.syncher.EntityDataAccessor.class)return false;int index=id.id();var items=raw.veltraph$watchItems();
        if(items==null||index<0||index>=items.length||items[index]==null||items[index].getClass()!=net.minecraft.network.syncher.SynchedEntityData.DataItem.class||!((Object)items[index] instanceof VeinWatchNativeFields.DataItemFields item))return false;
        var health=item.veltraph$watchValue();return ChocolateClickPrerequisiteRules.alive(entity.veltraph$watchRemoval(),health);
    }
    private static ChocolateClickPrerequisiteRules.Title title(Object value,List<Object> path,int depth,int[] totals){
        if(value==null||value.getClass()!=MutableComponent.class||!(value instanceof VeinWatchNativeFields.NameFields raw)||depth>16||++totals[0]>256)return null;
        for(Object prior:path)if(prior==value)return null;path.add(value);
        try{
            var contents=raw.veltraph$watchContents();String text;
            if(contents==PlainTextContents.EMPTY)text="";
            else if(contents!=null&&contents.getClass()==PlainTextContents.LiteralContents.class&&(Object)contents instanceof VeinWatchGraphFields.Literal literal)text=literal.veltraph$watchText();
            else return null;
            if(text==null||text.length()>4096||(totals[1]+=text.length())>16384)return null;
            var siblings=raw.veltraph$watchSiblings();if(siblings==null||siblings.getClass()!=ArrayList.class||siblings.size()>128)return null;
            var nodes=new ArrayList<ChocolateClickPrerequisiteRules.Title>();for(Object child:siblings){var node=title(child,path,depth+1,totals);if(node==null)return null;nodes.add(node);}return new ChocolateClickPrerequisiteRules.Title(text,nodes);
        }finally{path.remove(path.size()-1);}
    }
}
