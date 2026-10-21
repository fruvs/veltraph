package dev.veltraph.client.mining.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.mining.vein.VeinNativePickupAdapter;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.BlockPos;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;

/** Real existing shared Leg owns every effect. The loot association adds refusal only. */
public final class VeinCollectionMovement {
    private final Minecraft client;private final McMiningServices service;private final ConfigService config;
    private final LegacyTravelPublicationLease publication;
    private Association active,retained;private ParentContinuation continuation;private Object consumedEnable;private ApproachOwnership approach;private ParentCameraPublication parentCamera;private long parentCameraVersion;private VeinNativePickupAdapter.CollectionRequest refusedRequest;
    private final dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch cleanupDispatch=new dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch();
    private long generation;private AssociationSnapshot inactiveSnapshot;private boolean inProgress;private String failure="none";
    VeinCollectionMovement(Minecraft client,McMiningServices service,FarmServices farm,ConfigService config){
        this.client=client;this.service=service;this.config=config;
        publication=new LegacyTravelPublicationLease(client,farm,config,()->config.revision(TravelConfig.TRAVEL_APPROACH_MODE).orElse(-1));
    }
    void tick(BlockPos source,VeinNativePickupAdapter pickup){
        if(inProgress){if(active!=null){active.revoked=true;refusedRequest=active.request;}return;}
        inProgress=true;try{tickOwned(source,pickup);}finally{inProgress=false;}
    }
    private void tickOwned(BlockPos source,VeinNativePickupAdapter pickup){
        if(active!=null){
            var exact=active;
            // Exact native TAKE retires movement before inventory adoption; it grants no pickup credit.
            // The original ledger/deadline must still settle the later full36 receipt.
            if(!exact.revoked&&exact.request.takenObservationLeaf()&&active==exact){stop();return;}
            if(!exact.request.current()||active!=exact||exact.revoked){refusedRequest=exact.request;stop();return;}
            var from=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());
            var fence=new DynamicRouteQueryFence(System::nanoTime,exact.request::nativeLeaf,50_000_000L);
            if(!fence.query(()->exact.navigator.standable(exact.standing))
                ||!terminalEnvelope(exact.navigator,fence,from,client.player.getBoundingBox(),exact.goal,exact.request.itemBox())
                ||!exact.request.current()||active!=exact){refusedRequest=exact.request;stop();return;}
            // Immutable watched scene remains valid across own motion; any relevant write revokes it.
            if(exact.scene==null||!exact.nativeLeaf()||!exact.leg.current()){refusedRequest=exact.request;stop();return;}
            var state=exact.navigator.tick();if(state==WalkController.State.BLOCKED){failure=exact.navigator.diagnostics();refusedRequest=exact.request;stop();return;}
            exact.attest();return;
        }
        if(generation==Long.MAX_VALUE){failure="Collection generation exhausted";return;}
        var request=pickup.collectionRequest(service).orElse(null);if(request==null||request==refusedRequest)return;
        if(!source.equals(request.source())||!request.current())return;
        long admitted=++generation;
        var from=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());var fromBox=client.player.getBoundingBox();
        var itemBox=request.itemBox();BlockPos selected=null;Vec3 selectedGoal=null;double cost=Double.POSITIVE_INFINITY;
        var fence=new DynamicRouteQueryFence(System::nanoTime,()->generation==admitted&&active==null&&request.nativeLeaf()
            &&new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(from)&&client.player.getBoundingBox().equals(fromBox),50_000_000L);
        var geometry=new McNavigator(client,new dev.veltraph.client.service.stub.NoOpInputService(),config);
        // Original eight neighbors; a previously typed service may also use the supported source.
        // Every candidate retains the actual native pickup and body proofs.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            if(dx==0&&dz==0&&!cleanupDispatch.typed())continue;var cell=source.add(dx,0,dz);var center=geometry.position(cell);
            var goal=new Vec3(center.x()+Math.signum((itemBox.minX+itemBox.maxX)/2-center.x())*.10,center.y(),center.z()+Math.signum((itemBox.minZ+itemBox.maxZ)/2-center.z())*.10);
            if(Math.abs(from.y()-cell.y())>.12||Math.abs(from.y()-goal.y())>.12||from.horizontalDistanceTo(goal)>2.5
                ||!fence.query(()->geometry.standable(cell))||!terminalEnvelope(geometry,fence,from,fromBox,goal,itemBox))continue;
            double candidate=from.horizontalDistanceTo(goal);if(candidate<cost){cost=candidate;selected=cell;selectedGoal=goal;}
        }
        if(selected==null||generation!=admitted||active!=null||!request.current()
            ||!new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(from)||!client.player.getBoundingBox().equals(fromBox))return;
        var next=new Association(request,source,selected,selectedGoal,admitted);active=next;retained=next;continuation=null;parentCamera=null;if(parentCameraVersion<Long.MAX_VALUE)parentCameraVersion++;cleanupDispatch.enterTyped();service.collectionDestination(next.standing);
        // Genuine Root/Leg factory first; no prospective/caller-owned Leg can grant this association.
        next.leg=publication.beginExpected(admitted,(Module)request.owner(),SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,next.goal);
        if(next.leg==null||!request.current()||active!=next){failure="Original shared Leg admission refused";refusedRequest=request;stop();return;}
        var adapter=new LeaseGroundAdapter(next.leg);next.navigator=new McNavigator(client,adapter,config,adapter);
        var selectedFence=new DynamicRouteQueryFence(System::nanoTime,()->active==next&&generation==admitted&&request.nativeLeaf(),50_000_000L);
        next.scene=CollectionStandingGeometry.capture(client,from,next.goal,request.item(),selectedFence);
        if(next.scene==null||!next.nativeLeaf()||!next.navigator.startDynamic(next.goal,2500,32,.05)||!next.leg.current()){
            failure=next.navigator.diagnostics();refusedRequest=request;stop();return;
        }
        var state=next.navigator.tick();if(state==WalkController.State.BLOCKED){failure=next.navigator.diagnostics();refusedRequest=request;stop();return;}
        next.attest();
    }
    private static boolean terminalEnvelope(McNavigator geometry,DynamicRouteQueryFence fence,Vec3 from,net.minecraft.world.phys.AABB player,Vec3 goal,net.minecraft.world.phys.AABB item){
        for(double dx:new double[]{-.05,0,.05})for(double dz:new double[]{-.05,0,.05}){
            var end=new Vec3(goal.x()+dx,goal.y(),goal.z()+dz);
            if(!fence.query(()->geometry.standingClear(end))||!fence.query(()->geometry.walkClear(from,end))||!fence.query(()->geometry.readOnlyLandingBodyEnvelopeClear(from,end))
                ||!pickupOverlap(player.move(end.x()-from.x(),end.y()-from.y(),end.z()-from.z()),item))return false;
        }
        return fence.available();
    }
    private static boolean pickupOverlap(net.minecraft.world.phys.AABB p,net.minecraft.world.phys.AABB i){
        return dev.veltraph.client.mining.vein.VeinPickupEnvelope.overlaps(new dev.veltraph.client.mining.vein.VeinPickupEnvelope.Box(p.minX,p.minY,p.minZ,p.maxX,p.maxY,p.maxZ),new dev.veltraph.client.mining.vein.VeinPickupEnvelope.Box(i.minX,i.minY,i.minZ,i.maxX,i.maxY,i.maxZ));
    }
    /** Cleanup is permanently typed after an actual association, including failed admission. */
    dev.veltraph.client.mining.vein.VeinCollectionCleanupDispatch.Outcome<CleanupScope> stop(Runnable legacyStop){
        return cleanupDispatch.stop(this::retireTyped,legacyStop);
    }
    void stop(){stop(()->{});}
    boolean typedCleanup(){return cleanupDispatch.typed();}
    private CleanupScope retireTyped(){
        stopApproach();var exact=retained;var running=active;
        dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome=null;
        try{
            // retireOwned accounts for all nine keys and the actual camera ticket. Calling the
            // idle legacy navigator (or a neutral gameplay publication) cannot complete this cleanup.
            if(exact!=null&&exact.leg!=null){outcome=exact.leg.retireOwned();if(!outcome.cleanSuccess())failure="Original Leg retirement refused: "+outcome;}
        }finally{
            if(running!=null&&active==running){active=null;service.collectionDestination(null);if(generation<Long.MAX_VALUE)generation++;}
        }
        if(exact!=null&&exact.leg!=null&&outcome!=null&&outcome.cleanSuccess()&&exact.interaction==null
            &&exact.request.cleanupSourceCurrent()&&exact.request.owner() instanceof Module owner&&owner.isEnabled()){
            // The actual factory consumes this exact clean retired Leg once, while the original
            // source is still retained. No later service receipt or public state may be adopted.
            exact.interaction=exact.leg.retiredParentInteraction();
        }
        return exact==null&&continuation==null?null:new CleanupScope(exact,continuation,outcome);
    }
    final class CleanupScope {
        private final Association exact;private final ParentContinuation parent;private final dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome;
        private CleanupScope(Association exact,ParentContinuation parent,dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome){this.exact=exact;this.parent=parent;this.outcome=outcome;}
        private boolean originalCurrent(Object owner){
            if(parent!=null){if(continuation!=parent||active!=null||parent.source.owner()!=owner||!parent.source.cleanupCurrent())return false;
                if(parent.interaction!=null)return parent.interaction.cleanupCurrent();var proof=parent.leg.retiredCleanupParentContext();return proof!=null&&proof.parent()==owner;}
            if(exact==null||retained!=exact||active!=null||exact.leg==null||outcome==null||!outcome.logicalRetired()||exact.request.owner()!=owner
                ||!exact.request.cleanupSourceCurrent())return false;
            // The genuine private Leg recaptures actual Root/admission/PWC/camera/serial custody.
            // A cached outcome or copied public State is never sufficient.
            if(exact.interaction!=null)return exact.interaction.cleanupCurrent()&&exact.request.cleanupSourceCurrent();
            var nativeProof=exact.leg.retiredCleanupParentContext();
            return nativeProof!=null&&nativeProof.parent()==owner&&exact.request.cleanupSourceCurrent();
        }
        private boolean nativeCleanupCurrent(Object owner){
            if(active!=null)return false;
            if(parent!=null)return continuation==parent&&parent.source.owner()==owner&&parent.source.cleanupNativeContext()&&parent.interaction!=null&&parent.interaction.cleanupCurrent();
            return exact!=null&&retained==exact&&exact.request.owner()==owner&&exact.request.cleanupNativeContext()&&exact.interaction!=null&&exact.interaction.cleanupCurrent();
        }
        private void adoptPendingAimCleanup(LegacyTravelPublicationLease.RetiredParentInteraction interaction){
            if(interaction==null||interaction.cleanupCurrent()||!(service.farm().rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation))return;
            var actual=rotation.observedSourceAimOutcome(true);if(actual==null)return;
            // Canonical actual producer cache exists even during the authority callback's onDisable.
            // Only the genuine interaction can authenticate its prior publication/ADM/lifecycle lineage.
            if(actual.cleanupPublication()!=null)interaction.advanceAimCleanup(actual);
            else if(actual.metadataOnlyRefusal())interaction.advanceRefusal(actual);
        }
        void cleanupRemaining(){
            var owner=parent!=null?parent.source.owner():exact.request.owner();var interaction=parent!=null?parent.interaction:exact.interaction;
            adoptPendingAimCleanup(interaction);if(interaction==null||!nativeCleanupCurrent(owner))return;
            var keys=parent!=null?parent.keys:exact.keys;
            for(int i=0;i<keys.length;i++){
                final int index=i;var key=keys[index];if(key==null||!nativeCleanupCurrent(owner)||!keysCurrent(keys))return;
                var actual=dev.veltraph.client.service.mc.McHeldKeys.setHeldScoped(key,false,()->nativeCleanupCurrent(owner)&&keysCurrent(keys));
                if(!actual.confirmed()||!nativeCleanupCurrent(owner)||!keysCurrent(keys))return;
            }
            var sourceCancellation=interaction.cancelSourceAimOwnedCamera(true);
            if(sourceCancellation==null&&interaction.ownsCamera())interaction.cancelOwnedCamera(true);
        }
        boolean restoreOriginalSlot(Object owner){
            if((parent!=null?parent.restorationAttempted:exact.restorationAttempted)||!originalCurrent(owner)||!(service.farm().inventory() instanceof dev.veltraph.client.service.mc.McInventoryService inventory))return false;
            var slots=parent!=null?parent.source.cleanupSlots():exact.request.cleanupSlots();if(slots==null)return false;
            int selected=slots.selected(),prior=slots.original();
            if(prior<0||prior>8||selected<0||selected>8||!slots.current(selected,false))return false;
            if(parent!=null)parent.restorationAttempted=true;else exact.restorationAttempted=true; // Consume before observer, setter and packet callbacks.
            var actual=inventory.selectSlotScoped(selected,prior,
                ()->originalCurrent(owner)&&slots.current(selected,false),
                ()->originalCurrent(owner)&&slots.current(prior,true));
            return actual.confirmed()&&actual.localSelected()&&actual.packetSubmitted()&&actual.nativeSelectionHistory()!=null
                &&slots.restoredHistory(actual.nativeSelectionHistory())&&originalCurrent(owner);
        }
    }
    private net.minecraft.client.KeyMapping[] keys(){var o=client.options;return new net.minecraft.client.KeyMapping[]{o.keyUp,o.keyDown,o.keyLeft,o.keyRight,o.keyJump,o.keyShift,o.keySprint,o.keyAttack,o.keyUse};}
    private boolean keysCurrent(net.minecraft.client.KeyMapping[] actual){var expected=keys();return java.util.Arrays.equals(actual,expected);}
    private final class ParentContinuation {
        final dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source;
        final LegacyTravelPublicationLease.Leg leg;final LegacyTravelPublicationLease.RetiredParentInteraction interaction;
        final net.minecraft.client.KeyMapping[] keys=keys();boolean restorationAttempted;
        ParentContinuation(dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source,LegacyTravelPublicationLease.Leg leg,LegacyTravelPublicationLease.RetiredParentInteraction interaction){this.source=source;this.leg=leg;this.interaction=interaction;}
    }
    private final class ApproachOwnership {
        final dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentApproach source;final long version;final ApproachSnapshot snapshot;
        LegacyTravelPublicationLease.Leg leg;McNavigator navigator;
        ApproachOwnership(dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentApproach source,long version){this.source=source;this.version=version;snapshot=new ApproachSnapshot(this);}
        boolean nativeCurrent(Object exactLeg,long actualNow){return approach==this&&generation==version&&leg==exactLeg&&exactLeg!=null&&source.currentAt(actualNow);}
    }
    private final class ApproachSnapshot {
        final VeinCollectionMovement host=VeinCollectionMovement.this;final ApproachOwnership exact;
        ApproachSnapshot(ApproachOwnership exact){this.exact=exact;}
    }
    boolean startApproach(Object owner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentApproach source){
        if(!cleanupDispatch.typed()||active!=null||source==null||source.owner()!=owner||!source.current()||generation==Long.MAX_VALUE)return false;
        stopApproach();var geometry=new McNavigator(client,new dev.veltraph.client.service.stub.NoOpInputService(),config);var goal=geometry.position(source.standing());
        var fence=new DynamicRouteQueryFence(System::nanoTime,source::current,50_000_000L);
        if(!fence.query(()->geometry.standable(source.standing()))||!source.current())return false;
        var next=new ApproachOwnership(source,++generation);approach=next;parentCamera=null;if(parentCameraVersion<Long.MAX_VALUE)parentCameraVersion++;
        next.leg=publication.beginExpected(next.version,(Module)owner,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,goal);
        if(next.leg==null||!source.current()||approach!=next){stopApproach();return false;}
        var adapter=new LeaseGroundAdapter(next.leg);next.navigator=new McNavigator(client,adapter,config,adapter);
        // Original 2500-node/32-cell bounds; .27 stops strictly inside the unchanged .28 completion condition.
        if(!next.navigator.start(goal,2500,32,.27)||!source.current()||!next.leg.current()){stopApproach();return false;}return true;
    }
    WalkController.State tickApproach(){var exact=approach;
        if(exact==null||exact.navigator==null||!exact.source.current()||exact.leg==null||!exact.leg.current()){stopApproach();return WalkController.State.BLOCKED;}
        var actual=exact.navigator.tick();if(actual==WalkController.State.BLOCKED)stopApproach();return actual;
    }
    void stopApproach(){var exact=approach;if(exact==null)return;
        dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome=null;
        try{if(exact.leg!=null)outcome=exact.leg.retireOwned();}finally{if(approach==exact){approach=null;if(generation<Long.MAX_VALUE)generation++;}}
        if(exact.leg!=null&&outcome!=null&&outcome.logicalRetired()&&exact.source.activation()!=null){
            LegacyTravelPublicationLease.RetiredParentInteraction interaction=null;
            if(outcome.cleanSuccess()&&exact.source.current())interaction=exact.leg.retiredParentInteraction();
            continuation=new ParentContinuation(exact.source.activation(),exact.leg,interaction);parentCamera=null;if(parentCameraVersion<Long.MAX_VALUE)parentCameraVersion++;
        }
    }
    boolean activationCurrent(Object owner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source){
        if(source==null||source.owner()!=owner||!source.belongsTo(service))return false;
        if(continuation!=null)return continuation.source==source&&source.cleanupCurrent();
        return retained!=null&&retained.request.owner()==owner&&retained.request.cleanupActivationMatches(source);
    }
    boolean startActivation(Object owner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionActivation source){
        if(!cleanupDispatch.typed()||active!=null||source==null||source.owner()!=owner||source.receiptIdentity()==consumedEnable
            ||!source.registeredCurrent(service)||generation==Long.MAX_VALUE)return false;
        var position=new Vec3(client.player.getX(),client.player.getY(),client.player.getZ());var box=client.player.getBoundingBox();
        var fence=new DynamicRouteQueryFence(System::nanoTime,()->source.registeredCurrent(service),50_000_000L);
        var geometry=new McNavigator(client,new dev.veltraph.client.service.stub.NoOpInputService(),config);
        if(!fence.query(()->geometry.standingClear(position))||!fence.query(()->geometry.standable(service.farm().player().blockPos()))
            ||!source.registeredCurrent(service)||!new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(position)||!client.player.getBoundingBox().equals(box))return false;
        consumedEnable=source.receiptIdentity();long admitted=++generation;
        var leg=publication.beginExpected(admitted,(Module)owner,SharedMovementPolicy.Purpose.GENERAL_GROUND_TRAVEL,position);
        if(leg==null)return false;
        boolean original=source.registeredCurrent(service)&&leg.current()&&new Vec3(client.player.getX(),client.player.getY(),client.player.getZ()).equals(position)&&client.player.getBoundingBox().equals(box);
        var outcome=leg.retireOwned();
        if(!original||!source.registeredCurrent(service)||!outcome.cleanSuccess())return false;
        var interaction=leg.retiredParentInteraction();if(interaction==null||!source.registeredCurrent(service)||!interaction.current())return false;
        continuation=new ParentContinuation(source,leg,interaction);parentCamera=null;if(parentCameraVersion<Long.MAX_VALUE)parentCameraVersion++;return source.registeredCurrent(service)&&continuation.interaction.current();
    }
    boolean publishParentCamera(Vec3 goal,Object owner,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentOperation source){
        var exact=retained;var parent=continuation;var interaction=parent!=null?parent.interaction:exact==null?null:exact.interaction;
        if(active!=null||interaction==null||source==null||!source.current()
            ||(parent!=null?parent.source.owner()!=owner||!parent.source.cleanupCurrent():exact.request.owner()!=owner||!exact.request.cleanupSourceCurrent())
            ||!interaction.current()||!(service.farm().rotation() instanceof dev.veltraph.client.service.mc.McRotationService rotation))return false;
        var p=client.player;double dx=goal.x()-p.getX(),dy=goal.y()-p.getEyeY(),dz=goal.z()-p.getZ();
        float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90.0),pitch=(float)(-Math.toDegrees(Math.atan2(dy,Math.sqrt(dx*dx+dz*dz))));
        var outcome=rotation.aimWithOutcome(yaw,pitch,interaction.publicationFence(),source::current);
        if(outcome==null||retained!=exact||continuation!=parent||active!=null)return false;
        var publication=outcome.cleanupPublication();var gameplay=outcome.gameplayPublication();
        if(publication==null){if(outcome.metadataOnlyRefusal())interaction.advanceRefusal(outcome);return false;}
        boolean action=gameplay!=null&&source.current();
        // Actual own publication is adopted before the final action verdict, including a later source veto.
        boolean adopted=action?interaction.advance(gameplay):interaction.advanceAimCleanup(outcome);if(!adopted)return false;
        if(parentCameraVersion==Long.MAX_VALUE)return false;
        parentCamera=new ParentCameraPublication(exact,parent,source,rotation,outcome,publication,++parentCameraVersion);return action&&source.current();
    }
    void retireTaken(dev.veltraph.client.mining.vein.VeinNativePickupAdapter exact){var running=active;
        if(running!=null&&running.request.belongsToAdapter(exact)&&!running.revoked&&running.request.takenObservationLeaf()&&active==running)stop();
    }
    boolean sourceRevoked(){return active!=null&&!active.request.sourceCurrent();}
    boolean failedFor(VeinNativePickupAdapter exact){return refusedRequest!=null&&refusedRequest.belongsToAdapter(exact);}
    String diagnostics(){return failure;}
    AssociationObservation observation(BlockPos actual){var exact=active;return exact!=null&&exact.standing.equals(actual)&&exact.observedCurrent()?exact.observation:null;}
    private final class ParentCameraPublication {
        final Association association;final ParentContinuation parent;final dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentOperation source;
        final dev.veltraph.client.service.mc.McRotationService rotation;final dev.veltraph.client.service.mc.McRotationService.SourceAimOutcome outcome;
        final dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt receipt;final long version;final ParentCameraSnapshot snapshot;
        ParentCameraPublication(Association association,ParentContinuation parent,dev.veltraph.client.mining.vein.VeinMiningModule.CollectionParentOperation source,dev.veltraph.client.service.mc.McRotationService rotation,dev.veltraph.client.service.mc.McRotationService.SourceAimOutcome outcome,dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt receipt,long version){this.association=association;this.parent=parent;this.source=source;this.rotation=rotation;this.outcome=outcome;this.receipt=receipt;this.version=version;snapshot=new ParentCameraSnapshot(this);}
        /** Actual canonical producer handout/native fields only; receipt.current() would call providers. */
        boolean nativeCurrent(long actualNow){
            if(parentCamera!=this||parentCameraVersion!=version||active!=null||retained!=association||continuation!=parent||outcome==null
                ||outcome.cleanupPublication()!=receipt||outcome.gameplayPublication()!=receipt||!outcome.cleanupNativeCurrent(false)||!source.currentAt(actualNow))return false;
            var actual=receipt.after();long ticket=receipt.actualTicket();
            return actual!=null&&actual==outcome.after()&&actual.service()==rotation&&actual.configStore()==config
                &&actual.root()==dev.veltraph.client.VeltraphBootstrap.moduleManager()&&actual.player()==client.player&&actual.world()==client.level&&actual.connection()==client.getConnection()
                &&actual.parent()==(parent!=null?parent.source.owner():association.request.owner())
                &&ticket>0&&actual.publicationSerial()==ticket&&rotation.requestGeneration()==actual.generation()
                &&dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==actual.epoch()&&dev.veltraph.client.service.mc.McOwnedRotation.publicationSerial(client)==ticket
                &&outcome.cleanupNativeCurrent(false)&&source.currentAt(actualNow);
        }
    }
    private final class ParentCameraSnapshot {
        final VeinCollectionMovement host=VeinCollectionMovement.this;final ParentCameraPublication publication;
        ParentCameraSnapshot(ParentCameraPublication publication){this.publication=publication;}
    }
    /** Before callbacks: actual association/version/issued Leg identity, never a caller state grant. */
    Object snapshot(Object exactLeg){var exact=active;
        if(approach!=null&&exactLeg!=null){var travel=approach;return travel.nativeCurrent(exactLeg,System.nanoTime()/1_000_000)?travel.snapshot:null;}
        if(exact==null&&exactLeg==null&&parentCamera!=null){var issued=parentCamera;return issued.nativeCurrent(System.nanoTime()/1_000_000)?issued.snapshot:null;}
        if(exact==null&&exactLeg==null&&cleanupDispatch.typed())return null;
        if(exact==null){if(inactiveSnapshot==null||inactiveSnapshot.generation!=generation||inactiveSnapshot.exactLeg!=exactLeg)inactiveSnapshot=new AssociationSnapshot(null,generation,exactLeg);return inactiveSnapshot;}
        if(exact.leg!=exactLeg||exactLeg==null||!exact.nativeLeaf())return null;
        if(exact.snapshot==null)exact.snapshot=new AssociationSnapshot(exact,generation,exactLeg);return exact.snapshot;
    }
    /** No clocks, callbacks, role/provider getters or shape queries. Core supplies its actual time. */
    boolean snapshotCurrent(Object exactLeg,Object opaque,long actualNow){
        if(opaque instanceof VeinCollectionMovement.ApproachSnapshot snapshot)return snapshot.host==this&&snapshot.exact.snapshot==snapshot&&snapshot.exact.nativeCurrent(exactLeg,actualNow);
        if(opaque instanceof VeinCollectionMovement.ParentCameraSnapshot snapshot)return exactLeg==null&&snapshot.host==this
            &&snapshot.publication.snapshot==snapshot&&snapshot.publication.nativeCurrent(actualNow);
        if(exactLeg==null&&cleanupDispatch.typed())return false;
        if(!(opaque instanceof VeinCollectionMovement.AssociationSnapshot snapshot)||snapshot.host!=this||snapshot.generation!=generation
            ||snapshot.exactLeg!=exactLeg||snapshot.association!=active)return false;
        var exact=snapshot.association;
        if(exact==null)return snapshot==inactiveSnapshot;
        return exact.snapshot==snapshot&&exact.leg==exactLeg&&dev.veltraph.client.mining.vein.VeinCollectionWindow.current(actualNow,exact.request.deadlineFloor(),exact.request.deadline())&&exact.nativeLeaf();
    }
    private final class AssociationSnapshot {
        private final VeinCollectionMovement host=VeinCollectionMovement.this;private final Association association;private final long generation;private final Object exactLeg;
        private AssociationSnapshot(Association association,long generation,Object exactLeg){this.association=association;this.generation=generation;this.exactLeg=exactLeg;}
    }
    public static final class AssociationObservation {
        private final Association exact;private AssociationObservation(Association exact){this.exact=exact;}
        public BlockPos source(){return exact.source;}public BlockPos standing(){return exact.standing;}public Vec3 standingPosition(){return exact.goal;}
        public int itemId(){return exact.request.item().getId();}public java.util.UUID itemUuid(){return exact.request.item().getUUID();}
        public boolean observedFor(McMiningServices service,Object owner,BlockPos actualStanding){return exact.observation==this&&exact.outerService()==service&&exact.request.owner()==owner&&exact.standing.equals(actualStanding)&&exact.observedCurrent();}
    }
    private final class Association {
        final VeinNativePickupAdapter.CollectionRequest request;final BlockPos source,standing;final Vec3 goal;final long admitted;
        final AssociationObservation observation;AssociationSnapshot snapshot;LegacyTravelPublicationLease.Leg leg;LegacyTravelPublicationLease.RetiredParentInteraction interaction;McNavigator navigator;CollectionStandingGeometry.Proof scene;
        LegacyTravelPublicationLease.Leg.MovementPublicationObservation movement;boolean revoked,restorationAttempted;
        final net.minecraft.client.KeyMapping[] keys={client.options.keyUp,client.options.keyDown,client.options.keyLeft,client.options.keyRight,client.options.keyJump,client.options.keyShift,client.options.keySprint,client.options.keyAttack,client.options.keyUse};
        boolean keysCurrent(){var o=client.options;return o!=null&&keys[0]==o.keyUp&&keys[1]==o.keyDown&&keys[2]==o.keyLeft&&keys[3]==o.keyRight&&keys[4]==o.keyJump&&keys[5]==o.keyShift&&keys[6]==o.keySprint&&keys[7]==o.keyAttack&&keys[8]==o.keyUse;}
        Association(VeinNativePickupAdapter.CollectionRequest request,BlockPos source,BlockPos standing,Vec3 goal,long admitted){this.request=request;this.source=source;this.standing=standing;this.goal=goal;this.admitted=admitted;observation=new AssociationObservation(this);}
        McMiningServices outerService(){return service;}
        boolean nativeLeaf(){return !revoked&&active==this&&generation==admitted&&leg!=null&&request.belongsTo(service)&&request.observationLeaf()
            &&scene!=null&&scene.nativeCurrent()&&service.collectionDestinationIs(standing);}
        void attest(){if(active==this&&leg!=null){var actual=leg.observeMovementPublication();if(actual!=null)movement=actual;}}
        boolean observedCurrent(){return active==this&&generation==admitted&&service.collectionDestinationIs(standing)&&request.belongsTo(service)
            &&(request.observationLeaf()||request.takenObservationLeaf())&&movement!=null&&movement.observedCurrent();}
    }
}
