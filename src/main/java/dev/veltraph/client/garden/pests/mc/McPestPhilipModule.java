package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.garden.pests.philip.*;
import dev.veltraph.client.gui.MenuSnapshot;
import dev.veltraph.client.inventory.abiphone.*;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.rewarp.mc.NativeTeleportSignal;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.*;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import java.util.*;

/** Complete explicit Philip ingress -> native empty-bag exchange -> owned dialogue cleanup. */
public final class McPestPhilipModule extends AbstractModule {
    public static final String ID="garden_pest_philip";
    public enum State { IDLE, CALL, WAIT_COMMAND_MENU, WAIT_BARN, APPROACH, WAIT_MENU, EXCHANGE, COMPLETE, FAILED, CANCELLED }
    private final java.util.function.BooleanSupplier concreteOwnership;private final Minecraft client;private final FarmServices services;private final ConfigService config;private final McAbiphonePort phonePort;private final AbiphoneCall call;
    private final SourceContactCommand command;private final McNavigator navigator;private final McSharedMovementNavigator travel;private final McWorldService terrain;private final PestBagExchange exchange;private final IdentityHashMap<PestBagExchange.Observation,AbiphoneCall.Observation> menuBindings=new IdentityHashMap<>();
    private PestPhilipBindings bindings;private Object player,world,connection,ownedMenu;private RemotePlayer target;private State state=State.IDLE;
    private String frozen="",reason="Idle";private long deadline,teleportSerial;private boolean expectedTeleport,walking;private int exchanged;
    public McPestPhilipModule(Minecraft client,FarmServices services,ConfigService config){this(client,services,config,()->true);}
    public McPestPhilipModule(Minecraft client,FarmServices services,ConfigService config,java.util.function.BooleanSupplier concreteOwnership){super(ID,"Philip pest-bag exchange",ModuleCategory.INTERMEDIARY);this.client=client;this.services=services;this.config=config;this.concreteOwnership=Objects.requireNonNull(concreteOwnership);
        phonePort=new McAbiphonePort(client);call=new AbiphoneCall(phonePort);navigator=new McNavigator(client,services.input(),config);travel=new McSharedMovementNavigator(client,services,config);terrain=new McWorldService(client);
        command=new SourceContactCommand(new SourceContactCommand.Port(){
            public SourceContactCommand.Observation read(){var o=phonePort.read();return new SourceContactCommand.Observation(owner(),o.player(),o.world(),client.getConnection(),o.menu(),o.title(),o.revision(),o.rows(),o.cursorEmpty(),o.inventory(),o.selectedSlot());}
            public boolean send(SourceContactCommand.Observation expected,String value){var fresh=read();return owner()&&expected.live()&&fresh.live()&&expected.player()==fresh.player()&&expected.world()==fresh.world()&&expected.connection()==fresh.connection()&&expected.menu()==null&&fresh.menu()==null&&fresh.cursorEmpty()&&expected.inventory().equals(fresh.inventory())&&expected.selectedSlot()==fresh.selectedSlot()&&new McServerCommandService(client).sendCommand(value);}});
        exchange=new PestBagExchange(new PestBagExchange.Port(){
            public PestBagExchange.Observation read(){return readExchange();}
            public boolean click(PestBagExchange.Observation expected,int slot){var nativeRead=menuBindings.get(expected);return owner()&&nativeRead!=null&&phonePort.click(nativeRead,slot);}
            public boolean close(PestBagExchange.Observation expected){var nativeRead=menuBindings.get(expected);return owner()&&nativeRead!=null&&phonePort.close(nativeRead);}
        });}
    private long tick(){return client.player==null?0:client.player.tickCount;}
    private boolean owner(){return isEnabled()&&concreteOwnership.getAsBoolean()&&config.get(PestPhilipConfig.PEST_PHILIP_ENABLED)&&frozen.equals(config.get(PestPhilipConfig.PEST_PHILIP_BINDINGS))
        &&client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&!client.player.isSpectator();}
    private PestBagExchange.Observation readExchange(){menuBindings.clear();var o=phonePort.read();int bag=bindings==null?-1:PestItems.counter(client,bindings.vacuum(),bindings.bagPrefix()).orElse(-1);int count=bindings==null?0:PestItems.count(client,bindings.vacuum(),false);
        var result=new PestBagExchange.Observation(o.player(),o.world(),client.getConnection(),o.menu(),o.title(),o.revision(),o.rows(),o.cursorEmpty(),o.ownPrediction(),bag,count);menuBindings.put(result,o);return result;}
    @Override public Optional<String> preStartCheck(){
        if(!config.get(PestPhilipConfig.PEST_PHILIP_ENABLED))return Optional.of("Enable Philip exchange explicitly");if(call.active()||call.state()==AbiphoneCall.State.RECOVERY_REQUIRED||call.state()==AbiphoneCall.State.HANDOFF)return Optional.of("Original Abiphone ownership/cleanup remains active");
        if(client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null||!client.player.isAlive()||client.player.isSpectator()||client.gui.screen()!=null||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Healthy loaded owner, closed menus and empty cursor required");
        try{bindings=PestBindings.read(config.get(PestPhilipConfig.PEST_PHILIP_BINDINGS),PestPhilipBindings.class);var bag=PestItems.counter(client,bindings.vacuum(),bindings.bagPrefix());
            if(PestItems.count(client,bindings.vacuum(),false)!=1||bag.isEmpty()||bag.getAsInt()<1||bag.getAsInt()>bindings.exchange().maximumPests())return Optional.of("Exactly one native vacuum and positive bounded bag count required");
            if(bindings.route()==PestPhilipBindings.Route.ABIPHONE_PHONE_LEGACY){long phones=phonePort.read().inventory().stream().filter(i->i.equals(bindings.call().phone())).count();if(phones!=1)return Optional.of("Exactly one observed full-identity Abiphone required");}
            else if(bindings.route()==PestPhilipBindings.Route.BARN_NPC&&(client.player.getAbilities().flying||!client.player.onGround()))return Optional.of("Land before the configured barn request");
        }catch(RuntimeException invalid){return Optional.of("Invalid observed Philip contracts: "+invalid.getMessage());}return Optional.empty();}
    @Override public void onEnable(){var ready=preStartCheck();if(ready.isPresent()){reason=ready.get();state=State.FAILED;setEnabled(false);return;}
        player=client.player;world=client.level;connection=client.getConnection();frozen=config.get(PestPhilipConfig.PEST_PHILIP_BINDINGS);ownedMenu=null;walking=expectedTeleport=false;exchanged=0;release();if(!owner()){fail("Committed concrete Philip action ownership unavailable");return;}
        if(bindings.route()==PestPhilipBindings.Route.ABIPHONE_PHONE_LEGACY){call.start(bindings.call(),tick());state=State.CALL;deadline=tick()+bindings.call().timeoutTicks()*3L;reason="Calling the exact configured Philip contact";}
        else if(bindings.route()==PestPhilipBindings.Route.ABIPHONE_COMMAND){state=State.WAIT_COMMAND_MENU;deadline=tick()+bindings.commandCall().timeoutTicks();if(!command.start(bindings.commandCall(),tick()))fail(command.reason());else reason=command.reason();}
        else{teleportSerial=NativeTeleportSignal.read(client).serial();deadline=tick()+bindings.barn().timeoutTicks();state=State.WAIT_BARN;
            if(!new McServerCommandService(client).sendCommand(bindings.barn().command()))fail("Configured ordinary barn request refused");else reason="Waiting for received native barn arrival";}}
    private String ownerFacts(){return "enabled="+isEnabled()+",concrete="+concreteOwnership.getAsBoolean()+",optIn="+config.get(PestPhilipConfig.PEST_PHILIP_ENABLED)+",binding="+frozen.equals(config.get(PestPhilipConfig.PEST_PHILIP_BINDINGS))+",player="+(client.player==player)+",world="+(client.level==world)+",connection="+(client.getConnection()==connection)+",alive="+(client.player!=null&&client.player.isAlive())+",spectator="+(client.player!=null&&client.player.isSpectator());}
    @Override public void onTick(){if(!owner()){fail("Philip owner/settings/connection changed: "+ownerFacts());return;}if(tick()>deadline){fail("Philip ingress or exchange timed out; no retry");return;}
        switch(state){
            case CALL->{call.tick(tick());if(call.state()==AbiphoneCall.State.HANDOFF){ownedMenu=call.handoff().orElseThrow().menu();beginExchange();}
                else if(call.terminal())fail("Philip call failed: "+call.reason());}
            case WAIT_COMMAND_MENU->{command.tick(tick());if(command.ownedMenu()!=null)ownedMenu=command.ownedMenu();if(command.state()==SourceContactCommand.State.HANDOFF){if(!command.handoffValid()){fail("Current source command dialogue changed before exchange handoff");return;}beginExchange();}else if(command.state()==SourceContactCommand.State.FAILED)fail(command.reason());}
            case WAIT_BARN->{if(client.gui.screen()!=null){fail("Unrelated menu interrupted barn arrival");return;}var receipt=NativeTeleportSignal.read(client);if(receipt.serial()<=teleportSerial)return;
                if(receipt.position()==null||receipt.position().distanceTo(bindings.barn().spawn())>1){fail("Native barn request arrived at the wrong configured spawn");return;}
                var feet=position();if(feet.distanceTo(bindings.barn().spawn())>1||!GardenPlotBounds.contains(0,feet.x(),feet.z())||!client.player.onGround()||!terrain.bodyClear(feet,.3,1.8)||!terrain.hasGroundSupport(feet,.3))return;
                state=State.APPROACH;deadline=tick()+bindings.exchange().timeoutTicks();reason="Locating the exact loaded Philip profile";}
            case APPROACH->approach();
            case WAIT_MENU->{var o=phonePort.read();if(o.menu()==null)return;if(!o.title().equals(bindings.exchange().title())){fail("Unrelated NPC menu preserved");return;}ownedMenu=o.menu();beginExchange();}
            case EXCHANGE->{exchange.tick(tick());if(exchange.state()==PestBagExchange.State.COMPLETE){exchanged=exchange.exchanged();
                    if(bindings.route()==PestPhilipBindings.Route.ABIPHONE_PHONE_LEGACY&&!call.releaseHandoff()){fail("Philip exchange succeeded but original phone handoff could not be released");return;}
                    if(bindings.route()==PestPhilipBindings.Route.ABIPHONE_COMMAND&&!command.release()){fail("Completed Philip command dialogue handoff could not be released");return;}
                    state=State.COMPLETE;reason="Native Philip empty-bag exchange completed and owned dialogue closed";setEnabled(false);}
                else if(!exchange.active())fail(exchange.reason());}
            default->{}
        }}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean targetValid(){return target!=null&&client.level.getEntity(target.getId())==target&&target.isAlive()&&target.getUUID().equals(bindings.npc().uuid())&&MenuSnapshot.clean(target.getName().getString()).equals(bindings.npc().name())&&GardenPlotBounds.contains(0,target.getX(),target.getZ());}
    private void approach(){if(client.gui.screen()!=null){fail("Unrelated menu before Philip interaction");return;}
        if(target==null){var matches=new ArrayList<RemotePlayer>();for(var e:client.level.entitiesForRendering())if(e instanceof RemotePlayer npc&&npc.getUUID().equals(bindings.npc().uuid())&&MenuSnapshot.clean(npc.getName().getString()).equals(bindings.npc().name())&&GardenPlotBounds.contains(0,npc.getX(),npc.getZ()))matches.add(npc);
            if(matches.size()!=1){fail("Exact source RemotePlayer Philip profile missing/ambiguous");return;}target=matches.getFirst();}
        if(!targetValid()){fail("Observed Philip profile replaced");return;}
        if(client.player.distanceTo(target)>2.8||!client.player.hasLineOfSight(target)){if(!walking){var p=target.blockPosition();for(int[] off:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if(travel.start(navigator.position(new dev.veltraph.client.world.BlockPos(p.getX()+off[0],p.getY(),p.getZ()+off[1])),.35,SharedMovementPolicy.Purpose.GENERAL_TRAVEL)){walking=true;break;}
                if(!walking){fail("No loaded supported route to Philip");return;}}var status=travel.tick();if(status==McTravelNavigator.State.FAILED){fail("Philip route blocked");return;}if(status==McTravelNavigator.State.ARRIVED)walking=false;return;}
        travel.stop();walking=false;var d=target.getBoundingBox().getCenter().subtract(client.player.getEyePosition());float yaw=(float)Math.toDegrees(Math.atan2(-d.x,d.z));float pitch=(float)-Math.toDegrees(Math.atan2(d.y,Math.hypot(d.x,d.z)));services.rotation().track(yaw,pitch);
        if(Math.abs(WalkController.wrap(client.player.getYRot()-yaw))>2||Math.abs(client.player.getXRot()-pitch)>2)return;
        if(!(client.hitResult instanceof EntityHitResult hit)||hit.getEntity()!=target)return;
        client.gameMode.interact(client.player,target,hit,InteractionHand.MAIN_HAND);services.rotation().cancelRotation();state=State.WAIT_MENU;reason="One exact native Philip NPC interaction";}
    private void beginExchange(){if(!exchange.start(bindings.exchange(),tick())){fail(exchange.reason());return;}state=State.EXCHANGE;deadline=tick()+bindings.exchange().timeoutTicks()+1;reason=exchange.reason();}
    private void fail(String message){state=State.FAILED;reason=message;if(isEnabled())setEnabled(false);}
    private void release(){travel.stop();services.rotation().cancelRotation();services.input().releaseAllMovement();services.breaking().setBreaking(false);McHeldKeys.setHeld(client.options.keyUse,false);McHeldKeys.setHeld(client.options.keyAttack,false);}
    @Override public void onDisable(){if(state!=State.COMPLETE&&state!=State.FAILED){state=State.CANCELLED;reason="Philip exchange stopped";}exchange.cancel();command.cancel();release();
        if(call.state()==AbiphoneCall.State.HANDOFF){call.releaseHandoff();}else if(call.active())call.cancel();
        if(client.player==player&&client.level==world&&client.getConnection()==connection&&ownedMenu!=null&&client.player.containerMenu==ownedMenu){var o=phonePort.read();if(o.cursorEmpty()&&o.title().equals(bindings.exchange().title())&&o.rows().stream().filter(r->!r.playerInventory()&&!r.locked()&&r.item().equals(bindings.exchange().marker())).count()==1)phonePort.close(o);}
        target=null;ownedMenu=null;}
    @Override public boolean hasPendingCleanup(){return !isEnabled()&&(call.active()||call.recoveryReservesCurrentContext()||call.state()==AbiphoneCall.State.HANDOFF);}
    @Override public void maintenanceTick(){if(!isEnabled()&&call.active()){release();call.tick(tick());}}
    @Override public void onServerMessage(String line){call.onServerMessage(line);command.message(line);exchange.message(line);}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now){if(state==State.APPROACH&&owner()&&tick()<=deadline&&walking&&travel.consumeExpectedTeleport(position,now))return true;var receipt=NativeTeleportSignal.read(client);if(state!=State.WAIT_BARN||!owner()||tick()>deadline||expectedTeleport||receipt.serial()<=teleportSerial||receipt.position()==null||position==null||receipt.position().distanceTo(bindings.barn().spawn())>1||position.distanceTo(bindings.barn().spawn())>1)return false;expectedTeleport=true;return true;}
    @Override public boolean allowsScreen(String title){return isEnabled()&&bindings!=null&&(bindings.exchange().title().equals(MenuSnapshot.clean(title))||bindings.route()==PestPhilipBindings.Route.ABIPHONE_PHONE_LEGACY&&bindings.call().allowsTitle(MenuSnapshot.clean(title)));}
    @Override public boolean requiresSkyBlock(){return true;}@Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GARDEN);}
    /** Read-only acknowledgment after the operator restored the exact original phone pair and selection. */
    public boolean acknowledgeManualRecovery(){return !isEnabled()&&call.acknowledgeManualRecovery();}
    public State workflowState(){return state;}public int exchanged(){return exchanged;}public int exchangeClicks(){return exchange.clicks();}public int contactCommands(){return command.requests();}
    @Override public String diagnostics(){return state+": "+reason+" | "+call.state()+": "+call.reason()+" | "+exchange.state()+": "+exchange.reason()+" | "+command.state()+": "+command.reason();}
}
