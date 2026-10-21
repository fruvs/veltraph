package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.sven.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.rewarp.mc.NativeTeleportSignal;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;

/** Ghost-owned ingress and source buff child; normal native dispatches and observations only. */
public final class GhostNativePreflight {
    public enum State { INGRESS, WARP, BUFF, COMPLETE, FAILED, CANCELLED }
    private final Minecraft client;private final FarmServices services;private final GhostPreflightProfile profile;
    private final McGameContextService context;private final McMenuAccess menus;private final SvenBuffSignalPredicate signal=new SvenBuffSignalPredicate();
    private Object player,world,connection,screen,menu;private final UUID uuid;private final String dimension;
    private State state=State.INGRESS;private GhostWarpPermit permit;private GhostBuffController buff;
    private int originalSlot,selectedSlot,menuId=-1;private ItemStack opener=ItemStack.EMPTY;
    private ItemStack predictedCursor=ItemStack.EMPTY;private Object clickedMenu;private int clickedRevision=-1;private boolean teleportConsumed;
    private String expected="",status="Ghost source ingress";private boolean prefix,openerIssued,menuRequest,closeIssued,slotBorrowed;
    private GhostMenuTransition transitions;
    private java.util.function.Supplier<GhostSourceJumpBoostGate.Observed> sourceCookieAvailability;
    private long started,buffStartedAt=Long.MIN_VALUE;private final long lastBuff;private final GhostTransportLoop transport;
    public GhostNativePreflight(Minecraft client,FarmServices services,GhostPreflightProfile profile,long lastBuff){
        this.client=client;this.services=services;this.profile=profile;this.lastBuff=lastBuff;context=new McGameContextService(client);menus=new McMenuAccess(client);
        player=client.player;world=client.level;connection=client.getConnection();uuid=client.player.getUUID();dimension=client.level.dimension().identifier().toString();
        originalSlot=selectedSlot=services.inventory().selectedSlot();started=System.currentTimeMillis();transport=new GhostTransportLoop(started,profile.warpRetryIntervalMs(),profile.timeoutMs());
    }
    static GhostNativePreflight beginSearchBuffOnly(Minecraft client,FarmServices services,GhostPreflightProfile bound,long lastSourceEntry){
        var exact=new GhostPreflightProfile(bound.warpCommand(),bound.denial(),bound.anchor(),bound.timeoutMs(),true,GhostSourceJumpBoostGate.SOURCE_COOLDOWN_MS,
            bound.buff(),bound.buffCloseMessagePrefix(),bound.warpRetryIntervalMs());
        var helper=new GhostNativePreflight(client,services,exact,lastSourceEntry);
        // Source-helper entry and all subsequent phases use the same recovered process-cache predicate.
        // Ordinary ingress keeps its existing local profile grammar; no supplied generic acknowledgment is accepted.
        helper.sourceCookieAvailability=()->McGhostSourceCookieAccess.observe(client);
        helper.beginBuff(System.currentTimeMillis());
        if(helper.state!=State.BUFF||helper.buff==null){helper.cancel();throw new IllegalStateException("Actual source buff availability changed before entry");}return helper;
    }
    boolean ownsNativeViewPause(){
        if(!validContext()||buff==null)return false;
        if(client.gui.screen() instanceof AbstractContainerScreen<?>)return transitions!=null&&transitions.allows(menuObservation());
        return closeIssued&&(state==State.BUFF||state==State.COMPLETE)&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();
    }
    /** Lost source helper lease cannot restore a slot or close a screen under another owner. */
    void abandonSearchBuff(){if(transitions!=null)transitions.cancel();menuRequest=false;transport.cancel();state=State.CANCELLED;permit=null;buff=null;menus.clear();signal.reset();}
    boolean actualBuffCloseRestored(){return state==State.COMPLETE&&buff!=null&&buff.state()==GhostBuffController.State.COMPLETE&&closeIssued
        &&validContext()&&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
        &&!slotBorrowed&&services.inventory().selectedSlot()==originalSlot;}
    public long buffStartedAt(){return buffStartedAt;}
    public State state(){return state;}public String status(){return status;}public boolean complete(){return state==State.COMPLETE;}
    public Optional<GhostWarpPermit> pendingNativeWarp(){return state==State.WARP?Optional.ofNullable(permit):Optional.empty();}
    public Optional<GhostTransportLoop.Attempt> pendingTransportAttempt(){return state==State.WARP?transport.current():Optional.empty();}
    public int transportAttempts(){return transport.attempts();}
    public boolean atAnchor(){return validContext()&&context.current().inSkyBlock()&&context.current().area()==GameArea.DWARVEN_MINES&&loaded()&&position().distanceTo(profile.anchor())<1&&client.player.onGround();}
    public void tick(){
        if(state==State.COMPLETE||state==State.FAILED||state==State.CANCELLED)return;
        long now=System.currentTimeMillis();if(now-started>profile.timeoutMs()){fail("Ghost preflight acknowledgment exceeded explicit local timeout");return;}
        if(!validContext()){if(state!=State.WARP)fail("Ghost preflight native context/UUID changed");return;}
        services.input().releaseAllMovement();services.breaking().setBreaking(false);
        if(client.player.isDeadOrDying()||services.inventory().selectedSlot()!=selectedSlot||!cursorOwned()){fail("Ghost preflight death/slot/cursor custody changed");return;}
        switch(state){
            case INGRESS -> {
                if(client.gui.screen()!=null){fail("Unowned screen before Ghost ingress");return;}
                if(atAnchor()){beginBuff(now);return;}
                if(connection==null||!context.current().inSkyBlock()){fail("Ghost warp requires actual SkyBlock connection");return;}
                state=State.WARP;advanceIssuedTransport();
            }
            case WARP -> {if(client.gui.screen()!=null){fail("Unowned screen during Ghost warp");return;}if(arrival(permit,now))commitNativeWarp(permit);else advanceIssuedTransport();}
            case BUFF -> tickBuff();
            default -> {}
        }
    }
    /** Root may call only this transport branch while quiescent; it never starts buffs/grind or adopts P/W. */
    public void advanceIssuedTransport(){
        if(state!=State.WARP)return;long now=System.currentTimeMillis();transport.expire(now);
        if(transport.state()==GhostTransportLoop.State.EXHAUSTED){fail("Ghost absolute transport deadline exhausted after "+transport.attempts()+" source attempts");return;}
        if(arrival(permit,now))return; // Source tests arrival before deciding another clock-driven command.
        if(!validContext())return;
        if(client.player.isDeadOrDying()||services.inventory().selectedSlot()!=selectedSlot||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty()){fail("Ghost issued transport lost native slot/cursor/menu/health custody");return;}
        var next=transport.next(now);if(next.isEmpty())return;
        // Retire the previous permit before attempting another native dispatch. The observed serial
        // is a new floor; a cached receipt from any previous attempt cannot become this arrival.
        permit=null;teleportConsumed=false;var attempt=next.orElseThrow();var receipt=NativeTeleportSignal.read(client);
        boolean sent=false;
        if(connection!=null&&context.current().inSkyBlock()&&client.player.connection!=null){
            try{dev.veltraph.client.observation.unexpected.UnexpectedActions.expectTeleport(client,profile.anchor(),1,GhostTeleportObservation.ttlMillis(attempt,now));client.player.connection.sendCommand(profile.warpCommand());sent=true;}
            catch(NullPointerException unavailable){sent=false;} // Actual source helper returns false, ignored by caller.
        }
        transport.dispatched(attempt,sent);
        if(sent){
            permit=new GhostWarpPermit(player,world,connection,uuid,profile,dimension,receipt.serial(),attempt.issuedAt(),attempt.deadline(),attempt);
        }
        status="Ghost source warp attempt "+attempt.ordinal()+" | native dispatch="+sent+" | unchanged deadline="+attempt.deadline()+"; awaiting fresh receipt/cooldown";
    }
    public boolean arrival(GhostWarpPermit p,long now){
        if(p==null||p!=permit||transport.current().filter(a->a==p.attempt()).isEmpty()||!transport.dispatched()||client.player==null||client.level==null)return false;var r=NativeTeleportSignal.read(client);
        return p.arrival(client.player,client.level,client.getConnection(),client.player.getUUID(),client.player.isAlive(),client.player.level()==client.level,loaded(),
                context.current().area(),client.level.dimension().identifier().toString(),position(),r.serial(),r.position(),now)
                &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty()
                &&services.inventory().selectedSlot()==selectedSlot&&client.player.onGround()&&Math.hypot(client.player.getDeltaMovement().x,client.player.getDeltaMovement().z)<=.02;
    }
    public boolean commitNativeWarp(GhostWarpPermit p){
        long now=System.currentTimeMillis();if(state!=State.WARP||p!=permit||!arrival(p,now)||!transport.complete(p.attempt(),now))return false;
        player=client.player;world=client.level;connection=client.getConnection();menus.clear();permit=null;beginBuff(System.currentTimeMillis());return state!=State.FAILED;
    }
    private void beginBuff(long now){
        started=now;
        if(!profile.modifyJumpBoost()||!client.player.hasEffect(MobEffects.JUMP_BOOST)||now-lastBuff<=profile.buffCooldownMs()){state=State.COMPLETE;status="Ghost source buff gate skipped; route ready";return;}
        if(!cookieReady()){state=State.COMPLETE;status="Ghost source cookie signal absent; route ready";return;}
        buffStartedAt=now;GhostSourceJumpBoostClock.runtime().actualHelperEntry(now);transitions=new GhostMenuTransition(new GhostMenuTransition.Scope(player,world,connection,profile,8),started+profile.timeoutMs());buff=new GhostBuffController(profile.buff());state=State.BUFF;status="Ghost source potion-state preflight";
    }
    private void tickBuff(){
        if(!context.current().inSkyBlock()||context.current().area()!=GameArea.DWARVEN_MINES){fail("Ghost Dwarven/SkyBlock context lost during buff preflight");return;}
        if(!cookieReady()){fail("Ghost source cookie readiness lost during buff preflight");return;}
        if(!opener.isEmpty()&&!ItemStack.matches(opener,client.player.getInventory().getItem(8))){fail("Ghost SkyBlock opener item changed");return;}
        var snapshot=menus.read();
        if(client.gui.screen()!=null){
            if(!(client.gui.screen() instanceof AbstractContainerScreen<?> s)||snapshot==null){fail("Foreign non-container screen during Ghost buff");return;}
            if(transitions==null||!transitions.observe(menuObservation())){fail("Ghost buff menu arrived without exact live transition outcome");return;}
            screen=s;menu=s.getMenu();menuId=snapshot.id();menuRequest=transitions.pending().isPresent();
            if(!allowedCurrentTitle(snapshot.title())){fail("Ghost buff menu title changed outside requested transition");return;}
        }else if(screen!=null){
            if(!closeIssued){fail("Ghost owned buff menu closed externally");return;}
            screen=menu=null;menuId=-1;transitions.closed();menuRequest=false;
        }
        if(!client.player.containerMenu.getCarried().isEmpty())return;
        predictedCursor=ItemStack.EMPTY;clickedMenu=null;clickedRevision=-1;
        var action=buff.tick(snapshot,client.gui.screen()==null,client.player.hasEffect(MobEffects.JUMP_BOOST));status="Ghost buff "+buff.state();
        switch(action.kind()){
            case SELECT_SLOT_EIGHT -> {
                if(client.gui.screen()!=null){fail("Ghost SkyBlock slot requires closed native screen");return;}
                var item=client.player.getInventory().getItem(8);var b=profile.buff();
                if(item.isEmpty()||!BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals(b.skyblockItemId())||!MenuSnapshot.clean(item.getHoverName().getString()).contains(b.skyblockItemName())){fail("Ghost source hotbar slot 8 opener is unbound/mismatched");return;}
                opener=item.copy();slotBorrowed=true;services.inventory().selectSlot(8);selectedSlot=8;buff.slotEightObserved(services.inventory().selectedSlot()==8);
            }
            case USE_MENU_ITEM -> {
                if(openerIssued||client.gui.screen()!=null||client.gameMode==null||selectedSlot!=8||opener.isEmpty()){fail("Ghost source opener custody unavailable");return;}
                if(!request(profile.buff().skyblockMenu(),false))return;openerIssued=true;
                if(interactiveTarget()){
                    if(profile.buff().openCommand().isBlank()){fail("Ghost interactive-target SkyBlock fallback command is unbound");return;}
                    client.player.connection.sendCommand(profile.buff().openCommand());
                }else client.gameMode.useItem(client.player,InteractionHand.MAIN_HAND);
            }
            case CLICK -> {
                if(!openerIssued||snapshot!=action.menu()||screen!=client.gui.screen()||menu!=client.player.containerMenu){fail("Ghost source menu click lost custody");return;}
                if(buff.state()==GhostBuffController.State.OPEN_COOKIE_MENU){if(!request(profile.buff().cookieMenu(),false))return;}
                else if(buff.state()==GhostBuffController.State.OPEN_POTION_MENU||buff.state()==GhostBuffController.State.GO_NEXT_PAGE){if(!request(profile.buff().potionMenuPrefix(),true))return;}
                predictedCursor=client.player.containerMenu.getSlot(action.slot()).getItem().copy();clickedMenu=client.player.containerMenu;clickedRevision=client.player.containerMenu.getStateId();
                if(!menus.click(snapshot,action.slot()))fail("Ghost source menu revision/stack changed before click");
            }
            case CLOSE -> {if(screen!=client.gui.screen()||menu!=client.player.containerMenu||!client.player.containerMenu.getCarried().isEmpty()){fail("Ghost owned buff close lost custody");return;}closeIssued=true;if(!menus.close(menuId))fail("Ghost exact native buff close failed");}
            case COMPLETE -> {restoreSlot();state=State.COMPLETE;status="Ghost source buff menu closed; desired lore="+buff.desiredLoreObserved()+" | unresolved="+buff.unresolved()+" | native Jump Boost="+buff.nativeJumpBoostAtClose()+"; route continuation";}
            case FAILED -> fail(action.reason());
            case NONE -> {}
        }
    }
    private boolean cookieReady(){return GhostSourceBuffCookiePolicy.ready(sourceCookieAvailability,this::ordinaryProfileCookieReady);}
    private boolean ordinaryProfileCookieReady(){
        if(!(client.gui.hud.getTabList() instanceof dev.veltraph.client.mixin.PlayerListHudAccessor access))return false;
        var footer=access.getFooter();var rows=access.veltraph$getPlayerInfos().stream().filter(i->i.getTabListDisplayName()!=null).map(i->MenuSnapshot.clean(i.getTabListDisplayName().getString())).filter(s->!s.isEmpty()).toList();
        signal.reset();return signal.observe(footer==null?null:footer.getString(),rows,profile.buff());
    }
    private boolean expected(String title){return prefix?title.startsWith(expected):title.equals(expected);}
    private boolean allowedCurrentTitle(String title){return buff.state()==GhostBuffController.State.CLOSE_SBMENU&&(title.equals(profile.buff().skyblockMenu())||title.equals(profile.buff().cookieMenu())||title.startsWith(profile.buff().potionMenuPrefix()))||expected(title)||title.equals(profile.buff().skyblockMenu())&&buff.state()==GhostBuffController.State.OPEN_COOKIE_MENU||title.equals(profile.buff().cookieMenu())&&buff.state()==GhostBuffController.State.OPEN_POTION_MENU||title.startsWith(profile.buff().potionMenuPrefix())&&(buff.state()==GhostBuffController.State.TOGGLE_POTIONS||buff.state()==GhostBuffController.State.GO_NEXT_PAGE||buff.state()==GhostBuffController.State.CLOSE_SBMENU);}
    private boolean request(String title,boolean prefix){
        expected=title;this.prefix=prefix;
        var current=menuObservation().menu();
        var origin=screen==null?null:current;
        try{transitions.issue(origin,title,prefix,buff.state()==GhostBuffController.State.GO_NEXT_PAGE,System.currentTimeMillis());menuRequest=true;return true;}
        catch(IllegalStateException revoked){fail("Ghost menu request context/deadline revoked before dispatch");return false;}
    }
    private GhostMenuTransition.Observation menuObservation(){
        var currentScreen=client.gui.screen();GhostMenuTransition.Menu nativeMenu=null;
        if(currentScreen instanceof AbstractContainerScreen<?> s&&client.player!=null&&s.getMenu()==client.player.containerMenu)
            nativeMenu=new GhostMenuTransition.Menu(s,s.getMenu(),s.getMenu().containerId,s.getMenu().getStateId(),MenuSnapshot.clean(s.getTitle().getString()));
        var scope=new GhostMenuTransition.Scope(client.player,client.level,client.getConnection(),profile,client.player==null?-1:services.inventory().selectedSlot());
        boolean empty=client.player!=null&&client.player.containerMenu.getCarried().isEmpty();
        // No native packet producer certifies an in-place title transition here. Only the requested
        // new native menu generation is admitted; a prediction/revision alone grants no successor.
        return new GhostMenuTransition.Observation(scope,nativeMenu,client.player!=null&&client.player.isAlive(),empty,
                client.player!=null&&!empty&&cursorOwned(),false,System.currentTimeMillis());
    }
    public record MenuDiagnostics(boolean menuRequest,String expected,boolean prefix,long requestGeneration,
            Object requestToken,long requestIssuedAt,long deadline,String requestOriginTitle,int requestOriginId,int requestOriginRevision,
            Object ownedScreen,Object ownedMenu,int ownedMenuId,int ownedRevision,String ownedTitle,
            Object currentScreen,Object currentMenu,int currentMenuId,int currentRevision,String currentTitle,
            boolean cursorEmpty,boolean ownedPrediction,boolean contextMatches,boolean alive,String decision) {}
    /** Read-only: never observes/adopts a menu, consumes a request or changes the Root permission. */
    public MenuDiagnostics menuDiagnostics(){
        var o=menuObservation();var current=o.menu();var r=transitions==null?null:transitions.pending().orElse(null);var owned=transitions==null?null:transitions.owned();
        return new MenuDiagnostics(menuRequest,expected,prefix,r==null?-1:r.generation(),r==null?null:r.token(),r==null?-1:r.issuedAt(),started+profile.timeoutMs(),
                r==null||r.origin()==null?"":r.origin().title(),r==null||r.origin()==null?-1:r.origin().id(),r==null||r.origin()==null?-1:r.origin().revision(),
                screen,menu,menuId,owned==null?-1:owned.revision(),owned==null?"":owned.title(),
                client.gui.screen(),current==null?null:current.menu(),current==null?-1:current.id(),current==null?-1:current.revision(),current==null?"":current.title(),
                o.cursorEmpty(),o.ownedPrediction(),validContext(),o.alive(),transitions==null?"INACTIVE":transitions.decision(o).name());
    }
    public boolean allowsScreen(String title){
        if(state!=State.BUFF||!validContext()||services.inventory().selectedSlot()!=selectedSlot||!cursorOwned()||!(client.gui.screen() instanceof AbstractContainerScreen<?> s)||s.getMenu()!=client.player.containerMenu)return false;
        title=MenuSnapshot.clean(title);return transitions!=null&&transitions.allows(menuObservation())&&(s!=screen||s.getMenu()!=menu||allowedCurrentTitle(title));
    }
    public boolean consumeExpectedTeleport(Vec3 p,long now){if(state!=State.WARP||permit==null||!permit.live(now)||teleportConsumed||p==null||p.distanceTo(profile.anchor())>=1
                ||client.getConnection()!=permit.connection()||client.player==null||!uuid.equals(client.player.getUUID()))return false;
        var received=NativeTeleportSignal.read(client);if(received.serial()<=permit.receiptSerial()||received.position()==null||received.position().distanceTo(profile.anchor())>=1)return false;teleportConsumed=true;return true;}
    public void message(String text){
        if(text==null)return;String line=MenuSnapshot.clean(text);
        if(state==State.WARP&&line.equalsIgnoreCase(profile.denial()))fail("Ghost exact configured fast-travel denial");
        else if(state==State.BUFF&&validContext()&&!profile.buffCloseMessagePrefix().isBlank()&&line.startsWith(profile.buffCloseMessagePrefix()))buff.sourceCloseMessage();
    }
    private boolean cursorOwned(){
        if(client.player.containerMenu.getCarried().isEmpty())return true;
        return state==State.BUFF&&clickedMenu==client.player.containerMenu&&clickedRevision==client.player.containerMenu.getStateId()
                &&!predictedCursor.isEmpty()&&ItemStack.matches(predictedCursor,client.player.containerMenu.getCarried());
    }
    private boolean validContext(){return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.getUUID().equals(uuid)&&client.player.level()==client.level;}
    private boolean loaded(){if(client.level==null||client.player==null)return false;var a=profile.anchor().toBlockPos();return client.level.getChunkSource().hasChunk(client.player.blockPosition().getX()>>4,client.player.blockPosition().getZ()>>4)&&client.level.getChunkSource().hasChunk(a.x()>>4,a.z()>>4);}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean interactiveTarget(){
        if(client.hitResult instanceof EntityHitResult)return true;
        if(!(client.hitResult instanceof BlockHitResult h)||h.getType()!=HitResult.Type.BLOCK)return false;
        if(!client.level.isLoaded(h.getBlockPos()))return true;
        return Set.of(Blocks.CHEST,Blocks.TRAPPED_CHEST,Blocks.ENDER_CHEST,Blocks.FURNACE,Blocks.BLAST_FURNACE,Blocks.DISPENSER,Blocks.DROPPER,Blocks.HOPPER,Blocks.ENCHANTING_TABLE,Blocks.ANVIL,Blocks.BEACON,Blocks.CAULDRON,Blocks.BREWING_STAND).contains(client.level.getBlockState(h.getBlockPos()).getBlock());
    }
    private void restoreSlot(){if(slotBorrowed&&validContext()&&services.inventory().selectedSlot()==8&&!opener.isEmpty()&&ItemStack.matches(opener,client.player.getInventory().getItem(8)))services.inventory().selectSlot(originalSlot);selectedSlot=originalSlot;slotBorrowed=false;}
    private void fail(String reason){if(transitions!=null)transitions.cancel();menuRequest=false;transport.cancel();state=State.FAILED;status=reason;permit=null;restoreSlot();}
    public void cancel(){if(transitions!=null)transitions.cancel();menuRequest=false;transport.cancel();restoreSlot();state=State.CANCELLED;permit=null;buff=null;menus.clear();signal.reset();}
}
