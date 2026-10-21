package dev.veltraph.client.skills;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.rewarp.mc.NativeTeleportSignal;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.function.BooleanSupplier;

/** Fairy-owned explicit hop driver; vanilla packets and real loaded geometry supply all proofs. */
final class McFairySoulTraversal implements FairySoulTraversal.Port {
    private final Minecraft client;private final FarmServices services;private final McTravelNavigator travel;private final McNavigator terrain;
    private final Object player,world,connection;private final BooleanSupplier context;private final BooleanSupplier returning;private int expectedSlot;private final int originalSlot;private final float yaw,pitch;
    private boolean active;private final java.util.function.Supplier<java.util.List<Object>> tuning;private final java.util.List<Object> initialTuning;
    McFairySoulTraversal(Minecraft client,ConfigService config,FarmServices services,BooleanSupplier context){this(client,config,services,context,()->false);}
    McFairySoulTraversal(Minecraft client,ConfigService config,FarmServices services,BooleanSupplier context,BooleanSupplier returning){
        this.client=client;this.services=services;this.context=context;this.returning=returning;travel=new McTravelNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);
        tuning=()->java.util.List.of(config.get(dev.veltraph.client.config.TravelConfig.ETHERWARP_ABILITY_TOKEN),config.get(dev.veltraph.client.config.TravelConfig.ETHERWARP_STABLE_TICKS),config.get(dev.veltraph.client.config.TravelConfig.ETHERWARP_TIMEOUT_MS),config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER),client.options.sensitivity().get());initialTuning=tuning.get();
        player=client.player;world=client.level;connection=client.getConnection();originalSlot=expectedSlot=services.inventory().selectedSlot();yaw=client.player.getYRot();pitch=client.player.getXRot();
    }
    boolean supported(FairySoulRouteCompiler.Plan plan){
        for(var hop:java.util.stream.Stream.concat(plan.outbound().stream(),plan.returning().stream()).toList()){
            if(!terrain.standingClear(hop.node().position()))return false;
            if(hop.node().kind()==LocalRoute.Kind.WALK&&!GroundPathfinder.find(terrain,hop.from(),GroundPathfinder.node(hop.node().position()),2500,32).found())return false;
            if(hop.node().kind()==LocalRoute.Kind.ETHERWARP&&(!travel.hasEtherwarpTool()||!client.level.getChunkSource().hasChunk(hop.node().target().x()>>4,hop.node().target().z()>>4)))return false;
        }return true;
    }
    @Override public boolean current(){return context.getAsBoolean()&&initialTuning.equals(tuning.get())&&client.player==player&&client.level==world&&client.getConnection()==connection&&connection!=null
            &&client.gui.screen()==null&&client.player.containerMenu==client.player.inventoryMenu&&client.player.inventoryMenu.getCarried().isEmpty()&&services.inventory().selectedSlot()==expectedSlot;}
    @Override public boolean start(FairySoulRouteCompiler.Hop hop){
        if(!current())return false;services.rotation().cancelRotation();active=travel.start(hop.node(),.35,returning.getAsBoolean()?SharedMovementPolicy.Purpose.EXACT_RETURN:SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL);expectedSlot=services.inventory().selectedSlot();return active;
    }
    @Override public FairySoulTraversal.Motion tick(){if(!current())return FairySoulTraversal.Motion.FAILED;var state=travel.tick();expectedSlot=services.inventory().selectedSlot();return FairySoulTraversal.Motion.valueOf(state.name());}
    @Override public String diagnostics(){return travel.diagnostics();}
    @Override public boolean useDispatched(){return travel.etherwarpUseDispatched();}
    @Override public long receiptSerial(){return NativeTeleportSignal.read(client).serial();}
    @Override public Vec3 receiptPosition(){return NativeTeleportSignal.read(client).position();}
    @Override public Vec3 position(){return client.player==null?null:new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    @Override public void stop(){boolean owned=current();travel.stop();active=false;if(owned)expectedSlot=services.inventory().selectedSlot();}
    boolean consumeExpectedTeleport(Vec3 position,long now){return active&&current()&&travel.consumeExpectedTeleport(position,now);}
    float savedYaw(){return yaw;}
    float savedPitch(){return pitch;}
    boolean originalSlotRestored(){return current()&&services.inventory().selectedSlot()==originalSlot;}
}
