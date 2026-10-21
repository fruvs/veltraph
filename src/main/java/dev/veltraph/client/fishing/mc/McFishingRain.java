package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.support.RainProfile;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.inventory.selling.SellingCurrency;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.service.RotationService;
import dev.veltraph.client.service.mc.McInputService;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Supplier;

/** Source Vanessa walk/talk/bucket/buy/confirm/return route with received weather, timer and purse guards. */
final class McFishingRain {
    enum State { IDLE, WAIT_REEL, WALK, TALK, WAIT_MENU, CHECK, WAIT_BUY, CLOSE, RETURN, AIM, COMPLETE, FAILED }
    private final Minecraft client;private final ConfigService config;private final RotationService rotation;private final Runnable useRod;private final Supplier<GameArea> area;
    private final McFishingTravel travel;private final McNavigator nav;private final McMenuAccess menus;
    private State state=State.IDLE;private RainProfile profile;private String contract,error="";private Object player,world,connection,ownedMenu;
    private LivingEntity npc;private Vec3 anchor;private float yaw,pitch;private int rodSlot,beforeSeconds;private ItemStack rod=ItemStack.EMPTY;
    private long began,requested,lastAttempt;private BigDecimal beforeCoins,quote,spent=BigDecimal.ZERO;private int bought,beforeRevision;private boolean receipt,navOwned,rotOwned;
    private ItemStack predicted=ItemStack.EMPTY;private Object predictedMenu;private dev.veltraph.client.world.Vec3 normalReturnGoal;
    McFishingRain(Minecraft c,ConfigService cfg,RotationService r,Runnable use,Supplier<GameArea> area,dev.veltraph.client.service.FarmServices services){client=c;config=cfg;rotation=r;useRod=use;this.area=area;travel=new McFishingTravel(c,cfg,services,this);nav=new McNavigator(c,new McInputService(c),cfg);menus=new McMenuAccess(c);}
    static String problem(ConfigService cfg){if(!cfg.get(FishingConfig.FISHING_AUTO_RAIN))return "";try{RainProfile.parse(cfg.get(FishingConfig.FISHING_RAIN_PROFILE));return "";}catch(RuntimeException e){return e.getMessage();}}
    boolean active(){return state!=State.IDLE&&state!=State.COMPLETE&&state!=State.FAILED;}
    String status(){return state+" bought="+bought+" cost="+spent;}
    String failure(){return error;}
    boolean screen(String title){return active()&&profile!=null&&MenuSnapshot.clean(title).equals(profile.menuTitle())&&(state==State.WAIT_MENU||state==State.CHECK||state==State.WAIT_BUY||state==State.CLOSE);}
    void message(String text){if(state==State.WAIT_BUY&&profile.receipt(text,quote))receipt=true;}
    private BigDecimal purse(){var scoreboard=client.level.getScoreboard();var objective=scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);if(objective==null)return null;List<String> lines=new ArrayList<>();for(var entry:scoreboard.listPlayerScores(objective)){var team=scoreboard.getPlayersTeam(entry.owner());lines.add(team==null?entry.owner():team.getPlayerPrefix().getString()+entry.owner()+team.getPlayerSuffix().getString());}return SellingCurrency.parse(lines,profile.currencyPrefix());}
    private static ItemStack rodId(ItemStack stack){var copy=stack.copy();copy.remove(DataComponents.DAMAGE);return copy;}
    boolean tick(boolean allow){
        long now=System.currentTimeMillis();boolean was=active();if(client.player==null||client.level==null)return was;
        if(!was){
            if(!allow||!config.get(FishingConfig.FISHING_AUTO_RAIN)||area.get()!=GameArea.THE_PARK||client.level.isRaining()||client.player.isUnderWater()||client.gui.screen()!=null)return false;
            try{profile=RainProfile.parse(config.get(FishingConfig.FISHING_RAIN_PROFILE));}catch(RuntimeException e){fail(e.getMessage());return true;}
            if(now-lastAttempt<profile.minimumCheckIntervalMillis())return false;
            var matches=new ArrayList<LivingEntity>();var origin=new Vec3(profile.anchorX(),profile.anchorY(),profile.anchorZ());
            for(var e:client.level.entitiesForRendering())if(e instanceof LivingEntity living&&e!=client.player&&e.isAlive()&&e.getUUID().toString().equals(profile.npcUuid())&&e.getName().getString().equals(profile.npcName())&&BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(profile.npcType())&&e.position().distanceTo(origin)<3)matches.add(living);
            if(matches.size()!=1)return false;npc=matches.getFirst();contract=config.get(FishingConfig.FISHING_RAIN_PROFILE);player=client.player;world=client.level;connection=client.getConnection();anchor=client.player.position();yaw=client.player.getYRot();pitch=client.player.getXRot();rodSlot=client.player.getInventory().getSelectedSlot();rod=rodId(client.player.getMainHandItem());began=lastAttempt=requested=now;spent=BigDecimal.ZERO;bought=0;receipt=false;
            if(client.player.fishing!=null){state=State.WAIT_REEL;useRod.run();}else startWalk();return true;
        }
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||!client.player.isAlive()||area.get()!=GameArea.THE_PARK||!config.get(FishingConfig.FISHING_AUTO_RAIN)||!Objects.equals(contract,config.get(FishingConfig.FISHING_RAIN_PROFILE))||(client.player.getInventory().getSelectedSlot()!=rodSlot&&!(state==State.RETURN&&travel.rodSelectionCurrent(rodSlot)))||!ItemStack.matches(rod,rodId(client.player.getInventory().getItem(rodSlot)))){fail("Rain player/world/tool/contract changed");return true;}
        if(now-began>60000){fail("Rain route timed out");return true;}
        try{
            switch(state){
                case WAIT_REEL -> {if(client.player.fishing==null)startWalk();else timeout(now,"Rain initial reel not acknowledged");}
                case WALK -> {var value=travel.tick();if(value==WalkController.State.BLOCKED)fail("Vanessa path blocked");else if(value==WalkController.State.ARRIVED){travel.stop();navOwned=false;state=State.TALK;}}
                case TALK -> {
                    if(npc.isRemoved()||!npc.isAlive()||npc.distanceTo(client.player)>3.0||!client.player.hasLineOfSight(npc)){fail("Vanessa interaction is not reachable");break;}
                    var d=npc.getEyePosition().subtract(client.player.getEyePosition());float y=(float)(Math.toDegrees(Math.atan2(d.z,d.x))-90),p=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));rotation.track(y,p);rotOwned=true;
                    if(Math.hypot(net.minecraft.util.Mth.wrapDegrees(y-client.player.getYRot()),p-client.player.getXRot())>5||client.player.getDeltaMovement().length()>.1)break;
                    state=State.WAIT_MENU;requested=now;client.gameMode.interact(client.player,npc,new EntityHitResult(npc,npc.getEyePosition()),InteractionHand.MAIN_HAND);
                }
                case WAIT_MENU -> {var menu=menus.read();if(menu!=null){if(!menu.title().equals(profile.menuTitle())){fail("Unexpected rain menu");break;}ownedMenu=client.player.containerMenu;state=State.CHECK;}else timeout(now,"Vanessa menu not acknowledged");}
                case CHECK -> {
                    var menu=owned();if(menu==null)break;var slot=bucket(menu);beforeSeconds=profile.seconds(slot);beforeCoins=purse();
                    if(beforeSeconds/60>profile.targetMinutes()||bought>=profile.targetMinutes()){state=State.CLOSE;break;}
                    quote=profile.cost(slot);if(beforeCoins==null||beforeCoins.compareTo(quote)<0||spent.add(quote).compareTo(profile.maximumCost())>0)throw new IllegalArgumentException("Rain total quote/purse exceeds contract");
                    if(!client.player.containerMenu.getCarried().isEmpty())throw new IllegalArgumentException("Rain cursor is not empty");
                    beforeRevision=menu.revision();receipt=false;state=State.WAIT_BUY;requested=now;
                    if(!menus.click(menu,slot.id()))throw new IllegalArgumentException("Stale rain quote rejected");predictedMenu=client.player.containerMenu;predicted=client.player.containerMenu.getCarried().copy();
                }
                case WAIT_BUY -> {
                    var menu=owned();if(menu==null)break;var carry=client.player.containerMenu.getCarried();if(!carry.isEmpty()&&!(predictedMenu==ownedMenu&&ItemStack.matches(predicted,carry)))throw new IllegalArgumentException("Rain cursor ownership changed");
                    if(!carry.isEmpty()){timeout(now,"Rain predicted cursor was not cleared");break;}
                    var slot=bucket(menu);var coins=purse();
                    if(receipt&&carry.isEmpty()&&menu.revision()>beforeRevision&&profile.seconds(slot)>=beforeSeconds+60-2&&client.level.isRaining()&&coins!=null&&coins.compareTo(beforeCoins.subtract(quote))==0){spent=spent.add(quote);bought++;state=State.CHECK;predicted=ItemStack.EMPTY;}
                    else timeout(now,"Rain weather/timer/receipt/purse acknowledgment missing");
                }
                case CLOSE -> {var menu=owned();if(menu==null)break;if(!client.player.containerMenu.getCarried().isEmpty())throw new IllegalArgumentException("Rain cursor changed before close");client.player.closeContainer();ownedMenu=null;navOwned=true;normalReturnGoal=nav.position(new dev.veltraph.client.world.BlockPos((int)Math.floor(anchor.x),(int)Math.floor(anchor.y),(int)Math.floor(anchor.z)));if(!travel.start(normalReturnGoal,dev.veltraph.client.fishing.FishingTravelIntent.RAIN_RETURN))throw new IllegalArgumentException("Cannot return from Vanessa");state=State.RETURN;}
                case RETURN -> {var value=travel.tick();if(value==WalkController.State.BLOCKED)fail("Rain return path blocked");else if(value==WalkController.State.ARRIVED){travel.stop();navOwned=false;state=State.AIM;}}
                case AIM -> {rotation.track(yaw,pitch);rotOwned=true;if(Math.hypot(net.minecraft.util.Mth.wrapDegrees(yaw-client.player.getYRot()),pitch-client.player.getXRot())<1){release();state=State.COMPLETE;}}
                default -> {}
            }
        }catch(RuntimeException e){fail(e.getMessage());}
        return true;
    }
    private MenuSnapshot owned(){var current=menus.read();if(current==null||client.player.containerMenu!=ownedMenu||!current.title().equals(profile.menuTitle())){fail("Rain menu ownership changed");return null;}return current;}
    private MenuSnapshot.Slot bucket(MenuSnapshot menu){var slots=menu.slots().stream().filter(s->!s.playerInventory()&&s.name().equals(profile.bucketName())&&s.itemId().equals(profile.bucketId())&&client.player.containerMenu.getSlot(s.id()).getItem().getItem() instanceof BucketItem).toList();if(slots.size()!=1)throw new IllegalArgumentException("Rain bucket is missing or ambiguous");return slots.getFirst();}
    private void startWalk(){var center=npc.blockPosition();dev.veltraph.client.world.BlockPos target=null;double best=Double.MAX_VALUE;for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){var candidate=new dev.veltraph.client.world.BlockPos(center.getX()+x,center.getY(),center.getZ()+z);if(!nav.clear(candidate)||!nav.clear(candidate.up())||!nav.floor(candidate.down()))continue;double distance=Math.hypot(x,z);if(distance<1||distance>2.5)continue;double score=client.player.position().distanceTo(new Vec3(candidate.x()+.5,candidate.y(),candidate.z()+.5));if(score<best){best=score;target=candidate;}}navOwned=true;if(target==null||!travel.start(nav.position(target),dev.veltraph.client.fishing.FishingTravelIntent.VANESSA_APPROACH)){fail("No standing position near Vanessa");return;}state=State.WALK;}
    private void timeout(long now,String text){if(now-requested>4000)fail(text);}
    private void release(){if(navOwned)travel.stop();navOwned=false;if(rotOwned)rotation.cancelRotation();rotOwned=false;}
    private void fail(String text){error=text;state=State.FAILED;release();}
    void stop(){release();if(client.player!=null&&client.level!=null&&client.player==player&&client.level==world&&client.player.containerMenu==ownedMenu&&client.player.containerMenu.getCarried().isEmpty())client.player.closeContainer();if(active()){error="Rain route cancelled";state=State.FAILED;}ownedMenu=player=world=null;npc=null;menus.clear();}
    boolean rebindIdle(){if(active()||navOwned||rotOwned||!failure().isEmpty())return false;ownedMenu=player=world=predictedMenu=null;npc=null;predicted=rod=ItemStack.EMPTY;beforeCoins=quote=null;beforeSeconds=beforeRevision=0;receipt=false;menus.clear();return true;}
    boolean consumeExpectedTeleport(dev.veltraph.client.world.Vec3 position,long now){return active()&&travel.consumeExpectedTeleport(position,now);}
    McFishingTravel.NativeCleanupSource captureNativeCleanupSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return travel.captureNativeCleanupSource(exact);}
    McFishingTravel.NativeReturnSource captureNativeReturnSource(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg exact){return travel.captureNativeReturnSource(exact);}
    Object nativeReturnProfileIdentity(){return profile;}
    Object nativeReturnRuntimeIdentity(){return this;}
    int nativeReturnRodSlot(){return rodSlot;}
    Object nativeReturnCustodyIdentity(){return rod;}
    Object nativeReturnAnchorIdentity(){return anchor;}
    boolean nativeReturnSourceCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        if(client.player==null||client.level==null||rodSlot<0||rodSlot>8)return false;
        boolean semantic=ItemStack.matches(rod,rodId(client.player.getInventory().getItem(rodSlot)));
        return semantic&&nativeReturnSourceRawCurrent(exact,intent,goal,starting);
    }
    /** Stored private phase/source fields only; no stack comparison or codec visitor. */
    boolean nativeReturnSourceRawCurrent(McFishingTravel exact,dev.veltraph.client.fishing.FishingTravelIntent intent,dev.veltraph.client.world.Vec3 goal,boolean starting){
        return exact==travel&&intent==dev.veltraph.client.fishing.FishingTravelIntent.RAIN_RETURN&&goal==normalReturnGoal&&normalReturnGoal!=null
            &&navOwned&&(state==State.RETURN||starting&&state==State.CLOSE)&&ownedMenu==null&&error.isEmpty()&&profile!=null&&contract!=null
            &&client.player==player&&client.level==world&&client.getConnection()==connection&&anchor!=null&&rodSlot>=0&&rodSlot<9&&rod.is(Items.FISHING_ROD);
    }
}
