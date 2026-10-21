package dev.veltraph.client.combat.grind;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.inventory.NativeStackIdentity;
import dev.veltraph.client.fishing.FishingManaParser;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.scores.DisplaySlot;

/** Source base arena choreography; coordinates/commands/receipts are explicit local contracts. */
public final class ZealotArenaWorkflow {
    public enum Stage { TRY_COMMAND,PRE_SCRIPTING,GET_TO_GATE,CHECK_SPOTS,GO_TO_SPOT,FARMING,LOOT,RETURN_SPOT,SHUTDOWN,DRAGON_RETREAT,DRAGON_WAIT,FAILED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final LocalRouteStore store;private final GameContextService context;private final Runnable quiesce;
    private final ZealotArenaProfile profile;private final String authorized;private final List<LocalRoute> routes;private McTravelNavigator travel;
    private NativeStackIdentity.Snapshot idleWeapon;private boolean idleRotationPending,idleRotationRevoked;private long idleRotationEpoch;private Object idleRotationAuthority;
    private final ZealotArenaFailsafes failsafes=new ZealotArenaFailsafes();
    private final PrecursorBeamCycle beam=new PrecursorBeamCycle();private NativeStackIdentity.Snapshot helmet;
    private Object owner,world,connection;private UUID playerId;private Stage stage=Stage.TRY_COMMAND;private LocalRoute active;private int node,spot,arrivals,dragonYields;
    private boolean pathStarted,warpIssued,warpReceived,cancelled;private long started,lastDragon,manaAt,now;private int mana=-1;private PrecursorBeamCycle.State lastLore=PrecursorBeamCycle.State.UNKNOWN;
    private String status="Arena ready";private Stage afterShutdown=Stage.FARMING;
    public ZealotArenaWorkflow(Minecraft client,FarmServices services,ConfigService config,LocalRouteStore store,GameContextService context,Runnable quiesce){
        this.client=client;this.services=services;this.config=config;this.store=store;this.context=context;this.quiesce=quiesce;authorized=config.get(ZealotConfig.ZEALOT_ARENA_PROFILE);profile=ZealotArenaProfile.parse(authorized);routes=profile.load(store);travel=new McTravelNavigator(client,services,config);
    }
    public boolean consumeExpectedTeleport(Vec3 position,long time){return travel.consumeExpectedTeleport(position,time);}
    public ZealotArenaProfile profile(){return profile;}public boolean precursor(){return profile.mode()==ZealotArenaProfile.Mode.PRECURSOR;}
    public boolean expectedTransfer(){return warpIssued&&!warpReceived&&stage==Stage.PRE_SCRIPTING&&System.currentTimeMillis()-started<=profile.timeoutMs();}
    public int preferredSlot(){
        if(precursor())return config.get(ZealotConfig.ZEALOT_WEAPON_SLOT)-1;
        if(client.player==null)return -1;
        for(String name:profile.preferredBows())for(int i=0;i<9;i++){var s=client.player.getInventory().getItem(i);if(s.is(net.minecraft.world.item.Items.BOW)&&clean(s.getHoverName().getString()).contains(name))return i;}return -1;
    }
    public Optional<String> readiness(){
        var p=client.player;var c=context.current();if(p==null||client.level==null||!p.isAlive()||!c.inSkyBlock()||!profile.startAreas().contains(c.area()))return Optional.of("Observed permitted Hub/End arena ingress required");
        if(!profile.permitsStart(c.area(),client.level.dimension().identifier().toString()))return Optional.of("Arena dimension mismatch");
        if(routes.stream().flatMap(r->r.nodes().stream()).anyMatch(n->n.kind()==LocalRoute.Kind.ETHERWARP)&&!travel.hasEtherwarpTool())return Optional.of("Configured Arena route requires native Ether Transmission hotbar tool");
        if(preferredSlot()<0||preferredSlot()>8||p.getInventory().getItem(preferredSlot()).isEmpty())return Optional.of("Missing explicitly ordered preferred arena weapon");
        if(precursor())try{if(!expectedHelmet().matches(normalHelmet(),client.level.registryAccess()))return Optional.of("Exact configured native Precursor helmet/ability required");if(loreState()==PrecursorBeamCycle.State.UNKNOWN)return Optional.of("Fresh explicit native beam ON/OFF lore required");}catch(RuntimeException error){return Optional.of("Invalid native helmet identity: "+error.getMessage());}
        return Optional.empty();
    }
    private NativeStackIdentity.Snapshot expectedHelmet(){var expected=ItemStack.CODEC.parse(client.level.registryAccess().createSerializationContext(com.mojang.serialization.JsonOps.INSTANCE),profile.beam().helmet()).getOrThrow();return NativeStackIdentity.capture(normalWear(expected),client.level.registryAccess());}
    private ItemStack normalHelmet(){
        var stack=client.player.getItemBySlot(EquipmentSlot.HEAD).copy();var lore=stack.get(DataComponents.LORE);if(lore==null||lore.lines().stream().noneMatch(l->clean(l.getString()).equals(profile.beam().abilityLine())))throw new IllegalArgumentException("Beam ability lore absent");
        stack.set(DataComponents.LORE,new ItemLore(lore.lines().stream().filter(l->!clean(l.getString()).equals(profile.beam().offLore())&&!clean(l.getString()).equals(profile.beam().onLore())).toList()));return normalWear(stack);
    }
    /** Durability is mutable gameplay state; every other native component remains identity. */
    private static ItemStack normalWear(ItemStack stack){
        if(stack.isDamageableItem()){
            Integer damage=stack.get(DataComponents.DAMAGE);
            if(damage==null||damage<0||damage>=stack.getMaxDamage())throw new IllegalArgumentException("Invalid/broken native helmet durability");
            stack.setDamageValue(0);
        }return stack;
    }
    private PrecursorBeamCycle.State loreState(){
        var lore=client.player.getItemBySlot(EquipmentSlot.HEAD).get(DataComponents.LORE);if(lore==null)return PrecursorBeamCycle.State.UNKNOWN;
        long off=lore.lines().stream().filter(l->clean(l.getString()).equals(profile.beam().offLore())).count(),on=lore.lines().stream().filter(l->clean(l.getString()).equals(profile.beam().onLore())).count();
        return off==1&&on==0?PrecursorBeamCycle.State.OFF:on==1&&off==0?PrecursorBeamCycle.State.ON:PrecursorBeamCycle.State.UNKNOWN;
    }
    public void start(){failsafes.reset();idleRotationPending=idleRotationRevoked=false;owner=client.player;world=client.level;connection=client.getConnection();playerId=client.player.getUUID();started=System.currentTimeMillis();stage=Stage.TRY_COMMAND;if(precursor()){helmet=expectedHelmet();lastLore=loreState();beam.observe(lastLore);if(lastLore==PrecursorBeamCycle.State.ON){afterShutdown=Stage.TRY_COMMAND;stage=Stage.SHUTDOWN;beam.request(false,started);}}status=stage.name();}
    /** Only the one owned command's exact received area/landing may refresh native refs. */
    public boolean ownedWarpArrival(){return warpIssued&&warpReceived&&stage==Stage.PRE_SCRIPTING&&client.player!=null&&client.level!=null&&client.getConnection()==connection&&client.player.getUUID().equals(playerId)&&context.current().isArea(GameArea.THE_END)&&client.level.dimension().identifier().toString().equals(profile.dimension())&&point().distanceTo(profile.warpLanding())<1&&System.currentTimeMillis()-started<=profile.timeoutMs();}
    public void rebindExpectedWarp(){owner=client.player;world=client.level;travel=new McTravelNavigator(client,services,config);}
    public void rebindRest(){idleRotationPending=idleRotationRevoked=false;owner=client.player;world=client.level;connection=client.getConnection();travel=new McTravelNavigator(client,services,config);pathStarted=false;active=null;if(precursor()){lastLore=loreState();beam.reset();beam.observe(lastLore);}mana=-1;manaAt=0;}
    public void actionBar(String text){var current=FishingManaParser.parse(text);if(current.isPresent()){mana=current.getAsInt();manaAt=System.currentTimeMillis();}}
    public void message(String text){String line=clean(text);if(stage==Stage.PRE_SCRIPTING&&warpIssued){if(line.equals(profile.warpReceipt()))warpReceived=true;if(line.equals(profile.warpRefusal()))fail("Owned End warp refused; no retry");}
        // Chat names a receipt, but the current exact native helmet lore must acknowledge its state.
        if(precursor()&&sameContext()&&helmetOwned()){var nativeState=loreState();if(line.equals(profile.beam().onReceipt())&&nativeState==PrecursorBeamCycle.State.ON)beam.observe(nativeState);if(line.equals(profile.beam().offReceipt())&&nativeState==PrecursorBeamCycle.State.OFF)beam.observedOff();}}
    private boolean sameContext(){return client.player==owner&&client.level==world&&client.getConnection()==connection;}
    private boolean helmetOwned(){try{return sameContext()&&helmet.matches(normalHelmet(),client.level.registryAccess());}catch(RuntimeException e){return false;}}
    private boolean manaFresh(){return mana>=0&&now>=manaAt&&now-manaAt<=profile.beam().manaFreshMs();}
    private void beamTick(){
        if(!precursor())return;if(!helmetOwned()){fail("Beam helmet/current native identity changed; manual recovery required");return;}
        var state=loreState();if(state==PrecursorBeamCycle.State.UNKNOWN){fail("Beam effect lore unknown/replaced");return;}if(state!=lastLore){lastLore=state;if(state==PrecursorBeamCycle.State.OFF)beam.observedOff();else beam.observe(state);}
        services.input().setSneakHeld(beam.sneak(now,profile.beam().ackMs()));if(beam.failed())fail(beam.status());
    }
    public boolean safelyOff(){return !precursor()||beam.safelyOff()&&lastLore==PrecursorBeamCycle.State.OFF;}
    private void shutdown(Stage next){idleRotationPending=false;quiesce.run();if(precursor())beam.interruptPulse();afterShutdown=next;stage=Stage.SHUTDOWN;if(precursor()&&beam.state()==PrecursorBeamCycle.State.ON&&!beam.pending())beam.request(false,now);}
    public boolean beforeQuiver(){if(safelyOff())return true;shutdown(Stage.FARMING);return false;}
    public boolean beginLoot(){if(stage==Stage.LOOT)return true;if(!safelyOff()){shutdown(Stage.LOOT);return false;}stage=Stage.LOOT;return true;}
    public void finishLoot(){quiesce.run();stage=Stage.RETURN_SPOT;active=null;pathStarted=false;started=now;}
    public boolean safePause(){return !failsafes.pending()&&stage==Stage.FARMING&&safelyOff()&&!beam.pending();}
    public boolean hasCleanup(){return cancelled&&precursor()&&!safelyOff()&&sameContext();}
    public void cancel(){idleRotationPending=false;failsafes.reset();cancelled=true;travel.stop();quiesce.run();if(precursor())beam.interruptPulse();if(precursor()&&!safelyOff()){afterShutdown=Stage.FAILED;stage=Stage.SHUTDOWN;if(beam.state()==PrecursorBeamCycle.State.ON&&!beam.pending())beam.request(false,System.currentTimeMillis());}else stage=Stage.FAILED;}
    public void cleanupTick(){if(!hasCleanup()||!sameContext()||client.player==null||!client.player.isAlive()||client.gui.screen()!=null)return;now=System.currentTimeMillis();beamTick();if(beam.state()==PrecursorBeamCycle.State.ON&&!beam.pending()&&!beam.failed())beam.request(false,now);}
    public boolean step(){
        now=System.currentTimeMillis();if(cancelled)return false;
        if(!authorized.equals(config.get(ZealotConfig.ZEALOT_ARENA_PROFILE))||!config.get(ZealotConfig.ZEALOT_ARENA_ENABLED)){fail("Arena configuration changed");return false;}
        if(client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty()){fail("Unowned arena menu/cursor");return false;}
        if(precursor())beamTick();if(stage==Stage.FAILED)return false;
        boolean dragon=dragon();
        var decision=failsafes.tick(client.player.tickCount,stage==Stage.FARMING,
                config.get(ZealotConfig.PRECURSOR_DRAGON_FAILSAFE),dragon,
                config.get(ZealotConfig.PRECURSOR_NOT_AT_POS_FAILSAFE),stage==Stage.FARMING,
                expectedTransfer(),client.player.onGround(),stage==Stage.FARMING?point().distanceTo(anchor().position()):0);
        if(decision==ZealotArenaFailsafes.Decision.DISPLACEMENT_STOP){fail("Arena displacement failsafe: original fifteen-tick stop");return false;}
        if(decision==ZealotArenaFailsafes.Decision.DISPLACEMENT_PENDING){
            if(stage!=Stage.SHUTDOWN){shutdown(Stage.FARMING);} // Stop combat now; keep native OFF cleanup.
            if(precursor()&&beam.state()==PrecursorBeamCycle.State.ON&&!beam.pending()&&!beam.failed())beam.request(false,now);
            status="Arena displacement failsafe: pending fifteen-tick stop";return false;
        }
        if(decision==ZealotArenaFailsafes.Decision.DRAGON_STOP){
            // Detection/cadence are recovered. Retreat remains an explicit local gameplay contract;
            // the source's salted staged-exit delay and mimic actions are not reproduced.
            lastDragon=now;dragonYields++;shutdown(Stage.DRAGON_RETREAT);
        }
        if(dragon&&(stage==Stage.DRAGON_RETREAT||stage==Stage.DRAGON_WAIT))lastDragon=now;
        switch(stage){
            case TRY_COMMAND -> {quiesce.run();if(context.current().isArea(GameArea.THE_END)){stage=Stage.GET_TO_GATE;active=null;started=now;}else{warpIssued=new McServerCommandService(client).sendCommand(profile.warpCommand());if(!warpIssued){fail("Explicit End ingress command not issued");return false;}stage=Stage.PRE_SCRIPTING;started=now;}}
            case PRE_SCRIPTING -> {if(ownedWarpArrival()){rebindExpectedWarp();stage=Stage.GET_TO_GATE;active=null;started=now;}else if(now-started>profile.timeoutMs())fail("Owned End ingress receipt/area/landing not acknowledged");}
            case GET_TO_GATE -> {if(follow(routes.getFirst())){stage=Stage.CHECK_SPOTS;active=null;started=now;}}
            case CHECK_SPOTS -> {int chosen=-1;for(int i=0;i<profile.spots().size();i++)if(spotFree(i)){chosen=i;break;}if(chosen>=0){spot=chosen;stage=Stage.GO_TO_SPOT;active=null;started=now;}else if(now-started>profile.timeoutMs())fail("No unoccupied configured arena spot");}
            case GO_TO_SPOT,RETURN_SPOT -> {if(follow(routes.get(spot+1))){stage=Stage.FARMING;active=null;started=now;}}
            case FARMING -> {if(!context.current().isArea(GameArea.THE_END)){fail("Arena area lost");return false;}if(profile.checkPlayers()&&!spotFree(spot)){shutdown(Stage.DRAGON_RETREAT);return false;}
                if(precursor()&&(!manaFresh()||mana<profile.beam().manaLow())){if(!safelyOff())shutdown(Stage.FARMING);status="TIMEOUT: awaiting fresh mana above upper bound";return false;}return true;}
            case LOOT -> {return safelyOff();}
            case SHUTDOWN -> {if(safelyOff()){stage=afterShutdown;active=null;pathStarted=false;started=now;}else if(beam.state()==PrecursorBeamCycle.State.ON&&!beam.pending()&&!beam.failed())beam.request(false,now);}
            case DRAGON_RETREAT -> {if(follow(routes.getLast())){stage=Stage.DRAGON_WAIT;active=null;}}
            case DRAGON_WAIT -> {if(!dragon&&now-lastDragon>=profile.dragonQuietMs()){stage=Stage.GET_TO_GATE;active=null;started=now;}}
            case FAILED -> {}
        }status=stage+": "+(precursor()?beam.status():"local arena route");return false;
    }
    private boolean follow(LocalRoute r){if(now-started>profile.timeoutMs()){fail("Arena physical route timed out");return false;}if(active!=r){active=r;node=0;pathStarted=false;}if(!pathStarted){pathStarted=travel.start(r.nodes().get(node));if(!pathStarted){fail("Arena route failed: "+travel.status());return false;}}var result=travel.tick();if(result==McTravelNavigator.State.FAILED){fail("Arena route failed: "+travel.status());return false;}if(result==McTravelNavigator.State.ARRIVED){arrivals++;pathStarted=false;if(++node==r.nodes().size()){return true;}}return false;}
    private boolean spotFree(int i){if(!profile.checkPlayers())return true;Vec3 p=routes.get(i+1).nodes().getLast().position();return client.level.players().stream().noneMatch(other->other!=client.player&&new Vec3(other.getX(),other.getY(),other.getZ()).distanceTo(p)<=3);}
    private boolean dragon(){for(var e:client.level.entitiesForRendering())if(e instanceof EnderDragon&&e.isAlive())return true;if(profile.dragonMarker().isBlank())return false;var board=client.level.getScoreboard();var objective=board.getDisplayObjective(DisplaySlot.SIDEBAR);if(objective==null)return false;return board.listPlayerScores(objective).stream().anyMatch(s->{String name=s.owner();var team=board.getPlayersTeam(name);return clean(team==null?name:team.getPlayerPrefix().getString()+name+team.getPlayerSuffix().getString()).equals(profile.dragonMarker());});}
    /** Called only by the no-target owner after loot, quiver, target selection and beam shutdown. */
    public boolean idleLookAround(boolean entityAction){
        dev.veltraph.client.service.mc.McOwnedRotation.validate(client);
        Object authority=dev.veltraph.client.service.mc.McOwnedRotation.authority(client);
        long epoch=dev.veltraph.client.service.mc.McOwnedRotation.epoch(client);
        if(idleRotationPending&&(authority!=idleRotationAuthority||epoch!=idleRotationEpoch)){idleRotationPending=false;idleRotationRevoked=true;return false;}
        if(idleRotationRevoked||stage!=Stage.FARMING||failsafes.pending()||!sameContext()||!client.player.isAlive()
                ||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty()
                ||client.player.getInventory().getSelectedSlot()!=preferredSlot()
                ||!dev.veltraph.client.service.mc.McOwnedRotation.installed(client)||authority==null)return false;
        if(idleRotationPending&&!config.get(ZealotConfig.PRECURSOR_LOOK_AROUND)){idleRotationPending=false;services.rotation().cancelRotation();return false;}
        if(idleRotationPending){
            if(idleWeapon==null||!idleWeapon.matches(client.player.getInventory().getItem(preferredSlot()),client.level.registryAccess())){idleRotationPending=false;idleRotationRevoked=true;services.rotation().cancelRotation();return false;}
            return true;
        }
        boolean anotherTargetPending=Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(
                dev.veltraph.client.service.mc.McOwnedRotation.requestedYaw(client)-client.player.getYRot()))>.001
                ||Math.abs(dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client)-client.player.getXRot())>.001;
        var target=ZealotArenaIdleScan.target(config.get(ZealotConfig.PRECURSOR_LOOK_AROUND),false,entityAction,
                anotherTargetPending,safelyOff()&&!beam.pending(),client.player.getYRot(),client.player.getXRot(),
                java.util.concurrent.ThreadLocalRandom.current());
        if(target.isEmpty())return false;
        idleWeapon=NativeStackIdentity.capture(client.player.getInventory().getItem(preferredSlot()),client.level.registryAccess());
        idleRotationPending=true;idleRotationAuthority=authority;idleRotationEpoch=epoch;
        services.rotation().rotateTo(target.get().yaw(),target.get().pitch(),()->idleRotationPending=false);
        return true;
    }
    public void targetSelected(){if(idleRotationPending){idleRotationPending=false;services.rotation().cancelRotation();}}
    public void beamTarget(boolean safe){if(!precursor())return;if(!safe){if(!safelyOff())shutdown(Stage.FARMING);return;}if(beam.state()==PrecursorBeamCycle.State.OFF&&manaFresh()&&mana>profile.beam().manaHigh()&&!beam.pending())beam.request(true,now);}
    public boolean beamMaintaining(){return precursor()&&!beam.safelyOff();}
    public boolean beamActive(){return precursor()&&beam.state()==PrecursorBeamCycle.State.ON&&lastLore==PrecursorBeamCycle.State.ON&&!beam.pending();}
    public long cooldown(ItemStack item){if(profile.cooldownPrefix().isBlank())return 500;var lore=item.get(DataComponents.LORE);if(lore==null)return 500;Long found=null;for(var line:lore.lines()){String s=clean(line.getString());if(!s.startsWith(profile.cooldownPrefix()))continue;String[] parts=s.split(" ");try{double seconds=Double.parseDouble(parts[parts.length-1].replace("s",""));if(!Double.isFinite(seconds)||seconds<=0||seconds>60||found!=null)throw new IllegalArgumentException("Ambiguous arena bow cooldown");found=(long)(seconds*1000);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid observed arena bow cooldown");}}return found==null?500:found;}
    public LocalRoute.Node anchor(){return routes.get(spot+1).nodes().getLast();}public boolean failed(){return stage==Stage.FAILED;}public int arrivals(){return arrivals;}public int dragonYields(){return dragonYields;}public Stage stage(){return stage;}public String status(){return status;}
    public String routeSignature(){try{return profile.load(store).toString();}catch(RuntimeException e){return "unavailable route proof";}}
    private void fail(String reason){status=reason;stage=Stage.FAILED;travel.stop();quiesce.run();}
    private Vec3 point(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private static String clean(String s){return s==null?"":s.replaceAll("§.","").strip();}
}
