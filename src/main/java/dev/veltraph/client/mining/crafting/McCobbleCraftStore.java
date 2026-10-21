package dev.veltraph.client.mining.crafting;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import java.util.*;

/** Ordinary use/container packets only. Predicted clicks never credit a completed craft/store. */
public final class McCobbleCraftStore implements CobbleCraftStorePort {
    private java.math.BigDecimal purseBaseline;
    private final dev.veltraph.client.inventory.selling.SellingMenuAccess economic;
    private final java.util.function.BooleanSupplier leaseAllowed;
    private final Minecraft c;private final FarmServices farm;private final McNavigator nav;
    private CobbleCraftStoreProfile p;private Object owner,world,menu,oldMenu;
    private Vec3 savedPose;private String savedSupport;private float yaw,pitch;private int slot,source,input,remaining,batches,stored,storeBefore;
    private String phase="IDLE",reason="Idle",afterOpen;private long tick,due,next;
    private boolean active,failed,opened,walking,returningView;
    private final Map<CobbleCraftStoreProfile.Identity,Integer> initial=new HashMap<>();
    private List<ItemStack> baseline=List.of(),storageBaseline=List.of();private ItemStack expectedCursor=ItemStack.EMPTY;
    private List<ItemStack> predicted=List.of();private AbstractContainerMenu predictedMenu;private String afterClick;
    public McCobbleCraftStore(Minecraft c,FarmServices farm,ConfigService config,java.util.function.BooleanSupplier leaseAllowed){this.leaseAllowed=java.util.Objects.requireNonNull(leaseAllowed);this.c=c;this.farm=farm;economic=new dev.veltraph.client.inventory.selling.SellingMenuAccess(c);nav=new McNavigator(c,farm.input(),config);}
    private static net.minecraft.core.BlockPos pos(dev.veltraph.client.world.BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(dev.veltraph.client.world.BlockPos target){return c.level!=null&&c.level.isInWorldBounds(pos(target))&&c.level.getChunkSource().hasChunk(target.x()>>4,target.z()>>4);}
    private String support(dev.veltraph.client.world.BlockPos p){if(!loaded(p))return "unloaded";var state=c.level.getBlockState(pos(p));return state.toString()+"|"+state.getCollisionShape(c.level,pos(p)).toAabbs();}
    private void target(CobbleCraftStoreProfile.Target t){if(!loaded(t.position())||!BuiltInRegistries.BLOCK.getKey(c.level.getBlockState(pos(t.position())).getBlock()).toString().equals(t.block()))throw new IllegalStateException("Configured craft/store target changed or unloaded");}
    private static String clean(String s){return s.replaceAll("§.","").strip();}
    private boolean matches(ItemStack stack,CobbleCraftStoreProfile.Identity id){
        if(stack.isEmpty()||!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(id.id())||!clean(stack.getHoverName().getString()).equals(id.name()))return false;
        for(var component:stack.getComponents())if(component.type().isTransient())return false;
        var lore=stack.get(DataComponents.LORE);if(!(lore==null?List.of():lore.lines().stream().map(v->clean(v.getString())).toList()).equals(id.lore()))return false;
        return id.components().equals("default")?ItemStack.isSameItemSameComponents(stack,new ItemStack(stack.getItem())):net.minecraft.core.component.DataComponentMap.CODEC.encodeStart(c.level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE),stack.getComponents()).getOrThrow().toString().equals(id.components());
    }
    private int count(CobbleCraftStoreProfile.Identity id){int n=0;for(int i=0;i<36;i++){var s=c.player.getInventory().getItem(i);if(matches(s,id))n+=s.getCount();}return n;}
    private static List<ItemStack> inventory(net.minecraft.world.entity.player.Inventory inv){var result=new ArrayList<ItemStack>();for(int i=0;i<36;i++)result.add(inv.getItem(i).copy());return result;}
    @Override public String readiness(CobbleCraftStoreProfile profile){return readiness(profile,true);}
    @Override public boolean materialsReadyToStop(CobbleCraftStoreProfile profile){return readiness(profile,false).isEmpty();}
    private String readiness(CobbleCraftStoreProfile profile,boolean stationary){try{
        if(!leaseAllowed.getAsBoolean())return "Scheduled pause owns mining resources";if(active)return "Craft/store already owns resources";
        if(c.player==null||c.level==null||c.gameMode==null||!c.player.isAlive()||c.player.isSpectator()||c.gui.screen()!=null||!c.player.onGround()||stationary&&c.player.getDeltaMovement().length()>=.1||!c.player.containerMenu.getCarried().isEmpty()||c.player.isUsingItem())return "Craft/store requires supported gameplay with no menu/cursor/use loan";
        target(profile.craft());target(profile.store());
        if(!c.player.getInventory().getItem(profile.sink()).isEmpty()||count(profile.output().item())!=0)return "Reserve empty output sink and start with no matching recipe products";
        var needed=new HashMap<CobbleCraftStoreProfile.Identity,Integer>();for(var i:profile.inputs())needed.merge(i.item(),i.units()*profile.batches(),Integer::sum);
        for(var e:needed.entrySet())if(count(e.getKey())<e.getValue())return "Waiting for exact configured recipe materials";
        if(!nav.standingClear(new Vec3(c.player.getX(),c.player.getY(),c.player.getZ())))return "Craft/store footprint is not safely loaded";
        return "";
    }catch(RuntimeException ex){return ex.getMessage();}}
    @Override public boolean start(CobbleCraftStoreProfile profile){String error=readiness(profile);if(!error.isEmpty()){reason=error;return false;}
        p=profile;purseBaseline=null;owner=c.player;world=c.level;savedPose=new Vec3(c.player.getX(),c.player.getY(),c.player.getZ());savedSupport=support(savedPose.toBlockPos().down());yaw=c.player.getYRot();pitch=c.player.getXRot();slot=c.player.getInventory().getSelectedSlot();
        baseline=inventory(c.player.getInventory());initial.clear();for(var i:p.inputs())initial.put(i.item(),count(i.item()));initial.put(p.output().item(),count(p.output().item()));
        tick=batches=stored=input=remaining=0;failed=false;active=true;returningView=false;release();open(true,"FILL");return true;
    }
    private void release(){nav.stop();farm.breaking().setBreaking(false);farm.input().releaseAllMovement();farm.rotation().cancelRotation();McHeldKeys.setHeld(c.options.keyUse,false);}
    private void phase(String nextPhase){phase=nextPhase;due=tick+p.timeout();reason=nextPhase;}
    private void open(boolean craft,String after){release();oldMenu=c.player.containerMenu;menu=null;afterOpen=after;opened=walking=false;phase(craft?"OPEN_CRAFT":"OPEN_STORE");}
    private void close(){if(c.player!=null&&c.player==owner&&c.level==world&&c.player.containerMenu==menu){c.player.closeContainer();menu=null;}}
    private AbstractContainerScreen<?> screen(){return c.gui.screen() instanceof AbstractContainerScreen<?> s&&s.getMenu()==c.player.containerMenu?s:null;}
    private AbstractContainerMenu current(boolean craft){var s=screen();if(s==null||s.getMenu()!=menu||!clean(s.getTitle().getString()).equals((craft?p.craft():p.store()).title()))throw new IllegalStateException("Owned recipe/storage menu replaced");
        var m=s.getMenu();if(craft&&p.nativeTable()&&!(m instanceof CraftingMenu))throw new IllegalStateException("Native recipe requires real crafting menu");return m;}
    private int invSlot(AbstractContainerMenu m,int index){return m.slots.stream().filter(s->s.container==c.player.getInventory()&&s.getContainerSlot()==index).mapToInt(s->s.index).findFirst().orElseThrow(()->new IllegalStateException("Reserved inventory slot not exposed"));}
    private void cell(AbstractContainerMenu m,int index){if(index<0||index>=m.slots.size()||m.getSlot(index).container==c.player.getInventory())throw new IllegalStateException("Configured recipe/storage cell overlaps player inventory");}
    private void ingress(boolean craft){
        var t=craft?p.craft():p.store();target(t);var s=screen();if(s!=null){if(!opened||s.getMenu()==oldMenu||!clean(s.getTitle().getString()).equals(t.title())||!s.getMenu().getCarried().isEmpty())throw new IllegalStateException("Unexpected recipe/store ingress");menu=s.getMenu();if(craft){var m=current(true);if(!(m instanceof CraftingMenu)){if(p.currencyPrefix().isBlank()||!p.output().item().lore().contains("Cost: 0 Coins"))throw new IllegalStateException("Source recipe requires an explicit zero-cost result quote and readable purse prefix");var coins=economic.read(p.currencyPrefix()).coins();if(coins==null||purseBaseline!=null&&coins.compareTo(purseBaseline)!=0)throw new IllegalStateException("Source recipe purse missing/changed");purseBaseline=coins;}for(var i:p.inputs()){cell(m,i.slot());if(!m.getSlot(i.slot()).getItem().isEmpty())throw new IllegalStateException("Recipe grid contains unowned items");}cell(m,p.output().slot());}else cell(current(false),p.storeSlot());phase(afterOpen);next=tick+p.delay();return;}
        if(c.gui.screen()!=null)throw new IllegalStateException("Unexpected ingress screen");if(opened)return;
        var center=net.minecraft.world.phys.Vec3.atCenterOf(pos(t.position()));
        if(c.player.getEyePosition().distanceTo(center)>3.4){if(!walking){for(int[] d:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(nav.start(t.position().add(d[0],0,d[1]))){walking=true;break;}if(!walking)throw new IllegalStateException("No supported approach to configured target");}
            var state=nav.tick();if(state==WalkController.State.BLOCKED)throw new IllegalStateException("Craft/store approach blocked");if(state==WalkController.State.ARRIVED)walking=false;return;}
        nav.stop();farm.rotation().lookAt(new Vec3(center.x,center.y,center.z));if(c.player.getDeltaMovement().length()<.1&&c.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos(t.position()))){c.gameMode.useItemOn(c.player,InteractionHand.MAIN_HAND,hit);opened=true;}
    }
    /** Bind object, revision and every slot/cursor in the same client-thread action. */
    private void click(AbstractContainerMenu m,int index,int button,ContainerInput action,String after){
        if(!c.isSameThread()||c.player!=owner||c.level!=world||m!=menu||screen()==null||screen().getMenu()!=m||index<0||index>=m.slots.size())throw new IllegalStateException("Stale craft/store click");
        int revision=m.getStateId();var before=m.slots.stream().map(s->s.getItem().copy()).toList();var cursor=m.getCarried().copy();
        if(m!=c.player.containerMenu||revision!=m.getStateId()||!ItemStack.matches(cursor,m.getCarried()))throw new IllegalStateException("Menu/cursor changed before click");for(int i=0;i<before.size();i++)if(!ItemStack.matches(before.get(i),m.getSlot(i).getItem()))throw new IllegalStateException("Menu slots changed before click");
        c.gameMode.handleContainerInput(m.containerId,index,button,action,c.player);
        predictedMenu=m;predicted=m.slots.stream().map(s->s.getItem().copy()).toList();expectedCursor=m.getCarried().copy();afterClick=after;phase("CLICK_WAIT");next=tick+p.delay();
    }
    private void waitClick(){if(tick<next)return;var m=current(predictedMenu instanceof CraftingMenu||phaseTargetCraft());if(m!=predictedMenu||!ItemStack.matches(expectedCursor,m.getCarried()))throw new IllegalStateException("Server/menu cursor differs from issued transfer");for(int i=0;i<predicted.size();i++){if(phaseTargetCraft()&&i==p.output().slot())continue;if(!ItemStack.matches(predicted.get(i),m.getSlot(i).getItem()))throw new IllegalStateException("Server/menu slots differ from issued transfer");}phase(afterClick);}
    private boolean phaseTargetCraft(){return !Set.of("STORE_PUT","STORE_REOPEN","STORE_VERIFY").contains(afterClick);}
    private void fill(){var m=current(true);if(input>=p.inputs().size()){phase("TAKE");return;}var ingredient=p.inputs().get(input);cell(m,ingredient.slot());if(!m.getCarried().isEmpty()||!m.getSlot(ingredient.slot()).getItem().isEmpty())throw new IllegalStateException("Unowned recipe cursor/cell");
        source=-1;for(var s:m.slots)if(s.container==c.player.getInventory()&&matches(s.getItem(),ingredient.item())&&s.getItem().getCount()>=ingredient.units()){source=s.index;break;}if(source<0)throw new IllegalStateException("Exact unsplit ingredient stack unavailable");remaining=ingredient.units();click(m,source,0,ContainerInput.PICKUP,"PUT");}
    private void put(){var m=current(true);var i=p.inputs().get(input);if(!matches(m.getCarried(),i.item())||m.getCarried().getCount()<remaining)throw new IllegalStateException("Owned ingredient cursor changed");
        if(m.getCarried().getCount()==remaining){remaining=0;click(m,i.slot(),0,ContainerInput.PICKUP,"NEXT_INPUT");}
        else {remaining--;click(m,i.slot(),1,ContainerInput.PICKUP,remaining==0?"RETURN_INPUT":"PUT");}}
    private void returnInput(){var m=current(true);var expected=p.inputs().get(input).item();if(!matches(m.getCarried(),expected)||!m.getSlot(source).getItem().isEmpty())throw new IllegalStateException("Owned ingredient restitution source changed");click(m,source,0,ContainerInput.PICKUP,"NEXT_INPUT");}
    private void take(){var m=current(true);if(!m.getCarried().isEmpty())throw new IllegalStateException("Foreign recipe cursor");var r=p.output();var stack=m.getSlot(r.slot()).getItem();if(stack.isEmpty()||stack.is(net.minecraft.world.item.Items.BARRIER))return;
        if(!matches(stack,r.item())||stack.getCount()!=r.units()||p.output().units()*p.batches()>stack.getMaxStackSize())throw new IllegalStateException("Recipe result differs from configured identity/quantity");for(var i:p.inputs())if(!matches(m.getSlot(i.slot()).getItem(),i.item())||m.getSlot(i.slot()).getItem().getCount()!=i.units())throw new IllegalStateException("Recipe cell differs before craft");
        click(m,r.slot(),0,r.cursor()?ContainerInput.PICKUP:ContainerInput.QUICK_MOVE,r.cursor()?"SINK":"CRAFT_REOPEN");}
    private void sink(){var m=current(true);var r=p.output();if(!matches(m.getCarried(),r.item())||m.getCarried().getCount()!=r.units())throw new IllegalStateException("Craft did not produce owned cursor");var sink=m.getSlot(invSlot(m,p.sink())).getItem();if(batches==0?!sink.isEmpty():!matches(sink,r.item())||sink.getCount()!=batches*r.units())throw new IllegalStateException("Reserved recipe output sink changed");click(m,invSlot(m,p.sink()),0,ContainerInput.PICKUP,"CRAFT_REOPEN");}
    private void verifyInventory(int completed){verifyInventory(completed,0);}
    private void verifyInventory(int completed,int deposited){
        for(var e:initial.entrySet()){int consumed=p.inputs().stream().filter(i->i.item().equals(e.getKey())).mapToInt(CobbleCraftStoreProfile.Input::units).sum()*completed;
            int expected=e.getValue()-consumed+(e.getKey().equals(p.output().item())?p.output().units()*completed-deposited:0);if(count(e.getKey())!=expected)throw new IllegalStateException("Fresh server inventory recipe delta differs");}
        for(int i=0;i<36;i++){var old=baseline.get(i);if(old.isEmpty()){var actual=c.player.getInventory().getItem(i);if(!actual.isEmpty()&&initial.keySet().stream().noneMatch(id->matches(actual,id)))throw new IllegalStateException("Foreign item entered reserved empty inventory");continue;}if(initial.keySet().stream().anyMatch(id->matches(old,id)))continue;if(!ItemStack.matches(old,c.player.getInventory().getItem(i)))throw new IllegalStateException("Foreign inventory stack changed");}
    }
    private void craftVerify(){current(true);verifyInventory(batches+1);batches++;if(batches<p.batches()){input=0;phase("FILL");}else{close();open(false,"STORE_PICK");}}
    private void storePick(){var m=current(false);verifyInventory(batches);if(!m.getCarried().isEmpty())throw new IllegalStateException("Foreign storage cursor");var dest=m.getSlot(p.storeSlot()).getItem();int units=batches*p.output().units();if(!dest.isEmpty()&&(!matches(dest,p.output().item())||dest.getCount()+units>dest.getMaxStackSize()))throw new IllegalStateException("Configured storage slot lacks exact compatible capacity");storeBefore=dest.getCount();storageBaseline=m.slots.stream().map(s->s.getItem().copy()).toList();
        var sources=m.slots.stream().filter(s->s.container==c.player.getInventory()&&matches(s.getItem(),p.output().item())).toList();if(sources.size()!=1||sources.getFirst().getItem().getCount()!=units)throw new IllegalStateException("Produced output stack split or changed");click(m,sources.getFirst().index,0,ContainerInput.PICKUP,"STORE_PUT");}
    private void storePut(){var m=current(false);int units=batches*p.output().units();if(!matches(m.getCarried(),p.output().item())||m.getCarried().getCount()!=units)throw new IllegalStateException("Owned storage cursor changed");click(m,p.storeSlot(),0,ContainerInput.PICKUP,"STORE_REOPEN");}
    private void storeVerify(){var m=current(false);var dest=m.getSlot(p.storeSlot()).getItem();if(!m.getCarried().isEmpty()||!matches(dest,p.output().item())||dest.getCount()!=storeBefore+batches*p.output().units()||count(p.output().item())!=0)throw new IllegalStateException("Fresh server storage/inventory outcome differs");verifyInventory(batches,batches*p.output().units());for(int i=0;i<storageBaseline.size();i++)if(i!=p.storeSlot()&&m.getSlot(i).container!=c.player.getInventory()&&!ItemStack.matches(storageBaseline.get(i),m.getSlot(i).getItem()))throw new IllegalStateException("Unrelated storage slot changed");stored=batches*p.output().units();close();walking=false;phase("RETURN");}
    private void returning(){
        if(!returningView){
            if(!walking){if(!nav.start(savedPose))throw new IllegalStateException("No supported return to mining pose: "+nav.status());walking=true;}
            var state=nav.tick();if(state==WalkController.State.BLOCKED)throw new IllegalStateException("Mining return blocked: "+nav.status());
            if(state!=WalkController.State.ARRIVED)return;nav.stop();returningView=true;
        }
        if(!nav.standingClear(new Vec3(c.player.getX(),c.player.getY(),c.player.getZ()))||!loaded(savedPose.toBlockPos())||!savedSupport.equals(support(savedPose.toBlockPos().down())))throw new IllegalStateException("Mining return footprint changed");
        farm.rotation().track(yaw,pitch);double distance=new Vec3(c.player.getX(),c.player.getY(),c.player.getZ()).distanceTo(savedPose);
        if(distance<.35&&c.player.blockPosition().equals(pos(savedPose.toBlockPos()))&&Math.abs(net.minecraft.util.Mth.wrapDegrees(c.player.getYRot()-yaw))<1.5&&Math.abs(c.player.getXRot()-pitch)<1.5&&c.player.onGround()&&c.player.getDeltaMovement().length()<.1){active=false;release();phase="COMPLETE";reason="Fresh server craft/storage acknowledged; mining pose restored";}
    }
    @Override public void tick(){if(!active)return;try{tick++;if(c.player!=owner||c.level!=world||c.player==null||!c.player.isAlive()||c.player.isSpectator()||c.gameMode==null)throw new IllegalStateException("Craft/store player/world changed");target(p.craft());target(p.store());if(purseBaseline!=null){var coins=economic.read(p.currencyPrefix()).coins();if(coins==null||coins.compareTo(purseBaseline)!=0)throw new IllegalStateException("Source recipe zero-spend purse contract changed");}if(c.player.getInventory().getSelectedSlot()!=slot)throw new IllegalStateException("Craft/store selected tool changed");if(tick>due)throw new IllegalStateException("Craft/store outcome timed out in "+phase);
        if(phase.equals("CLICK_WAIT")){waitClick();return;}if(tick<next)return;
        switch(phase){case "OPEN_CRAFT"->ingress(true);case "OPEN_STORE"->ingress(false);case "FILL"->fill();case "PUT"->put();case "RETURN_INPUT"->returnInput();case "NEXT_INPUT"->{input++;phase("FILL");}case "TAKE"->take();case "SINK"->sink();case "CRAFT_REOPEN"->{if(!current(true).getCarried().isEmpty())throw new IllegalStateException("Recipe cursor not deposited");close();open(true,"CRAFT_VERIFY");}case "CRAFT_VERIFY"->craftVerify();case "STORE_PICK"->storePick();case "STORE_PUT"->storePut();case "STORE_REOPEN"->{if(!current(false).getCarried().isEmpty())throw new IllegalStateException("Storage cursor not deposited");close();open(false,"STORE_VERIFY");}case "STORE_VERIFY"->storeVerify();case "RETURN"->returning();default->throw new IllegalStateException("Invalid craft/store phase");}
    }catch(RuntimeException e){reason=e.getMessage();failed=true;stop();phase="FAILED";}}
    /** Closing the owned vanilla menu returns its grid/cursor through the server; no local item writes. */
    @Override public void stop(){if(!active)return;active=false;release();if(c.player==owner&&c.level==world&&c.player!=null&&c.player.containerMenu==menu){var cursor=c.player.containerMenu.getCarried();if(cursor.isEmpty()||ItemStack.matches(cursor,expectedCursor))close();else reason+="; foreign cursor left for manual inspection";}predicted=List.of();expectedCursor=ItemStack.EMPTY;}
    @Override public boolean active(){return active;}@Override public boolean failed(){return failed;}
    @Override public boolean allowsScreen(String title){return active&&p!=null&&clean(title).equals((phase.equals("OPEN_STORE")||Set.of("STORE_PICK","STORE_PUT","STORE_REOPEN","STORE_VERIFY").contains(phase)||phase.equals("CLICK_WAIT")&&!phaseTargetCraft()?p.store():p.craft()).title());}
    @Override public String status(){return phase+": "+reason+" batches="+batches+" stored="+stored;}@Override public int stored(){return stored;}
}
