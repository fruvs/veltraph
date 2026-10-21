package dev.veltraph.client.galatea.trash;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.inventory.selling.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/** Source trash travel/aim/inventory/drop/close flow, with exact configured stack and native outcome. */
public final class LushlilacTrashModule extends AbstractModule {
    public enum Stage { CALCULATING,TRAVELLING,AIMING,OPEN_INVENTORY,THROWING_ITEMS,CLOSE_INVENTORY,COMPLETE,FAILED,CANCELLED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;
    private final McNavigator walk;private final SellingMenuAccess access;private final SellingRuntime runtime=new SellingRuntime();
    private Stage stage=Stage.CALCULATING;private Object owner,world,connection,screen,menu;
    private String spotsProfile,rulesProfile,reason="Idle";private LushlilacTrashSpots.Spot spot;
    private SellingRule rule;private SellingRuntime.Settings settings;private long tick,deadline,timeout;private int discarded;
    public LushlilacTrashModule(Minecraft client,FarmServices services,ConfigService config) {
        super("lushlilac_trash","Lushlilac Trash",ModuleCategory.FORAGING);this.client=client;this.services=services;this.config=config;
        walk=new McNavigator(client,services.input(),config);access=new SellingMenuAccess(client);
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GALATEA);}
    private List<SellingRule> rules(){return SellingRule.parse(config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_RULES_JSON));}
    private boolean available(){return client.player!=null&&client.level!=null&&client.player.isAlive()&&client.getConnection()!=null;}
    private boolean launchOverlay(){return client.gui.screen() instanceof dev.veltraph.client.ui.VeltraphScreen||client.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen;}
    private boolean loaded(LushlilacTrashSpots.Spot s){return available()&&s.dimension().equals(client.level.dimension().identifier().toString())
            &&client.level.getChunkSource().hasChunk((int)Math.floor(s.position().x())>>4,(int)Math.floor(s.position().z())>>4)&&walk.standingClear(s.position());}
    @Override public Optional<String> preStartCheck(){
        if(!config.get(LushlilacTrashConfig.LUSHLILAC_THROW_TRASH))return Optional.of("Enable the explicit Lushlilac trash permission");
        if(!available()||client.gui.screen()!=null&&!launchOverlay()||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Living native world and empty cursor/closed menus required");
        try {
            if(LushlilacTrashSpots.parse(config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_SPOTS_JSON)).stream().noneMatch(this::loaded))return Optional.of("No loaded, supported configured trash spot");
            var rules=rules();if(rules.stream().anyMatch(r->r.channel()!=AutoSellMode.TRASH))return Optional.of("Only exact TRASH stack rules belong to this flow");
            var observed=access.read("");if(rules.stream().filter(r->r.matches(observed.inventoryStack(r.inventoryIndex()).orElse(null))).count()!=1)return Optional.of("One unique exact configured main-inventory trash stack required");
        }catch(RuntimeException e){return Optional.of("Required local trash profile: "+e.getMessage());}return Optional.empty();
    }
    @Override public void onEnable(){
        var denial=preStartCheck();if(denial.isPresent()){reason=denial.get();setEnabled(false);return;}
        if(launchOverlay())client.gui.setScreen(null);
        release();runtime.reset();access.clear();screen=menu=null;discarded=0;tick=0;
        owner=client.player;world=client.level;connection=client.getConnection();spotsProfile=config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_SPOTS_JSON);rulesProfile=config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_RULES_JSON);
        timeout=Math.max(40,Math.min(1200,config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_TIMEOUT_TICKS)));deadline=timeout;
        var p=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
        spot=LushlilacTrashSpots.choose(LushlilacTrashSpots.parse(spotsProfile),p,this::loaded,java.util.concurrent.ThreadLocalRandom.current()).orElseThrow();
        var observed=access.read("");rule=rules().stream().filter(r->r.matches(observed.inventoryStack(r.inventoryIndex()).orElse(null))).findFirst().orElseThrow();
        settings=new SellingRuntime.Settings(AutoSellMode.TRASH,"","","","",-1,-1,"",64,BigDecimal.ZERO,BigDecimal.ZERO,rule.total(),2,(int)timeout);
        if(!walk.start(spot.position())){fail("No ordinary path to configured trash spot");return;}stage=Stage.TRAVELLING;reason="Walking to source-style selected trash spot";
    }
    private void release(){services.breaking().setBreaking(false);services.input().releaseAllMovement();services.rotation().cancelRotation();walk.stop();}
    private boolean owned(){return available()&&client.player==owner&&client.level==world&&client.getConnection()==connection;}
    private boolean profile(){return config.get(LushlilacTrashConfig.LUSHLILAC_THROW_TRASH)&&Objects.equals(spotsProfile,config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_SPOTS_JSON))
            &&Objects.equals(rulesProfile,config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_RULES_JSON))&&timeout==Math.max(40,Math.min(1200,config.get(LushlilacTrashConfig.LUSHLILAC_TRASH_TIMEOUT_TICKS)));}
    @Override public void onTick(){
        if(!isEnabled())return;tick++;
        if(!owned()||!profile()||!loaded(spot)){fail("Trash ownership/profile or loaded destination changed");return;}
        if(tick>deadline){fail("Trash travel/action timed out");return;}
        if(stage!=Stage.THROWING_ITEMS&&client.gui.screen()!=null){fail("Unexpected screen before inventory stage");return;}
        if(stage==Stage.TRAVELLING){var state=walk.tick();if(state==WalkController.State.BLOCKED){fail("Trash travel failed");return;}
            if(state==WalkController.State.ARRIVED){walk.stop();stage=Stage.AIMING;}return;}
        if(stage==Stage.AIMING){services.rotation().track(spot.yaw(),spot.pitch());
            if(Math.abs(net.minecraft.util.Mth.wrapDegrees(client.player.getYRot()-spot.yaw()))>1.5||Math.abs(client.player.getXRot()-spot.pitch())>1.5)return;
            services.rotation().cancelRotation();client.gui.setScreen(new InventoryScreen(client.player));screen=client.gui.screen();menu=client.player.containerMenu;
            stage=Stage.THROWING_ITEMS;runtime.start(access.read(""),rule,settings,tick);deadline=tick+timeout;return;}
        if(stage==Stage.THROWING_ITEMS){
            // The Selling engine can close its exact owned screen only after the server drop
            // spawn, full inventory decrement, revision and empty cursor have been observed.
            if(client.gui.screen()!=screen&&!(runtime.state()==SellingRuntime.State.CLOSING&&client.gui.screen()==null)) {fail("Trash screen replaced");return;}
            if(client.gui.screen()!=null&&client.player.containerMenu!=menu){fail("Trash native menu replaced");return;}
            var observed=access.read("");var action=runtime.tick(observed,tick);
            if(action.isPresent()){var a=action.get();boolean valid=runtime.valid(a,access.read(""));runtime.applied(a,valid&&access.act(a,rule,""),tick);}
            reason=runtime.reason();
            if(runtime.terminal()){if(runtime.state()==SellingRuntime.State.COMPLETE){discarded=rule.quantity();stage=Stage.COMPLETE;setEnabled(false);}else fail(reason);}
        }
    }
    private void fail(String why){reason=why;stage=Stage.FAILED;setEnabled(false);}
    @Override public void onDisable(){if(stage!=Stage.COMPLETE&&stage!=Stage.FAILED){stage=Stage.CANCELLED;reason="Cancelled";}release();if(owned()&&client.gui.screen()==screen&&client.player.containerMenu==menu&&client.player.containerMenu.getCarried().isEmpty()){
            client.player.closeContainer();client.gui.setScreen(null);}access.clear();}
    @Override public boolean allowsScreen(String title){return isEnabled()&&owned()&&client.gui.screen()==screen&&client.player.containerMenu==menu;}
    public Stage stage(){return stage;}public int discarded(){return discarded;}
    @Override public String diagnostics(){return id()+" | "+stage+" | "+runtime.state()+" | discarded="+discarded+" | "+reason;}
}
