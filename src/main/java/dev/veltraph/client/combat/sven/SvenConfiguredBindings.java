package dev.veltraph.client.combat.sven;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.input.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import static dev.veltraph.client.combat.sven.SvenIngressPort.*;

/** Normal-constructor implementation of readable source helpers; salted values are validated JSON bindings. */
public final class SvenConfiguredBindings implements SvenSourceBindings {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final McGameContextService area;private final McMenuAccess menus;private final McNavigator terrain;
    private final McTravelNavigator travel;private final Random random=new Random();
    private Request active;private Consumer<Result> callback;private SvenBindingProfile profile;
    private Object player,world,connection,ownedScreen;private int ownedMenu=-1;
    private long started,lastBuff,warpIssuedAt;private boolean sent,clicked,warpIssued;
    private List<SvenParkRoutes.Edge> route=List.of();private int edge;private boolean walking,etherwarping;
    private BlockPos warpTarget;private int movedSlot=-1;private ItemStack movedStack;private int originalUnits;
    private Object transferMenu;
    private final SvenBuffSignalPredicate buffSignals=new SvenBuffSignalPredicate();
    private SvenWarpPermit warpPermit;
    private SvenBuffController buffController;private int buffOriginalSlot=-1;private ItemStack buffMenuItem;
    public SvenConfiguredBindings(Minecraft client,ConfigService config,FarmServices services){
        this.client=client;this.config=config;this.services=services;area=new McGameContextService(client);menus=new McMenuAccess(client);
        terrain=new McNavigator(client,services.input(),config);travel=new McTravelNavigator(client,services,new SvenTravelConfig(config,()->profile==null?null:profile.travelToolLoreToken()));
    }
    @Override public Optional<SvenPreparationPolicy.Observation> preparationObservation(){
        // The dedicated source constructor leaves its optional prerequisite collaborator null.
        // Native class construction also attaches no prerequisite collaborator; this is actual absence.
        var parsed=SvenBindingProfile.parse(config.get(SvenConfig.SVEN_SOURCE_BINDING_PROFILE),config.get(SvenConfig.SVEN_CAVE_TYPE));
        if(!parsed.bound()||client.player==null||client.level==null)return Optional.empty();
        profile=parsed.profile();var context=area.current();
        if(!context.inSkyBlock())return Optional.of(new SvenPreparationPolicy.Observation(SvenPreparationPolicy.Prerequisite.ABSENT,Optional.empty(),Optional.empty(),position()));
        if(context.area()!=GameArea.THE_PARK)return Optional.of(new SvenPreparationPolicy.Observation(SvenPreparationPolicy.Prerequisite.ABSENT,Optional.of(false),Optional.empty(),position()));
        var nearest=profile.profile().combatWaypoints().stream().min(Comparator.comparingDouble(p->new Vec3(p.x(),p.y(),p.z()).squaredDistanceTo(position()))).orElseThrow();
        var result=GroundPathfinder.find(terrain,GroundPathfinder.node(position()),nearest,2500,32);
        Optional<Boolean> found=result.reason().equals("Path search budget reached")?Optional.empty():Optional.of(result.found());
        return Optional.of(new SvenPreparationPolicy.Observation(SvenPreparationPolicy.Prerequisite.ABSENT,Optional.of(true),found,position()));
    }
    @Override public void begin(Request request,Consumer<Result> completed){
        var parsed=SvenBindingProfile.parse(config.get(SvenConfig.SVEN_SOURCE_BINDING_PROFILE),request.profile());
        if(!parsed.bound()){completed.accept(Result.blocked(parsed.error()));return;}
        if(active!=null){completed.accept(Result.failed("Configured source helper is already pending"));return;}
        profile=parsed.profile();active=request;callback=completed;player=client.player;world=client.level;connection=client.getConnection();started=now();sent=clicked=false;movedSlot=-1;
        switch(request.operation()){
            case TRAVEL_PREREQUISITE -> {
                boolean found=false;for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);var lore=stack.get(net.minecraft.core.component.DataComponents.LORE);
                    if(lore!=null&&lore.lines().stream().map(c->MenuSnapshot.clean(c.getString())).anyMatch(line->line.contains(profile.travelToolLoreToken()))){found=true;break;}}
                if(!found)finish(Result.failed("Required source travel-tool lore token is absent from hotbar"));else {var buff=buffSelected();finish(buff.isEmpty()?Result.blocked("Source buff footer/ordered-row observation is unavailable"):Result.complete(buff.get()?Continuation.BUFF:Continuation.TRAVEL));}
            }
            case PARENT_WARP, WARP_ROUTE_START -> tickWarp();
            case PROFILE_ROUTE -> {route=SvenParkRoutes.route(request.profile());edge=0;walking=etherwarping=false;tickRoute();}
            case BUFF -> {if(profile.buff()==null)finish(Result.blocked("Current seven-state SkyBlock/Cookie/Potion menu bindings are blank"));else{lastBuff=now();buffOriginalSlot=services.inventory().selectedSlot();buffController=new SvenBuffController(profile.buff());tickBuff();}}
            case FREE_SLOT -> tickFreeSlot();
            case PREPARE_INSIDE_TOOL -> finish(Result.blocked("WALK_INSIDE tool switcher ranks hotbar 0..7 by salted name/path exclusions, enchantability, item attribute and parent/entity priority. Current attribute/priority helper contract is unbound; configured Combat Weapon Slot is not substituted for source ranking"));
            case OPEN_BAZAAR, CLICK_SIGN, ENTER_INPUT, CLICK_TNT, CLICK_BUY_INSTANTLY, CLICK_BUY_ONE, CLOSE_BAZAAR -> tickBazaar();
            case MOVE_TNT_INTO_HOTBAR -> tickResourceMove();
            default -> finish(Result.blocked("Configured helper does not own "+request.operation()));
        }
    }
    /** Source effect gate is actual Jump Boost + configured MODIFY_JUMP_BOOST + cooldown + 0.3 draw. */
    @Override public Result preparationContinuation(Result result){
        if(result.continuation()!=Continuation.COMBAT)return result;var buff=buffSelected();
        if(buff.isEmpty())return Result.blocked("Source buff footer/ordered-row observation is unavailable");
        return buff.get()?Result.complete(Continuation.COMBAT_AFTER_BUFF):result;
    }
    private Optional<Boolean> buffSelected(){
        if(!profile.modifyJumpBoost()||!client.player.hasEffect(MobEffects.JUMP_BOOST))return Optional.of(false);
        if(client.gui==null||!(client.gui.hud.getTabList() instanceof dev.veltraph.client.mixin.PlayerListHudAccessor accessor))return Optional.empty();
        var footer=accessor.getFooter();var rows=accessor.veltraph$getPlayerInfos().stream().filter(info->info.getTabListDisplayName()!=null)
                .map(info->MenuSnapshot.clean(info.getTabListDisplayName().getString())).filter(s->!s.isEmpty()).toList();
        return Optional.of(buffSignals.observe(footer==null?null:footer.getString(),rows,profile.buff())&&now()-lastBuff>profile.buffCooldownMs()&&random.nextDouble()<.3);
    }
    @Override public void tick(){
        if(active==null)return;if(!contextValid()){if(awaitingNativeArrival())return;finish(Result.failed("Configured helper lost its native context"));return;}
        if(now()-started>profile.operationTimeoutMs()){finish(Result.failed(active.operation()+" acknowledgment exceeded explicit local operationTimeoutMs"));return;}
        switch(active.operation()){
            case PARENT_WARP, WARP_ROUTE_START -> tickWarp();case PROFILE_ROUTE -> tickRoute();case BUFF -> tickBuff();case FREE_SLOT -> tickFreeSlot();case MOVE_TNT_INTO_HOTBAR -> tickResourceMove();
            case OPEN_BAZAAR, CLICK_SIGN, ENTER_INPUT, CLICK_TNT, CLICK_BUY_INSTANTLY, CLICK_BUY_ONE, CLOSE_BAZAAR -> tickBazaar();default -> {}
        }
    }
    private void tickWarp(){
        if(client.gui.screen()!=null){finish(Result.failed("Screen opened during source warp"));return;}
        boolean arrived=area.current().area()==GameArea.THE_PARK&&(active.operation()==Operation.WARP_ROUTE_START?birch(position()):position().distanceTo(SvenCaveProfile.PARK_SPAWN)<15.0);
        if(arrived){warpIssued=false;var buff=active.operation()==Operation.PARENT_WARP?buffSelected():Optional.of(false);finish(buff.isEmpty()?Result.blocked("Source buff footer/ordered-row observation is unavailable"):Result.complete(buff.get()?Continuation.BUFF:Continuation.TRAVEL));return;}
        if(!sent){
            if(client.getConnection()==null){finish(Result.failed("Warp connection unavailable"));return;}
            // Exact command is explicit binding; actual Park arrival remains the completion boundary.
            var observation=SvenTeleportIntent.forOperation(profile.operationTimeoutMs());
            dev.veltraph.client.observation.unexpected.UnexpectedActions.expectTeleport(client,observation.target(),observation.radius(),observation.ttlMillis());
            client.player.connection.sendCommand(profile.warpCommand());sent=warpIssued=true;warpIssuedAt=now();warpPermit=new SvenWarpPermit(active.ticket(),active.operation(),player,world,connection,profile.warpCommand(),warpIssuedAt,warpIssuedAt+profile.operationTimeoutMs());
        }
    }
    private void tickRoute(){
        if(edge>=route.size()){travel.stop();finish(Result.complete());return;}
        var hop=route.get(edge);var first=mc(hop.etherwarpCandidates().getFirst());
        // Stable source waits five ticks for this chunk; native ticks keep ownership while waiting.
        if(!client.level.isLoaded(first))return;
        if(!walking&&!etherwarping){
            List<BlockPos> starts=new ArrayList<>();for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){var p=hop.standingStart().add(x,0,z);if(terrain.standable(p))starts.add(p);}
            var start=starts.isEmpty()?hop.standingStart():starts.get(random.nextInt(starts.size()));
            var targets=hop.etherwarpCandidates().stream().filter(p->client.level.isLoaded(mc(p))&&!passable(p)).toList();
            if(targets.isEmpty()){finish(Result.failed("Source route has no loaded non-passable etherwarp candidate for "+hop.to()));return;}
            warpTarget=targets.get(random.nextInt(targets.size()));
            if(!travel.start(new LocalRoute.Node(terrain.position(start),0,0))){finish(Result.failed("Source standing-start path unavailable: "+travel.status()));return;}walking=true;
        }
        var state=travel.tick();if(state==McTravelNavigator.State.FAILED){finish(Result.failed("Source physical route failed: "+travel.status()));return;}
        if(state!=McTravelNavigator.State.ARRIVED)return;
        if(walking){
            walking=false;travel.stop();Vec3 landing=terrain.position(warpTarget.up());
            if(!terrain.standingClear(landing)){finish(Result.blocked("Source solid route candidate has no observed supported etherwarp landing"));return;}
            if(!travel.start(new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,landing,0,0,warpTarget,Direction.UP))){finish(Result.failed("Loaded etherwarp target/tool binding unavailable: "+travel.status()));return;}if(services.inventory().selectedSlot()!=requiredTravelSlot()){travel.stop();finish(Result.blocked("Shared etherwarp selected a different source prerequisite tool"));return;}etherwarping=true;
        }else{etherwarping=false;travel.stop();edge++;}
    }
    private void tickBuff(){
        if(buffController==null){finish(Result.blocked("Recovered seven-state buff child has no bound configuration"));return;}
        var menu=menus.read();var action=buffController.tick(menu,client.gui.screen()==null,client.player.hasEffect(MobEffects.JUMP_BOOST));
        switch(action.kind()){
            case NONE -> {}
            case SELECT_SLOT_EIGHT -> {
                if(client.gui.screen()!=null){finish(Result.failed("Unowned screen before source SkyBlock slot selection"));return;}
                var stack=client.player.getInventory().getItem(8);var b=profile.buff();
                if(stack.isEmpty()||!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(b.skyblockItemId())
                        ||!MenuSnapshot.clean(stack.getHoverName().getString()).contains(b.skyblockItemName())){finish(Result.blocked("SWITCH_TO_SBMENU: actual hotbar slot 8 does not match the bound SkyBlock menu item"));return;}
                buffMenuItem=stack.copy();services.inventory().selectSlot(8);buffController.slotEightObserved(services.inventory().selectedSlot()==8);
            }
            case USE_MENU_ITEM -> {
                if(client.gui.screen()!=null||client.gameMode==null||services.inventory().selectedSlot()!=8||buffMenuItem==null
                        ||!ItemStack.matches(buffMenuItem,client.player.getInventory().getItem(8))){finish(Result.failed("Source SkyBlock held-item proof changed before use"));return;}
                // Source helper has a command fallback when the crosshair would activate a world target.
                if(sourceMenuTargetInteractive()){
                    if(profile.buff().openCommand().isBlank()){finish(Result.blocked("OPEN_SBMENU: source SkyBlock-menu fallback command is unbound for current interactive crosshair"));return;}
                    client.player.connection.sendCommand(profile.buff().openCommand());
                }else client.gameMode.useItem(client.player,net.minecraft.world.InteractionHand.MAIN_HAND);
                sent=true;
            }
            case CLICK -> {
                if(!sent&&ownedScreen==null){finish(Result.failed("Buff menu arrived without an owned SkyBlock use"));return;}
                own(action.menu());if(!menus.click(action.menu(),action.slot()))finish(Result.failed("Source buff menu stack/revision changed before click"));
            }
            case CLOSE -> {own(action.menu());if(!closeOwned())finish(Result.failed("Owned source buff menu close unavailable"));}
            case COMPLETE -> {if(buffOriginalSlot>=0)services.inventory().selectSlot(buffOriginalSlot);buffOriginalSlot=-1;buffMenuItem=null;buffController=null;finish(Result.complete());}
            case FAILED -> finish(Result.failed(action.reason()));
        }
    }
    /** Literal source interaction blacklist, avoiding a guessed fallback for ordinary ground. */
    private boolean sourceMenuTargetInteractive(){
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult)return true;
        if(!(client.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit)||hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return false;
        if(!client.level.isLoaded(hit.getBlockPos()))return true;
        var block=client.level.getBlockState(hit.getBlockPos()).getBlock();
        return Set.of(Blocks.CHEST,Blocks.TRAPPED_CHEST,Blocks.ENDER_CHEST,Blocks.FURNACE,Blocks.BLAST_FURNACE,
                Blocks.DISPENSER,Blocks.DROPPER,Blocks.HOPPER,Blocks.ENCHANTING_TABLE,Blocks.ANVIL,Blocks.BEACON,Blocks.CAULDRON,Blocks.BREWING_STAND).contains(block);
    }
    private void tickBazaar(){
        var b=profile.bazaar();if(b==null){finish(Result.blocked("Current Bazaar command/search/product/button/quote bindings are blank"));return;}
        var menu=menus.read();
        switch(active.operation()){
            case OPEN_BAZAAR -> {
                if(menu!=null&&menu.title().equals(b.searchMenu())&&sent){own(menu);finish(Result.complete());return;}
                if(!sent){if(client.gui.screen()!=null){finish(Result.failed("Unowned screen before Bazaar command"));return;}client.player.connection.sendCommand(b.command());sent=true;}
            }
            case CLICK_SIGN -> {
                if(client.gui.screen() instanceof AbstractSignEditScreen&&clicked){ownedScreen=client.gui.screen();finish(Result.complete());return;}
                if(!clicked&&expected(menu,b.searchMenu()))clicked=clickContains(menu,b.searchButton());
            }
            case ENTER_INPUT -> {
                if(menu!=null&&menu.title().equals(b.resultsMenu())&&sent){own(menu);finish(Result.complete());return;}
                if(!sent){
                    if(!(client.gui.screen() instanceof AbstractSignEditScreen sign)||client.gui.screen()!=ownedScreen){finish(Result.failed("Owned Bazaar sign unavailable"));return;}
                    if(!sign.keyPressed(new KeyEvent(0,1073741898,0))||!sign.keyPressed(new KeyEvent(0,1073741901,1))||!sign.keyPressed(new KeyEvent(0,8,0))){finish(Result.failed("Native sign edit shortcuts rejected"));return;}
                    for(int cp:b.searchInput().codePoints().toArray())if(!sign.charTyped(new CharacterEvent(cp))){finish(Result.failed("Native search character rejected"));return;}
                    sign.onClose();sent=true;
                }
            }
            case CLICK_TNT -> advanceMenu(menu,b.resultsMenu(),b.productName(),b.productMenu());
            case CLICK_BUY_INSTANTLY -> advanceMenu(menu,b.productMenu(),b.buyInstantly(),b.buyMenu());
            case CLICK_BUY_ONE -> {
                if(!expected(menu,b.buyMenu()))return;
                var slot=unique(menu,b.buyOne());if(slot.isEmpty())return;
                var price=SvenPurchaseQuote.price(slot.get().lore(),b.priceLorePrefix());
                if(price.isEmpty()){finish(Result.blocked("Actual buy-one quote lacks its explicitly bound price lore prefix"));return;}
                if(price.getAsLong()>b.maxCoins()){finish(Result.failed("Actual buy-one quote exceeds configured source price cap"));return;}
                if(menus.click(menu,slot.get().id())){clicked=true;finish(Result.complete());}
                else finish(Result.failed("Bound buy-one menu/stack/revision changed before purchase"));
                // Completion only proves dispatch. Controller still waits for exact chat receipt and inventory.
            }
            case CLOSE_BAZAAR -> {if(client.gui.screen()==null){finish(Result.complete());return;}if(closeOwned())finish(Result.complete());else finish(Result.failed("Unowned Bazaar close boundary"));}
            default -> {}
        }
    }
    private void advanceMenu(MenuSnapshot menu,String before,String button,String after){
        if(clicked&&menu!=null&&menu.title().equals(after)){own(menu);finish(Result.complete());return;}
        if(!clicked&&expected(menu,before))clicked=clickContains(menu,button);
    }
    private boolean expected(MenuSnapshot menu,String title){
        if(menu==null)return false;if(menu.title().equals(title)&&client.gui.screen()==ownedScreen&&menu.id()==ownedMenu)return true;
        finish(Result.failed("Owned source menu identity/title changed"));return false;
    }
    private Optional<MenuSnapshot.Slot> unique(MenuSnapshot menu,String token){
        var matches=menu.slots().stream().filter(s->!s.playerInventory()&&!s.locked()&&s.count()>0&&s.name().contains(token)).toList();
        if(matches.size()!=1){finish(Result.failed("Source label selector is missing or ambiguous: "+token));return Optional.empty();}return Optional.of(matches.getFirst());
    }
    private boolean clickContains(MenuSnapshot menu,String token){var slot=unique(menu,token);if(slot.isEmpty())return false;if(!menus.click(menu,slot.get().id())){finish(Result.failed("Source menu revision/stack changed before click"));return false;}return true;}
    private void tickFreeSlot(){
        if(profile.inventory()==null){finish(Result.blocked("Current protected-tool tag and registry-path substring are blank"));return;}
        if(!(client.gui.screen() instanceof InventoryScreen)||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()){
            finish(Result.failed("Actual empty-cursor player inventory boundary unavailable"));return;}
        var menu=client.player.inventoryMenu;
        if(movedSlot>=0){
            if(menu!=transferMenu){finish(Result.failed("Inventory menu changed during QUICK_MOVE"));return;}
            if(menu.getSlot(movedSlot).getItem().isEmpty()&&units(movedStack)==originalUnits){finish(Result.complete());return;}return;
        }
        var tag=TagKey.create(Registries.ITEM,Identifier.parse(profile.inventory().protectedToolTag()));
        var observations=menu.slots.stream().map(slot->new SvenInventoryPlan.Slot(slot.index,slot.getItem().getCount(),slot.container==client.player.getInventory(),
                slot.getItem().is(tag),BuiltInRegistries.ITEM.getKey(slot.getItem().getItem()).getPath())).toList();
        var decision=SvenInventoryPlan.choose(observations,profile.inventory().protectedRegistryPathSubstring(),random);
        if(decision.kind()==SvenInventoryPlan.Kind.READY){finish(Result.complete());return;}
        if(decision.kind()==SvenInventoryPlan.Kind.FAILED){finish(Result.failed(decision.reason()));return;}
        var slot=menu.getSlot(decision.slot());movedSlot=slot.index;movedStack=slot.getItem().copy();originalUnits=units(movedStack);transferMenu=menu;
        client.gameMode.handleContainerInput(menu.containerId,slot.index,0,ContainerInput.QUICK_MOVE,client.player);
    }
    /** The source arm is executable, but the controller deliberately has no invented incoming edge. */
    private void tickResourceMove(){
        if(profile.inventory()==null||profile.inventory().resourceMoveName().isBlank()){finish(Result.blocked("Current resource QUICK_MOVE selector is blank; incoming source transition remains unrecovered"));return;}
        if(!(client.gui.screen() instanceof InventoryScreen)||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty()){
            finish(Result.failed("Actual empty-cursor player inventory boundary unavailable"));return;
        }
        var menu=client.player.inventoryMenu;
        if(movedSlot>=0){if(menu!=transferMenu){finish(Result.failed("Resource inventory menu changed during QUICK_MOVE"));return;}
            boolean hotbar=false;for(int i=0;i<9;i++){var stack=client.player.getInventory().getItem(i);if(!stack.isEmpty()&&MenuSnapshot.clean(stack.getHoverName().getString()).contains(profile.inventory().resourceMoveName())){hotbar=true;break;}}
            if(menu.getSlot(movedSlot).getItem().isEmpty()&&units(movedStack)==originalUnits&&hotbar)finish(Result.complete());return;
        }
        var source=menu.slots.stream().filter(slot->slot.container==client.player.getInventory()&&slot.index>=9&&!slot.getItem().isEmpty()
                &&MenuSnapshot.clean(slot.getItem().getHoverName().getString()).contains(profile.inventory().resourceMoveName())).findFirst();
        if(source.isEmpty()){finish(Result.complete());return;} // literal source missing-item callback
        var slot=source.get();movedSlot=slot.index;movedStack=slot.getItem().copy();originalUnits=units(movedStack);transferMenu=menu;
        client.gameMode.handleContainerInput(menu.containerId,slot.index,0,ContainerInput.QUICK_MOVE,client.player);
    }
    private int requiredTravelSlot(){for(int i=0;i<9;i++){var lore=client.player.getInventory().getItem(i).get(net.minecraft.core.component.DataComponents.LORE);if(lore!=null&&lore.lines().stream().map(c->MenuSnapshot.clean(c.getString())).anyMatch(line->line.contains(profile.travelToolLoreToken())))return i;}return -1;}
    private int units(ItemStack needle){int count=0;for(int i=0;i<36;i++){var s=client.player.getInventory().getItem(i);if(ItemStack.isSameItemSameComponents(needle,s))count+=s.getCount();}return count;}
    private void own(MenuSnapshot menu){ownedScreen=client.gui.screen();ownedMenu=menu.id();}
    private boolean closeOwned(){if(client.gui.screen()==null)return true;if(client.player!=player||client.level!=world||client.getConnection()!=connection)return false;if(client.gui.screen()==ownedScreen&&client.gui.screen() instanceof AbstractSignEditScreen){client.gui.setScreen(null);return true;}var menu=menus.read();if(menu==null||client.gui.screen()!=ownedScreen||menu.id()!=ownedMenu)return false;return menus.close(menu.id());}
    private boolean contextValid(){
        if(client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive())return true;
        return false;
    }
    @Override public boolean awaitingNativeArrival(){return warpIssued&&active!=null&&(active.operation()==Operation.PARENT_WARP||active.operation()==Operation.WARP_ROUTE_START)&&client.getConnection()==connection&&now()-warpIssuedAt<=profile.operationTimeoutMs();}
    @Override public Optional<SvenWarpPermit> pendingWarpPermit(){return awaitingNativeArrival()?Optional.ofNullable(warpPermit):Optional.empty();}
    @Override public boolean permitsNativeRebind(){return warpPermit!=null&&awaitingNativeArrival()
            &&client.player!=null&&client.player.level()==client.level&&warpPermit.arrival(client.player,client.level,client.getConnection(),client.player.isAlive(),area.current().area(),position(),now());}
    @Override public boolean commitNativeWarp(SvenWarpPermit permit){
        if(permit==null||permit!=warpPermit||!permitsNativeRebind())return false;
        player=client.player;world=client.level;warpPermit=null;return true;
    }
    @Override public boolean consumeExpectedTeleport(Vec3 p,long now){if(travel.consumeExpectedTeleport(p,now))return true;return warpIssued&&p!=null&&now>=warpIssuedAt&&now-warpIssuedAt<=profile.operationTimeoutMs()&&p.distanceTo(SvenCaveProfile.PARK_SPAWN)<15&&area.current().area()==GameArea.THE_PARK;}
    @Override public boolean allowsScreen(String title){
        if(active==null&&ownedScreen==null)return false;
        if(client.gui.screen()==ownedScreen)return true;
        if(active!=null&&active.operation()==Operation.CLICK_SIGN&&clicked&&client.gui.screen() instanceof AbstractSignEditScreen)return true;
        if(profile==null)return false;if(active!=null&&active.operation()==Operation.BUFF&&sent&&profile.buff()!=null){var buff=profile.buff();return MenuSnapshot.clean(title).equals(buff.skyblockMenu())||MenuSnapshot.clean(title).equals(buff.cookieMenu())||MenuSnapshot.clean(title).startsWith(buff.potionMenuPrefix());}var b=profile.bazaar();return active!=null&&(sent||clicked)&&b!=null&&List.of(b.searchMenu(),b.resultsMenu(),b.productMenu(),b.buyMenu()).contains(MenuSnapshot.clean(title));
    }
    @Override public void cancel(){active=null;callback=null;travel.stop();menus.clear();closeOwned();ownedScreen=null;ownedMenu=-1;warpIssued=false;warpPermit=null;if(buffOriginalSlot>=0&&client.player==player&&client.level==world&&client.getConnection()==connection)services.inventory().selectSlot(buffOriginalSlot);buffOriginalSlot=-1;buffMenuItem=null;buffController=null;walking=etherwarping=false;movedSlot=-1;movedStack=null;transferMenu=null;}
    private void finish(Result result){var completed=callback;active=null;callback=null;if(result.outcome()!=Outcome.COMPLETE)travel.stop();if(completed!=null)completed.accept(result);}
    private Vec3 position(){return client.player==null?null:new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private static net.minecraft.core.BlockPos mc(BlockPos p){return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z());}
    private boolean passable(BlockPos p){var state=client.level.getBlockState(mc(p));if(state.getBlock() instanceof FenceGateBlock)return state.getValue(FenceGateBlock.OPEN);if(state.getBlock() instanceof SnowLayerBlock)return state.getValue(SnowLayerBlock.LAYERS)==1;
        try{return state.getBlock()!=Blocks.SNOW&&state.getBlock().defaultBlockState().getCollisionShape(net.minecraft.world.level.EmptyBlockGetter.INSTANCE,net.minecraft.core.BlockPos.ZERO).isEmpty();}catch(RuntimeException e){return false;}}
    private static boolean birch(Vec3 p){return p!=null&&p.x()>=-335&&p.x()<=-255&&p.y()>=50&&p.y()<=110&&p.z()>=-90&&p.z()<=20;}
    private long now(){return services.clock().nowMillis();}
}
