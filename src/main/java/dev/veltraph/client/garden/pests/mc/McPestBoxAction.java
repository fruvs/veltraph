package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McHeldKeys;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import java.util.*;

/** Source assigned-spawn Box2 hold: observed occupants then empty, exact bag delta and no retries. */
final class McPestBoxAction {
    enum State { IDLE, TRAVEL, SELECT, HOLD, COMPLETE, FAILED, CANCELLED }
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final AssignedPlotTravelPort travel;private final PestInventoryLease lease;
    private State state=State.IDLE;private PestBoxBindings box;private PestBindings.Cleaner cleaner;private AssignedPlotTravelPort.Target target;private PestBoxGeometry.Bounds bounds;
    private Object player,world,connection;private int initialBag;private long start,holdDeadline,nextTick;private boolean seen,ack;private final Set<Entity> observed=Collections.newSetFromMap(new IdentityHashMap<>());
    private String reason="Idle";
    McPestBoxAction(Minecraft client,FarmServices services,ConfigService config,AssignedPlotTravelPort travel){this.client=client;this.services=services;this.config=config;this.travel=travel;lease=new PestInventoryLease(client);}
    Optional<String> ready(){try{box=PestBindings.read(config.get(PestLoadoutConfig.PEST_BOX_BINDINGS),PestBoxBindings.class);cleaner=PestBindings.read(config.get(PestConfig.PEST_CLEANER_BINDINGS),PestBindings.Cleaner.class);
        if(travel==null||!config.get(PestConfig.PEST_CLEANER_ENABLED)||box.selected().plotId()!=config.get(PestConfig.PEST_ACTION_PLOT)||!cleaner.pest().registryId().equals("minecraft:armor_stand"))return Optional.of("Coordinated assigned travel, exact selected cleaner plot and source armor-stand model required");
        if(client.player==null||client.level==null||client.getConnection()==null||client.gui.screen()!=null||PestItems.uniqueSlot(client,cleaner.vacuum())<0||PestItems.counter(client,cleaner.vacuum(),cleaner.bagPrefix()).isEmpty())return Optional.of("Identified vacuum, readable bag and closed live Garden player required");
        if(config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)&&!GardenContestSignal.active(sidebar()))return Optional.of("Active Jacob's Contest required");return Optional.empty();
    }catch(RuntimeException bad){return Optional.of(bad.getMessage());}}
    private List<String> sidebar(){var location=new dev.veltraph.client.game.mc.McGameContextService(client);location.resolution();return location.signals().sidebarRowsRaw().stream().map(LocationText::stripColorCodes).toList();}
    boolean start(){if(ready().isPresent())return false;player=client.player;world=client.level;connection=client.getConnection();var selected=box.selected();bounds=PestBoxGeometry.bounds(selected);
        target=new AssignedPlotTravelPort.Target(selected.plotId(),selected.spawn(),selected.yaw(),selected.pitch());initialBag=PestItems.counter(client,cleaner.vacuum(),cleaner.bagPrefix()).orElseThrow();observed.clear();seen=ack=false;start=services.clock().nowMillis();
        if(!travel.start(target,GameArea.GARDEN)){fail("Assigned spawn request refused");return false;}state=State.TRAVEL;reason="Waiting for authoritative assigned-plot spawn";return true;}
    private boolean owned(){return client.player==player&&client.level==world&&client.getConnection()==connection&&client.player!=null&&client.player.isAlive()&&client.gui.screen()==null
        &&client.player.containerMenu==client.player.inventoryMenu&&client.player.containerMenu.getCarried().isEmpty();}
    private List<Entity> models(boolean boxOnly){var result=new ArrayList<Entity>();for(var e:client.level.entitiesForRendering())if(e instanceof net.minecraft.world.entity.decoration.ArmorStand stand&&stand.getHealth()>0&&e.getY()>=62&&PestItems.entityMatches(e,cleaner.pest())
        &&(boxOnly?bounds.contains(new Vec3(e.getX(),e.getY(),e.getZ())):e.getY()<92&&GardenPlotBounds.contains(target.plotId(),e.getX(),e.getZ())))result.add(e);return result;}
    void tick(){if(!active())return;long now=services.clock().nowMillis();
        if(!owned()||now<start||now-start>box.spawnWaitMs()+15_000||(config.get(PestConfig.PEST_ACTION_CONTEST_ONLY)&&!GardenContestSignal.active(sidebar()))){fail("Box player/world/cursor/contest or bounded time changed");return;}
        if(state==State.TRAVEL){var s=travel.tick(target,GameArea.GARDEN);if(s==AssignedPlotTravelRuntime.State.FAILED||s==AssignedPlotTravelRuntime.State.CANCELLED){fail("Assigned plot native arrival rejected");return;}
            if(s!=AssignedPlotTravelRuntime.State.ARRIVED)return;travel.cancel();services.input().releaseAllMovement();services.rotation().cancelRotation();
            if(!PestBoxGeometry.atSpawn(box.selected(),position())||!lease.begin(cleaner.vacuum(),config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT))){fail("Assigned spawn or vacuum relocation changed");return;}
            state=State.SELECT;return;}
        if(state==State.SELECT){if(!lease.ready())return;services.rotation().track(target.yaw(),target.pitch());
            if(Math.abs(dev.veltraph.client.navigation.WalkController.wrap(client.player.getYRot()-target.yaw()))>2||Math.abs(client.player.getXRot()-target.pitch())>2)return;
            state=State.HOLD;start=now;holdDeadline=Long.MAX_VALUE;nextTick=services.clock().currentTick()+box.delayTicks();return;}
        if(!lease.activeTool()||!PestBoxGeometry.atSpawn(box.selected(),position())||Math.abs(dev.veltraph.client.navigation.WalkController.wrap(client.player.getYRot()-target.yaw()))>2||Math.abs(client.player.getXRot()-target.pitch())>2){fail("Box held tool, view or source spawn distance changed");return;}
        var current=models(true);var all=models(false);if(all.size()>cleaner.maxPests()){fail("Assigned plot model count exceeds bounded box budget");return;}
        observed.addAll(current);if(observed.size()>cleaner.maxPests()){fail("Box model identities exceed budget");return;}
        if(!current.isEmpty()&&!seen){seen=true;holdDeadline=now+cleaner.holdMs();}
        int bag=PestItems.counter(client,cleaner.vacuum(),cleaner.bagPrefix()).orElse(-1);int vanished=(int)observed.stream().filter(e->client.level.getEntity(e.getId())!=e||!e.isAlive()).count();
        if(bag<initialBag||bag-initialBag>observed.size()){fail("Box bag changed without exact observed pest identities");return;}
        if(seen&&current.isEmpty()){
            McHeldKeys.setHeld(client.options.keyUse,false);
            if(ack&&vanished>0&&bag==initialBag+vanished){state=State.COMPLETE;reason="Observed assigned box occupied then empty with exact bag increases";cleanup();}
            else if(now>=holdDeadline)fail("Box disappearance/bag/message acknowledgment withheld");return;
        }
        if(!seen&&now-start>=box.spawnWaitMs()||seen&&now>=holdDeadline){fail("Box spawn/capture deadline expired; no retry");return;}
        if(services.clock().currentTick()<nextTick)return;
        if(client.hitResult instanceof net.minecraft.world.phys.EntityHitResult hit&&!PestItems.entityMatches(hit.getEntity(),cleaner.pest())
            ||client.hitResult instanceof net.minecraft.world.phys.BlockHitResult block&&client.level.getBlockEntity(block.getBlockPos())!=null){fail("Box use ray intersects an unrelated interactive target");return;}
        McHeldKeys.setHeld(client.options.keyUse,true);
    }
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private void cleanup(){McHeldKeys.setHeld(client.options.keyUse,false);travel.cancel();lease.restore();}
    private void fail(String text){state=State.FAILED;reason=text;cleanup();}
    void cancel(){if(!active())return;state=State.CANCELLED;reason="Box stopped";cleanup();}
    boolean active(){return state==State.TRAVEL||state==State.SELECT||state==State.HOLD;}
    boolean complete(){return state==State.COMPLETE;}boolean hasPlotModels(){return owned()&&!models(false).isEmpty();}
    void message(String line){travel.onServerMessage(line);if(active()&&cleaner!=null&&cleaner.captureAck().equals(LocationText.stripColorCodes(line)))ack=true;}
    boolean consumeExpectedTeleport(Vec3 position,long now){return active()&&travel.consumeExpectedTeleport(position,now);}
    String status(){return state+": "+reason+" | "+travel.status();}
}
