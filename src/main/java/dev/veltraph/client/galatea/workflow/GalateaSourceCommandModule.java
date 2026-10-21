package dev.veltraph.client.galatea.workflow;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.mixin.AgathaChatAccessor;
import dev.veltraph.client.rewarp.FarmReturnPoint;
import dev.veltraph.client.rewarp.mc.NativeTeleportSignal;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McWorldService;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;

/** Single ordinary server command; no guessed command, pose write, retry or cross-world lease. */
public final class GalateaSourceCommandModule extends AbstractModule implements GalateaSourceTravelCycle.Actions {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final GalateaSourceTravelCycle.Kind kind;private final McWorldService world;
    private GalateaSourceTravelCycle cycle=new GalateaSourceTravelCycle();
    private GalateaSourceTravelCycle.Profile profile;private FarmReturnPoint destination;
    private Set<GuiMessage> before=Collections.newSetFromMap(new IdentityHashMap<>());
    private boolean started;private String problem="Idle";
    public GalateaSourceCommandModule(GalateaSourceTravelCycle.Kind kind,Minecraft client,ConfigService config,FarmServices services){
        super(kind==GalateaSourceTravelCycle.Kind.MURKWATER?"galatea_murkwater":"galatea_source_reset",kind==GalateaSourceTravelCycle.Kind.MURKWATER?"Mangrove Murkwater":"Galatea Source Reset",ModuleCategory.FORAGING);
        this.kind=kind;this.client=client;this.config=config;this.services=services;world=new McWorldService(client);
    }
    @Override public boolean requiresSkyBlock(){return true;}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.GALATEA);}
    public GalateaSourceTravelCycle.Kind kind(){return kind;}
    private String text(ConfigKey<String> key){return config.get(key).strip();}
    private static String clean(String s){return s.replaceAll("§.","").strip();}
    private List<GuiMessage> messages(){return client.gui.hud.getChat() instanceof AgathaChatAccessor a?List.copyOf(a.veltraphAgathaMessages()):List.of();}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private FarmReturnPoint configuredDestination(){
        if(kind==GalateaSourceTravelCycle.Kind.MURKWATER)return new FarmReturnPoint(client.level.dimension().identifier().toString(),GameArea.GALATEA,GalateaSourceTravelCycle.MURKWATER,client.player.getYRot(),client.player.getXRot());
        String[] fields=text(GalateaWorkflowConfig.GALATEA_RESET_DESTINATION).split(",",-1);
        if(fields.length!=6)throw new IllegalArgumentException("Reset destination requires exact dimension,x,y,z,yaw,pitch");
        var p=new Vec3(Double.parseDouble(fields[1]),Double.parseDouble(fields[2]),Double.parseDouble(fields[3]));float yaw=Float.parseFloat(fields[4]),pitch=Float.parseFloat(fields[5]);
        if(!GalateaSourceTravelCycle.finite(p)||!Float.isFinite(yaw)||!Float.isFinite(pitch)||Math.abs(pitch)>90)throw new IllegalArgumentException("Finite reset destination/view required");
        return new FarmReturnPoint(fields[0],GameArea.GALATEA,p,yaw,pitch);
    }
    private GalateaSourceTravelCycle.Profile currentProfile(FarmReturnPoint goal){return new GalateaSourceTravelCycle.Profile(kind,config.get(GalateaWorkflowConfig.GALATEA_SOURCE_COMMANDS),text(kind==GalateaSourceTravelCycle.Kind.MURKWATER?GalateaWorkflowConfig.GALATEA_MURKWATER_COMMAND:GalateaWorkflowConfig.GALATEA_RESET_COMMAND),text(kind==GalateaSourceTravelCycle.Kind.MURKWATER?GalateaWorkflowConfig.GALATEA_MURKWATER_RECEIPT:GalateaWorkflowConfig.GALATEA_RESET_RECEIPT),text(GalateaWorkflowConfig.GALATEA_COMMAND_REJECTION),goal.dimension(),goal.position(),Math.clamp(config.get(GalateaWorkflowConfig.GALATEA_COMMAND_TIMEOUT_MS),1000,60000));}
    public Optional<String> activationReadiness(){
        if(isEnabled())return Optional.of("Source command helper already active");
        return readiness();
    }
    private Optional<String> readiness(){
        if(client.player==null||client.level==null||client.getConnection()==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.containerMenu!=client.player.inventoryMenu||!client.player.containerMenu.getCarried().isEmpty())return Optional.of("Healthy closed native owner required");
        if(!(client.gui.hud.getChat() instanceof AgathaChatAccessor))return Optional.of("Native server chat receipts unavailable");
        try{var goal=configuredDestination();var candidate=currentProfile(goal);if(!GalateaSourceTravelCycle.valid(candidate)||!goal.dimension().equals(client.level.dimension().identifier().toString()))return Optional.of("Explicit command permission and exact command/destination/receipt/rejection metadata required");
            if(kind==GalateaSourceTravelCycle.Kind.MURKWATER&&(!config.get(GalateaConfig.MANGROVE_WARP_TO_MURKWATER)||position().distanceTo(GalateaSourceTravelCycle.MARSH)>=20))return Optional.of("Source Murkwater requires preference and distance under 20 from Marsh spawn");
        }catch(RuntimeException e){return Optional.of(e.getMessage());}return Optional.empty();
    }
    @Override public Optional<String> preStartCheck(){return activationReadiness();}
    /** Activation captures only; the lease commits before the next module tick dispatches input. */
    @Override public void onEnable(){
        var denied=readiness();if(denied.isPresent()){problem=denied.get();setEnabled(false);return;}
        cycle=new GalateaSourceTravelCycle();started=false;before.clear();problem="Prepared";
        try{destination=configuredDestination();profile=currentProfile(destination);}catch(RuntimeException error){problem="Invalid explicit command profile";setEnabled(false);}
    }
    private boolean receipt(String expected){return !expected.isBlank()&&messages().stream().anyMatch(m->!before.contains(m)&&m.source()==GuiMessageSource.SYSTEM_SERVER&&clean(m.content().getString()).equals(expected));}
    private GalateaSourceTravelCycle.Observation observation(){
        var p=client.player;var signal=NativeTeleportSignal.read(client);
        boolean healthy=p!=null&&client.level!=null&&client.getConnection()!=null&&p.isAlive()&&p.level()==client.level&&client.gui.screen()==null&&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty()&&!p.isPassenger()&&!p.getAbilities().flying;
        Vec3 feet=p==null?null:position();boolean support=healthy&&p.onGround()&&p.getDeltaMovement().length()<.1&&world.bodyClear(feet,.3,1.8)&&world.hasGroundSupport(feet,.3);
        return new GalateaSourceTravelCycle.Observation(healthy,p,client.level,client.getConnection(),client.level==null?"":client.level.dimension().identifier().toString(),feet,support,p==null?0:p.tickCount,signal.serial(),signal.position(),profile!=null&&receipt(profile.receipt()),profile!=null&&receipt(profile.rejection()),services.clock().nowMillis());
    }
    @Override public void onTick(){
        if(!isEnabled())return;
        try{var current=currentProfile(configuredDestination());if(!started){started=true;if(!Objects.equals(profile,current)||!cycle.start(profile,observation(),this)){problem=cycle.reason();setEnabled(false);return;}}
            else cycle.tick(current,observation(),this);
        }catch(RuntimeException error){problem="Source command context/profile unavailable";cycle.cancel(this);setEnabled(false);return;}
        problem=cycle.reason();if(cycle.state()!=GalateaSourceTravelCycle.State.WAITING)setEnabled(false);
    }
    @Override public boolean send(String command){
        if(client.getConnection()==null||client.gui.screen()!=null||!GalateaSourceTravelCycle.commandSafe(command))return false;
        before.clear();before.addAll(messages());client.getConnection().sendCommand(command.substring(1));return true;
    }
    @Override public void release(){services.breaking().setBreaking(false);services.input().releaseAllMovement();services.rotation().cancelRotation();}
    @Override public void onDisable(){cycle.cancel(this);}
    public boolean completed(){return cycle.state()==GalateaSourceTravelCycle.State.COMPLETE;}
    public FarmReturnPoint destination(){return configuredDestination();}
    public boolean consumeExpectedTeleport(Vec3 position,long now){return started&&cycle.consumeExpectedTeleport(observation(),position,now);}
    @Override public String diagnostics(){return id()+" | "+cycle.state()+" | "+problem;}
}
