package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.world.Vec3;
import java.util.List;
import net.minecraft.client.Minecraft;

/** Loaded goal coordinator, with normal server-permitted ability flight delegated unchanged. */
public final class McChunkGoalNavigator {
    private final Minecraft client;private final InputService input;private final McFlightNavigator flight;private final McNavigator geometry;
    private final ChunkGoalController controller=new ChunkGoalController();private final ChunkGoalController.Driver driver;
    private net.minecraft.client.player.LocalPlayer owner;private net.minecraft.client.multiplayer.ClientLevel ownerLevel;private boolean calculating;
    private float lastHealth;private final LegacyTravelPublicationLease.Leg publication;
    public McChunkGoalNavigator(Minecraft c,InputService input,ConfigService config) {this(c,input,config,null);}
    public McChunkGoalNavigator(Minecraft c,InputService input,ConfigService config,LegacyTravelPublicationLease.Leg publication) {
        this.publication=publication;client=c;this.input=input;flight=new McFlightNavigator(c,input,config);geometry=new McNavigator(c,input,config);
        driver=new ChunkGoalController.Driver() {
            public boolean ready(Vec3 p) { return (publication==null||publication.current())&&client.level!=null&&client.level.getChunkSource().hasChunk(ChunkGoalController.chunk(p.x()),ChunkGoalController.chunk(p.z()))&&geometry.bodyClear(p,false); }
            public boolean start(Vec3 p) { return publication==null?flight.start(p):publication.current()&&flight.start(p,java.util.concurrent.ThreadLocalRandom.current().nextLong(),publication); }
            public ChunkGoalController.DriveState tick() { return switch(flight.tick()) { case FLYING -> ChunkGoalController.DriveState.ACTIVE;case ARRIVED -> ChunkGoalController.DriveState.ARRIVED;default -> ChunkGoalController.DriveState.FAILED; }; }
            public void stop() { flight.stop(); }
            public String status() { return flight.status(); }
        };
    }
    public boolean start(int chunkX,int chunkZ,List<Vec3> standingPositions) {
        stop();
        if(client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null)return false;
        owner=client.player;ownerLevel=client.level;lastHealth=owner.getHealth();return controller.start(chunkX,chunkZ,standingPositions,System.currentTimeMillis());
    }
    public void calculating(boolean calculating) { this.calculating=calculating; }
    public McTravelNavigator.State tick() {
        boolean available=owner!=null&&client.player==owner&&client.level==ownerLevel&&owner.isAlive()&&client.gui.screen()==null&&owner.getHealth()>=lastHealth-.01f;
        if(available)lastHealth=owner.getHealth();
        Vec3 p=available?new Vec3(owner.getX(),owner.getY(),owner.getZ()):null;
        var state=controller.tick(new ChunkGoalController.Input(p,available,true,calculating,System.currentTimeMillis()),driver,5000);
        if(state!=ChunkGoalController.State.TRAVELLING)input.releaseAllMovement();
        return switch(state) { case ARRIVED -> McTravelNavigator.State.ARRIVED;case WAITING_LOADED,TRAVELLING -> McTravelNavigator.State.ACTIVE;default -> McTravelNavigator.State.FAILED; };
    }
    public void stop() { controller.stop(driver);owner=null;ownerLevel=null;calculating=false;input.releaseAllMovement(); }
    public String status() { return controller.status(); }
}
