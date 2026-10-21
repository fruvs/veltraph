package dev.veltraph.client.foraging.hub;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.game.mc.McGameContextService;
import dev.veltraph.client.service.ServerCommandService;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
/** Sends only an explicitly configured gameplay command using vanilla command service. */
public final class McHubWarpAccess implements McHubSourceLoop.WarpAccess {
    private final Minecraft client;private final ConfigService config;private final ServerCommandService commands;
    private final McGameContextService context;private final HubWarpCycle cycle=new HubWarpCycle();
    public McHubWarpAccess(Minecraft client,ConfigService config){this.client=client;this.config=config;commands=new McServerCommandService(client);context=new McGameContextService(client);}
    public boolean permittedPendingLoading(){
        thread();var connection=client.getConnection();var screen=client.gui.screen();
        Object listenerTracker=connection instanceof dev.veltraph.client.mixin.ScheduledClientLevelLoadAccessor listener?listener.veltraph$levelLoadTracker():null;
        Object screenTracker=screen instanceof dev.veltraph.client.mixin.ScheduledLevelLoadingScreenAccessor loading?loading.veltraph$loadTracker():null;
        return cycle.permitsLoadingScreen(connection,screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen,listenerTracker,screenTracker,System.currentTimeMillis());
    }
    private HubWarpCycle.Observation observation(){
        var p=client.player;var world=client.level;boolean closedScreen=client.gui.screen()==null;
        boolean healthy=(p==null||p.isAlive())&&(closedScreen||permittedPendingLoading())
            &&(p==null||p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty());
        boolean bodyReady=p!=null&&world!=null&&p.level()==world&&closedScreen&&healthy
            &&world.getChunkSource().hasChunk(p.blockPosition().getX()>>4,p.blockPosition().getZ()>>4)&&p.onGround()&&world.noCollision(p,p.getBoundingBox());
        return new HubWarpCycle.Observation(p,client.getConnection(),world,world==null?"":world.dimension().identifier().toString(),
            p==null?null:new Vec3(p.getX(),p.getY(),p.getZ()),context.current().isArea(GameArea.HUB),healthy,p==null?null:p.getUUID(),bodyReady,p==null?Long.MIN_VALUE:p.tickCount);
    }
    private HubWarpCycle.Contract contract(){
        String raw=config.get(HubForagingConfig.HUB_WARP_DESTINATION);Vec3 destination=null;
        try {var fields=raw.split(",",-1);if(fields.length==3)destination=new Vec3(Double.parseDouble(fields[0].trim()),Double.parseDouble(fields[1].trim()),Double.parseDouble(fields[2].trim()));}catch(RuntimeException ignored){}
        String command=config.get(HubForagingConfig.HUB_WARP_COMMAND).trim();while(command.startsWith("/"))command=command.substring(1).trim();
        Set<String> rejected=new HashSet<>();for(String line:config.get(HubForagingConfig.HUB_WARP_REJECTED_MESSAGES).split("\\R"))if(!line.isBlank())rejected.add(line);
        return new HubWarpCycle.Contract(command,config.get(HubForagingConfig.HUB_WARP_ACCEPTED_MESSAGE),rejected,config.get(HubForagingConfig.HUB_WARP_DIMENSION),destination,config.get(HubForagingConfig.HUB_WARP_DESTINATION_RADIUS),config.get(HubForagingConfig.HUB_WARP_TIMEOUT_MS));
    }
    public boolean request(){thread();var bound=contract();if(!cycle.start(bound,observation(),System.currentTimeMillis(),dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client).serial()))return false;cycle.sent(commands.sendCommand(bound.command()));return cycle.state()==HubWarpCycle.State.WAITING;}
    public HubSourceLoop.Motion motion(){thread();if(!configurationMatches()){cycle.cancel();return HubSourceLoop.Motion.FAILED;}long now=System.currentTimeMillis();var observed=observation();observeReceipt(observed,now);cycle.tick(observed,now);cycle.nextCommand(observed,now).ifPresent(command->cycle.sent(commands.sendCommand(command)));return switch(cycle.state()){case WAITING -> HubSourceLoop.Motion.ACTIVE;case ARRIVED -> HubSourceLoop.Motion.ARRIVED;default -> HubSourceLoop.Motion.FAILED;};}
    public void feedback(String message){thread();if(!configurationMatches()){cycle.cancel();return;}cycle.feedback(observation(),message,System.currentTimeMillis());}
    public boolean consumeExpectedTeleport(Vec3 position,long now){thread();if(!configurationMatches()){cycle.cancel();return false;}return cycle.consumeExpectedTeleport(observation(),position,receipt(),now);}
    public McHubSourceLoop.OwnerTransition expectedOwnerTransition(){
        thread();if(!configurationMatches()){cycle.cancel();return McHubSourceLoop.OwnerTransition.DENIED;}
        long now=System.currentTimeMillis();var observed=observation();var ticket=cycle.issuedTicket();
        if(cycle.bindsArrivedReplacement(observed))return McHubSourceLoop.OwnerTransition.ARRIVED;
        if(!cycle.permitsPendingReplacement(ticket,observed,now))return McHubSourceLoop.OwnerTransition.DENIED;
        observeReceipt(observed,now);cycle.tick(observed,now);cycle.nextCommand(observed,now).ifPresent(command->cycle.sent(commands.sendCommand(command)));
        if(cycle.bindsArrivedReplacement(observed))return McHubSourceLoop.OwnerTransition.ARRIVED;
        return cycle.state()==HubWarpCycle.State.WAITING?McHubSourceLoop.OwnerTransition.WAITING:McHubSourceLoop.OwnerTransition.DENIED;
    }
    private HubWarpCycle.TeleportReceipt receipt(){var nativeReceipt=dev.veltraph.client.rewarp.mc.NativeTeleportSignal.read(client);return new HubWarpCycle.TeleportReceipt(nativeReceipt.serial(),nativeReceipt.position());}
    private void observeReceipt(HubWarpCycle.Observation observed,long now){cycle.observeTeleport(observed,receipt(),now);}
    public boolean hasIssuedWarp(){thread();return configurationMatches()&&cycle.hasIssuedWarp();}
    public McHubSourceLoop.OwnerTransition confirmedOwnerTransition(){
        thread();if(!configurationMatches())return McHubSourceLoop.OwnerTransition.DENIED;
        var observed=observation();
        if(cycle.bindsArrivedReplacement(observed))return McHubSourceLoop.OwnerTransition.ARRIVED;
        return cycle.pendingContextMatches(observed,System.currentTimeMillis())?McHubSourceLoop.OwnerTransition.WAITING:McHubSourceLoop.OwnerTransition.DENIED;
    }
    private boolean configurationMatches(){return cycle.activeContract()==null||cycle.activeContract().equals(contract());}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Hub warp requires the client thread");}
    public void cancel(){thread();cycle.reset();}
    public String reason(){return cycle.reason();}
}
