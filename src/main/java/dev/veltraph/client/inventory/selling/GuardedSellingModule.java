package dev.veltraph.client.inventory.selling;

import com.google.gson.JsonParser;
import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.npc.villager.Villager;

/** Exact-stack NPC/George/discard workflows. Root drains maintenanceTick after disabled transfers. */
public final class GuardedSellingModule extends AbstractModule {
    public static final String ID="auto_sell_inventory";
    private enum Phase { IDLE, APPROACH, WAIT_MENU, AIM_TRASH, COOKIE_MENU, WORK }
    private record Anchor(String dimension,Vec3 position,float yaw,float pitch) {}
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final McNavigator navigator;private final McSharedMovementNavigator mobility;private final dev.veltraph.client.navigation.mc.McParentMotionCleanup motionCleanup;private final SellingMenuAccess access;private final SellingRuntime runtime=new SellingRuntime();
    private Phase phase=Phase.IDLE;private SellingRuntime.Settings settings;private SellingRule rule;private AutoSellMode mode;
    private LivingEntity npc;private Object playerOwner,worldOwner,connectionOwner,menuOwner;private Anchor anchor;
    private long tick,deadline,queueDeadline;private boolean walking,stopping;private String status="Idle";private String pursePrefix="";
    private String outcomeId="";private SellingAcknowledgment.Denomination denomination=SellingAcknowledgment.Denomination.UNKNOWN;
    private final NpcCookieMenuRuntime cookie;private NpcCookieBindings cookieBindings;private boolean cookieEnabled,cookieCommand;private String cookieJson="";
    private int queueSelected;private long lastQueueReceipt;private Object queueMenu;private int queueMenuId;
    private List<SellingRule> queue=List.of();private int queueIndex;private Map<Integer,String> queueLedger=Map.of();private BigDecimal queuePurse;private final List<SellingAcknowledgment> queueReceipts=new ArrayList<>();
    private String authorizedRules="",targetName="",authorizedAnchor="";
    public GuardedSellingModule(Minecraft client,FarmServices services,ConfigService config){
        super(ID,"Guarded Inventory Selling",ModuleCategory.MISC);this.client=client;this.services=services;this.config=config;
        navigator=new McNavigator(client,services.input(),config);mobility=new McSharedMovementNavigator(client,services,config);motionCleanup=new dev.veltraph.client.navigation.mc.McParentMotionCleanup(client,services,()->this);access=new SellingMenuAccess(client);cookie=new NpcCookieMenuRuntime(new McNpcCookieMenuPort(client));
    }
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){try{return isEnabled()&&owner()&&phase==Phase.APPROACH&&tick<=deadline&&(!cookieEnabled||tick<=queueDeadline)&&!(!config.get(MiscConfig.AUTO_SELL_ENABLED)||config.get(MiscConfig.AUTO_SELL_SACKS)||config.get(MiscConfig.AUTO_SELL_MODE)!=mode
                    ||!config.get(SellingConfig.SELLING_RULES_JSON).equals(authorizedRules)||!options(mode).equals(settings)
                    ||cookieEnabled&&(!config.get(SellingConfig.SELLING_NPC_COOKIE_ENABLED)||config.get(SellingConfig.AUTO_SELL_NPC_VIA_COMMAND)!=cookieCommand||!config.get(SellingConfig.SELLING_NPC_COOKIE_BINDINGS).equals(cookieJson))
                    ||config.get(SellingConfig.SELLING_CURRENCY_DENOMINATION)!=denomination
                    ||!config.get(SellingConfig.SELLING_CURRENCY_PREFIX).equals(pursePrefix)
                    ||mode==AutoSellMode.TRASH&&(!config.get(SellingConfig.SELLING_TRASH_ENABLED)||!config.get(SellingConfig.SELLING_TRASH_ANCHOR_JSON).equals(authorizedAnchor))
                    ||mode!=AutoSellMode.TRASH&&!config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_NAME:SellingConfig.SELLING_NPC_NAME).equals(targetName))&&mobility.consumeExpectedTeleport(position,now);}catch(RuntimeException invalid){return false;}}
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return config.get(MiscConfig.AUTO_SELL_MODE)==AutoSellMode.GEORGE_PETS?Optional.of(GameArea.HUB):Optional.empty();}
    @Override public boolean allowsScreen(String title){return cookieBindings!=null&&cookieBindings.allows(title)||runtime.allowsScreen(title)||settings!=null&&MenuSnapshot.clean(title).equals(settings.menuTitle());}
    private void release(){motionCleanup.release(mobility);}
    private SellingRuntime.Settings options(AutoSellMode mode){
        return new SellingRuntime.Settings(mode,config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_MENU_TITLE:SellingConfig.SELLING_NPC_MENU_TITLE),
                config.get(SellingConfig.SELLING_GEORGE_ACCEPT_LABEL),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_TITLE),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_LABEL),
                config.get(SellingConfig.SELLING_GEORGE_DEPOSIT_SLOT),config.get(SellingConfig.SELLING_GEORGE_CONFIRM_PET_SLOT),config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_RECEIPT_TEMPLATE:SellingConfig.SELLING_NPC_RECEIPT_TEMPLATE),
                config.get(SellingConfig.SELLING_MAX_QUANTITY),BigDecimal.valueOf(config.get(SellingConfig.SELLING_MIN_PROCEEDS)),
                BigDecimal.valueOf(config.get(SellingConfig.SELLING_MAX_PROCEEDS)),BigDecimal.valueOf(config.get(SellingConfig.SELLING_TRASH_MAX_VALUE)),
                config.get(SellingConfig.SELLING_DELAY_TICKS),config.get(SellingConfig.SELLING_TIMEOUT_TICKS));
    }
    private static Anchor anchor(String json){
        var value=JsonParser.parseString(json).getAsJsonObject();
        if(!value.keySet().equals(Set.of("dimension","x","y","z","yaw","pitch")))throw new IllegalArgumentException("Trash anchor requires dimension,x,y,z,yaw,pitch");
        var pos=new Vec3(value.get("x").getAsDouble(),value.get("y").getAsDouble(),value.get("z").getAsDouble());float yaw=value.get("yaw").getAsFloat(),pitch=value.get("pitch").getAsFloat();
        if(!Double.isFinite(pos.x())||!Double.isFinite(pos.y())||!Double.isFinite(pos.z())||!Float.isFinite(yaw)||!Float.isFinite(pitch)||pitch<-90||pitch>90)
            throw new IllegalArgumentException("Trash anchor pose is invalid");return new Anchor(value.get("dimension").getAsString(),pos,yaw,pitch);
    }
    private LivingEntity findNpc(String required){
        if(required==null||required.isBlank()||client.level==null||client.player==null)return null;
        List<LivingEntity> matches=new ArrayList<>();
        for(var entity:client.level.entitiesForRendering())if(entity!=client.player&&(entity instanceof Player||entity instanceof Villager)
                &&entity instanceof LivingEntity living&&living.isAlive()&&living.distanceTo(client.player)<=16
                &&MenuSnapshot.clean(living.getName().getString()).equals(MenuSnapshot.clean(required)))matches.add(living);
        return matches.size()==1?matches.getFirst():null;
    }
    private boolean launchOverlay(){return client.gui.screen() instanceof dev.veltraph.client.ui.VeltraphScreen
            ||client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;}
    private Optional<String> ready(boolean commit){
        if(runtime.cleaning())return Optional.of("Wait for owned-item restitution to finish");
        if(!config.get(MiscConfig.AUTO_SELL_ENABLED))return Optional.of("Enable Auto-sell explicitly");
        var mode=config.get(MiscConfig.AUTO_SELL_MODE);if(mode==AutoSellMode.BAZAAR)return Optional.of("Use the separate Bazaar inventory helper");
        if(config.get(MiscConfig.AUTO_SELL_SACKS))return Optional.of("Sack selling is not supported by this stack workflow");
        if(client.player==null||client.level==null||!client.player.isAlive())return Optional.of("Living player/world required");
        try{
            var settings=options(mode);String prefix=config.get(SellingConfig.SELLING_CURRENCY_PREFIX);var observed=access.read(prefix);
            if(!observed.cursorEmpty())return Optional.of("Cursor must be empty");
            var rules=SellingRule.parse(config.get(SellingConfig.SELLING_RULES_JSON));
            var candidates=rules.stream().filter(r->r.channel()==mode&&r.matches(observed.inventoryStack(r.inventoryIndex()).orElse(null))).toList();
            boolean useCookie=mode==AutoSellMode.NPC&&config.get(SellingConfig.SELLING_NPC_COOKIE_ENABLED);
            if(candidates.isEmpty()||!useCookie&&candidates.size()!=1)return Optional.of("Exactly one configured main-slot stack must match; hotbar/tools are protected");
            var rule=candidates.getFirst();for(var candidate:candidates)if(candidate.quantity()>settings.maxQuantity()||mode!=AutoSellMode.TRASH&&(candidate.total().compareTo(settings.minProceeds())<0||candidate.total().compareTo(settings.maxProceeds())>0))return Optional.of("Frozen queue quantity/proceeds bound exceeded");
            if(rule.quantity()>settings.maxQuantity())return Optional.of("Configured quantity cap exceeded");
            if(mode==AutoSellMode.TRASH){
                if(!config.get(SellingConfig.SELLING_TRASH_ENABLED))return Optional.of("Explicit trash permission is disabled");
                var anchor=anchor(config.get(SellingConfig.SELLING_TRASH_ANCHOR_JSON));
                if(!anchor.dimension.equals(client.level.dimension().identifier().toString())||rule.total().compareTo(settings.trashMaxValue())>0)
                    return Optional.of("Trash anchor dimension/value cap mismatch");
                if(!client.level.getChunkSource().hasChunk((int)Math.floor(anchor.position.x())>>4,(int)Math.floor(anchor.position.z())>>4))
                    return Optional.of("Trash anchor chunk is not actually loaded");
                if(commit)this.anchor=anchor;
            }else{
                if(prefix.isBlank()||observed.coins()==null)return Optional.of("Configure one readable, exact sidebar currency prefix");
                if(rule.total().compareTo(settings.minProceeds())<0||rule.total().compareTo(settings.maxProceeds())>0)return Optional.of("Configured proceeds bound exceeded");
                String name=config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_NAME:SellingConfig.SELLING_NPC_NAME);
                if(!useCookie&&name.isBlank())return Optional.of("Configure the exact target NPC name");
                boolean open=observed.menu()!=null&&observed.menu().title().equals(settings.menuTitle());
                NpcCookieBindings binding=null;
                if(useCookie){binding=NpcCookieBindings.parse(config.get(SellingConfig.SELLING_NPC_COOKIE_BINDINGS));if(!binding.sellerTitle().equals(settings.menuTitle()))return Optional.of("Observed seller title differs from sale contract");if(observed.menu()!=null)return Optional.of("Cookie parent requires initially closed menus");if(client.gui.screen()!=null&&!launchOverlay())return Optional.of("Close foreign screens before cookie opening");}
                var npc=open||useCookie?null:findNpc(name);
                if(!open&&!useCookie&&(client.gui.screen()!=null&&!launchOverlay()||npc==null))return Optional.of("Open the configured menu or load one uniquely named NPC within 16 blocks");
                if(open){var trial=new SellingRuntime();trial.start(observed,rule,settings,tick);if(trial.terminal())return Optional.of(trial.reason());}
                if(commit){this.npc=npc;this.cookieBindings=binding;}
            }
            if(commit){this.mode=mode;this.settings=settings;this.rule=rule;this.cookieEnabled=useCookie;this.cookieCommand=config.get(SellingConfig.AUTO_SELL_NPC_VIA_COMMAND);this.cookieJson=config.get(SellingConfig.SELLING_NPC_COOKIE_BINDINGS);
                this.queue=useCookie?NpcSellingQueue.freeze(candidates,observed,java.util.concurrent.ThreadLocalRandom.current().nextBoolean()):List.of(rule);this.rule=this.queue.getFirst();this.queueIndex=0;this.queueLedger=observed.ledger();this.queuePurse=observed.coins();this.queueReceipts.clear();this.queueSelected=client.player.getInventory().getSelectedSlot();this.lastQueueReceipt=0;this.queueMenu=null;this.queueMenuId=-1;this.pursePrefix=prefix;this.authorizedRules=config.get(SellingConfig.SELLING_RULES_JSON);
                this.authorizedAnchor=config.get(SellingConfig.SELLING_TRASH_ANCHOR_JSON);
                this.targetName=mode==AutoSellMode.TRASH?"":config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_NAME:SellingConfig.SELLING_NPC_NAME);}
            return Optional.empty();
        }catch(RuntimeException e){return Optional.of("Required selling contract: "+e.getMessage());}
    }
    @Override public Optional<String> preStartCheck(){return ready(false);}
    @Override public void onEnable(){
        if(runtime.cleaning()){setEnabled(false);return;}
        release();tick=0;runtime.reset();access.clear();stopping=false;walking=false;phase=Phase.IDLE;
        playerOwner=client.player;worldOwner=client.level;connectionOwner=client.getConnection();menuOwner=null;outcomeId=UUID.randomUUID().toString();denomination=config.get(SellingConfig.SELLING_CURRENCY_DENOMINATION);
        var blocker=ready(true);if(blocker.isPresent()){fail(blocker.get());return;}
        if(launchOverlay())client.gui.setScreen(null);
        deadline=tick+settings.timeoutTicks();queueDeadline=Math.addExact(tick,Math.multiplyExact((long)settings.timeoutTicks(),queue.size()));
        if(mode==AutoSellMode.TRASH){
            if(client.gui.screen() instanceof InventoryScreen&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()){
                client.player.closeContainer();client.gui.setScreen(null);
            }
            if(client.gui.screen()!=null){fail("Close other menus before starting an anchored discard");return;}
            if(!mobility.start(anchor.position,.35,SharedMovementPolicy.Purpose.EXACT_RETURN)){fail("Trash anchor has no loaded, supported safe path");return;}
            walking=true;phase=Phase.APPROACH;status="Walking to explicitly configured trash anchor";
        }else{
            var observed=access.read(pursePrefix);if(cookieEnabled){startCookie();return;}if(observed.menu()!=null){begin(observed);return;}
            phase=Phase.APPROACH;status="Approaching exact named NPC";
        }
    }
    private void startCookie(){if(!cookie.start(cookieBindings,cookieCommand,tick,settings.delayTicks(),settings.timeoutTicks())){fail(cookie.reason());return;}phase=Phase.COOKIE_MENU;status=cookie.reason();}
    private boolean owner(){return client.player==playerOwner&&client.level==worldOwner&&client.getConnection()==connectionOwner&&client.player!=null&&client.player.isAlive();}
    private void begin(SellingObservation observed){
        if(cookieEnabled){if(queueMenu!=null&&(client.player.containerMenu!=queueMenu||observed.menu()==null||observed.menu().id()!=queueMenuId)){fail("Original native seller window replaced between frozen queue slots");return;}if(queueMenu==null){queueMenu=client.player.containerMenu;queueMenuId=observed.menu().id();}}
        mobility.stop();runtime.start(observed,rule,settings,tick,cookieEnabled&&queueIndex+1<queue.size());menuOwner=client.player.containerMenu;phase=Phase.WORK;
        if(runtime.terminal())fail(runtime.reason());else status=runtime.reason();
    }
    @Override public void onTick(){
        tick++;if(!owner()){runtime.abandon("Player/world replaced; preserve new state");setEnabled(false);return;}
        try{
            boolean changed=!config.get(MiscConfig.AUTO_SELL_ENABLED)||config.get(MiscConfig.AUTO_SELL_SACKS)||config.get(MiscConfig.AUTO_SELL_MODE)!=mode
                    ||!config.get(SellingConfig.SELLING_RULES_JSON).equals(authorizedRules)||!options(mode).equals(settings)
                    ||cookieEnabled&&(!config.get(SellingConfig.SELLING_NPC_COOKIE_ENABLED)||config.get(SellingConfig.AUTO_SELL_NPC_VIA_COMMAND)!=cookieCommand||!config.get(SellingConfig.SELLING_NPC_COOKIE_BINDINGS).equals(cookieJson))
                    ||config.get(SellingConfig.SELLING_CURRENCY_DENOMINATION)!=denomination
                    ||!config.get(SellingConfig.SELLING_CURRENCY_PREFIX).equals(pursePrefix)
                    ||mode==AutoSellMode.TRASH&&(!config.get(SellingConfig.SELLING_TRASH_ENABLED)||!config.get(SellingConfig.SELLING_TRASH_ANCHOR_JSON).equals(authorizedAnchor))
                    ||mode!=AutoSellMode.TRASH&&!config.get(mode==AutoSellMode.GEORGE_PETS?SellingConfig.SELLING_GEORGE_NAME:SellingConfig.SELLING_NPC_NAME).equals(targetName);
            if(changed){status="Selling authorization/contract changed; stop before another request";setEnabled(false);return;}
        }catch(RuntimeException invalid){status="Selling contract invalidated; stop before another request";setEnabled(false);return;}
        if(cookieEnabled&&tick>queueDeadline){fail("Original cumulative NPC queue deadline expired; no rearm");return;}
        if(phase==Phase.COOKIE_MENU){var o=access.read(pursePrefix);if(!o.ledger().equals(queueLedger)||o.coins()==null||o.coins().compareTo(queuePurse)!=0){fail("Frozen queue inventory/purse changed during cookie ingress");return;}cookie.tick(tick);status=cookie.reason();if(cookie.state()==NpcCookieMenuRuntime.State.HANDOFF){if(!cookie.handoffValid()){fail("Cookie callback custody changed");return;}begin(access.read(pursePrefix));}else if(!cookie.active())fail(cookie.reason());return;}
        if(phase==Phase.WORK){if(cookieEnabled&&client.player.getInventory().getSelectedSlot()!=queueSelected){fail("Manual selected slot changed during frozen NPC queue");return;}work(false);return;}
        if(tick>deadline){fail("Approach/menu acknowledgment timed out");return;}
        if(mode==AutoSellMode.TRASH){
            if(phase==Phase.APPROACH){var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){fail("Explicit discard anchor approach blocked");return;}
                if(state==McTravelNavigator.State.ARRIVED){mobility.stop();phase=Phase.AIM_TRASH;}return;}
            if(phase==Phase.AIM_TRASH){
                if(motionCleanup.beforePublication(2)){services.rotation().track(anchor.yaw,anchor.pitch);motionCleanup.remember();}
                if(Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot()-anchor.yaw))>3||Math.abs(client.player.getXRot()-anchor.pitch)>3)return;
                client.gui.setScreen(new InventoryScreen(client.player));begin(access.read(pursePrefix));return;
            }
        }
        var observed=access.read(pursePrefix);
        if(observed.menu()!=null&&observed.menu().title().equals(settings.menuTitle())){begin(observed);return;}
        if(client.gui.screen()!=null){fail("Unexpected screen during named NPC approach");return;}
        if(npc==null||!npc.isAlive()||client.level.getEntity(npc.getId())!=npc||!MenuSnapshot.clean(npc.getName().getString()).equals(MenuSnapshot.clean(targetName))){fail("Named NPC was replaced or renamed");return;}
        if(phase==Phase.WAIT_MENU)return;
        if(walking||client.player.distanceTo(npc)>2.8||!client.player.hasLineOfSight(npc)){
            if(!walking){var p=npc.blockPosition();boolean found=false;for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{1,0},{-1,0},{0,1},{0,-1}})
                if(mobility.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+d[0],p.getY(),p.getZ()+d[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){found=true;break;}
                if(!found){fail("No loaded, supported path to named NPC");return;}walking=true;}
            var state=mobility.tick();if(state==McTravelNavigator.State.FAILED){fail("Named NPC path blocked");return;}if(state==McTravelNavigator.State.ARRIVED)walking=false;return;
        }
        mobility.stop();var delta=npc.getBoundingBox().getCenter().subtract(client.player.getEyePosition());
        if(motionCleanup.beforePublication(2)){services.rotation().track((float)Math.toDegrees(Math.atan2(-delta.x,delta.z)),(float)-Math.toDegrees(Math.atan2(delta.y,Math.hypot(delta.x,delta.z))));motionCleanup.remember();}
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&hit.getEntity()==npc&&client.gameMode!=null){
            client.gameMode.interact(client.player,npc,hit,InteractionHand.MAIN_HAND);phase=Phase.WAIT_MENU;deadline=tick+settings.timeoutTicks();status="Waiting for real NPC interaction menu";
        }
    }
    private void work(boolean maintenance){
        var observed=access.read(pursePrefix);
        if(observed.menu()!=null&&client.player.containerMenu!=menuOwner){
            boolean confirmation=runtime.state()==SellingRuntime.State.WAIT_CONFIRM&&observed.menu().title().equals(settings.confirmTitle());
            if(!confirmation){runtime.abandon("Container object replaced; preserve current menu/cursor");if(isEnabled())setEnabled(false);return;}
        }
        var action=runtime.tick(observed,tick);
        if(observed.menu()!=null&&runtime.ownedMenu()==observed.menu().id())menuOwner=client.player.containerMenu;
        if(action.isPresent()){
            var request=action.get();var fresh=access.read(pursePrefix);
            boolean accepted=runtime.valid(request,fresh)&&access.act(request,rule,pursePrefix);runtime.applied(request,accepted,tick);
            dev.veltraph.client.log.VeltraphLog.details(displayName(),runtime.state()+": "+runtime.reason());
        }
        status=runtime.reason();
        if(runtime.state()==SellingRuntime.State.COMPLETE&&cookieEnabled&&isEnabled()){
            var proof=runtime.acknowledgedSale().orElseThrow();var o=access.read(pursePrefix);var expected=new HashMap<>(queueLedger);expected.remove(rule.inventoryIndex());
            boolean remaining=queueIndex+1<queue.size();
            if(proof.menuId()!=queueMenuId||proof.matchedReceiptSerial()<=lastQueueReceipt){fail("NPC queue native menu/receipt sequence changed");return;}lastQueueReceipt=proof.matchedReceiptSerial();
            if((remaining?(o.menu()==null||client.player.containerMenu!=menuOwner||!o.menu().title().equals(settings.menuTitle())):o.menu()!=null)||!o.cursorEmpty()||!o.ledger().equals(expected)||o.coins()==null||o.coins().compareTo(queuePurse.add(rule.total()))!=0){fail("Independent closed full-stack queue conservation failed");return;}
            queueReceipts.add(acknowledgment(proof,UUID.randomUUID().toString()));queueLedger=Map.copyOf(expected);queuePurse=o.coins();queueIndex++;
            if(queueIndex<queue.size()){rule=queue.get(queueIndex);runtime.reset();begin(o);return;}
        }
        if(runtime.terminal()&&isEnabled())setEnabled(false);
        if(maintenance&&runtime.terminal()&&!runtime.escrowHeld())closeOwned();
    }
    /** Root calls every client tick, including while disabled, solely to return this workflow's pet. */
    @Override public void maintenanceTick(){
        if(isEnabled()||!runtime.cleaning())return;tick++;
        if(!owner()||client.player.containerMenu!=menuOwner){runtime.abandon("Restitution ownership lost; preserve new cursor/menu");return;}
        work(true);
    }
    public boolean maintenancePending(){return runtime.cleaning();}
    @Override public boolean hasPendingCleanup(){return runtime.cleaning();}
    private void closeOwned(){
        if(owner()&&client.player.containerMenu==menuOwner&&!runtime.escrowHeld())access.closeOwned(runtime.ownedMenu(),client.gui.screen()==null?"":MenuSnapshot.clean(client.gui.screen().getTitle().getString()));
    }
    @Override public void onDisable(){
        release();cookie.cancel();
        if(owner()&&(!runtime.terminal()||runtime.escrowHeld()))runtime.cancel(access.read(pursePrefix),runtime.terminal()?runtime.reason()+"; return only acknowledged owned pet":"Stopped; return only owned pet under exact guards",tick);
        if(!runtime.cleaning()&&!runtime.escrowHeld())closeOwned();
    }
    private void fail(String why){status=why;runtime.fail(why);stopping=true;setEnabled(false);stopping=false;}
    @Override public void onServerMessage(String line){if(isEnabled())runtime.onMessage(line);}
    public Optional<SellingAcknowledgment> acknowledgedSale(){
        if(!client.isSameThread())throw new IllegalStateException("Sale evidence requires client thread");
        if(!owner()||connectionOwner==null)return Optional.empty();
        if(cookieEnabled)return !isEnabled()&&runtime.state()==SellingRuntime.State.COMPLETE&&queueReceipts.size()==1?Optional.of(queueReceipts.getFirst()):Optional.empty();
        return runtime.acknowledgedSale().map(p->acknowledgment(p,outcomeId));
    }
    private SellingAcknowledgment acknowledgment(SellingRuntime.SaleProof p,String id){return new SellingAcknowledgment(id,p.channel(),p.itemIdentity(),p.inventoryIndex(),p.quantity(),p.proceeds(),p.purseBefore(),p.purseAfter(),pursePrefix,denomination,playerOwner,worldOwner,connectionOwner,
            new dev.veltraph.client.metrics.FinancialActionReceipt.Evidence(menuOwner,p.menuId(),p.requestRevision(),p.receiptRevision(),p.matchedReceiptSerial(),p.channel().name()));}
    public List<SellingAcknowledgment> acknowledgedSales(){return owner()?cookieEnabled?List.copyOf(queueReceipts):acknowledgedSale().stream().toList():List.of();}
    public SellingRuntime.State sellingState(){return runtime.state();}
    public SellingInspector.Result inspect(SellingInspectorRequest request){return SellingInspector.inspect(request,this::inspectionSnapshot);}
    private SellingInspector.Snapshot inspectionSnapshot(int mainIndex){
        if(!client.isSameThread())throw new IllegalStateException("Inspection requires the client thread");
        var player=client.player;var world=client.level;
        if(player==null||world==null)return null;var inventory=player.getInventory();
        if(mainIndex<9||mainIndex>35||mainIndex>=inventory.getContainerSize())return null;
        return new SellingInspector.Snapshot(player,world,inventory,player.containerMenu,player.isAlive(),SellingMenuAccess.describeInventoryStack(inventory.getItem(mainIndex),mainIndex));
    }
    /** Read-only config template; the user must supply value/quote fields, never an inferred price. */
    public String ruleTemplate(AutoSellMode channel,int inventoryIndex,String petPath){
        var request=new SellingInspectorRequest(true,"",SellingInspectorRequest.Kind.INSPECT,channel,inventoryIndex,petPath==null?"":petPath);
        var result=inspect(request);if(!result.valid())throw new IllegalArgumentException(result.message());
        return com.google.gson.JsonParser.parseString(result.json()).getAsJsonArray().get(0).toString();
    }
    @Override public String diagnostics(){return "Selling "+phase+" / "+runtime.state()+": "+status+(maintenancePending()?"; returning owned pet":"");}
}
