package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.service.InputService;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EquipmentSlot;

/** Source medium/headroom contract with input-driven local steering; no ability or position writes. */
final class McWaterNavigator implements AmphibiousPathfinder.Terrain,WaterTravelController.Geometry {
    private final Minecraft client;private final InputService input;private final ConfigService config;private final McNavigator ground;private final WaterPublicationActor publication;
    private final WaterTravelController controller=new WaterTravelController();
    private LocalPlayer owner;private net.minecraft.client.multiplayer.ClientLevel ownerLevel;
    private Vec3 goal;private int maxDrop;private long seed;private String reason="Idle";
    private boolean neutralStart;
    private Vec3 breathingColumn;
    private boolean resumingAir;
    private Object planIdentity;private McNavigator.WaterTerminalHold terminalHold;
    private WaterPublicationIdentity.Cursor terminalCursor;
    McWaterNavigator(Minecraft c,InputService input,ConfigService config,McNavigator ground) {this(c,input,config,ground,null);}
    McWaterNavigator(Minecraft c,InputService input,ConfigService config,McNavigator ground,WaterPublicationActor actor) {client=c;this.input=actor==null?input:WaterPublicationInputs.require(input,actor);this.config=config;this.ground=ground;publication=actor;}
    private boolean fresh(){return publication!=null?publication.current():!dev.veltraph.client.service.mc.McOwnedRotation.installed(client);}
    private boolean planFresh(){return fresh()&&(publication==null||publication.planCurrent(planIdentity));}
    private Vec3 playerPosition() { return new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()); }
    private static net.minecraft.core.BlockPos mc(BlockPos p) { return new net.minecraft.core.BlockPos(p.x(),p.y(),p.z()); }
    private boolean waterAt(double x,double y,double z) {
        var p=new BlockPos((int)Math.floor(x),(int)Math.floor(y),(int)Math.floor(z));
        if(!fresh()||client.level==null||!client.level.getChunkSource().hasChunk(p.x()>>4,p.z()>>4))return false;
        var fluid=client.level.getFluidState(mc(p));
        return fluid.is(FluidTags.WATER)&&p.y()+fluid.getHeight(client.level,mc(p))>y+1e-5&&fresh();
    }
    /** Recovered Pathfinder.isHitboxClear: every corner must encounter water along the body. */
    public boolean water(Vec3 p) {
        if(!fresh()||!ground.bodyClear(p,true))return false;
        for(double dx:new double[]{-.299,.299})for(double dz:new double[]{-.299,.299}) {
            boolean wet=false;for(double y=0;y<=1.8;y+=.5)if(waterAt(p.x()+dx,p.y()+.001+y,p.z()+dz)) { wet=true;break; }
            if(!wet)return false;
        }
        return fresh();
    }
    public boolean ground(Vec3 p) {return fresh()&&ground.standingClear(p,true)&&fresh();}
    public Vec3 position(BlockPos node) {
        var p=new Vec3(node.x()+.5,node.y(),node.z()+.5);
        var supported=ground.position(node);
        return ground(supported)?supported:water(p)?p:supported;
    }
    public boolean occupiable(Vec3 p) { return fresh()&&ground.bodyClear(p,true)&&(water(p)||ground(p))&&fresh(); }
    public boolean startClear(Vec3 p) { return fresh()&&ground.bodyClear(p,true)&&(occupiable(p)||airRecovery(p)||client.player!=null&&(client.player.onGround()||client.player.isInWater()))&&fresh(); }
    public boolean airRecovery(Vec3 p) {
        return fresh()&&resumingAir&&owner==client.player&&ownerLevel==client.level&&breathingColumn!=null
                &&p.horizontalDistanceTo(breathingColumn)<.9&&p.y()>=breathingColumn.y()-.2&&p.y()<breathingColumn.y()+3.25
                &&sweep(p,breathingColumn,false);
    }
    private boolean sweep(Vec3 a,Vec3 b,boolean requireSupport) {
        if(!fresh())return false;
        int count=Math.max(1,(int)Math.ceil(a.distanceTo(b)*10));
        for(int i=0;i<=count;i++) {
            Vec3 p=TravelPath.lerp(a,b,(double)i/count);
            if(!ground.bodyClear(p,true)||requireSupport&&!water(p)&&!ground.standingClear(p))return false;
        }
        return fresh();
    }
    private boolean deepLanding(Vec3 p) {
        if(!fresh())return false;
        for(double dx:new double[]{-.3,.3})for(double dz:new double[]{-.3,.3})for(int dy=0;dy<3;dy++)
            if(!waterAt(p.x()+dx,p.y()-.01-dy,p.z()+dz))return false;
        return fresh();
    }
    private double nearbyWaterTop(Vec3 p) {
        if(!fresh())return Double.NaN;
        int base=(int)Math.floor(p.y());double top=Double.NaN;
        for(int y=base;y<=base+3;y++) {
            var pos=new BlockPos((int)Math.floor(p.x()),y,(int)Math.floor(p.z()));
            if(!client.level.getChunkSource().hasChunk(pos.x()>>4,pos.z()>>4))return Double.NaN;
            var fluid=client.level.getFluidState(mc(pos));if(!fluid.is(FluidTags.WATER))break;
            top=y+fluid.getHeight(client.level,mc(pos));
        }
        return fresh()?top:Double.NaN;
    }
    public boolean connection(Vec3 a,Vec3 b) { return connection(a,b,maxDrop); }
    public boolean connection(Vec3 a,Vec3 b,int dropBound) {
        if(!fresh())return false;
        if(!ground.bodyClear(a,true)||!occupiable(b))return false;
        if(ground.walkClear(a,b,true))return fresh();
        boolean fromWater=water(a),toWater=water(b);double dy=b.y()-a.y();
        if(!fromWater&&toWater&&dy<=0&&dy>=-3.25&&breathingColumn!=null
                &&(controller.state()==WaterTravelController.State.SURFACING||controller.state()==WaterTravelController.State.REFILLING||resumingAir)
                &&a.horizontalDistanceTo(breathingColumn)<.9&&sweep(a,b,false))return fresh();
        if(!fromWater&&!toWater&&Math.abs(dy)<=1.001) {
            if(ground.walkClear(a,b)||ground.stepClear(a,b))return fresh();
            // Native water-exit momentum can briefly place feet in air above the supported lip.
            return !ground.standingClear(a)&&client.player!=null&&!client.player.onGround()
                    &&dy<=.1&&dy>=-.6&&sweep(a,b,false);
        }
        if(fromWater&&toWater&&sweep(a,b,true))return fresh();
        if(Math.abs(dy)<=.6&&sweep(a,b,true))return fresh();
        if(dy>.05&&(dy>.6&&dy<=1.001||fromWater&&ground(b)&&dy<=2&&b.y()<=nearbyWaterTop(a)+.6)) {
            double apex=fromWater?Math.max(a.y(),b.y())+.15:Math.max(a.y()+1.25,b.y()+.25);
            var raisedA=new Vec3(a.x(),apex,a.z());var raisedB=new Vec3(b.x(),apex,b.z());
            return sweep(a,raisedA,false)&&sweep(raisedA,raisedB,false)&&sweep(raisedB,b,false);
        }
        if(dy<-.6) {
            double fall=-dy;int bound=toWater?Math.max(3,dropBound):Math.min(3,dropBound);
            if(fall>bound+.001||fall>3&&!deepLanding(b))return false;
            var ledge=new Vec3(b.x(),a.y(),b.z());
            return sweep(a,ledge,false)&&sweep(ledge,b,false);
        }
        return false;
    }
    boolean start(Vec3 destination,int dropBound,double terminalRadius) {
        if(!Double.isFinite(terminalRadius)||terminalRadius<.05||terminalRadius>.3){stop();reason="Invalid strict water terminal radius";return false;}
        return startConfigured(destination,dropBound,terminalRadius);
    }
    boolean start(Vec3 destination,int dropBound) {return startConfigured(destination,dropBound,Double.NaN);}
    private boolean startConfigured(Vec3 destination,int dropBound,double terminalRadius) {
        stop();maxDrop=dropBound;goal=destination;seed=java.util.concurrent.ThreadLocalRandom.current().nextLong();
        if(!fresh()||client.player==null||client.level==null||!client.player.isAlive()||client.gui.screen()!=null||client.player.isInLava()||client.player.getAbilities().flying) { reason="Available healthy non-lava player without active ability flight required";return false; }
        var result=AmphibiousPathfinder.find(this,playerPosition(),goal,48,6000,maxDrop);
        reason=result.reason();if(!result.found()||!fresh())return false;
        var route=shape(playerPosition(),result.nodes());
        if(!fresh())return false;owner=client.player;ownerLevel=client.level;planIdentity=new Object();
        if(publication!=null&&!publication.bindPlan(planIdentity)){stop();reason="WATER plan custody changed after geometry";return false;}
        neutralStart=owner.isSprinting()||owner.isSwimming();if(Double.isFinite(terminalRadius))controller.start(route,owner.getHealth(),System.currentTimeMillis(),terminalRadius);else controller.start(route,owner.getHealth(),System.currentTimeMillis());reason="Dry/water travel";return true;
    }
    private List<Vec3> shape(Vec3 from,List<Vec3> nodes) {
        var path=TravelPath.shape(from,nodes,(a,b)->sweep(a,b,true),seed);
        var supported=new ArrayList<Vec3>();
        for(int i=0;i<path.size();i++) {
            Vec3 p=path.get(i),surface=ground.surfacePosition(p);
            supported.add(i<path.size()-1&&ground(surface)?surface:p);
        }
        return fresh()?List.copyOf(supported):List.of();
    }
    private Vec3 breathableSurface(Vec3 from) {
        // Search loaded nearby columns only. The entire ascent path and standing body must fit.
        Vec3 best=null;double cost=Double.POSITIVE_INFINITY;
        int y0=(int)Math.floor(from.y());
        for(int radius=0;radius<=6;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            double x=Math.floor(from.x())+dx+.5,z=Math.floor(from.z())+dz+.5;
            for(int y=y0;y<=y0+24;y++) {
                var pos=new BlockPos((int)Math.floor(x),y,(int)Math.floor(z));
                if(!client.level.getChunkSource().hasChunk(pos.x()>>4,pos.z()>>4))break;
                var fluid=client.level.getFluidState(mc(pos));
                if(!fluid.is(FluidTags.WATER))continue;
                double top=y+fluid.getHeight(client.level,mc(pos));
                if(waterAt(x,top+.01,z))continue;
                Vec3 candidate=new Vec3(x,top-owner.getEyeHeight()+.2,z);
                if(!occupiable(candidate)||waterAt(x,candidate.y()+owner.getEyeHeight(),z))continue;
                double score=from.distanceTo(candidate);if(score>=cost)continue;
                var path=AmphibiousPathfinder.find(this,from,candidate,32,3000,0);
                if(path.found()) { best=candidate;cost=score; }
            }
        }
        return fresh()?best:null;
    }
    private boolean automaticSneak() {
        for(var slot:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET}) {
            var stack=owner.getItemBySlot(slot);
            if(stack.isEmpty()||!stack.getHoverName().getString().contains("Salmon "))return true;
        }
        return false;
    }
    WalkController.State tick() {try{return tickOwned();}catch(WaterPublicationRevokedException refused){stop();reason="WATER key publication revoked";return WalkController.State.BLOCKED;}}
    private WalkController.State tickOwned() {
        if(!planFresh()||owner==null||client.player!=owner||client.level!=ownerLevel||!owner.isAlive()||client.gui.screen()!=null||owner.getAbilities().flying) { stop();reason="Water/drop ownership or world unavailable";return WalkController.State.BLOCKED; }
        if(neutralStart) { neutralStart=false;input.releaseAllMovement();reason="Preparing stable native water input";return WalkController.State.WALKING; }
        var v=owner.getDeltaMovement();
        var in=new WaterTravelController.Input(playerPosition(),new Vec3(v.x,v.y,v.z),owner.getYRot(),owner.onGround(),owner.isInWater(),waterAt(owner.getX(),owner.getEyeY(),owner.getZ()),
                owner.getAirSupply(),owner.getMaxAirSupply(),owner.getHealth(),true,owner.isInLava(),automaticSneak(),System.currentTimeMillis());
        if(!planFresh()){stop();return WalkController.State.BLOCKED;}
        if(controller.needsAir(in)) {
            Vec3 surface=breathableSurface(in.position());
            breathingColumn=surface;
            var route=surface==null?List.<Vec3>of():AmphibiousPathfinder.find(this,in.position(),surface,32,3000,0).nodes();
            var shaped=surface==null?List.<Vec3>of():shape(in.position(),route);
            if(!planFresh()){stop();return WalkController.State.BLOCKED;}
            controller.surface(shaped,in.now());
        }
        var before=controller.state();
        var out=controller.tick(in,this,6000,config.get(dev.veltraph.client.config.RotationConfig.ROTATION_SPEED_MULTIPLIER));
        if(!planFresh()){stop();return WalkController.State.BLOCKED;}
        if(before==WaterTravelController.State.REFILLING&&out.state()==WaterTravelController.State.TRAVELLING) {
            resumingAir=true;AmphibiousPathfinder.Result resumed;
            try { resumed=AmphibiousPathfinder.find(this,playerPosition(),goal,48,6000,maxDrop); }
            finally { resumingAir=false; }
            var shaped=resumed.found()?shape(playerPosition(),resumed.nodes()):List.<Vec3>of();
            if(!planFresh()){stop();return WalkController.State.BLOCKED;}
            controller.resume(shaped,System.currentTimeMillis());
            input.releaseAllMovement();reason=controller.status();
            return resumed.found()?WalkController.State.WALKING:WalkController.State.BLOCKED;
        }
        input.releaseAllMovement();reason=controller.status();
        if(out.state()==WaterTravelController.State.BLOCKED)reason+=" [onGround="+owner.onGround()+" inWater="+owner.isInWater()+" pose="+owner.getPose()
                +" velocity="+v+" currentBody="+ground.bodyClear(in.position(),true)+" supported="+ground.standingClear(in.position())+"]";
        if(out.state()!=WaterTravelController.State.BLOCKED&&out.state()!=WaterTravelController.State.ARRIVED&&out.state()!=WaterTravelController.State.IDLE) {
            if(!planFresh()){stop();return WalkController.State.BLOCKED;}
            if(owner.getAbilities().mayfly)((dev.veltraph.client.mixin.FlightToggleAccessor)owner).veltraph$clearFlightToggleWindow(0);
            if(publication!=null){if(publication.publishYaw(out.yaw())<=0||!planFresh()){stop();return WalkController.State.BLOCKED;}}else if(fresh())dev.veltraph.client.service.mc.McOwnedRotation.plan(client,out.yaw(),dev.veltraph.client.service.mc.McOwnedRotation.requestedPitch(client));
            if(!planFresh()){stop();return WalkController.State.BLOCKED;}input.setForwardHeld(out.forward());input.setBackHeld(out.back());input.setLeftHeld(out.left());input.setRightHeld(out.right());input.setJumpHeld(out.jump());input.setSneakHeld(out.sneak());
        }
        return switch(out.state()) { case ARRIVED -> WalkController.State.ARRIVED;case BLOCKED,IDLE -> WalkController.State.BLOCKED;default -> WalkController.State.WALKING; };
    }
    java.util.Optional<McNavigator.WaterTerminalHold> retainTerminalHold(Object authority,long expiresAt){
        long now=System.currentTimeMillis();
        if(!planFresh()||!controller.terminalHoldReady()||owner==null||client.player!=owner||client.level!=ownerLevel||!owner.isAlive()
                ||client.gui.screen()!=null||owner.containerMenu!=owner.inventoryMenu||!owner.containerMenu.getCarried().isEmpty()||client.getConnection()==null||!(authority instanceof dev.veltraph.client.core.Module module)||!module.isEnabled()
                ||dev.veltraph.client.service.mc.McOwnedRotation.authority(client)!=authority||expiresAt<now)return java.util.Optional.empty();
        if(terminalHold==null){terminalHold=new McNavigator.WaterTerminalHold(this,new WaterTerminalLease(planIdentity,authority,owner,ownerLevel,client.getConnection(),goal,controller.terminalRadius(),now,expiresAt));if(publication!=null)terminalCursor=new WaterPublicationIdentity.Cursor(planIdentity,publication.scope());}
        return planFresh()&&(terminalCursor==null||terminalCursor.current(planIdentity,publication.scope()))&&terminalHold.lease.expiresAt(expiresAt)&&terminalHold.lease.valid(planIdentity,authority,client.player,client.level,client.getConnection(),goal,controller.terminalRadius(),true,now)?java.util.Optional.of(terminalHold):java.util.Optional.empty();
    }
    WalkController.State tickTerminalHold(McNavigator.WaterTerminalHold receipt,Object authority){try{return tickTerminalOwned(receipt,authority);}catch(WaterPublicationRevokedException refused){if(receipt!=null)receipt.lease.revoke();stop();return WalkController.State.BLOCKED;}}
    private WalkController.State tickTerminalOwned(McNavigator.WaterTerminalHold receipt,Object authority){
        boolean enabled=authority instanceof dev.veltraph.client.core.Module module&&module.isEnabled()
                &&dev.veltraph.client.service.mc.McOwnedRotation.authority(client)==authority;
        if(!planFresh()||terminalCursor!=null&&!terminalCursor.current(planIdentity,publication.scope())||receipt!=terminalHold||receipt==null||!receipt.lease.valid(planIdentity,authority,client.player,client.level,client.getConnection(),goal,controller.terminalRadius(),enabled,System.currentTimeMillis()))return WalkController.State.BLOCKED;
        var v=owner.getDeltaMovement();
        var in=new WaterTravelController.Input(playerPosition(),new Vec3(v.x,v.y,v.z),owner.getYRot(),owner.onGround(),owner.isInWater(),waterAt(owner.getX(),owner.getEyeY(),owner.getZ()),
                owner.getAirSupply(),owner.getMaxAirSupply(),owner.getHealth(),owner.isAlive()&&owner.level()==ownerLevel&&client.gui.screen()==null&&owner.containerMenu==owner.inventoryMenu&&owner.containerMenu.getCarried().isEmpty()&&!owner.getAbilities().flying,owner.isInLava(),automaticSneak(),System.currentTimeMillis());
        var out=controller.tickTerminalHold(in,this);reason=controller.status();if(!planFresh()||terminalCursor!=null&&!terminalCursor.current(planIdentity,publication.scope())){receipt.lease.revoke();return WalkController.State.BLOCKED;}input.releaseAllMovement();
        if(out.state()!=WaterTravelController.State.BLOCKED){input.setForwardHeld(out.forward());input.setBackHeld(out.back());input.setLeftHeld(out.left());input.setRightHeld(out.right());input.setJumpHeld(out.jump());input.setSneakHeld(out.sneak());}
        else receipt.lease.revoke();
        return out.state()==WaterTravelController.State.BLOCKED?WalkController.State.BLOCKED:out.state()==WaterTravelController.State.ARRIVED?WalkController.State.ARRIVED:WalkController.State.WALKING;
    }
    boolean trackTerminalView(McNavigator.WaterTerminalHold receipt,Object authority,float yaw,float pitch){
        if(publication==null||!planFresh()||receipt==null||receipt!=terminalHold||terminalCursor==null
                ||!terminalCursor.current(planIdentity,publication.scope())||!receipt.lease.valid(planIdentity,authority,client.player,client.level,client.getConnection(),goal,controller.terminalRadius(),true,System.currentTimeMillis()))return false;
        var before=publication.scope();long ticket=publication.publishTrackedPose(yaw,pitch);var after=publication.scope();
        return ticket>0&&terminalCursor.published(planIdentity,before,after,ticket)&&planFresh();
    }
    /** Exact farm route path has no foreign opaque water actor. All statements are original logical resets. */
    boolean stopIssuedFarmResumeLogical(){
        if(publication!=null||!dev.veltraph.client.service.mc.McOwnedRotation.installed(client))return false;
        if(terminalCursor!=null)terminalCursor.revoke();terminalCursor=null;if(terminalHold!=null)terminalHold.lease.revoke();
        terminalHold=null;planIdentity=null;controller.stop();owner=null;ownerLevel=null;goal=null;breathingColumn=null;resumingAir=false;neutralStart=false;
        return true;
    }
    boolean issuedFarmResumeLogicalAvailable(){return publication==null&&dev.veltraph.client.service.mc.McOwnedRotation.installed(client);}
    void stop() {
        var ownPlan=planIdentity;boolean legacy=fresh()&&publication==null;
        try{if(publication!=null&&ownPlan!=null)publication.cleanupOwned();}
        finally{
            if(terminalCursor!=null)terminalCursor.revoke();terminalCursor=null;if(terminalHold!=null)terminalHold.lease.revoke();
            terminalHold=null;planIdentity=null;controller.stop();owner=null;ownerLevel=null;goal=null;breathingColumn=null;resumingAir=false;neutralStart=false;
            if(publication!=null)publication.retirePlan(ownPlan);else if(legacy)input.releaseAllMovement();
        }
    }
    dev.veltraph.client.farming.module.FarmMaintenanceState maintenanceState() {
        return dev.veltraph.client.farming.module.FarmMaintenanceState.of(this,
                "client",client,"input",input,"config",config,"ground",ground,"controller",controller.maintenanceState(),
                "goal",goal,"maxDrop",maxDrop,"seed",seed,"reason",reason,"neutralStart",neutralStart,
                "breathingColumn",breathingColumn,"resumingAir",resumingAir,"planIdentity",planIdentity,
                "terminalHold",terminalHold==null?null:terminalHold.lease.maintenanceState());
    }
    record NativeBinding(LocalPlayer player,net.minecraft.client.multiplayer.ClientLevel level) {}
    NativeBinding maintenanceBinding(){return owner==null||ownerLevel==null?null:new NativeBinding(owner,ownerLevel);}
    boolean maintenanceOwns(NativeBinding binding){return binding!=null&&owner==binding.player()&&ownerLevel==binding.level();}
    boolean maintenanceActive(){return !resumingAir&&(controller.state()==WaterTravelController.State.TRAVELLING
            ||controller.state()==WaterTravelController.State.SURFACING||controller.state()==WaterTravelController.State.REFILLING);}
    boolean maintenanceGeometryClear(Vec3 anchor){return maintenanceActive()&&controller.maintenanceGeometryClear(anchor,this);}
    boolean maintenanceRebind(NativeBinding expected){
        if(publication!=null||!maintenanceOwns(expected))return false; // Opaque farm rebind requires its separate actual Root role bridge.
        owner=client.player;ownerLevel=client.level;return true;
    }
    String status() { return reason+" target="+controller.target(); }
}
