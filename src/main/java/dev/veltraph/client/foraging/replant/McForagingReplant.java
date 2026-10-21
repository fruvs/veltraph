package dev.veltraph.client.foraging.replant;

import dev.veltraph.client.config.*;
import dev.veltraph.client.foraging.ForagingAxePolicy;
import dev.veltraph.client.foraging.module.AutoForagingModule;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.LocalRoute;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Client-thread normal inventory SWAP/use-on/attack/return port. World geometry is read-only. */
public final class McForagingReplant implements ForagingReplantCycle.Port {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final dev.veltraph.client.navigation.mc.McParentRotationService interactionRotation;
    private final java.util.function.Supplier<dev.veltraph.client.core.Module> parent;
    private final BlockPos anchor;private final List<BlockPos> cells;private final McTravelNavigator returnNav;
    private net.minecraft.client.player.LocalPlayer owner;private net.minecraft.client.multiplayer.ClientLevel world;
    private Object connection;
    private Vec3 home;private int originalSlot,lastSlot=-1,transferTick=-1;private InventoryScreen inventoryScreen;
    private final ArrayDeque<Loan> loans=new ArrayDeque<>();private Loan pending;private boolean restoring,returnStarted,reversePending;
    private AutoForagingModule harvest;private int totalLogs,totalItems;private String problem="";
    private record Loan(int source,int hotbar,ItemStack displaced,ItemStack resource) {}
    public McForagingReplant(Minecraft client,ConfigService config,FarmServices services,BlockPos anchor){this(client,config,services,anchor,()->null);}
    public McForagingReplant(Minecraft client,ConfigService config,FarmServices services,BlockPos anchor,java.util.function.Supplier<dev.veltraph.client.core.Module> parent){
        this.parent=parent;interactionRotation=new dev.veltraph.client.navigation.mc.McParentRotationService(client,services,parent);this.client=client;this.config=config;this.services=services;this.anchor=anchor;cells=new ArrayList<>(List.of(anchor,anchor.add(1,0,0),anchor.add(0,0,1),anchor.add(1,0,1)));returnNav=new McTravelNavigator(client,services,config);
    }
    public static Optional<BlockPos> parseAnchor(String text){
        try{String[] a=text.strip().split("[,\\s]+");if(a.length!=3)return Optional.empty();int x=Integer.parseInt(a[0]),y=Integer.parseInt(a[1]),z=Integer.parseInt(a[2]);
            if(Math.abs((long)x)>29_999_980||Math.abs((long)z)>29_999_980||y< -2048||y>2020)return Optional.empty();return Optional.of(new BlockPos(x,y,z));
        }catch(RuntimeException e){return Optional.empty();}
    }
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean loaded(BlockPos p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean bound(){return client.player==owner&&client.level==world&&client.getConnection()==connection&&connection!=null&&owner!=null&&owner.isAlive()&&client.gameMode!=null;}
    private boolean screenOwned(){return client.gui.screen()!=null&&client.gui.screen()==inventoryScreen;}
    public boolean ownsInventoryScreen(){return screenOwned();}
    private boolean cursorEmpty(){return client.player!=null&&client.player.containerMenu.getCarried().isEmpty();}
    private boolean resource(ItemStack stack,ForagingReplantCycle.Resource r){return !stack.isEmpty()&&stack.is(r==ForagingReplantCycle.Resource.SAPLING?Items.DARK_OAK_SAPLING:Items.BONE_MEAL);}
    private int count(ForagingReplantCycle.Resource r){if(client.player==null)return 0;int n=0;for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);if(resource(s,r))n+=s.getCount();}return n;}
    public int axeSlot(){
        if(client.player==null)return -1;for(var item:List.of(Items.GOLDEN_AXE,Items.WOODEN_AXE))for(int i=0;i<9;i++)if(client.player.getInventory().getItem(i).is(item))return i;
        for(int i=0;i<9;i++){var s=client.player.getInventory().getItem(i);if(s.is(net.minecraft.tags.ItemTags.AXES)&&ForagingAxePolicy.isAxe(s.getHoverName().getString()))return i;}return -1;
    }
    private boolean volumeLoaded(){for(int x=-8;x<=9;x++)for(int z=-8;z<=9;z++)if(!loaded(anchor.add(x,0,z)))return false;return true;}
    private List<BlockPos> logs(){
        if(!volumeLoaded())return List.of();List<BlockPos> out=new ArrayList<>();
        for(int x=-8;x<=9;x++)for(int z=-8;z<=9;z++)for(int y=0;y<=24;y++){var p=anchor.add(x,y,z);if(client.level.getBlockState(mc(p)).is(Blocks.DARK_OAK_LOG))out.add(p);}
        return List.copyOf(out);
    }
    public Optional<String> preStart(){return preStart(false);}
    public Optional<String> preStart(boolean supplyAllowed){
        if(client.player==null||client.level==null||client.gameMode==null||!client.player.isAlive())return Optional.of("Load the private island first.");
        if(!cursorEmpty())return Optional.of("Clear the inventory cursor first.");if(client.gui.screen()!=null)return Optional.of("Close the current screen first.");
        var o=read();if(!o.loaded())return Optional.of("Load the complete configured planting/growth volume.");if(!o.soilValid())return Optional.of("All four supports must be dirt or grass blocks.");
        if(o.wrongSpecies()||o.cells().stream().anyMatch(c->c!=ForagingReplantCycle.Cell.EMPTY)||!logs().isEmpty())return Optional.of("The configured 2x2 footprint and growth volume must be clear of other saplings/logs.");
        if(!supplyAllowed&&(count(ForagingReplantCycle.Resource.SAPLING)<4||count(ForagingReplantCycle.Resource.BONEMEAL)<1))return Optional.of("Provide four Dark Oak saplings and bonemeal in inventory or hotbar.");
        if(axeSlot()<0)return Optional.of("Put a real axe in the hotbar (Golden/Treecapitator or Wooden preferred).");
        for(BlockPos p:cells)if(client.player.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(p.down())).add(0,.499,0))>4.5)return Optional.of("Stand within placement reach of all four configured supports.");return Optional.empty();
    }
    public void start(){owner=client.player;world=client.level;connection=client.getConnection();home=new Vec3(owner.getX(),owner.getY(),owner.getZ());
        // Opaque source setup order is local geometry: far supports first prevent an earlier
        // sapling from occluding a later soil face, while retaining the four indexed source steps.
        cells.sort(Comparator.comparingDouble((BlockPos p)->owner.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(p)))).reversed());
        originalSlot=services.inventory().selectedSlot();normalReturnHistory=null;normalReturnAffiliation=null;normalReturnSlot=-1;lastSlot=-1;problem="";restoring=false;returnStarted=false;normalReturnCompletionRefused=false;totalLogs=totalItems=0;}
    public ForagingReplantCycle.Observation read(){return read(true);}
    private ForagingReplantCycle.Observation read(boolean requireBound){
        boolean available=client.player!=null&&client.level!=null&&client.player.isAlive()&&client.gameMode!=null&&(!requireBound||owner==null||bound());boolean loaded=available&&volumeLoaded();
        boolean soil=loaded,wrong=false;List<ForagingReplantCycle.Cell> states=new ArrayList<>();
        for(BlockPos p:cells){if(!loaded){states.add(ForagingReplantCycle.Cell.WRONG);continue;}var below=client.level.getBlockState(mc(p.down()));soil&=below.is(Blocks.DIRT)||below.is(Blocks.GRASS_BLOCK);var s=client.level.getBlockState(mc(p));
            var state=s.isAir()?ForagingReplantCycle.Cell.EMPTY:s.is(Blocks.DARK_OAK_SAPLING)?ForagingReplantCycle.Cell.SAPLING:s.is(Blocks.DARK_OAK_LOG)?ForagingReplantCycle.Cell.LOG:ForagingReplantCycle.Cell.WRONG;states.add(state);wrong|=state==ForagingReplantCycle.Cell.WRONG;
        }
        if(loaded)for(int x=-8;x<=9;x++)for(int z=-8;z<=9;z++)for(int y=0;y<=24;y++){var s=client.level.getBlockState(mc(anchor.add(x,y,z)));if(s.is(net.minecraft.tags.BlockTags.LOGS)&&!s.is(Blocks.DARK_OAK_LOG))wrong=true;}
        boolean grown=loaded&&states.stream().allMatch(s->s==ForagingReplantCycle.Cell.LOG)&&!logs().isEmpty();
        return new ForagingReplantCycle.Observation(available,loaded,soil,cursorEmpty(),wrong,client.gui.screen()!=null&&!screenOwned(),states,count(ForagingReplantCycle.Resource.SAPLING),count(ForagingReplantCycle.Resource.BONEMEAL),grown,
                harvest!=null&&harvest.ownedHarvestComplete(),harvest!=null&&(!harvest.isEnabled()||harvest.ownedHarvestFailed()),!returnStarted&&!normalReturnCompletionRefused&&home!=null&&available&&home.distanceTo(new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()))<.5,System.currentTimeMillis());
    }
    private void select(int slot){if(services.inventory().selectedSlot()!=slot)services.inventory().selectSlot(slot);lastSlot=slot;}
    private boolean ready(){return bound()&&cursorEmpty()&&client.player.containerMenu==client.player.inventoryMenu&&(client.gui.screen()==null||screenOwned());}
    private boolean poll(){
        if(pending==null)return true;if(!ready()||owner.tickCount<=transferTick)return false;
        var inv=owner.getInventory();if(!ItemStack.matches(inv.getItem(pending.source()),pending.displaced())||!ItemStack.matches(inv.getItem(pending.hotbar()),pending.resource())){problem="Owned inventory SWAP was corrected or changed";return false;}
        if(!reversePending)loans.push(pending);pending=null;reversePending=false;return true;
    }
    private boolean swap(Loan loan,boolean reverse){
        if(!ready())return false;if(client.gui.screen()==null){inventoryScreen=new InventoryScreen(owner);client.gui.setScreen(inventoryScreen);return false;}
        var slots=owner.inventoryMenu.slots.stream().filter(s->s.container==owner.getInventory()&&s.getContainerSlot()==loan.source()).toList();if(slots.size()!=1)return false;
        client.gameMode.handleContainerInput(owner.inventoryMenu.containerId,slots.getFirst().index,loan.hotbar(),ContainerInput.SWAP,owner);transferTick=owner.tickCount;pending=loan;reversePending=reverse;return true;
    }
    private void closeOwned(){if(screenOwned()){owner.closeContainer();client.gui.setScreen(null);}inventoryScreen=null;}
    @Override public boolean equip(ForagingReplantCycle.Resource r){
        if(!ready()||!poll()||!problem.isEmpty())return false;
        for(int i=0;i<9;i++)if(resource(owner.getInventory().getItem(i),r)){closeOwned();select(i);return true;}
        int slot=r==ForagingReplantCycle.Resource.SAPLING?6:7;
        for(int i=9;i<36;i++)if(resource(owner.getInventory().getItem(i),r)){swap(new Loan(i,slot,owner.getInventory().getItem(slot).copy(),owner.getInventory().getItem(i).copy()),false);return false;}return false;
    }
    private boolean use(BlockPos target,boolean top){
        if(!ready()||client.gui.screen()!=null||!loaded(target))return false;
        var point=net.minecraft.world.phys.Vec3.atCenterOf(mc(target)).add(0,top?.499:0,0);
        if(top){point=null;for(double x:new double[]{.5,.15,.85})for(double z:new double[]{.5,.15,.85}){
            var candidate=new net.minecraft.world.phys.Vec3(target.x()+x,target.y()+.999,target.z()+z);
            var ray=client.level.clip(new net.minecraft.world.level.ClipContext(owner.getEyePosition(),candidate,net.minecraft.world.level.ClipContext.Block.OUTLINE,net.minecraft.world.level.ClipContext.Fluid.NONE,owner));
            if(point==null&&ray.getType()==HitResult.Type.BLOCK&&ray.getBlockPos().equals(mc(target))&&ray.getDirection()==net.minecraft.core.Direction.UP)point=candidate;
        }if(point==null)return false;}
        var d=point.subtract(owner.getEyePosition());if(d.length()>4.5)return false;
        interactionRotation.track((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        if(!(client.hitResult instanceof BlockHitResult hit)||hit.getType()!=HitResult.Type.BLOCK||!hit.getBlockPos().equals(mc(target))||top&&hit.getDirection()!=net.minecraft.core.Direction.UP)return false;
        client.gameMode.useItemOn(owner,InteractionHand.MAIN_HAND,hit);return true;
    }
    @Override public boolean plant(int index){return equip(ForagingReplantCycle.Resource.SAPLING)&&resource(owner.getMainHandItem(),ForagingReplantCycle.Resource.SAPLING)&&client.level.getBlockState(mc(cells.get(index))).isAir()&&use(cells.get(index).down(),true);}
    @Override public boolean bonemeal(int index){
        if(!equip(ForagingReplantCycle.Resource.BONEMEAL)||!resource(owner.getMainHandItem(),ForagingReplantCycle.Resource.BONEMEAL))return false;
        // All four plants belong to this run. Bonemeal any nearest matching sapling in the 2x2;
        // aiming at the far planting index would be occluded by the now-complete near row.
        var target=cells.stream().filter(p->client.level.getBlockState(mc(p)).is(Blocks.DARK_OAK_SAPLING)).min(Comparator.comparingDouble(p->owner.getEyePosition().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(mc(p)))));
        return target.isPresent()&&use(target.get(),false);
    }
    @Override public boolean axe(){if(!ready()||!poll())return false;int slot=axeSlot();if(slot<0){problem="Axe unavailable";return false;}closeOwned();select(slot);return true;}
    @Override public boolean beginHarvest(){
        var logs=logs();if(logs.isEmpty()||logs.size()>256)return false;var sampled=new HashSet<>(logs);
        if(logs.stream().anyMatch(p->p.x()<=anchor.x()-8||p.x()>=anchor.x()+9||p.z()<=anchor.z()-8||p.z()>=anchor.z()+9||p.y()>=anchor.y()+24))return false;
        harvest=new AutoForagingModule("island_owned_tree","Island Owned Tree",GameArea.ISLAND,client,config,services,parent);harvest.setEnabled(true);
        if(!harvest.beginOwnedHarvest(new ArrayList<>(sampled),"dark_oak",axeSlot())){harvest.setEnabled(false);return false;}return true;
    }
    @Override public void harvestTick(){if(harvest!=null)harvest.onTick();}
    @Override public void clearSapling(int index){
        var p=cells.get(index);if(!ready()||client.gui.screen()!=null||!client.level.getBlockState(mc(p)).is(Blocks.DARK_OAK_SAPLING)){services.breaking().setBreaking(false);return;}
        var d=net.minecraft.world.phys.Vec3.atCenterOf(mc(p)).subtract(owner.getEyePosition());interactionRotation.track((float)Math.toDegrees(Math.atan2(-d.x,d.z)),(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z))));
        services.breaking().setBreaking(d.length()<=4.5&&client.hitResult instanceof BlockHitResult hit&&hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(mc(p)));
    }
    @Override public void release(){
        // Each motion port must retire only its retained exact publication/leg. Empty Root
        // ownership is not permission to clear manual or subsequently published native controls.
        // Retiring an unfinished real return can never re-enable the broad home shortcut.
        // Record abandonment before the original cleanup window captures its retained fields.
        if(returnStarted)normalReturnCompletionRefused=true;
        try(var original=openNormalReturnCleanupWindow()) {interactionRotation.release(returnNav);}
        returnStarted=false;
        if(harvest!=null&&bound()){
            totalLogs+=harvest.harvestedLogs();totalItems+=harvest.collectedItems();harvest.setEnabled(false);harvest=null;
        }
    }
    @Override public boolean returnHome(){
        if(normalReturnCompletionRefused||!bound())return false;if(!returnStarted){
            if(normalReturnHistory==null){normalReturnSlot=owner.getInventory().getSelectedSlot();
                normalReturnHistory=dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(owner.getInventory());}
            if(normalReturnHistory==null||!normalReturnHistory.current())return false;
            normalReturnEntering=true;boolean began;
            try(var original=openNormalReturnCleanupWindow()) {began=returnNav.start(new LocalRoute.Node(home,0,0),.35,dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.EXACT_RETURN);}
            catch(RuntimeException|Error originalFailure){normalReturnCompletionRefused=true;throw originalFailure;}
            finally{normalReturnEntering=false;}
            if(!began)return false;returnStarted=true;}
        try(var original=openNormalReturnCleanupWindow()) {
            var state=returnNav.tick();if(state==McTravelNavigator.State.ARRIVED){
                returnNav.stop();
                // A refused real terminal/history proof can never re-enter the proximity shortcut.
                if(!returnNav.normalReturnRetirementClean()||home==null
                    ||home.distanceTo(new Vec3(owner.getX(),owner.getY(),owner.getZ()))>.35
                    ||original==null||!original.completionFieldsCurrent()||!original.context.rawCurrent()){normalReturnCompletionRefused=true;return false;}
                returnStarted=false;normalReturnHistory=null;normalReturnAffiliation=null;normalReturnSlot=-1;
                return true;
            }return false;
        }catch(RuntimeException|Error originalFailure){normalReturnCompletionRefused=true;throw originalFailure;}
    }
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        if(!client.isSameThread()||!bound())return false;
        if(harvest!=null&&harvest.isEnabled())return harvest.consumeExpectedTeleport(position,now);
        return returnStarted&&returnNav.consumeExpectedTeleport(position,now);
    }
    public void feedback(String line){if(harvest!=null)harvest.onFeedback(line);}
    public void beginCleanup(){release();restoring=true;}
    public void cleanupTick(){
        if(!bound()){problem="World/player changed; inventory restoration requires the original session";
            // A scheduled boundary admits no outstanding physical loans. A selected-slot marker
            // alone must not reserve cleanup forever or select a slot in a foreign native world.
            if(pending==null&&loans.isEmpty()){lastSlot=-1;inventoryScreen=null;}return;}if(!cursorEmpty())return;
        if(pending!=null&&!poll())return;
        if(!loans.isEmpty()){
            Loan loan=loans.peek();var inv=owner.getInventory();if(!ItemStack.matches(inv.getItem(loan.source()),loan.displaced())||(!inv.getItem(loan.hotbar()).isEmpty()&&!ItemStack.isSameItemSameComponents(inv.getItem(loan.hotbar()),loan.resource()))){problem="Restoration identity changed; refusing further inventory actions";return;}
            if(client.gui.screen()!=null&&!screenOwned())return;
            var reverse=new Loan(loan.source(),loan.hotbar(),inv.getItem(loan.hotbar()).copy(),loan.displaced().copy());
            if(swap(reverse,true))loans.pop();return;
        }
        closeOwned();if(lastSlot>=0&&services.inventory().selectedSlot()==lastSlot)services.inventory().selectSlot(originalSlot);lastSlot=-1;
    }
    /** Settle original physical inventory loans before handing the closed inventory to supply. */
    public boolean prepareForSupply(){
        if(!bound()||harvest!=null||returnStarted||!cursorEmpty())return false;
        restoring=true;cleanupTick();if(pendingCleanup())return false;restoring=false;return ready()&&client.gui.screen()==null;
    }
    public boolean pendingCleanup(){return restoring&&(pending!=null||!loans.isEmpty()||screenOwned()||lastSlot>=0);}
    public boolean inventoryLoanActive(){return pending!=null||!loans.isEmpty()||screenOwned();}
    private dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NativeNormalReturnAffiliation normalReturnAffiliation;
    private NativeNormalReturnCleanupWindow normalReturnCleanupWindow;
    private static final List<net.minecraft.world.entity.EquipmentSlot> NORMAL_RETURN_GEAR=List.of(net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET,net.minecraft.world.entity.EquipmentSlot.OFFHAND);
    private boolean normalReturnCompletionRefused;
    private boolean normalReturnEntering;
    private int normalReturnSlot=-1;
    private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory normalReturnHistory;
    /** Exact actual private return child, session/home and original selected native history. */
    public final class NativeNormalReturnSource {
        private final dev.veltraph.client.foraging.replant.ForagingReplantModule parent;
        private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
        private final dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation;
        private final Object player=owner,level=world,listener=connection,inventory=owner.getInventory(),menu=owner.inventoryMenu,options=client.options,gameMode=client.gameMode;
        private final Vec3 originalHome=home;
        private final int originalSlot=normalReturnSlot;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalHistory=normalReturnHistory;
        private final java.util.List<ItemStack> stacks,gear;
        private NativeNormalReturnSource(dev.veltraph.client.foraging.replant.ForagingReplantModule parent,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation){
            this.parent=parent;this.leg=leg;this.affiliation=affiliation;
            var copies=new java.util.ArrayList<ItemStack>(36);for(int i=0;i<36;i++)copies.add(((net.minecraft.world.entity.player.Inventory)inventory).getItem(i).copy());stacks=java.util.List.copyOf(copies);gear=NORMAL_RETURN_GEAR.stream().map(slot->((net.minecraft.client.player.LocalPlayer)player).getItemBySlot(slot).copy()).toList();
        }
        boolean semanticCurrent(){
            if(client.player==null)return false;
            // Components can invoke public Module callbacks. Raw retained identity is checked last.
            for(int i=0;i<36;i++)if(!ItemStack.matches(stacks.get(i),((net.minecraft.world.entity.player.Inventory)inventory).getItem(i)))return false;
            for(int i=0;i<NORMAL_RETURN_GEAR.size();i++)if(!ItemStack.matches(gear.get(i),((net.minecraft.client.player.LocalPlayer)player).getItemBySlot(NORMAL_RETURN_GEAR.get(i))))return false;
            return affiliation.nativeCurrent()&&affiliation.selectedSlotCurrent(originalSlot,originalHistory);
        }
        boolean rawCurrent(){
            if(client.player==null)return false;
            if(!client.isSameThread()||owner!=player||world!=level||connection!=listener||client.player!=player||client.level!=level
                ||client.getConnection()!=listener||client.options!=options||client.gameMode!=gameMode||home!=originalHome||normalReturnSlot!=originalSlot||normalReturnHistory!=originalHistory
                ||!(normalReturnEntering||returnStarted)||normalReturnCompletionRefused||restoring||harvest!=null||pending!=null||reversePending
                ||client.player.getInventory()!=inventory||client.player.inventoryMenu!=menu||client.player.containerMenu!=menu
                ||!client.player.inventoryMenu.getCarried().isEmpty()||client.gui.screen()!=null||client.isPaused()||!client.player.isAlive()
                ||client.player.isPassenger()||client.player.isScoping()||client.player.isInLava()||client.player.level()!=client.level
                ||client.getCameraEntity()!=player||client.level.tickRateManager().isEntityFrozen(client.player)
                ||leg.parent()!=parent||originalHistory==null||originalSlot<0||originalSlot>8||!affiliation.matchesActualLeg(leg)
                ||!affiliation.rawCurrent()||!affiliation.rawSelectedSlotCurrent(originalSlot,originalHistory))return false;
            return true;
        }
        public boolean nativeCurrent(){return semanticCurrent()&&rawCurrent();}
    }
    public NativeNormalReturnSource captureNativeNormalReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact,
            dev.veltraph.client.foraging.replant.ForagingReplantModule actualParent){
        boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McForagingReplant.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==ForagingReplantModule.NativeNormalReturnSource.class&&first.getMethodName().equals("<init>");
        });
        if(!canonical||!client.isSameThread()||!bound()||!(normalReturnEntering||returnStarted)||actualParent==null||parent.get()!=actualParent
            ||home==null||exact==null||exact.parent()!=actualParent||normalReturnHistory==null||restoring||harvest!=null||pending!=null||reversePending)return null;
        var affiliation=returnNav.captureNativeNormalReturnAffiliation(exact,actualParent,home);if(affiliation==null)return null;
        var captured=new NativeNormalReturnSource(actualParent,exact,affiliation);if(!captured.nativeCurrent())return null;
        if(normalReturnAffiliation!=null&&normalReturnAffiliation!=affiliation)return null;
        normalReturnAffiliation=affiliation;
        var original=normalReturnCleanupWindow;if(original!=null&&!original.bindActualAffiliation(affiliation))return null;
        return captured;
    }
    /** Synchronous scope around the actual original child call, never public FAILED/state authority. */
    final class NativeNormalReturnCleanupWindow implements AutoCloseable {
        final ForagingReplantModule.NativeNormalReturnCleanupContext context;
        private final Object player=owner,level=world,listener=connection,inventory=owner.getInventory(),menu=owner.inventoryMenu,options=client.options,gameMode=client.gameMode;
        private final Vec3 originalHome=home;
        private final int returnSlot=normalReturnSlot,runSlot=originalSlot,heldSlot=lastSlot;
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory history=normalReturnHistory;
        private final Object originalHarvest=harvest,originalPending=pending,originalScreen=inventoryScreen;
        private final List<Loan> originalLoans=List.copyOf(loans);
        private final boolean wasRestoring=restoring,wasStarted=returnStarted,wasEntering=normalReturnEntering,wasReverse=reversePending,wasCompletionRefused=normalReturnCompletionRefused;
        private final List<ItemStack> stacks,gear;
        private dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation=normalReturnAffiliation;
        private boolean closed,revoked;
        private NativeNormalReturnCleanupWindow(ForagingReplantModule.NativeNormalReturnCleanupContext context){
            this.context=context;var copied=new ArrayList<ItemStack>(36);for(int i=0;i<36;i++)copied.add(((net.minecraft.world.entity.player.Inventory)inventory).getItem(i).copy());
            stacks=List.copyOf(copied);gear=NORMAL_RETURN_GEAR.stream().map(slot->((net.minecraft.client.player.LocalPlayer)player).getItemBySlot(slot).copy()).toList();
        }
        private boolean bindActualAffiliation(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NativeNormalReturnAffiliation actual){
            if(closed||revoked||normalReturnCleanupWindow!=this||actual==null)return false;
            if(affiliation==null&&wasEntering)affiliation=actual;return affiliation==actual;
        }
        boolean belongsTo(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return affiliation!=null&&affiliation.matchesActualLeg(exact);}
        boolean semanticCurrent(){
            if(client.player==null)return false;
            for(int i=0;i<36;i++)if(!ItemStack.matches(stacks.get(i),((net.minecraft.world.entity.player.Inventory)inventory).getItem(i)))return false;
            for(int i=0;i<NORMAL_RETURN_GEAR.size();i++)if(!ItemStack.matches(gear.get(i),((net.minecraft.client.player.LocalPlayer)player).getItemBySlot(NORMAL_RETURN_GEAR.get(i))))return false;
            return affiliation!=null&&affiliation.cleanupCurrent()&&affiliation.cleanupSelectedSlotCurrent(returnSlot,history);
        }
        boolean rawCurrent(){return completionFieldsCurrent()&&affiliation.cleanupRawCurrent()&&affiliation.cleanupRawSelectedSlotCurrent(returnSlot,history);}
        private boolean completionFieldsCurrent(){
            if(client.player==null||loans.size()!=originalLoans.size())return false;
            int loanIndex=0;for(var loan:loans)if(loan!=originalLoans.get(loanIndex++))return false;
            // Every session/runtime/resource field is evaluated after all component callbacks.
            return !closed&&!revoked&&normalReturnCleanupWindow==this&&client.isSameThread()
                &&normalReturnAffiliation==affiliation&&normalReturnHistory==history&&normalReturnSlot==returnSlot
                &&owner==player&&world==level&&connection==listener&&home==originalHome&&originalSlot==runSlot&&lastSlot==heldSlot
                &&client.player==player&&client.level==level&&client.getConnection()==listener&&client.options==options&&client.gameMode==gameMode
                &&client.player.getInventory()==inventory&&client.player.inventoryMenu==menu&&client.player.containerMenu==menu
                &&client.player.inventoryMenu.getCarried().isEmpty()&&client.gui.screen()==null&&!client.isPaused()&&client.player.isAlive()
                &&!client.player.isPassenger()&&!client.player.isScoping()&&!client.player.isInLava()&&client.player.level()==client.level
                &&client.getCameraEntity()==player&&!client.level.tickRateManager().isEntityFrozen(client.player)
                &&normalReturnCompletionRefused==wasCompletionRefused&&restoring==wasRestoring&&returnStarted==wasStarted&&normalReturnEntering==wasEntering&&reversePending==wasReverse
                &&harvest==originalHarvest&&originalHarvest==null&&pending==originalPending&&originalPending==null
                &&inventoryScreen==originalScreen&&originalScreen==null
                &&history!=null&&returnSlot>=0&&returnSlot<9;
        }
        boolean nativeCurrent(){return semanticCurrent()&&rawCurrent();}
        @Override public void close(){closed=true;if(normalReturnCleanupWindow==this)normalReturnCleanupWindow=null;}
    }
    private NativeNormalReturnCleanupWindow openNormalReturnCleanupWindow(){
        boolean canonical=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McForagingReplant.class).findFirst().orElse(null);
            return first!=null&&(first.getDeclaringClass()==ForagingReplantCycle.class
                ||first.getDeclaringClass()==ForagingReplantModule.class&&first.getMethodName().equals("onDisable"));
        });
        if(!canonical)return null;
        if(normalReturnCleanupWindow!=null){normalReturnCleanupWindow.revoked=true;return null;}
        if(!client.isSameThread()||!bound()||home==null||normalReturnHistory==null||harvest!=null||pending!=null||inventoryScreen!=null)return null;
        var actualParent=parent.get();if(!(actualParent instanceof ForagingReplantModule actual))return null;
        var context=actual.openNativeNormalReturnCleanupContext(this);if(context==null)return null;
        var original=new NativeNormalReturnCleanupWindow(context);normalReturnCleanupWindow=original;return original;
    }
    NativeNormalReturnCleanupWindow currentNativeNormalReturnCleanupWindow(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){
        var original=normalReturnCleanupWindow;return original!=null&&original.belongsTo(exact)&&original.nativeCurrent()?original:null;
    }
    public boolean nativeOwnerBound(){return bound();}
    public Optional<String> scheduledRestReadiness(){
        if(restoring||inventoryLoanActive()||harvest!=null||returnStarted)return Optional.of("Replant inventory loan, child harvest or return is active");
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!cursorEmpty())return Optional.of("Replant requires a healthy closed native menu");
        if(!volumeLoaded())return Optional.of("Replant growth volume is unloaded");
        var observation=read(false);if(!observation.soilValid()||observation.wrongSpecies()||observation.cells().stream().anyMatch(c->c!=ForagingReplantCycle.Cell.EMPTY))return Optional.of("Replant footprint/soil is not clear");
        if(axeSlot()<0||count(ForagingReplantCycle.Resource.SAPLING)<4||count(ForagingReplantCycle.Resource.BONEMEAL)<1)return Optional.of("Replant resources or axe are unavailable");
        return Optional.empty();
    }
    public boolean rebindForScheduledRest(){
        if(inventoryLoanActive()||harvest!=null||returnStarted||restoring)return false;
        owner=client.player;world=client.level;connection=client.getConnection();
        return scheduledRestReadiness().isEmpty();
    }
    public String problem(){return problem;}public int harvestedLogs(){return totalLogs+(harvest==null?0:harvest.harvestedLogs());}public int collectedItems(){return totalItems+(harvest==null?0:harvest.collectedItems());}
}
