package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McServerCommandService;
import dev.veltraph.client.world.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;

/** Scoped source mixed-route execution. Explicit legs remain delegated to the accepted navigators. */
public final class McAutomaticTravelNavigator implements AutomaticTravelPolicy.Scene {
    private final Minecraft client;private final FarmServices services;private final ConfigService config;private final McNavigator terrain;private final McTravelNavigator legs;
    private AutomaticTravelPolicy.Request request;private TravelAtlas atlas;private McAutomaticTravelAccess access;private McAutomaticTravelAccess.Snapshot owner;
    private AutomaticTravelPolicy.Plan plan;private int index,recoveries;private final Set<String> failed=new HashSet<>();private McTravelNavigator.State state=McTravelNavigator.State.FAILED;
    private final LegacyTravelPublicationLease authority;
    private LegacyTravelPublicationLease.Leg ownLeg,lastOwnRetired;private LegacyTravelPublicationIdentity.State originalRoot;
    private long journey,nonce;private boolean childActive,childContinuationPending,retirementRefused;private dev.veltraph.client.movement.device.DeviceUseReceipt.Applied acceptedEther;private dev.veltraph.client.movement.device.DeviceUseReceipt.Issue acceptedEtherIssue;private AutomaticCommandReceipt.Issue commandIssue;private AutomaticCommandReceipt.Applied acceptedCommand;
    private boolean issued,rejected,ackConsumed,expectedIssued;private long issuedAt;private TravelAtlas.Shortcut expected;private String reason="Idle";
    public McAutomaticTravelNavigator(Minecraft client,FarmServices services,ConfigService config) {this.client=client;this.services=services;this.config=config;terrain=new McNavigator(client,services.input(),config);legs=new McTravelNavigator(client,services,config);authority=new LegacyTravelPublicationLease(client,services,config,()->config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));}
    private Vec3 position(){var p=client.player;return new Vec3(p.getX(),p.getY(),p.getZ());}
    private boolean owned(McAutomaticTravelAccess.Snapshot s){return s.valid()&&s.player()==owner.player()&&s.world()==owner.world()&&s.connection()==owner.connection()
            &&s.area()==request.area()&&s.dimension().equals(request.dimension())&&s.revision()==owner.revision();}
    public boolean start(AutomaticTravelPolicy.Request request,TravelAtlas atlas,McAutomaticTravelAccess access) {
        stop();if(retirementRefused||request==null||atlas==null||access==null)return false;this.request=request;this.atlas=atlas;this.access=access;access.bind(atlas);owner=access.snapshot();
        if(!owner.valid()||VeltraphBootstrap.gameContext()==null||!VeltraphBootstrap.gameContext().current().isArea(request.area())||owner.area()!=request.area()||!owner.dimension().equals(request.dimension()))return fail("Known actual scoped player required");
        ownLeg=authority.begin(++journey);originalRoot=ownLeg==null?null:ownLeg.commandContext();
        if(originalRoot==null)return fail("Known actual registered automatic Root required");
        state=McTravelNavigator.State.ACTIVE;return plan();
    }
    private boolean plan(){plan=AutomaticTravelPolicy.select(this,request,atlas,access.snapshot().capabilities(),position(),failed);index=0;
        if(!plan.found())return fail(plan.reason());reason="Selected "+(plan.shortcutId().isEmpty()?plan.legs().getFirst().physical().kind():plan.shortcutId())+" cost="+plan.cost();return dispatch();}
    private boolean lineageCurrent(){return ownLeg!=null&&ownLeg.current()||childActive&&legs.currentOwnedLegacyLeg()||!childActive&&ownLeg==null&&lastOwnRetired!=null&&lastOwnRetired.retirementContinuationContext()!=null;}
    private boolean rootCurrent(){return access!=null&&rootCurrent(access.snapshot());}
    private boolean rootCurrent(McAutomaticTravelAccess.Snapshot snapshot){
        var root=VeltraphBootstrap.moduleManager();return originalRoot!=null&&owned(snapshot)&&client.isSameThread()&&VeltraphBootstrap.gameContext()!=null&&VeltraphBootstrap.gameContext().current().isArea(request.area())
            &&root==originalRoot.root()&&root.get(((dev.veltraph.client.core.Module)originalRoot.parent()).id()).orElse(null)==originalRoot.parent()
            &&root.inputOwner().orElse(null)==originalRoot.parent()&&McOwnedRotation.authority(client)==originalRoot.parent()
            &&VeltraphBootstrap.config()==originalRoot.config()&&client.player==originalRoot.player()&&client.level==originalRoot.world()&&client.getConnection()==originalRoot.connection();
    }
    private boolean closeOwn(){
        if(ownLeg==null)return !retirementRefused;var old=ownLeg;ownLeg=null;lastOwnRetired=old;childContinuationPending=false;
        try{boolean clean=old.retireOwned().cleanSuccess();retirementRefused|=!clean;return clean;}
        catch(RuntimeException unconfirmed){retirementRefused=true;return false;}
    }
    private boolean reacquire(){
        if(retirementRefused||!rootCurrent()||ownLeg!=null||childActive)return false;
        var predecessor=childContinuationPending?legs.retiredContinuationLeg():lastOwnRetired;
        var before=predecessor==null?null:predecessor.retirementContinuationContext();if(before==null)return false;
        ownLeg=authority.beginAfter(++journey,predecessor);var next=ownLeg==null?null:ownLeg.commandContext();
        childContinuationPending=false;
        return AutomaticCommandReceipt.continuation(before,next)&&next.root()==originalRoot.root()&&next.parent()==originalRoot.parent()&&next.player()==originalRoot.player()
            &&next.world()==originalRoot.world()&&next.connection()==originalRoot.connection()&&next.config()==originalRoot.config()&&next.revision()==originalRoot.revision();
    }
    private boolean retireChild(){
        if(!childActive)return !retirementRefused;childActive=false;
        try{boolean clean=legs.retireForHandoff();childContinuationPending=clean;retirementRefused|=!clean;return clean;}
        catch(RuntimeException unconfirmed){retirementRefused=true;return false;}
    }
    private boolean dispatch() {
        var leg=plan.legs().get(index);issued=rejected=false;
        if(!rootCurrent()||ownLeg==null||!ownLeg.current())return fail("Automatic dispatch custody expired");
        if(leg.physical()!=null) {
            if(!Double.isFinite(distance(leg.physical().kind(),position(),leg.destination())))return recover(null,"Physical geometry changed before leg");
            if(!ownLeg.current()||!closeOwn())return fail("Automatic physical handoff cleanup refused");
            childActive=true;
            return legs.startLegacyHop(new LocalRoute.Node(leg.physical().kind(),leg.destination(),0,0),lastOwnRetired)||recover(null,"Physical start refused");
        }
        var s=leg.shortcut();var caps=access.snapshot().capabilities();
        if(position().distanceTo(s.entrance())>.4||!shortcutRejection(s,position()).isEmpty()||!permitted(s,caps)||!suffixClear(s))return recover(s,"Shortcut authorization/geometry changed before dispatch");
        if(!ownLeg.current()||!rootCurrent())return fail("Shortcut custody changed before dispatch");
        expected=s;expectedIssued=false;issuedAt=System.currentTimeMillis();ackConsumed=false;acceptedCommand=null;acceptedEther=null;acceptedEtherIssue=null;
        if(s.kind()==TravelAtlas.Kind.ETHERWARP){
            if(!closeOwn())return fail("Automatic Ether handoff cleanup refused");childActive=true;
            return legs.startLegacyHop(new LocalRoute.Node(LocalRoute.Kind.ETHERWARP,s.arrival(),0,0,s.target(),s.face()),lastOwnRetired)||recover(s,"Ether start refused");
        }
        var context=ownLeg.commandContext();if(context==null)return fail("Command Root expired before issue");
        commandIssue=new AutomaticCommandReceipt.Issue(context,s,++nonce,NativeAutomaticCommandReceipt.baseline(client),issuedAt,issuedAt+10000);
        if(!NativeAutomaticCommandReceipt.arm(client,commandIssue,ownLeg))return recover(s,"Fast warp issue unavailable");
        final var sendingIssue=commandIssue;final var sendingLeg=ownLeg;
        NativeAutomaticCommandReceipt.open(sendingIssue);
        try {
            // Every geometry/access callback precedes the final real Root/Leg context fence.
            boolean ready=AutomaticCommandReceipt.readyToSend(sendingIssue,()->{
                boolean geometry=suffixClear(s);var fresh=access.snapshot();
                return geometry&&owned(fresh)&&permitted(s,fresh.capabilities())&&rootCurrent(fresh)
                    &&position().distanceTo(s.entrance())<=.4&&NativeAutomaticCommandReceipt.baseline(client)==sendingIssue.baseline();
            },()->sendingLeg==ownLeg&&sendingIssue==commandIssue?sendingLeg.commandContext():null);
            // A reentrant replacement is a different transaction, including its cleanup rights.
            if(sendingLeg!=ownLeg||sendingIssue!=commandIssue)return false;
            if(!ready)return recover(s,"Fast warp pre-send proof expired");
            boolean nativeReady=NativeAutomaticCommandReceipt.attempted(sendingIssue);
            if(sendingLeg!=ownLeg||sendingIssue!=commandIssue)return false;
            if(!nativeReady)return recover(s,"Fast warp native submission window expired");
            issued=true;
            boolean normal=new McServerCommandService(client).sendCommand(sendingIssue.command());
            if(normal)NativeAutomaticCommandReceipt.returnedNormally(sendingIssue);
            if(sendingLeg!=ownLeg||sendingIssue!=commandIssue)return false;
            expectedIssued=NativeAutomaticCommandReceipt.submitted(sendingIssue);
            return expectedIssued||fail("Unknown dispatched transport outcome; native command submission unproved");
        }catch(RuntimeException failure){
            if(sendingLeg!=ownLeg||sendingIssue!=commandIssue)return false;
            return fail("Unknown dispatched transport outcome; ordinary command failed");
        }finally{NativeAutomaticCommandReceipt.close(sendingIssue);}
    }
    private boolean permitted(TravelAtlas.Shortcut s,AutomaticTravelPolicy.Access a){return s.kind()==TravelAtlas.Kind.ETHERWARP?a.etherTool()&&a.mana()>=s.manaCost():a.permissions().contains(s.permissionKey());}
    private boolean suffixClear(TravelAtlas.Shortcut s){var next=plan.legs().get(index+1).physical();return Double.isFinite(distance(next.kind(),s.arrival(),request.goal()));}
    private boolean recover(TravelAtlas.Shortcut s,String why) {
        boolean retired=retireChild();boolean ownClosed=closeOwn();
        if(commandIssue!=null){NativeAutomaticCommandReceipt.retire(commandIssue);commandIssue=null;}
        if(!retired||!ownClosed)return fail("Automatic recovery retirement refused");if(s!=null)failed.add(s.id());
        if(issued&&!rejected)return fail("Unknown dispatched transport outcome: "+why);
        expected=null;expectedIssued=false;
        if(recoveries++>=request.options().recoveries())return fail(why);
        reason="Replanning original goal after "+why;if(!reacquire())return fail("Original automatic Root unavailable for recovery");return plan();
    }
    public McTravelNavigator.State tick() {
        if(state!=McTravelNavigator.State.ACTIVE)return state;
        var snapshot=access.snapshot();if(!rootCurrent()||!owned(snapshot)){fail("Actual owner/area/configuration changed");return state;}
        var leg=plan.legs().get(index);
        if(leg.physical()!=null) {
            var result=legs.tick();if(result==McTravelNavigator.State.FAILED){recover(null,"Physical travel failed");return state;}
            if(result==McTravelNavigator.State.ARRIVED){if(!retireChild()||!reacquire())fail("Original automatic Root unavailable after physical arrival");else advance();}return state;
        }
        var s=leg.shortcut();
        if(s.kind()==TravelAtlas.Kind.ETHERWARP) {
            if(!issued&&(!permitted(s,snapshot.capabilities())||!suffixClear(s)||!shortcutRejection(s,position()).isEmpty())){recover(s,"Pre-use proof expired");return state;}
            McTravelNavigator.State result;
            try{result=legs.tick();}catch(RuntimeException unconfirmed){issued|=legs.etherwarpUseDispatched();expectedIssued|=issued;fail(issued?"Unknown dispatched transport outcome; native Ether use failed":"Ether helper failed before use");return state;}
            issued|=legs.etherwarpUseDispatched();expectedIssued|=issued;
            if(result==McTravelNavigator.State.FAILED){recover(s,"Ether attempt failed");return state;}
            if(result==McTravelNavigator.State.ARRIVED){
                if(!Double.isFinite(distance(plan.legs().get(index+1).physical().kind(),position(),request.goal()))){fail("Actual shortcut suffix is obstructed or unloaded");return state;}
                acceptedEther=legs.etherwarpAppliedReceipt();acceptedEtherIssue=legs.etherwarpIssue();var latestEther=dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.current(client);
                if(acceptedEther==null||acceptedEtherIssue==null||latestEther==null||latestEther.serial()!=acceptedEther.serial()||!acceptedEtherIssue.context().same(acceptedEther.context())
                    ||acceptedEtherIssue.nonce()!=acceptedEther.nonce()||acceptedEtherIssue.domain()!=acceptedEther.domain()
                    ||acceptedEther.serial()<=acceptedEtherIssue.baseline()||acceptedEther.receivedAt()<acceptedEtherIssue.issuedAt()
                    ||acceptedEther.receivedAt()>=acceptedEtherIssue.deadline()){fail("Ether arrival lacks typed native receipt");return state;}
                if(!retireChild()||!reacquire())fail("Original automatic Root unavailable after Ether arrival");else advance();return state;
            }
        }else {
            if(ownLeg==null||!ownLeg.current()){fail("Command scope superseded");return state;}
            var applied=NativeAutomaticCommandReceipt.applied(commandIssue);
            if(NativeAutomaticCommandReceipt.revoked(commandIssue)){fail("Unknown dispatched transport outcome; command receipt revoked");return state;}
            if(applied!=null&&applied.serial()!=NativeAutomaticCommandReceipt.baseline(client)){fail("Unknown dispatched transport outcome; command arrival superseded");return state;}
            if(applied!=null&&client.player.onGround()&&terrain.standingClear(position())) {
                if(!ownLeg.current()||!Double.isFinite(distance(plan.legs().get(index+1).physical().kind(),position(),request.goal()))){fail("Actual shortcut suffix is obstructed or unloaded");return state;}
                acceptedCommand=applied;NativeAutomaticCommandReceipt.retire(commandIssue);commandIssue=null;
                advance();return state;
            }
        }
        if(rejected){recover(s,"Received configured rejection");return state;}
        if(System.currentTimeMillis()-issuedAt>10000)fail("Unknown dispatched transport outcome; no retry");
        return state;
    }
    private void advance() {
        if(++index<plan.legs().size()){dispatch();return;}
        if(position().horizontalDistanceTo(request.goal())>=.3||Math.abs(position().y()-request.goal().y())>=.2||!terrain.bodyClear(position(),false)) {fail("Actual final goal differs");return;}
        state=McTravelNavigator.State.ARRIVED;reason="Observed original goal reached cost="+plan.cost();if(!closeOwn())fail("Final automatic retirement refused");
    }
    private boolean fail(String why){state=McTravelNavigator.State.FAILED;reason=why;retireChild();closeOwn();if(commandIssue!=null){NativeAutomaticCommandReceipt.retire(commandIssue);commandIssue=null;}return false;}
    public void onServerMessage(String message){if(access!=null)access.onServerMessage(message);if(state==McTravelNavigator.State.ACTIVE&&expected!=null&&issued&&message!=null&&!expected.rejectionReceipt().isBlank()
            &&message.replaceAll("§.","").strip().equals(expected.rejectionReceipt()))rejected=true;}
    public void onActionBar(String message){if(access!=null)access.onActionBar(message);}
    public boolean consumeExpectedTeleport(Vec3 p,long now){
        if(state==McTravelNavigator.State.FAILED||expected==null||!expectedIssued||ackConsumed||!rootCurrent()||!lineageCurrent()||now<issuedAt||now-issuedAt>10000)return false;
        if(expected.kind()==TravelAtlas.Kind.ETHERWARP){
            if(acceptedEther!=null){var latest=dev.veltraph.client.movement.device.NativeDeviceTeleportReceipt.current(client);if(latest==null||latest.serial()!=acceptedEther.serial()||now>=acceptedEtherIssue.deadline()||acceptedEther.context().parent()!=originalRoot.parent()||acceptedEther.context().player()!=originalRoot.player()||acceptedEther.context().world()!=originalRoot.world()||acceptedEther.context().connection()!=originalRoot.connection()||acceptedEther.context().policy()!=originalRoot.config()||p==null||p.distanceTo(expected.arrival())>=.9)return false;ackConsumed=true;return true;}
            boolean consumed=childActive&&legs.consumeExpectedTeleport(p,now);if(consumed)ackConsumed=true;return consumed;
        }
        var receipt=acceptedCommand!=null?acceptedCommand:NativeAutomaticCommandReceipt.applied(commandIssue);
        if(receipt==null||receipt.serial()!=NativeAutomaticCommandReceipt.baseline(client)||p==null||p.distanceTo(expected.arrival())>=.9)return false;
        ackConsumed=true;return true;
    }
    /** Logical automatic transaction stays visible through its child handoffs. */
    public boolean hasLineage(){return originalRoot!=null;}
    public boolean retirementRefused(){return retirementRefused;}
    /** Cancellation-only proof for the enclosing Root's local finite stop, never movement admission. */
    public LegacyTravelPublicationIdentity.State postRetirementOwnParentContext(){
        if(!retirementRefused)return null;var own=lastOwnRetired==null?null:lastOwnRetired.postRetirementOwnParentContext();
        return own!=null?own:legs.postLegacyRetirementOwnParentContext();
    }
    public AutomaticTravelPolicy.Plan selectedPlan(){return plan;}
    public Set<String> failedShortcuts(){return Set.copyOf(failed);}
    public void stop(){
        try{retireChild();closeOwn();if(commandIssue!=null)NativeAutomaticCommandReceipt.retire(commandIssue);}
        finally{commandIssue=null;acceptedCommand=null;acceptedEther=null;acceptedEtherIssue=null;originalRoot=null;childContinuationPending=false;if(!retirementRefused)lastOwnRetired=null;request=null;atlas=null;access=null;owner=null;plan=null;expected=null;failed.clear();index=recoveries=0;issued=rejected=ackConsumed=expectedIssued=false;state=McTravelNavigator.State.FAILED;reason="Stopped";}
    }
    public String status(){return "Automatic travel: "+state+" | "+reason+" | leg="+index+" failed="+failed;}
    private boolean swept(Vec3 a,Vec3 b){int n=Math.max(1,(int)Math.ceil(a.distanceTo(b)*4));for(int i=0;i<=n;i++)if(!terrain.bodyClear(TravelPath.lerp(a,b,(double)i/n),false))return false;return true;}
    @Override public double distance(LocalRoute.Kind kind,Vec3 from,Vec3 to) {
        if(!terrain.bodyClear(from,false)||!terrain.bodyClear(to,false))return Double.POSITIVE_INFINITY;
        List<Vec3> points;
        if(kind==LocalRoute.Kind.WALK){if(!terrain.standingClear(from)||!terrain.standingClear(to))return Double.POSITIVE_INFINITY;
            var r=GroundPathfinder.find(terrain,GroundPathfinder.node(from),GroundPathfinder.node(to),6000,48);if(!r.found())return Double.POSITIVE_INFINITY;
            var p=new ArrayList<Vec3>();for(var node:r.steps())p.add(terrain.position(node));p.add(to);Vec3 previous=from;for(var point:p){if(!terrain.stepClear(previous,point))return Double.POSITIVE_INFINITY;previous=point;}points=p;
        }else if(kind==LocalRoute.Kind.FLY){if(client.player==null||!client.player.getAbilities().mayfly)return Double.POSITIVE_INFINITY;
            Vec3 first=terrain.standingClear(from)?new Vec3(from.x(),from.y()+1,from.z()):from;
            Vec3 last=terrain.standingClear(to)?new Vec3(to.x(),to.y()+1.25,to.z()):to;if(!swept(from,first)||!swept(last,to))return Double.POSITIVE_INFINITY;
            var r=FlightPathfinder.find(new FlightPathfinder.Airspace(){public boolean clear(BlockPos p){return terrain.bodyClear(new Vec3(p.x()+.5,p.y(),p.z()+.5),false);}public boolean poseClear(Vec3 p){return terrain.bodyClear(p,false);}},first,last,64,12000,this::swept);
            if(!r.found())return Double.POSITIVE_INFINITY;var p=new ArrayList<Vec3>();p.add(first);p.addAll(r.nodes());p.add(to);points=p;
        }else return Double.POSITIVE_INFINITY;
        double result=0;Vec3 previous=from;for(var p:points){result+=previous.distanceTo(p);previous=p;}return result;
    }
    @Override public String shortcutRejection(TravelAtlas.Shortcut s,Vec3 from) {
        if(!terrain.standingClear(from)||!terrain.standingClear(s.arrival()))return "Actual/provider feet lack loaded support";
        if(s.kind()==TravelAtlas.Kind.FAST_WARP)return "";
        var target=new net.minecraft.core.BlockPos(s.target().x(),s.target().y(),s.target().z());
        if(!client.level.getChunkSource().hasChunk(target.getX()>>4,target.getZ()>>4)||client.level.getBlockState(target).getCollisionShape(client.level,target).isEmpty())return "Target unloaded/empty";
        var eye=new net.minecraft.world.phys.Vec3(from.x(),from.y()+client.player.getEyeHeight(),from.z());
        var point=net.minecraft.world.phys.Vec3.atCenterOf(target).add(s.face().dx()*.499,s.face().dy()*.499,s.face().dz()*.499);
        if(eye.distanceTo(point)>100)return "Target exceeds source acquisition range";
        var hit=client.level.clip(new ClipContext(eye,point,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,client.player));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(target)&&hit.getDirection().name().equals(s.face().name())?"":"Exact shortcut ray/face blocked";
    }
}
