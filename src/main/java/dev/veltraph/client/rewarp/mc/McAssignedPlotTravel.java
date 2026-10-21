package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.rewarp.AssignedPlotTravelPort;
import dev.veltraph.client.rewarp.AssignedPlotTravelRuntime;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.service.mc.McWorldService;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;

/** Ordinary single-command assigned-plot travel with pinned configuration and native receipt proof. */
public final class McAssignedPlotTravel implements AssignedPlotTravelPort {
    private final Minecraft client;private final InputService input;private final McWorldService world;
    private final AssignedPlotTravelRuntime runtime;
    private Target target;private GameArea area;
    public McAssignedPlotTravel(Minecraft client,InputService input) {
        this.client=client;this.input=input;world=new McWorldService(client);
        runtime=new AssignedPlotTravelRuntime(id->{
            if(target!=null)dev.veltraph.client.observation.unexpected.UnexpectedActions.expectTeleport(client,target.spawn(),1.0,5000);
            return new McServerCommandService(client).sendCommand("tptoplot "+id);
        });
    }
    private AssignedPlotTravelRuntime.Observation observation(GameArea area) {
        var p=client.player;var receipt=NativeTeleportSignal.read(client);
        boolean valid=p!=null&&client.level!=null&&client.getConnection()!=null&&p.level()==client.level&&p.isAlive()
                &&client.gui.screen()==null&&!p.isInLava()&&!p.isPassenger()&&!p.getAbilities().flying;
        Vec3 feet=p==null?null:new Vec3(p.getX(),p.getY(),p.getZ());
        boolean supported=valid&&p.onGround()&&world.bodyClear(feet,.3,1.8)&&world.hasGroundSupport(feet,.3);
        return new AssignedPlotTravelRuntime.Observation(valid,p,client.level,client.getConnection(),
                client.level==null?"":client.level.dimension().identifier().toString(),area,feet,supported,receipt.serial(),receipt.position(),System.currentTimeMillis());
    }
    @Override public boolean start(Target requested,GameArea currentArea) {
        cancel();target=requested;area=currentArea;input.releaseAllMovement();return runtime.start(requested,observation(currentArea));
    }
    @Override public AssignedPlotTravelRuntime.State tick(Target selected,GameArea currentArea) {
        boolean active=runtime.active();
        if(runtime.state()==AssignedPlotTravelRuntime.State.WAITING_ARRIVAL)input.releaseAllMovement();
        area=currentArea;var result=runtime.tick(selected,observation(currentArea));
        if(active&&(result==AssignedPlotTravelRuntime.State.FAILED||result==AssignedPlotTravelRuntime.State.CANCELLED))input.releaseAllMovement();
        return result;
    }
    @Override public boolean arrived(Target selected,GameArea currentArea) {return tick(selected,currentArea)==AssignedPlotTravelRuntime.State.ARRIVED;}
    @Override public boolean consumeExpectedTeleport(Vec3 position,long now) {return runtime.consumeExpectedTeleport(target,observation(area),position,now);}
    @Override public void onServerMessage(String message) {runtime.onServerMessage(message);}
    @Override public void cancel() {runtime.cancel();target=null;area=null;input.releaseAllMovement();}
    @Override public String status() {return runtime.status();}
}
