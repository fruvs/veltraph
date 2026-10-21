package dev.veltraph.client.galatea.recovery;

import dev.veltraph.client.config.*;
import dev.veltraph.client.fishing.FishingManaParser;
import dev.veltraph.client.navigation.LocalRoute;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.mc.McNavigator;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Direction;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Native source-goal acquisition and ordinary Ether Transmission. Never modifies player physics. */
public final class McFigFallRecovery {
    private final Minecraft client;private final ConfigService config;private final FarmServices services;
    private final McTravelNavigator navigation;private final McNavigator terrain;
    private final FigFallRecoveryCycle cycle=new FigFallRecoveryCycle();
    private Object owner,world,connection,manaOwner,manaWorld,manaConnection;
    private int mana=-1,cost,toolSlot=-1,originalSlot=-1;private long manaAt,timeout;private boolean arrivalSeen;
    private int supportedTicks,lastSupportTick=Integer.MIN_VALUE;
    private ItemStack tool=ItemStack.EMPTY;
    public McFigFallRecovery(Minecraft client,ConfigService config,FarmServices services) {
        this.client=client;this.config=config;this.services=services;
        navigation=new McTravelNavigator(client,services,config);terrain=new McNavigator(client,services.input(),config);
    }
    /** Caller must pass a received server action-bar event, never a synthetic local text. */
    public void onActionBar(String text) {
        var value=FishingManaParser.parse(text);mana=value.orElse(-1);manaAt=services.clock().nowMillis();
        manaOwner=client.player;manaWorld=client.level;manaConnection=client.getConnection();
    }
    public boolean inArea() {return client.player!=null&&FigFallAtlas.containing(position()).isPresent();}
    private Vec3 position(){return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());}
    private boolean loaded(BlockPos p){return client.level!=null&&client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4);}
    private boolean enoughMana() {
        long now=services.clock().nowMillis();return cost>0&&mana>=cost&&now>=manaAt&&now-manaAt<=5000
                &&manaOwner==client.player&&manaWorld==client.level&&manaConnection==client.getConnection();
    }
    private LocalRoute.Node acquire(BlockPos target) {
        if(client.player==null||client.level==null||!loaded(target))return null;
        var landing=new Vec3(target.x()+.5,target.y()+1,target.z()+.5);
        if(!terrain.standingClear(landing))return null;
        var p=new net.minecraft.core.BlockPos(target.x(),target.y(),target.z());var eye=client.player.getEyePosition();
        // Source feasibility caps acquisition at 56 blocks and raycasts the exact goal.
        for(var face:Direction.values()) {
            var point=net.minecraft.world.phys.Vec3.atCenterOf(p).add(face.dx()*.499,face.dy()*.499,face.dz()*.499);
            if(eye.distanceTo(point)>56)continue;
            HitResult hit=client.level.clip(new ClipContext(eye,point,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,client.player));
            if(hit instanceof BlockHitResult h&&hit.getType()==HitResult.Type.BLOCK&&h.getBlockPos().equals(p))
                return new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,landing,0,0,target,Direction.valueOf(h.getDirection().name()));
        }
        return null;
    }
    public boolean start() {
        if(cycle.state()!=FigFallRecoveryCycle.State.IDLE)return false;
        owner=client.player;world=client.level;connection=client.getConnection();
        cost=config.get(FigRecoveryConfig.FIG_RECOVERY_MANA_COST);timeout=Math.max(1000,Math.min(30000,config.get(FigRecoveryConfig.FIG_RECOVERY_TIMEOUT_MS)));
        if(!config.get(FigRecoveryConfig.FIG_FALL_RECOVERY)||owner==null||world==null||connection==null||client.gui.screen()!=null||!client.player.isAlive()||cost>100000||!enoughMana()||!navigation.hasEtherwarpTool()) {
            cycle.fail("Recovery needs a configured positive mana cost, fresh server mana and Ether Transmission tool");return false;
        }
        var nodes=new HashMap<BlockPos,LocalRoute.Node>();
        boolean ready=cycle.start(position(),goal->{var node=acquire(goal);if(node!=null)nodes.put(goal,node);return node!=null;},java.util.concurrent.ThreadLocalRandom.current(),services.clock().nowMillis());
        if(!ready)return false;
        services.breaking().setBreaking(false);services.input().releaseAllMovement();services.rotation().cancelRotation();
        originalSlot=services.inventory().selectedSlot();arrivalSeen=false;supportedTicks=0;lastSupportTick=Integer.MIN_VALUE;
        if(!navigation.start(nodes.get(cycle.goal()),.35,SharedMovementPolicy.Purpose.EXACT_RETURN)){cycle.fail(navigation.status());return false;}
        toolSlot=services.inventory().selectedSlot();tool=client.player.getInventory().getItem(toolSlot).copy();return true;
    }
    public FigFallRecoveryCycle.State tick() {
        if(cycle.state()!=FigFallRecoveryCycle.State.TRAVELLING)return cycle.state();
        boolean owned=owner==client.player&&world==client.level&&connection==client.getConnection()&&client.player!=null&&client.player.isAlive()&&client.gui.screen()==null
                &&config.get(FigRecoveryConfig.FIG_FALL_RECOVERY)&&config.get(FigRecoveryConfig.FIG_RECOVERY_MANA_COST)==cost
                &&Math.max(1000,Math.min(30000,config.get(FigRecoveryConfig.FIG_RECOVERY_TIMEOUT_MS)))==timeout
                &&(services.inventory().selectedSlot()==toolSlot||arrivalSeen&&services.inventory().selectedSlot()==originalSlot)
                &&ItemStack.isSameItemSameComponents(tool,client.player.getInventory().getItem(toolSlot));
        // Recheck admission before the actual use. A dispatched action is observed through its
        // bounded native arrival; a later lower mana receipt cannot erase an in-flight outcome.
        if(!owned||(!navigation.etherwarpUseDispatched()&&!enoughMana())) {
            cycle.fail("Recovery ownership, tool, configuration or fresh mana changed");navigation.stop();return cycle.state();
        }
        boolean sent=navigation.etherwarpUseDispatched();var result=navigation.tick();
        if(result==McTravelNavigator.State.ARRIVED)arrivalSeen=true;
        if(!sent&&navigation.etherwarpUseDispatched()) {
            // One observation cannot fund a later recovery. Wait for a new actual mana
            // receipt rather than subtracting a guessed server charge from a local counter.
            mana=-1;manaAt=0;manaOwner=manaWorld=manaConnection=null;
        }
        var goal=cycle.goal();var landing=new Vec3(goal.x()+.5,goal.y()+1,goal.z()+.5);
        if(loaded(goal)&&!terrain.standingClear(landing)){cycle.fail("Recovery landing support changed");navigation.stop();return cycle.state();}
        boolean supportedArrival=result==McTravelNavigator.State.ARRIVED&&client.player.onGround()
                &&position().distanceTo(landing)<.9&&terrain.standingClear(position());
        if(lastSupportTick!=client.player.tickCount){lastSupportTick=client.player.tickCount;supportedTicks=supportedArrival?supportedTicks+1:0;}
        cycle.observe(owned,loaded(goal),supportedTicks>=3,result==McTravelNavigator.State.FAILED,services.clock().nowMillis(),timeout);
        if(cycle.state()==FigFallRecoveryCycle.State.FAILED)navigation.stop();
        return cycle.state();
    }
    public boolean consumeExpectedTeleport(Vec3 p,long now){return navigation.consumeExpectedTeleport(p,now);}
    public void reset(){navigation.stop();cycle.reset();owner=world=connection=null;tool=ItemStack.EMPTY;toolSlot=originalSlot=-1;arrivalSeen=false;supportedTicks=0;lastSupportTick=Integer.MIN_VALUE;}
    public void cancel(){cycle.cancel();navigation.stop();}
    public FigFallRecoveryCycle.State state(){return cycle.state();}
    public String status(){return cycle.status();}
    public BlockPos goal(){return cycle.goal();}
}
