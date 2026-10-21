package dev.veltraph.client.fishing.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.MapConfigService;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.fishing.FishingTravelIntent;
import dev.veltraph.client.fishing.module.FishingModule;
import dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver;
import dev.veltraph.client.navigation.WalkController;
import dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease;
import dev.veltraph.client.navigation.mc.McSharedMovementNavigator;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.*;

/** The installed private helper retains its original rod/loadout across an exact shared return. */
final class McFishingTravel {
    private final Minecraft client;private final ConfigService config;private final Object helper;
    private final McSharedMovementNavigator travel;
    private NativeJourney journey;private boolean starting;private long generation;
    private NativeInvocation invocation;
    private enum InvocationKind { START, TICK, STOP }
    private static final List<EquipmentSlot> GEAR=List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.OFFHAND);
    McFishingTravel(Minecraft client,ConfigService config,FarmServices services,Object helper){
        this.client=client;this.config=config;this.helper=helper;
        travel=services==null?null:new McSharedMovementNavigator(client,services,config);
    }
    private static ItemStack stableRod(ItemStack stack){var copy=stack.copy();if(copy.is(Items.FISHING_ROD))copy.remove(DataComponents.DAMAGE);return copy;}
    private boolean actualStartCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.filter(f->f.getDeclaringClass()!=McFishingTravel.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass()==helper.getClass()&&switch(first.getMethodName()){
            case "startWalking","startWalk","selectCliff","startReturn","walkBack","tick","tickWaterPublicationFence"->true;default->false;};
    });}
    boolean start(Vec3 goal,FishingTravelIntent intent){
        if(travel==null||starting||journey!=null||!actualStartCaller())return false;
        journey=null;
        if(intent.purpose()!=dev.veltraph.client.navigation.SharedMovementPolicy.Purpose.EXACT_RETURN)
            return travel.startGround(goal,intent.nodes(),intent.radius(),intent.tolerance(),intent.purpose());
        var root=VeltraphBootstrap.moduleManager();var owner=root==null?null:root.inputOwner().orElse(null);
        if(!(owner instanceof FishingModule fishing)||config!=VeltraphBootstrap.config()||!client.isSameThread()
            ||client.player==null||client.level==null||client.getConnection()==null||client.gameMode==null
            ||generation==Long.MAX_VALUE)return false;
        var declaration=root.currentModuleTickDeclaration(fishing);var publication=VeltraphBootstrap.originalRootPublicationFor(fishing);
        if(declaration==null||publication==null||!declaration.issuingCurrent(fishing))return false;
        starting=true;NativeJourney candidate=null;boolean accepted=false;
        try{
            candidate=new NativeJourney(root,fishing,declaration,publication,goal,intent,++generation);
            journey=candidate;
            var actual=beginInvocation(candidate,InvocationKind.START);
            if(actual==null)return false;
            if(!candidate.originalFieldsCurrent()||!helperCurrent(intent,goal)
                ||VeltraphBootstrap.originalRootPublicationFor(fishing)!=publication||!declaration.issuingCurrent(fishing))return false;
            accepted=travel.startGround(goal,intent.nodes(),intent.radius(),intent.tolerance(),intent.purpose());
            return accepted;
        }finally{if(!accepted&&journey==candidate)journey=null;closeInvocation(InvocationKind.START);starting=false;}
    }
    /** Exact private helper phase and saved cursor, never an enum-only source declaration. */
    private boolean helperCurrent(FishingTravelIntent intent,Vec3 goal){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingRain actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        if(helper instanceof FishingCombat actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnSourceCurrent(this,intent,goal,starting);
        return false;
    }
    private boolean helperRawCurrent(FishingTravelIntent intent,Vec3 goal){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingRain actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        if(helper instanceof FishingCombat actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnSourceRawCurrent(this,intent,goal,starting);
        return false;
    }
    private Object helperCustody(){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnCustodyIdentity();
        if(helper instanceof McFishingRain actual)return actual.nativeReturnCustodyIdentity();
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnCustodyIdentity();
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnCustodyIdentity();
        if(helper instanceof FishingCombat actual)return actual.nativeReturnCustodyIdentity();
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnCustodyIdentity();
        return null;
    }
    private Object helperAnchor(){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnAnchorIdentity();
        if(helper instanceof McFishingRain actual)return actual.nativeReturnAnchorIdentity();
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnAnchorIdentity();
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnAnchorIdentity();
        if(helper instanceof FishingCombat actual)return actual.nativeReturnAnchorIdentity();
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnAnchorIdentity();
        return null;
    }
    private Object helperProfile(){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnProfileIdentity();
        if(helper instanceof McFishingRain actual)return actual.nativeReturnProfileIdentity();
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnProfileIdentity();
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnProfileIdentity();
        if(helper instanceof FishingCombat actual)return actual.nativeReturnProfileIdentity();
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnProfileIdentity();
        return null;
    }
    private Object helperRuntime(){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnRuntimeIdentity();
        if(helper instanceof McFishingRain actual)return actual.nativeReturnRuntimeIdentity();
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnRuntimeIdentity();
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnRuntimeIdentity();
        if(helper instanceof FishingCombat actual)return actual.nativeReturnRuntimeIdentity();
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnRuntimeIdentity();
        return null;
    }
    private int helperRodSlot(){
        if(helper instanceof McFishingReturn actual)return actual.nativeReturnRodSlot();
        if(helper instanceof McFishingRain actual)return actual.nativeReturnRodSlot();
        if(helper instanceof McFishingTrash actual)return actual.nativeReturnRodSlot();
        if(helper instanceof McFishingGrapple actual)return actual.nativeReturnRodSlot();
        if(helper instanceof FishingCombat actual)return actual.nativeReturnRodSlot();
        if(helper instanceof McFishingOffense actual)return actual.nativeReturnRodSlot();
        return -1;
    }
    private final class NativeJourney {
        final ModuleManager root;final FishingModule owner;final ModuleManager.ModuleTickDeclaration declaration;
        final VeltraphBootstrap.RootOriginalInputPublication publication;final Vec3 goal;final FishingTravelIntent intent;final long serial;
        final Object player=client.player,world=client.level,connection=client.getConnection(),inventory=client.player.getInventory(),menu=client.player.inventoryMenu;
        final Object gameMode=client.gameMode,options=client.options;final int originalSlot=client.player.getInventory().getSelectedSlot(),rodSlot=helperRodSlot();
        final McPestInventoryRestorationObserver.SelectionHistory selection=McPestInventoryRestorationObserver.selectedSlotHistory(inventory);
        final MapConfigService.StoredStateSnapshot settings=config instanceof MapConfigService map?map.storedStateSnapshot()
            :config instanceof dev.veltraph.client.config.JsonConfigService json?json.storedStateSnapshot():null;
        final List<ItemStack> items,gear;final Object originalProfile=helperProfile(),originalRuntime=helperRuntime();
        final Object originalHelperCustody=helperCustody(),originalAnchor=helperAnchor();
        final Object originalEngine;final ModuleManager.LifecycleSnapshot originalEnabled;
        private LegacyTravelPublicationLease.Leg leg;private McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation;
        private NativeJourney(ModuleManager root,FishingModule owner,ModuleManager.ModuleTickDeclaration declaration,
                VeltraphBootstrap.RootOriginalInputPublication publication,Vec3 goal,FishingTravelIntent intent,long serial){
            this.root=root;this.owner=owner;this.declaration=declaration;this.publication=publication;this.goal=goal;this.intent=intent;this.serial=serial;
            originalEngine=owner.runtimeIdentity();originalEnabled=declaration.beforeSnapshot();
            var copied=new ArrayList<ItemStack>(36);for(int i=0;i<36;i++)copied.add(i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i).copy());
            items=List.copyOf(copied);gear=GEAR.stream().map(slot->client.player.getItemBySlot(slot).copy()).toList();
        }
        private boolean originalFieldsCurrent(){return nativeStoredFieldsCurrent()&&owner.isEnabled();}
        private boolean originalCleanupFieldsCurrent(){return nativeStoredFieldsCurrent()&&root.ownCleanupLifecycleCurrent(originalEnabled,owner);}
        /** All original source/PWC/native fields, without component comparison or registry visitor. */
        private boolean nativeStoredRawCurrent(){
            if(journey!=this||serial!=generation||invocation!=null&&(invocation.closed||invocation.revoked)||originalHelperCustody==null||helperCustody()!=originalHelperCustody||originalAnchor==null||helperAnchor()!=originalAnchor||helperRodSlot()!=rodSlot||originalProfile==null||originalRuntime==null||helperProfile()!=originalProfile||helperRuntime()!=originalRuntime||root!=VeltraphBootstrap.moduleManager()||config!=VeltraphBootstrap.config()
                ||!client.isSameThread()||owner.runtimeIdentity()!=originalEngine||originalEngine==null||client.player!=player||client.level!=world||client.getConnection()!=connection
                ||client.gameMode!=gameMode||client.options!=options||client.player.getInventory()!=inventory
                ||client.player.inventoryMenu!=menu||client.player.containerMenu!=menu||client.gui.screen()!=null
                ||!client.player.inventoryMenu.getCarried().isEmpty()||!client.player.isAlive()||client.player.isPassenger()
                ||client.player.isScoping()||client.player.fishing!=null||client.player.isUsingItem()||client.isPaused()
                ||client.getCameraEntity()!=player||client.player.level()!=world||client.level.tickRateManager().isEntityFrozen(client.player)
                ||settings==null||!settings.current()||rodSlot<0||rodSlot>8||originalSlot<0||originalSlot>8||selection==null
                ||!items.get(rodSlot).is(Items.FISHING_ROD))return false;
            return true;
        }
        /** Every component comparison precedes the same original source and native field veto. */
        private boolean nativeStoredFieldsCurrent(){
            var expectedInvocation=invocation;
            if(!nativeStoredRawCurrent())return false;
            for(int i=0;i<36;i++)if(!ItemStack.matches(items.get(i),i==rodSlot?stableRod(client.player.getInventory().getItem(i)):client.player.getInventory().getItem(i)))return false;
            for(int i=0;i<GEAR.size();i++)if(!ItemStack.matches(gear.get(i),client.player.getItemBySlot(GEAR.get(i))))return false;
            return nativeStoredRawCurrent()&&invocation==expectedInvocation;
        }
        private boolean selectionCurrent(){return affiliation==null?selection.current()&&client.player.getInventory().getSelectedSlot()==originalSlot
            :affiliation.nativeCurrent()&&affiliation.selectedSlotCurrent(originalSlot,selection);}
        private boolean cleanupSelectionCurrent(){return affiliation==null?selection.current()&&client.player.getInventory().getSelectedSlot()==originalSlot
            :affiliation.cleanupCurrent()&&affiliation.cleanupSelectedSlotCurrent(originalSlot,selection);}
        private boolean rawSelectionCurrent(){return affiliation==null?selection.current()&&client.player.getInventory().getSelectedSlot()==originalSlot
            :affiliation.rawCurrent()&&affiliation.rawSelectedSlotCurrent(originalSlot,selection);}
        private boolean cleanupRawSelectionCurrent(){return affiliation==null?selection.current()&&client.player.getInventory().getSelectedSlot()==originalSlot
            :affiliation.cleanupRawCurrent()&&affiliation.cleanupRawSelectedSlotCurrent(originalSlot,selection);}
        private boolean positiveRawCurrent(){return nativeStoredRawCurrent()&&owner.isEnabled()&&helperRawCurrent(intent,goal)
            &&(invocation==null||!invocation.revoked&&invocation.kind!=InvocationKind.STOP)&&rawSelectionCurrent()
            &&(declaration.issuingCurrent(owner)||declaration.normalReturnedCurrent(owner))
            &&VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,client);}
        private boolean nativeCurrent(){
            var expectedInvocation=invocation;
            return helperCurrent(intent,goal)&&originalFieldsCurrent()&&selectionCurrent()
                &&(declaration.issuingCurrent(owner)||declaration.normalReturnedCurrent(owner))
                &&VeltraphBootstrap.originalNativeGuiAffiliationCurrent(publication,client)
                &&positiveRawCurrent()&&invocation==expectedInvocation;
        }
    }
    /** Private actual helper invocation; failed helper state alone can never issue cleanup. */
    private final class NativeInvocation {
        private final NativeJourney original;private final InvocationKind kind;
        private final ModuleManager.ModuleTickDeclaration tick;private final ModuleManager.PendingOwnDisable disable;
        private boolean closed,revoked;
        private NativeInvocation(NativeJourney original,InvocationKind kind,ModuleManager.ModuleTickDeclaration tick,ModuleManager.PendingOwnDisable disable){
            this.original=original;this.kind=kind;this.tick=tick;this.disable=disable;
        }
        private boolean current(){return original.originalCleanupFieldsCurrent()
            &&VeltraphBootstrap.originalNormalReturnCleanupAffiliationCurrent(original.publication,original.owner,client,original.originalEnabled,tick,disable)
            &&rawCurrent();}
        private boolean rawCurrent(){return invocation==this&&!closed&&!revoked&&journey==original&&original.nativeStoredRawCurrent()
            &&original.root.ownCleanupLifecycleCurrent(original.originalEnabled,original.owner)
            &&VeltraphBootstrap.originalNormalReturnCleanupAffiliationCurrent(original.publication,original.owner,client,original.originalEnabled,tick,disable);}
    }
    private boolean actualInvocationCaller(InvocationKind kind){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.filter(f->f.getDeclaringClass()!=McFishingTravel.class).findFirst().orElse(null);
        if(first==null||first.getDeclaringClass()!=helper.getClass())return false;
        return switch(kind){
            case START->Set.of("startWalking","startReturn","walkBack","tick","tickWaterPublicationFence").contains(first.getMethodName());
            case TICK->Set.of("walk","returnTick","tick","tickWaterPublicationFence").contains(first.getMethodName());
            case STOP->Set.of("release","returnTick","tick","stop","stopReturnTravel").contains(first.getMethodName());
        };
    });}
    private NativeInvocation beginInvocation(NativeJourney original,InvocationKind kind){
        if(invocation!=null){invocation.revoked=true;return null;}
        if(original==null||!actualInvocationCaller(kind)||!original.originalCleanupFieldsCurrent())return null;
        var disable=original.root.pendingActualOwnDisable(original.owner).orElse(null);
        var tick=disable==null?original.root.currentModuleTickDeclaration(original.owner):null;
        var actual=new NativeInvocation(original,kind,tick,disable);invocation=actual;
        if(!actual.current()){invocation=null;actual.revoked=true;return null;}return actual;
    }
    private void closeInvocation(InvocationKind kind){var actual=invocation;
        if(actual!=null&&actual.kind==kind){actual.closed=true;invocation=null;}}
    final class NativeCleanupSource {
        private final NativeJourney original;private final NativeInvocation actual;
        private final LegacyTravelPublicationLease.Leg leg;private final McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation;
        private NativeCleanupSource(NativeJourney original,NativeInvocation actual,LegacyTravelPublicationLease.Leg leg){
            this.original=original;this.actual=actual;this.leg=leg;affiliation=original.affiliation;
        }
        boolean nativeCurrent(){
            if(affiliation==null||!actual.current()||!affiliation.cleanupCurrent()||!original.cleanupSelectionCurrent()
                ||!original.originalCleanupFieldsCurrent())return false;
            return actual.original==original&&original==journey&&original.leg==leg&&original.affiliation==affiliation
                &&affiliation.matchesActualLeg(leg)&&affiliation.cleanupRawCurrent()&&original.cleanupRawSelectionCurrent()
                &&original.nativeStoredRawCurrent()&&actual.rawCurrent();
        }
    }
    NativeCleanupSource captureNativeCleanupSource(LegacyTravelPublicationLease.Leg exact){
        var original=journey;var actual=invocation;
        if(original==null||actual==null||actual.original!=original||exact==null||exact!=original.leg||!actual.current())return null;
        var result=new NativeCleanupSource(original,actual,exact);return result.nativeCurrent()?result:null;
    }
    final class NativeReturnSource {
        private final NativeJourney original;private final LegacyTravelPublicationLease.Leg leg;
        private final McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation;
        private NativeReturnSource(NativeJourney original,LegacyTravelPublicationLease.Leg leg,McSharedMovementNavigator.NativeNormalReturnAffiliation affiliation){this.original=original;this.leg=leg;this.affiliation=affiliation;}
        ModuleManager.ModuleTickDeclaration declaration(){return original.declaration;}
        VeltraphBootstrap.RootOriginalInputPublication publication(){return original.publication;}
        boolean nativeCurrent(){return original.nativeCurrent()&&original==journey&&leg==original.leg&&affiliation==original.affiliation
            &&affiliation.matchesActualLeg(leg)&&affiliation.rawCurrent()&&original.positiveRawCurrent();}
    }
    NativeReturnSource captureNativeReturnSource(LegacyTravelPublicationLease.Leg exact){
        var original=journey;
        if(original==null||exact==null||!helperCurrent(original.intent,original.goal)||!original.originalFieldsCurrent())return null;
        var affiliation=original.affiliation;
        if(affiliation==null){affiliation=travel.captureNativeNormalReturnAffiliation(exact,original.owner,config,original.goal);
            if(affiliation==null)return null;original.leg=exact;original.affiliation=affiliation;}
        else if(original.leg!=exact){
            // Only Shared's real clean predecessor retirement and actual beginAfter can certify this successor.
            if(!affiliation.matchesActualLeg(exact)||affiliation.predecessorIdentity()!=original.leg)return null;
            original.leg=exact;
        }
        var result=new NativeReturnSource(original,exact,affiliation);return result.nativeCurrent()?result:null;
    }
    /** Passive loan history observation. This invokes no role, source, shared ownership or slot permission query. */
    boolean rodSelectionCurrent(int expected){var original=journey;if(original==null)return false;var expectedInvocation=invocation;
        return original.originalFieldsCurrent()&&original.selectionCurrent()&&original==journey&&expected==original.originalSlot
            &&original.nativeStoredRawCurrent()&&original.rawSelectionCurrent()&&invocation==expectedInvocation;}
    WalkController.State tick(){
        if(travel==null)return WalkController.State.BLOCKED;
        var actual=journey==null?null:beginInvocation(journey,InvocationKind.TICK);
        try{return switch(travel.tick()){case ACTIVE->WalkController.State.WALKING;case ARRIVED->WalkController.State.ARRIVED;case FAILED->WalkController.State.BLOCKED;};}
        finally{if(actual!=null)closeInvocation(InvocationKind.TICK);}
    }
    void stop(){
        var actual=journey==null?null:beginInvocation(journey,InvocationKind.STOP);
        try{if(travel!=null)travel.stop();}
        finally{if(actual!=null)closeInvocation(InvocationKind.STOP);journey=null;}
    }
    String diagnostics(){return travel==null?"Actual shared Fishing services unavailable":travel.status();}
    boolean consumeExpectedTeleport(Vec3 position,long now){return travel!=null&&travel.hasActiveLeg()&&travel.consumeExpectedTeleport(position,now);}
}
