package dev.veltraph.client.rewarp.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.navigation.*;
import dev.veltraph.client.navigation.mc.McTravelNavigator;
import dev.veltraph.client.rewarp.*;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.Connection;

/** Saved-anchor recovery with one private native session and controller set per actual journey. */
public final class McSavedAnchorRecovery implements SavedAnchorRecoveryModule.Driver {
    private final Minecraft client;
    private final FarmServices services;
    private final ConfigService config;
    private final FarmMaintenanceCoordinator.SavedReturnAdapter savedReturnAdapter;
    private AlignmentCall selectedAlignmentCall;
    private Journey activeJourney;
    private long generation;
    private boolean generationExhausted;
    private SavedAnchorRecovery.State terminal=SavedAnchorRecovery.State.IDLE;
    private String terminalStatus="Saved anchor: IDLE",terminalDiagnostic="none";
    public McSavedAnchorRecovery(Minecraft client,FarmServices services,ConfigService config){this(client,services,config,null);}
    McSavedAnchorRecovery(Minecraft client,FarmServices services,ConfigService config,FarmMaintenanceCoordinator.SavedReturnAdapter adapter){this.client=client;this.services=services;this.config=config;savedReturnAdapter=adapter;}
    private boolean advance(){if(generation==Long.MAX_VALUE){generationExhausted=true;return false;}generation++;return !generationExhausted;}
    private void thread(){if(!client.isSameThread())throw new IllegalStateException("Saved-anchor recovery requires client thread");}

    /** Only one actual private Journey constructs this canonical loaded-return source. */
    public final class LoadedReturnCall {
        private final Journey journey;
        private final FarmMaintenanceCoordinator.SavedReturnAdapter originalSavedReturnAdapter=savedReturnAdapter;
        private boolean dispatching,returned,failed;
        private String operation;
        private boolean retiringOriginalStartPredecessor;
        private final int originalSlot=client.player.getInventory().getSelectedSlot();
        private final dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory initialHistory=
            dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.selectedSlotHistory(client.player.getInventory());
        private dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory expectedHistory=initialHistory;
        private final java.util.Set<Object> consumedRestorations=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        private LoadedReturnCall(Journey journey){this.journey=journey;}
        public McSavedAnchorRecovery returningIdentity(){return McSavedAnchorRecovery.this;}
        public McTravelNavigator travelIdentity(){return journey.travel;}
        public IssuedReturnTravelRequest issuedRequestIdentity(){return journey.issuedRequest;}
        public FarmReturnPoint canonicalTargetPoint(){return journey.target;}
        public Vec3 canonicalTarget(){return journey.target.position();}
        public ConfigService configIdentity(){return config;}public FarmServices servicesIdentity(){return services;}
        public Object playerIdentity(){return journey.owner;}public Object worldIdentity(){return journey.world;}
        public Object connectionIdentity(){return journey.listener;}public Object transportIdentity(){return journey.connection;}
        /** Exact original private saved-return adapter affiliation; it grants no SDK action authority. */
        public boolean matchingSavedReturnAdapter(McSavedAnchorRecovery exact,FarmMaintenanceCoordinator.SavedReturnAdapter adapter){
            return exact==McSavedAnchorRecovery.this&&adapter!=null&&originalSavedReturnAdapter==adapter&&savedReturnAdapter==adapter
                &&activeJourney==journey&&journey.loadedReturnCall==this&&generation==journey.serial&&!generationExhausted;
        }
        /** Only the actual pre-selection Shared admission body may ask the original installed adapter to issue a source. */
        public FarmMaintenanceCoordinator.LoadedReturnDeviceAuthority issueRestoredMaintenanceDeviceAuthority(
                dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment segment,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.NormalReturnDeviceAdmission admission){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
                    &&first.getMethodName().equals("captureRestoredMaintenanceDeviceAuthority");});
            if(!caller||originalSavedReturnAdapter==null||!matchingSavedReturnAdapter(McSavedAnchorRecovery.this,originalSavedReturnAdapter)
                ||segment==null||admission==null||segment.returnCallIdentity()!=this||!segment.nativeRealmCurrent()
                ||segment.sharedChildIdentity()!=admission.issuerIdentity()||segment.parentIdentity()!=admission.parentIdentity()
                ||!segment.segmentPosition().equals(admission.targetPosition())||!nativeDispatchCurrent(journey.travel))return null;
            return originalSavedReturnAdapter.issueLoadedReturnDeviceAuthority(this,segment,admission);
        }
        public long journeySerial(){return journey.serial;}
        public long originalJourneyDeadlineMillis(){return journey.started>Long.MAX_VALUE-45000?-1:journey.started+45000;}
        public int originalSlot(){return originalSlot;}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalSelectionHistory(){return initialHistory;}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory expectedSelectionHistory(){return expectedHistory;}
        public boolean originalSelectionHistoryCurrent(){return expectedHistory!=null&&expectedHistory.current()
            &&client.player==journey.owner&&journey.owner.getInventory().getSelectedSlot()==originalSlot;}
        /** Only the real native restoration body can advance this whole loaded-Journey baseline. */
        public boolean advanceOriginalSelection(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.NormalReturnRestorationWitness proof,
                dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass().getEnclosingClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
                    &&first.getMethodName().equals("applyNormalReturnDeviceSlotSelection");});
            if(!caller||proof==null||source==null||source.returnCallIdentity()!=this||!source.nativeRealmCurrent()
                ||consumedRestorations.contains(proof)||proof.originalSlot()!=originalSlot||expectedHistory==null
                ||!proof.originalHistory().sameStoredObservation(expectedHistory)||!proof.retainedLineageCurrent()
                ||proof.originalLegIdentity()!=source.sharedChildIdentity().actualLoadedLegIdentity(source)
                ||proof.restoredHistory()==null||!proof.restoredHistory().current()
                ||journey.owner.getInventory().getSelectedSlot()!=originalSlot||!nativeSceneCurrent())return false;
            consumedRestorations.add(proof);expectedHistory=proof.restoredHistory();return originalSelectionHistoryCurrent();
        }
        /** Issued native slot restoration advances the same whole loaded-Journey baseline without rearming manual history. */
        public boolean advanceOriginalIssuedSelection(McIssuedReturnInventory.Snapshot.DeviceSlotCustody.RestorationWitness proof,
                dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass().getEnclosingClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
                    &&first.getMethodName().equals("applyIssuedDeviceSlotSelection");});
            var request=journey.issuedRequest;var history=expectedHistory;var restored=proof==null?null:proof.restoredHistory();
            var transit=proof==null?null:proof.transitIdentity();var selected=transit==null?null:transit.loadedSegmentIdentity();
            if(!caller||request==null||proof==null||transit==null||selected==null||source==null||source.returnCallIdentity()!=this||!source.nativeRealmCurrent()
                ||consumedRestorations.contains(proof)||proof.sourceHeaderIdentity()!=request.sourceHeader()
                ||proof.transitIdentity().requestIdentity()!=request||proof.transitIdentity().sharedChildIdentity()!=source.sharedChildIdentity()
                ||proof.transitIdentity().loadedSegmentIdentity()==null||proof.transitIdentity().loadedSegmentIdentity().sourceIdentity()!=source
                ||proof.transitIdentity().originalRestoration!=null
                ||proof.originalSlot()!=originalSlot||history==null||!proof.originalHistory().sameStoredObservation(history)
                ||!proof.retainedLineageCurrent()||restored==null||!restored.current()
                ||journey.owner.getInventory().getSelectedSlot()!=originalSlot||!nativeSceneCurrent())return false;
            // The last provider/native visitor cannot replace the selected source or pending restoration tuple.
            if(journey.issuedRequest!=request||request.deviceTransit!=transit||request.loadedSegment!=selected
                ||selected.source!=source||transit.selectedLoadedSegment!=selected||transit.originalRestoration!=null
                ||expectedHistory!=history||consumedRestorations.contains(proof)||activeJourney!=journey
                ||generation!=journey.serial||generationExhausted)return false;
            consumedRestorations.add(proof);expectedHistory=restored;
            transit.originalRestoration=proof;return originalSelectionHistoryCurrent();
        }
        /** Geometry/source data only. Existing actual native Leg/Shared capsules still own every effect. */
        public boolean nativeSceneCurrent(){return client.isSameThread()&&!failed&&!journey.retiring&&activeJourney==journey&&journey.loadedReturnCall==this
            &&generation==journey.serial&&!generationExhausted&&journey.nativeCurrent()
            &&journey.owner.isAlive()&&client.gui.screen()==null&&!client.isPaused()&&!journey.owner.isPassenger()
            &&!journey.owner.isInLava()&&journey.owner.containerMenu==journey.owner.inventoryMenu
            &&journey.owner.inventoryMenu.getCarried().isEmpty()&&client.getCameraEntity()==journey.owner;}
        public boolean nativeDispatchCurrent(McTravelNavigator exact){return exact==journey.travel&&dispatching&&("START".equals(operation)||"TICK".equals(operation))&&nativeSceneCurrent()
            &&(journey.issuedRequest==null||journey.issuedRequest.nativeCurrent());}
        public boolean nativeContinuationCurrent(McTravelNavigator exact){return exact==journey.travel&&returned&&nativeSceneCurrent()
            &&(journey.issuedRequest==null||journey.issuedRequest.nativeContinuationCurrent());}
        /** Callback supplied only by the exact named Travel body reached from the private Journey action. */
        public <T>T withOriginalTravelDispatch(McTravelNavigator exact,String phase,java.util.function.Supplier<T> actual){
            if(exact!=journey.travel||actual==null||dispatching||!nativeSceneCurrent()||!originalTravelCaller(phase))
                throw new IllegalStateException("No actual loaded Journey travel dispatch");
            returned=false;operation=phase;dispatching=true;
            try{T result=actual.get();if(nativeSceneCurrent())returned=true;return result;}
            catch(RuntimeException|Error failure){failed=true;returned=false;throw failure;}
            finally{dispatching=false;operation=null;}
        }
        /** Original request predecessor cleanup stays in its genuine START source; it cannot open another dispatcher. */
        private void retireOriginalStartPredecessor(IssuedReturnTravelRequest exact){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==IssuedReturnTravelRequest.class&&first.getMethodName().equals("retireBeforeInitialChildStart");});
            if(!caller||exact!=journey.issuedRequest||!dispatching||!"START".equals(operation)||retiringOriginalStartPredecessor
                ||!nativeDispatchCurrent(journey.travel))throw new IllegalStateException("No original loaded START predecessor retirement");
            retiringOriginalStartPredecessor=true;
            try{journey.releaseOwnTravel();}finally{retiringOriginalStartPredecessor=false;}
        }
        /** Actual private Journey stop only; this source is cleanup data and cannot select a new segment. */
        private <T>T withOriginalJourneyStop(java.util.function.Supplier<T> actual){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==Journey.class&&first.getMethodName().equals("releaseOwnTravel");});
            if(!caller||dispatching||actual==null)throw new IllegalStateException("Loaded original Journey stop reentered");
            returned=false;dispatching=true;operation="STOP";
            try{return actual.get();}
            catch(RuntimeException|Error failure){failed=true;throw failure;}
            finally{dispatching=false;operation=null;returned=false;}
        }
        /** Stored cleanup scene only; actual Root old-loan/Leg cleanup capsules remain mandatory for effects. */
        public boolean cleanupSceneCurrent(){return client.isSameThread()&&activeJourney==journey&&journey.loadedReturnCall==this&&generation==journey.serial&&!generationExhausted
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==journey.registry&&dev.veltraph.client.VeltraphBootstrap.config()==config
            &&client.player==journey.owner&&client.level==journey.world&&client.getConnection()==journey.listener
            &&client.getConnection()!=null&&client.getConnection().getConnection()==journey.connection;}
        public boolean originalDispatchScopeCurrent(McTravelNavigator exact){return exact==journey.travel&&dispatching&&cleanupSceneCurrent()
            &&("START".equals(operation)||"TICK".equals(operation)||"STOP".equals(operation));}
        public boolean cleanupDispatchCurrent(McTravelNavigator exact){return exact==journey.travel&&dispatching&&"STOP".equals(operation)&&cleanupSceneCurrent();}
        public boolean selectingSourceCurrent(McTravelNavigator exact){return nativeDispatchCurrent(exact)
            &&("START".equals(operation)||"TICK".equals(operation));}
        private boolean originalTravelCaller(String phase){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var frames=stream.filter(f->f.getDeclaringClass()!=LoadedReturnCall.class).limit(32).toList();
            if(frames.isEmpty()||frames.getFirst().getDeclaringClass()!=McTravelNavigator.class)return false;
            String expected="START".equals(phase)?"startOriginalLoadedReturn":"TICK".equals(phase)?"tickOriginalLoadedReturn":null;
            if(expected==null||!frames.getFirst().getMethodName().equals(expected))return false;
            var producer=frames.stream().filter(f->f.getDeclaringClass()!=McTravelNavigator.class).findFirst().orElse(null);
            return producer!=null&&producer.getDeclaringClass()==Journey.class&&producer.getMethodName().equals(
                "START".equals(phase)?"startOriginalIssuedLoadedWalk":"tickOriginalIssuedTravel");
        });}
    }

    private enum IssuedOperation { WALK, LOADED_WALK, FLIGHT, TICK, ALIGN, RELEASE }
    /** Privately produced by one actual selected Journey, never by a global view getter. */
    public final class IssuedReturnTravelRequest {
        private final Journey journey;
        private final McSuspendedFarmReturnCoordinator.MovementView original;
        private final Object header,checkpoint,driver;
        private final Vec3 target;
        private final long originalReturnDeadline;
        private DeviceQueueContinuation deviceQueueContinuation;
        private McSuspendedFarmReturnCoordinator.MovementView activeView,lastNormallyReturnedView;
        private IssuedOperation operation;
        private boolean dispatching,revoked;
        private Instruction instruction,retainedInstruction;
        private DeviceTransit deviceTransit;
        private DeviceSelectionCall deviceSelectionCall;
        private McSuspendedFarmReturnCoordinator.FinalEntryContinuation finalEntryContinuation;
        private Instruction finalEntryInstruction;
        private LoadedSegment loadedSegment;
        /** Genuine controller-selected nested child source; canonical request target remains unchanged. */
        public final class LoadedSegment {
            private final dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source;
            private final Instruction originalInstruction;
            private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessor;
            private final dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome predecessorRetirement;
            private DeviceTransit admittedDeviceTransit;
            private LoadedSegment(dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source,Instruction originalInstruction){
                this.source=source;this.originalInstruction=originalInstruction;
                predecessor=source.originalLegIdentity();predecessorRetirement=source.actualRetirementIdentity();
            }
            public IssuedReturnTravelRequest requestIdentity(){return IssuedReturnTravelRequest.this;}
            public dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment sourceIdentity(){return source;}
            public dev.veltraph.client.navigation.mc.McSharedMovementNavigator sharedChildIdentity(){return source.sharedChildIdentity();}
            public FarmReturnPoint canonicalTargetPoint(){return journey.target;}
            public Vec3 canonicalTarget(){return target;}public Vec3 segmentPosition(){return source.segmentPosition();}
            public long originalDeadlineMillis(){return Math.min(originalReturnDeadline,source.originalDeadlineMillis());}
            public double terminalRadius(){return source.terminalRadius();}
            public boolean canonicalFinalSegment(){return source.canonicalFinalSegment();}
            public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg predecessorIdentity(){return predecessor;}
            public dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome predecessorRetirementIdentity(){return predecessorRetirement;}
            private boolean predecessorCurrent(){return predecessor!=null&&predecessorRetirement!=null
                &&source.originalLegIdentity()==predecessor&&source.actualRetirementIdentity()==predecessorRetirement
                &&predecessor.retirementOutcome()==predecessorRetirement&&predecessorRetirement.cleanSuccess();}
            private boolean rawCurrent(){return loadedSegment==this&&!originalInstruction.failed&&predecessorCurrent()&&sourceFieldsCurrent()
                &&source.issuedRequestIdentity()==IssuedReturnTravelRequest.this&&source.returnCallIdentity().returningIdentity()==McSavedAnchorRecovery.this
                &&source.returnCallIdentity().travelIdentity()==journey.travel&&source.canonicalTargetPoint()==journey.target
                &&target.equals(source.canonicalTarget())&&ownsSharedChild(source.sharedChildIdentity())&&source.nativeRealmCurrent();}
            public boolean nativeCurrent(){return rawCurrent()&&(IssuedReturnTravelRequest.this.nativeCurrent()||nativeContinuationCurrent());}
            public boolean cleanupCurrent(){return loadedSegment==this&&!originalInstruction.failed&&predecessorCurrent()&&sourceFieldsCurrent()
                &&source.issuedRequestIdentity()==IssuedReturnTravelRequest.this&&source.canonicalTargetPoint()==journey.target
                &&target.equals(source.canonicalTarget())&&source.cleanupSourceCurrent();}
        }
        /** Actual typed Shared start alone claims the currently selected original LOADED_WALK/TICK instruction. */
        public LoadedSegment captureOriginalLoadedSegment(dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=IssuedReturnTravelRequest.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class&&first.getMethodName().equals("startOriginalLoadedSegment");});
            var actual=instruction;
            if(!caller||source==null||actual==null||actual.failed||!nativeCurrent()
                ||!(operation==IssuedOperation.LOADED_WALK||operation==IssuedOperation.TICK)
                ||source.issuedRequestIdentity()!=this||!source.initialSharedStartCurrent(source.sharedChildIdentity())
                ||source.canonicalTargetPoint()!=journey.target||!target.equals(source.canonicalTarget())||!ownsSharedChild(source.sharedChildIdentity())
                ||source.originalLegIdentity()==null||source.actualRetirementIdentity()==null
                ||source.originalLegIdentity().retirementOutcome()!=source.actualRetirementIdentity()
                ||!source.actualRetirementIdentity().cleanSuccess()||!source.originalSelectionHistoryCurrent()
                ||!Double.isFinite(source.terminalRadius())||source.terminalRadius()<=0||source.terminalRadius()>.35)return null;
            if(loadedSegment!=null&&loadedSegment.source==source)return loadedSegment.rawCurrent()?loadedSegment:null;
            if(loadedSegment!=null){
                var old=loadedSegment;
                if(old.originalInstruction.failed||old.rawCurrent()||old.cleanupCurrent()||!old.source.retiredLineageCurrent()
                    ||old.source.actualRetiredLegIdentity()!=source.originalLegIdentity()
                    ||old.source.actualSegmentCleanupIdentity()!=source.actualRetirementIdentity()
                    ||old.source.actualRetiredLegIdentity().retirementOutcome()!=old.source.actualSegmentCleanupIdentity()
                    ||!old.source.actualSegmentCleanupIdentity().cleanSuccess()
                    ||deviceSelectionCall!=null||deviceTransit!=null&&deviceTransit.custodySourceCurrent()
                    ||old.admittedDeviceTransit!=null&&!old.admittedDeviceTransit.completedLoadedRetirementCurrent(old,
                        old.source.actualRetiredLegIdentity(),old.source.actualSegmentCleanupIdentity()))return null;
            }
            var previous=loadedSegment;var previousQueue=deviceQueueContinuation;var previousEntry=finalEntryContinuation;
            var previousEntryInstruction=finalEntryInstruction;var previousTransit=deviceTransit;var previousSelection=deviceSelectionCall;
            long originalGeneration=generation;var next=new LoadedSegment(source,actual);boolean committed=false;
            loadedSegment=next;
            try{
                if(!next.rawCurrent()||!source.originalSelectionHistoryCurrent())return null;
                if(previous!=null&&(!previous.source.retiredLineageCurrent()
                    ||previous.source.actualRetiredLegIdentity()!=next.predecessor
                    ||previous.source.actualSegmentCleanupIdentity()!=next.predecessorRetirement
                    ||previous.admittedDeviceTransit!=null&&!previous.admittedDeviceTransit.completedLoadedRetirementCurrent(previous,
                        previous.source.actualRetiredLegIdentity(),previous.source.actualSegmentCleanupIdentity())))return null;
                // Exact final data fence after all visitors: no newer capsule or old pending tuple is adopted/erased.
                if(loadedSegment!=next||instruction!=actual||actual.failed||generation!=originalGeneration||generationExhausted
                    ||activeJourney!=journey||journey.issuedRequest!=this||!dispatching
                    ||!(operation==IssuedOperation.LOADED_WALK||operation==IssuedOperation.TICK)
                    ||deviceQueueContinuation!=previousQueue||finalEntryContinuation!=previousEntry
                    ||finalEntryInstruction!=previousEntryInstruction||deviceTransit!=previousTransit||deviceSelectionCall!=previousSelection
                    ||source.originalLegIdentity()!=next.predecessor||source.actualRetirementIdentity()!=next.predecessorRetirement)return null;
                if(previous!=null){deviceQueueContinuation=null;finalEntryContinuation=null;finalEntryInstruction=null;deviceTransit=null;}
                committed=true;return next;
            }finally{if(!committed){actual.failed=true;if(loadedSegment==next)loadedSegment=previous;}}
        }
        public LoadedSegment currentLoadedSegmentIdentity(){var actual=loadedSegment;return actual!=null&&(actual.rawCurrent()||actual.cleanupCurrent())?actual:null;}
        public boolean currentLoadedSegmentNative(dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){var actual=loadedSegment;return actual!=null&&actual.source==source&&actual.nativeCurrent();}
        public boolean currentLoadedSegmentFieldsCurrent(dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){var actual=loadedSegment;return actual!=null&&actual.source==source&&actual.rawCurrent();}
        public boolean currentLoadedSegmentCleanup(dev.veltraph.client.navigation.mc.McLoadedGroundReturnNavigator.SelectedLoadedSegment source){var actual=loadedSegment;return actual!=null&&actual.source==source&&actual.cleanupCurrent();}

        /** Private actual admission lineage. Structural custody is not a native permission. */
        public final class DeviceTransit {
            private final IssuedReturnTravelRequest request=IssuedReturnTravelRequest.this;
            private final dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission;
            private final Instruction originalInstruction;
            private final LoadedSegment selectedLoadedSegment;
            private final int originalSlot;
            private dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue;
            private Object ownedSource;
            private McSuspendedFarmReturnCoordinator.DeviceAckTransit ackTransit;
            private McIssuedReturnInventory.Snapshot.DeviceSlotCustody.RestorationWitness originalRestoration;
            private DeviceTransit(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission,Instruction actual){
                this.admission=admission;originalInstruction=actual;selectedLoadedSegment=admission.loadedSegmentIdentity();
                originalSlot=client.player.getInventory().getSelectedSlot();
            }
            public IssuedReturnTravelRequest requestIdentity(){return request;}
            public Minecraft client(){return client;}
            public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeader(){return request.sourceHeader();}
            public Object checkpointIdentity(){return checkpoint;}
            public Object driverIdentity(){return driver;}
            public FarmReturnPoint canonicalTargetPoint(){return journey.target;}
            public Vec3 targetPosition(){return target;}
            public Vec3 movementTargetPosition(){return selectedLoadedSegment==null?target:selectedLoadedSegment.segmentPosition();}
            public LoadedSegment loadedSegmentIdentity(){return selectedLoadedSegment;}
            public double movementTerminalRadius(){return selectedLoadedSegment==null?Math.min(.35,journey.arrivalRadius):selectedLoadedSegment.terminalRadius();}
            private boolean movementSourceFieldsCurrent(){return selectedLoadedSegment==null
                ?loadedSegment==null&&admission.loadedSegmentIdentity()==null&&target.equals(admission.targetPosition())
                :loadedSegment==selectedLoadedSegment&&admission.loadedSegmentIdentity()==selectedLoadedSegment&&selectedLoadedSegment.requestIdentity()==request
                    &&selectedLoadedSegment.sharedChildIdentity()==admission.issuerIdentity()
                    &&(selectedLoadedSegment.rawCurrent()||selectedLoadedSegment.cleanupCurrent())
                    &&selectedLoadedSegment.segmentPosition().equals(admission.targetPosition());}
            public int originalSlot(){return originalSlot;}
            public int deviceSlot(){return admission.deviceSlot();}
            public dev.veltraph.client.navigation.mc.McSharedMovementNavigator sharedChildIdentity(){return admission.issuerIdentity();}
            public dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admissionIdentity(){return admission;}
            public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg originalLegIdentity(){return admission.legIdentity();}
            public dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issueIdentity(){return issue;}
            public Object ownedUseSourceIdentity(){return ownedSource;}
            public Object sourceIdentity(){return ownedSource;}
            public McSuspendedFarmReturnCoordinator.DeviceAckTransit ackTransitIdentity(){return ackTransit;}
            /** Exact completed old facts only. This predicate cannot authorize a device or rejoin its old source. */
            private boolean completedLoadedRetirementCurrent(LoadedSegment exact,
                    dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg retired,
                    dev.veltraph.client.navigation.LegacyTravelRetirement.RetirementOutcome outcome){
                return selectedLoadedSegment==exact&&exact.admittedDeviceTransit==this&&!originalInstruction.failed
                    &&sourceFieldsCurrent()&&originalRestoration!=null&&originalRestoration.transitIdentity()==this
                    &&originalRestoration.sourceHeaderIdentity()==sourceHeader()&&originalRestoration.retainedLineageCurrent()
                    &&originalRestoration.restoredHistory()!=null&&originalRestoration.restoredHistory().current()
                    &&journey.loadedReturnCall!=null&&journey.loadedReturnCall.originalSelectionHistoryCurrent()
                    &&originalRestoration.restoredHistory().sameStoredObservation(journey.loadedReturnCall.expectedSelectionHistory())
                    &&ackTransit!=null&&ackTransit.completedLoadedRetirementCurrent(this,exact,retired,outcome);
            }
            public boolean belongsTo(McSuspendedFarmReturnCoordinator.SourceHeader exact){return exact==header&&sourceHeader()==exact;}
            /** No Snapshot, header inventory, current view, role or provider is consulted here. */
            public boolean custodySourceCurrent(){return deviceTransit==this&&!originalInstruction.failed&&sourceFieldsCurrent()
                &&admission.requestIdentity()==request&&admission.sourceHeader()==header
                &&admission.canonicalTargetPoint()==journey.target&&movementSourceFieldsCurrent()
                &&request.ownsSharedChild(admission.issuerIdentity())&&originalSlot>=0&&originalSlot<9
                &&deviceSlot()>=0&&deviceSlot()<9&&admission.custodySourceCurrent();}
            /** Identity only. Actual view permission is checked separately before every effect. */
            public McSuspendedFarmReturnCoordinator.MovementView currentViewIdentity(){
                var view=dispatching?activeView:lastNormallyReturnedView;
                return custodySourceCurrent()&&sameSource(view)?view:null;
            }
            /** Retained actual terminal affiliation only; full current/retained view guards remain with its consumer. */
            public McSuspendedFarmReturnCoordinator.MovementView terminalViewIdentity(
                    dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt exact){
                var view=dispatching?activeView:lastNormallyReturnedView;
                return terminalSourceFieldsCurrent(exact)&&sameSource(view)?view:null;
            }
            public boolean nativeCurrent(){return custodySourceCurrent()&&admission.nativeSourceCurrent()
                &&(request.nativeCurrent()||request.nativeContinuationCurrent());}
            public boolean cleanupCurrent(){return custodySourceCurrent()&&admission.cleanupSourceCurrent()
                &&((request.nativeCurrent()||request.nativeContinuationCurrent())
                    ||journey.issuedCleanupInProgress&&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.nativeCurrent());}
            /** Actual terminal source before retirement; old SDK receipts are not revived afterwards. */
            public boolean terminalSourceFieldsCurrent(dev.veltraph.client.movement.device.McInstantTransmission.TerminalCleanupReceipt exact){
                return exact!=null&&deviceTransit==this&&!originalInstruction.failed&&sourceFieldsCurrent()
                    &&admission.requestIdentity()==request&&admission.sourceHeader()==header
                    &&admission.canonicalTargetPoint()==journey.target&&movementSourceFieldsCurrent()
                    &&request.ownsSharedChild(admission.issuerIdentity())&&admission.terminalSourceFieldsCurrent(exact);
            }
            /** Genuine issue/source and native scene only; independent of the serial being joined at RETURN. */
            public boolean nativeIssueCurrent(){return custodySourceCurrent()&&issue!=null&&ownedSource!=null
                &&admission.issueIdentity()==issue&&admission.actualOwnedUseSourceIdentity()==ownedSource
                &&admission.nativeSourceCurrent()&&currentViewIdentity()!=null&&client.isSameThread()
                &&client.player==journey.owner&&client.level==journey.world&&client.getConnection()==journey.listener
                &&journey.listener.getConnection()==journey.connection&&issue.context().player()==journey.owner
                &&issue.context().world()==journey.world&&issue.context().connection()==journey.listener;}
        }
        /** One genuine select/restore call. Public fences can veto; they cannot construct this call. */
        public final class DeviceSelectionCall {
            private final DeviceTransit transit;
            private final Instruction originalInstruction;
            private final dev.veltraph.client.service.mc.McInventoryService inventory;
            private final int expected,requested;
            private final boolean cleanup;
            private final Thread thread=Thread.currentThread();
            private boolean closed;
            private DeviceSelectionCall(DeviceTransit transit,Instruction instruction,dev.veltraph.client.service.mc.McInventoryService inventory,
                    int expected,int requested,boolean cleanup){this.transit=transit;originalInstruction=instruction;this.inventory=inventory;
                this.expected=expected;this.requested=requested;this.cleanup=cleanup;}
            public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeader(){return IssuedReturnTravelRequest.this.sourceHeader();}
            public DeviceTransit transitIdentity(){return transit;}
            public int expectedSlot(){return expected;}public int requestedSlot(){return requested;}
            public boolean cleanupRequested(){return cleanup;}
            public dev.veltraph.client.service.mc.McInventoryService inventoryServiceIdentity(){return inventory;}
            public boolean belongsTo(McSuspendedFarmReturnCoordinator.SourceHeader exact){return exact==header&&transit.belongsTo(exact);}
            /** Acyclic source leaf: no inventory snapshot validation can call itself through this predicate. */
            public boolean custodySourceCurrent(){return !closed&&Thread.currentThread()==thread&&deviceSelectionCall==this
                &&transit==deviceTransit&&transit.custodySourceCurrent()
                &&expected!=requested&&expected>=0&&expected<9&&requested>=0&&requested<9
                &&(cleanup?expected==transit.deviceSlot()&&requested==transit.originalSlot():expected==transit.originalSlot()&&requested==transit.deviceSlot())
                &&(originalInstruction!=null?instruction==originalInstruction&&dispatching&&!originalInstruction.failed
                    &&(originalInstruction.operation==IssuedOperation.WALK||originalInstruction.operation==IssuedOperation.TICK)
                    :cleanup&&journey.issuedCleanupInProgress&&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalJourney(journey));}
            public boolean nativeCurrent(){return custodySourceCurrent()&&!cleanup&&transit.nativeCurrent();}
            public boolean cleanupCurrent(){return custodySourceCurrent()&&cleanup&&transit.cleanupCurrent();}
        }
        private final class Instruction {
            final McSuspendedFarmReturnCoordinator.MovementView view;final IssuedOperation operation;boolean failed,admissionClaimed,cameraClaimed;Object admissionChild;
            Instruction(McSuspendedFarmReturnCoordinator.MovementView view,IssuedOperation operation){this.view=view;this.operation=operation;}
        }
        private final class AppliedObservation {
            final Instruction original;AppliedObservation(Instruction original){this.original=original;}
        }
        public Object publicationObservationSnapshot(Object exactInstruction){
            if(!(exactInstruction instanceof McSavedAnchorRecovery.IssuedReturnTravelRequest.Instruction actual)||actual.failed
                ||actual!=retainedInstruction||lastNormallyReturnedView!=actual.view||!publicationSourceSnapshotCurrent(actual))return null;
            return new AppliedObservation(actual);
        }
        /** Read-only applied-frame lineage. It grants no new key, camera, queue, or actor permission. */
        public boolean publicationObservationSnapshotCurrent(Object exactCapsule){
            if(!(exactCapsule instanceof McSavedAnchorRecovery.IssuedReturnTravelRequest.AppliedObservation actual)
                ||actual.original.failed||!sourceFieldsCurrent()||!sameSource(actual.original.view))return false;
            return actual.original.view.publicationObservationNativeCurrent();
        }
        public Object publicationSourceSnapshot(){var actual=instruction;return actual!=null&&!actual.failed&&nativeCurrent()?actual:null;}
        public boolean publicationSourceSnapshotCurrent(Object exact){
            if(!(exact instanceof McSavedAnchorRecovery.IssuedReturnTravelRequest.Instruction actual)||actual.failed||!sourceFieldsCurrent())return false;
            return actual==instruction&&dispatching&&activeView==actual.view&&sameSource(actual.view)&&actual.view.nativeCurrent()
                ||actual==retainedInstruction&&lastNormallyReturnedView==actual.view&&sameSource(actual.view)&&actual.view.retainedNativeCurrent();
        }
        /** The original named ALIGN producer alone claims this exact instruction before service providers. */
        public boolean claimOriginalCameraDispatch(dev.veltraph.client.service.mc.McRotationService exact,Object originalInstruction){
            var actual=instruction;
            if(actual==null||actual!=originalInstruction||actual.failed||actual.cameraClaimed||actual.operation!=IssuedOperation.ALIGN
                ||operation!=IssuedOperation.ALIGN||services.rotation()!=exact||!nativeCurrent())return false;
            boolean originalCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.limit(40).filter(f->f.getDeclaringClass()!=IssuedReturnTravelRequest.class
                    &&f.getDeclaringClass()!=dev.veltraph.client.service.mc.McRotationService.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==Journey.class&&first.getMethodName().equals("trackOriginalIssuedView");
            });
            if(!originalCaller)return false;actual.cameraClaimed=true;return true;
        }
        private IssuedReturnTravelRequest(Journey journey,McSuspendedFarmReturnCoordinator.MovementView original){
            this.journey=journey;this.original=original;header=original.sourceHeader();checkpoint=original.checkpointIdentity();driver=original.driverIdentity();target=original.targetPosition();
            originalReturnDeadline=original.originalReturnDeadlineMillis();
        }
        public McSavedAnchorRecovery returningIdentity(){return McSavedAnchorRecovery.this;}
        public McTravelNavigator travelIdentity(){return journey.travel;}public Object journeyIdentity(){return journey;}
        public long journeySerial(){return journey.serial;}
        public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeader(){return original.sourceHeader();}
        public dev.veltraph.client.farming.module.FarmModule.IssuedReturnCheckpoint checkpointIdentity(){return original.checkpointIdentity();}
        public dev.veltraph.client.core.Module driverIdentity(){return original.driverIdentity();}
        public Vec3 targetPosition(){return target;}
        public FarmReturnPoint canonicalTargetPoint(){return journey.target;}
        public McSuspendedFarmReturnCoordinator.MovementView initialView(){return original;}
        public SharedMovementPolicy.Purpose purpose(){return SharedMovementPolicy.Purpose.EXACT_RETURN;}
        public DeviceTransit currentDeviceTransitIdentity(){var actual=deviceTransit;return actual!=null&&actual.custodySourceCurrent()?actual:null;}
        /** Actual original same-checkpoint runtime deadline; scalar alone can only refuse. */
        public long originalReturnDeadlineMillis(){
            var view=dispatching?activeView:lastNormallyReturnedView;
            return originalReturnDeadline>0&&sourceFieldsCurrent()&&sameSource(view)
                &&view.originalReturnDeadlineSourceCurrent(sourceHeader(),checkpointIdentity(),originalReturnDeadline)
                &&(nativeCurrent()||nativeContinuationCurrent())?originalReturnDeadline:-1;
        }
        /** Only the actual selected Journey can produce the device queue's cross-TICK source lineage. */
        public DeviceQueueContinuation captureDeviceQueueContinuation(
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,Object originalInstruction){
            return journey.captureOriginalIssuedDeviceQueue(admission,leg,originalInstruction);
        }
        public final class DeviceQueueContinuation {
            private final DeviceTransit transit;
            private final Instruction queuedBy;
            private final dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission;
            private final dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg;
            private final long deadline;
            private DeviceQueueContinuation(DeviceTransit transit,Instruction queuedBy,
                    dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission,
                    dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg){
                this.transit=transit;this.queuedBy=queuedBy;this.admission=admission;this.leg=leg;
                deadline=Math.min(originalReturnDeadline,Math.min(admission.deviceDeadlineMillis(),admission.journeyDeadlineMillis()));
            }
            public IssuedReturnTravelRequest requestIdentity(){return IssuedReturnTravelRequest.this;}
            public DeviceTransit transitIdentity(){return transit;}
            public dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admissionIdentity(){return admission;}
            public dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg legIdentity(){return leg;}
            public Object originalInstructionIdentity(){return queuedBy;}
            public long deadlineMillis(){return deadline;}
            /** Original source affiliation only; records no new permission and does not require an active Root frame. */
            public boolean metadataCurrent(){
                long now=System.currentTimeMillis();
                return deviceQueueContinuation==this&&!queuedBy.failed&&queuedBy.operation==IssuedOperation.TICK
                    &&transit==deviceTransit&&transit.admissionIdentity()==admission&&transit.originalLegIdentity()==leg
                    &&admission.requestIdentity()==IssuedReturnTravelRequest.this&&admission.legIdentity()==leg
                    &&sourceFieldsCurrent()&&admission.custodySourceCurrent()
                    &&services.clock() instanceof dev.veltraph.client.service.SystemClockService
                    &&deadline>0&&now>=admission.deviceStartedAtMillis()&&now<deadline
                    &&deadline==Math.min(originalReturnDeadline,Math.min(admission.deviceDeadlineMillis(),admission.journeyDeadlineMillis()))
                    &&original.originalReturnDeadlineSourceCurrent(sourceHeader(),checkpointIdentity(),originalReturnDeadline);
            }
            /** A fixed genuine queue instruction plus separately genuine evolving current/retained TICK. */
            public boolean current(){
                var actual=dispatching?instruction:retainedInstruction;var view=dispatching?activeView:lastNormallyReturnedView;
                long now=System.currentTimeMillis();
                return metadataCurrent()&&sameSource(view)&&actual!=null&&!actual.failed&&actual.operation==IssuedOperation.TICK
                    &&services.clock() instanceof dev.veltraph.client.service.SystemClockService
                    &&deadline>0&&now>=admission.deviceStartedAtMillis()&&now<deadline
                    &&deadline==Math.min(originalReturnDeadline,Math.min(admission.deviceDeadlineMillis(),admission.journeyDeadlineMillis()))
                    &&view.originalReturnDeadlineSourceCurrent(sourceHeader(),checkpointIdentity(),originalReturnDeadline)
                    &&(dispatching?nativeCurrent():nativeContinuationCurrent())&&transit.nativeCurrent()&&admission.nativeSourceCurrent();
            }
            public boolean dispatchCurrent(){return dispatching&&operation==IssuedOperation.TICK&&current()&&nativeCurrent();}
        }
        /** The selected Journey is the only private producer; a copied admission cannot qualify. */
        public DeviceTransit captureDeviceTransit(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission actual){
            return journey.captureOriginalIssuedDeviceTransit(actual);
        }
        /** Called after the real Shared private source handoff, before ordinary native item use. */
        public boolean bindDeviceIssue(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission actual,
                dev.veltraph.client.movement.device.DeviceUseReceipt.Issue issue,Object actualOwnedSource){
            if(!originalSharedDeviceCaller(true,"ownedUseSnapshot"))return false;
            var transit=deviceTransit;
            if(transit==null||actual!=transit.admission||issue==null||actualOwnedSource==null||!transit.nativeCurrent()
                ||actual.issueIdentity()!=issue||actual.actualOwnedUseSourceIdentity()!=actualOwnedSource)return false;
            if(transit.issue!=null)return transit.issue==issue&&transit.ownedSource==actualOwnedSource
                &&transit.ackTransit!=null&&transit.nativeIssueCurrent();
            transit.issue=issue;transit.ownedSource=actualOwnedSource;
            var view=transit.currentViewIdentity();
            var ack=view==null?null:view.captureDeviceAckTransit(transit);
            if(ack==null||!transit.nativeIssueCurrent())return false;
            transit.ackTransit=ack;return transit.nativeIssueCurrent();
        }
        /** Exact direct original entry only; ancestor names and public caller-supplied Booleans grant nothing. */
        private boolean originalSharedDeviceCaller(boolean adapter,String... methods){
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.limit(40).filter(frame->frame.getDeclaringClass()!=IssuedReturnTravelRequest.class
                    &&frame.getDeclaringClass()!=Journey.class).findFirst().orElse(null);
                if(first==null||!java.util.Set.of(methods).contains(first.getMethodName()))return false;
                var owner=first.getDeclaringClass();
                return adapter?owner.isAnonymousClass()&&owner.getEnclosingClass()==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class
                    :owner==dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class;
            });
        }
        /** Exact typed replacement entry after actual device custody and same-Leg retirement. */
        public boolean claimFinalEntryInstruction(McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,Object originalInstruction,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            return journey.captureOriginalIssuedFinalEntryInstruction(exact,originalInstruction,shared,goal,radius);
        }
        private boolean finalEntrySourceFieldsCurrent(McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            return exact!=null&&exact.requestIdentity()==this&&exact.sourceHeader()==header&&exact.checkpointIdentity()==checkpoint
                &&exact.driverIdentity()==driver&&exact.canonicalTargetPoint()==journey.target&&ownsSharedChild(shared)
                &&(exact.loadedSegmentIdentity()==null?loadedSegment==null&&target.equals(goal)
                    &&Double.doubleToLongBits(radius)==Double.doubleToLongBits(Math.min(.35,journey.arrivalRadius))
                    :exact.loadedSegmentIdentity()==loadedSegment&&loadedSegment.requestIdentity()==this
                        &&loadedSegment.sharedChildIdentity()==shared&&loadedSegment.rawCurrent()
                        &&loadedSegment.segmentPosition().equals(goal)
                        &&Double.doubleToLongBits(radius)==Double.doubleToLongBits(loadedSegment.terminalRadius())
                        &&radius>0&&radius<=.35)
                &&sourceFieldsCurrent();
        }
        /** Fixed original claim lineage plus a separately genuine evolving active/retained TICK. */
        public boolean currentFinalEntryInstructionCurrent(McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,Object originalInstruction,
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            if(finalEntryContinuation!=exact||finalEntryInstruction==null||finalEntryInstruction!=originalInstruction
                ||finalEntryInstruction.failed||finalEntryInstruction.operation!=IssuedOperation.TICK
                ||!finalEntrySourceFieldsCurrent(exact,shared,goal,radius))return false;
            var actual=dispatching?instruction:retainedInstruction;
            return actual!=null&&!actual.failed&&actual.operation==IssuedOperation.TICK
                &&(dispatching?nativeCurrent():nativeContinuationCurrent());
        }
        /** Execution callback carries the private call; it supplies no permission or native setter. */
        public <T>T withDeviceSelection(dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission actual,
                dev.veltraph.client.service.mc.McInventoryService inventory,int expected,int requested,boolean cleanup,
                java.util.function.Function<DeviceSelectionCall,T> action){
            return journey.captureOriginalIssuedDeviceSelection(actual,inventory,expected,requested,cleanup,action);
        }
        public McSuspendedFarmReturnCoordinator.ReturnCleanupCall currentOperation(){var view=activeView;return view==null?null:view.originalOperation();}
        public McSuspendedFarmReturnCoordinator.MovementView currentView(){return activeView;}
        public boolean sourceKindCurrent(LocalRoute.Kind kind){return nativeCurrent()&&(operation==IssuedOperation.WALK||operation==IssuedOperation.LOADED_WALK?kind==LocalRoute.Kind.WALK:operation==IssuedOperation.FLIGHT&&kind==LocalRoute.Kind.FLY);}
        public boolean ownsPublicationLease(dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease exact,Object child){return journey.travel.issuedReturnPublicationLease(this,exact,child);}
        public boolean ownsSharedChild(dev.veltraph.client.navigation.mc.McSharedMovementNavigator actual){return journey.travel.issuedReturnSharedChild(this,actual);}
        private boolean sameSource(McSuspendedFarmReturnCoordinator.MovementView view){return view!=null&&view.returningIdentity()==McSavedAnchorRecovery.this
            &&view.sourceHeader()==header&&view.checkpointIdentity()==checkpoint&&view.driverIdentity()==driver
            &&view.purpose()==purpose()&&journey.target==view.canonicalTargetPoint()&&target.equals(view.targetPosition())&&target.equals(journey.target.position())
            &&view.originalOperation().ownerIdentity()==driver&&view.sourceHeader().farmServicesIdentity()==services&&view.sourceHeader().configIdentity()==config;}
        private boolean sourceFieldsCurrent(){return !revoked&&activeJourney==journey&&generation==journey.serial&&!generationExhausted
            &&journey.issuedRequest==this&&journey.nativeCurrent()&&journey.controlOwner==driver&&target.equals(journey.target.position());}
        public boolean current(){var view=activeView;return dispatching&&sameSource(view)&&view.current()&&nativeCurrent();}
        /** Callback-free active instruction. No native continuation is inferred from dispatch alone. */
        public boolean nativeCurrent(){var view=activeView;return dispatching&&sourceFieldsCurrent()&&sameSource(view)&&view.nativeCurrent();}
        /** Only the original normal-return Core/Root marker can cover the next native consumer. */
        public boolean nativeContinuationCurrent(){var view=lastNormallyReturnedView;return sourceFieldsCurrent()&&sameSource(view)&&view.retainedNativeCurrent();}
        public boolean startDispatchCurrent(McTravelNavigator actual){return actual==journey.travel&&nativeCurrent()
            &&(operation==IssuedOperation.WALK||operation==IssuedOperation.LOADED_WALK||operation==IssuedOperation.FLIGHT)
            &&actualIssuedCaller("startOriginalIssuedWalk","startOriginalIssuedLoadedWalk","startOriginalIssuedFlight");}
        /** Affiliation is passive; admission additionally requires the original typed parent start entry. */
        /** One initial child handout from the actual named private Journey action, before child callbacks. */
        public boolean claimInitialChildAdmission(McTravelNavigator exact,Object child){
            var originalInstruction=instruction;
            if(exact!=journey.travel||originalInstruction==null||originalInstruction.admissionClaimed||!startDispatchCurrent(exact)
                ||!exact.issuedReturnInitialChild(this,child,operation==IssuedOperation.WALK))return false;
            originalInstruction.admissionClaimed=true;originalInstruction.admissionChild=child;return true;
        }
        /** The original child start retires its actual predecessor through the same typed Journey cleanup. */
        public boolean retireBeforeInitialChildStart(McTravelNavigator exact){
            var actual=instruction;
            if(exact!=journey.travel||actual==null||!actual.admissionClaimed||!startDispatchCurrent(exact))return false;
            if(journey.loadedReturnCall!=null&&journey.loadedReturnCall.dispatching&&"START".equals(journey.loadedReturnCall.operation))
                journey.loadedReturnCall.retireOriginalStartPredecessor(this);
            else journey.releaseOwnTravel();
            return startDispatchCurrent(exact)&&nativeCurrent();
        }
        /** Passive nested-child admission check of the already genuine initial handout. */
        public boolean nativeChildAdmissionCurrent(Object child){
            var exact=instruction;return exact!=null&&!exact.failed&&exact.admissionClaimed&&exact.admissionChild==child
                &&nativeCurrent()&&journey.travel.issuedReturnInitialChild(this,child,exact.operation==IssuedOperation.WALK);
        }
        public boolean sharedStartDispatchCurrent(dev.veltraph.client.navigation.mc.McSharedMovementNavigator exact){
            if(!ownsSharedChild(exact)||!nativeChildAdmissionCurrent(exact)||operation!=IssuedOperation.WALK)return false;
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.limit(40).filter(f->f.getDeclaringClass()!=IssuedReturnTravelRequest.class
                    &&f.getDeclaringClass()!=dev.veltraph.client.navigation.mc.McSharedMovementNavigator.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==McTravelNavigator.class&&first.getMethodName().equals("startIssuedReturn");
            });
        }
        public boolean tickDispatchCurrent(McTravelNavigator actual){return actual==journey.travel&&nativeCurrent()&&operation==IssuedOperation.TICK&&actualIssuedCaller("tickOriginalIssuedTravel");}
        private boolean actualIssuedCaller(String... methods){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.limit(40).filter(f->f.getDeclaringClass()!=IssuedReturnTravelRequest.class&&f.getDeclaringClass()!=McTravelNavigator.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==Journey.class&&java.util.Set.of(methods).contains(first.getMethodName());
        });}
        private <T>T dispatch(IssuedOperation op,java.util.function.Supplier<T> actual){
            if(dispatching){revoked=true;throw new IllegalStateException("Original issued Journey dispatch reentered");}
            retainedInstruction=null;lastNormallyReturnedView=null; // Revoke old continuity before any new provider.
            var core=original.coordinatorIdentity();var view=core.currentMovementView();
            if(!sameSource(view)||!sourceFieldsCurrent()||!view.current())throw new IllegalStateException("Original issued Journey source changed");
            activeView=view;operation=op;dispatching=true;var originalInstruction=new Instruction(view,op);instruction=originalInstruction;
            try{T result=actual.get();if(!originalInstruction.failed&&nativeCurrent()){lastNormallyReturnedView=view;retainedInstruction=originalInstruction;}return result;}
            catch(RuntimeException|Error failure){originalInstruction.failed=true;retainedInstruction=null;lastNormallyReturnedView=null;throw failure;}
            finally{dispatching=false;operation=null;activeView=null;instruction=null;}
        }
    }
    private McSuspendedFarmReturnCoordinator.MovementView originalIssuedView(FarmReturnPoint target){
        var core=dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator();var view=core==null?null:core.currentMovementView();
        if(view==null||view.returningIdentity()!=this||view.sourceHeader().farmServicesIdentity()!=services||view.sourceHeader().configIdentity()!=config
            ||target==null||target!=view.canonicalTargetPoint()||!target.position().equals(view.targetPosition())||!originalCoreStartCaller())return null;
        return view;
    }
    private boolean originalCoreStartCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.limit(40).filter(f->f.getDeclaringClass()!=McSavedAnchorRecovery.class&&f.getDeclaringClass().getEnclosingClass()!=McSavedAnchorRecovery.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass()==McSuspendedFarmReturnCoordinator.class;
    });}

    /** The actual selected Journey alone constructs this original retirement scope. */
    public final class IssuedReturnCleanup {
        private final Journey journey;
        private final McSuspendedFarmReturnCoordinator.ReturnCleanupCall call;
        private final McSuspendedFarmReturnCoordinator.CloseCall close;
        private final McSuspendedFarmReturnCoordinator.SourceHeader header;
        private final McSuspendedFarmReturnCoordinator.MetadataCloseCall metadata;
        private final IssuedReturnTravelRequest request;
        private final Thread thread=Thread.currentThread();
        private IssuedReturnCleanup(Journey journey,McSuspendedFarmReturnCoordinator.ReturnCleanupCall call){this.journey=journey;this.call=call;close=null;metadata=null;header=call.sourceHeader();request=journey.issuedRequest;}
        private IssuedReturnCleanup(Journey journey,McSuspendedFarmReturnCoordinator.CloseCall close){this.journey=journey;this.close=close;call=null;metadata=null;header=close.sourceHeader();request=journey.issuedRequest;}
        private IssuedReturnCleanup(Journey journey,McSuspendedFarmReturnCoordinator.MetadataCloseCall metadata){this.journey=journey;this.metadata=metadata;header=metadata.sourceHeader();call=null;close=null;request=journey.issuedRequest;}
        public McSavedAnchorRecovery returningIdentity(){return McSavedAnchorRecovery.this;}
        public McTravelNavigator travelIdentity(){return journey.travel;}
        public IssuedReturnTravelRequest requestIdentity(){return request;}
        public McSuspendedFarmReturnCoordinator.ReturnCleanupCall originalOperation(){return call;}
        public McSuspendedFarmReturnCoordinator.CloseCall originalClose(){return close;}
        public McSuspendedFarmReturnCoordinator.SourceHeader sourceHeader(){return header;}
        public Minecraft client(){return client;}public Object driverIdentity(){return request==null?null:request.driverIdentity();}
        public Object journeyIdentity(){return journey;}public long journeySerial(){return journey.serial;}
        public dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt originalOwnAim(){return journey.issuedOwnAim;}
        /** Exact old logical source only. It never authorizes a native setter or a new admission. */
        public boolean metadataCurrent(){return Thread.currentThread()==thread&&journey.issuedCleanupInProgress&&journey.selected()
            &&request!=null&&journey.issuedRequest==request&&request.sourceHeader()==header&&request.returningIdentity()==McSavedAnchorRecovery.this
            &&header.farmServicesIdentity()==services&&header.configIdentity()==config&&(metadata==null||metadata.currentMetadata()&&metadata.returningIdentity()==McSavedAnchorRecovery.this); }
        public boolean current(){return metadataCurrent()&&(call!=null?call.current():close!=null?close.current():true)&&nativeCurrent();}
        /** Native caller/Journey fields only; no input/rotation read-fence recursion. */
        public boolean nativeCurrent(){return metadataCurrent()&&journey.nativeCurrent()&&(call!=null
            ?call.returningIdentity()==McSavedAnchorRecovery.this&&call.client()==client&&call.ownerIdentity()==journey.controlOwner&&call.nativeCurrent()
            :close!=null&&close.returningIdentity()==McSavedAnchorRecovery.this&&close.driverIdentity()==journey.controlOwner
                &&close.originalJourneyClaimed(journey)&&close.nativeCurrent());}
    }

    /** Mandatory exact capsule even when no Journey is selected; null always means capture failure. */
    public PassiveLifecycleContinuation capturePassiveLifecycleContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual){
        thread();if(actual==null||!actual.sourceBeforeCurrent(client))return null;
        var j=activeJourney;
        if(j!=null&&(!j.nativeCurrent()||actual.registryIdentity()!=j.registry
            ||!j.registry.lifecycleSameHistory(j.lifecycle.value(),actual.beforeLifecycle())))return null;
        var travel=j==null?null:j.travel.capturePassiveLifecycleContinuation(actual);if(j!=null&&travel==null)return null;
        var result=new PassiveLifecycleContinuation(actual,j,travel);
        return result.rawCurrent()&&actual.sourceBeforeCurrent(client)?result:null;
    }
    public final class PassiveLifecycleContinuation {
        private final dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual;private final Journey journey;
        private final dev.veltraph.client.navigation.mc.PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot>.Continuation lifecycle;
        private final McTravelNavigator.PassiveLifecycleContinuation travel;
        private final long serial=generation;private final boolean exhausted=generationExhausted;
        private final AlignmentCall originalSelected=selectedAlignmentCall,originalCall;
        private final dev.veltraph.client.service.mc.McRotationService.SavedReturnQueuedHandle originalHandle;
        private final boolean wasRetiring,retirementRefused;
        private final Object options=client.options,gameMode=client.gameMode,mouse=client.mouseHandler;
        private final Object originalInput,originalMenu,originalInventoryMenu,originalCarried;
        private final double x,y,z,vx,vy,vz;private final float yaw,pitch;private final Object posture;
        private boolean prepared,committed;
        private PassiveLifecycleContinuation(dev.veltraph.client.VeltraphBootstrap.RootPassiveTransition actual,Journey journey,McTravelNavigator.PassiveLifecycleContinuation travel){
            this.actual=actual;this.journey=journey;this.travel=travel;lifecycle=journey==null?null:journey.lifecycle.capture();
            originalCall=journey==null?null:journey.alignmentCall;originalHandle=journey==null?null:journey.alignmentHandle;
            wasRetiring=journey!=null&&journey.retiring;retirementRefused=journey!=null&&journey.alignmentRetirementRefused;
            var p=journey==null?null:journey.owner;originalInput=p==null?null:p.input;originalMenu=p==null?null:p.containerMenu;originalInventoryMenu=p==null?null:p.inventoryMenu;
            originalCarried=p==null?null:p.inventoryMenu.getCarried();x=p==null?0:p.getX();y=p==null?0:p.getY();z=p==null?0:p.getZ();yaw=p==null?0:p.getYRot();pitch=p==null?0:p.getXRot();posture=p==null?null:p.getPose();
            var v=p==null?net.minecraft.world.phys.Vec3.ZERO:p.getDeltaMovement();vx=v.x;vy=v.y;vz=v.z;}
        public boolean beforeCurrent(){return rawCurrent()&&actual.sourceBeforeCurrent(client)
            &&(journey==null||journey.registry.lifecycleCurrent(lifecycle.before()));}
        public boolean rawCurrent(){return !committed&&client.isSameThread()&&activeJourney==journey&&generation==serial&&generationExhausted==exhausted
            &&selectedAlignmentCall==originalSelected&&(journey==null||lifecycle!=null&&lifecycle.rawCurrent()&&travel.rawCurrent()
                &&journey.alignmentCall==originalCall&&journey.alignmentHandle==originalHandle&&journey.retiring==wasRetiring
                &&journey.alignmentRetirementRefused==retirementRefused&&nativeSceneCurrent());}
        private boolean nativeSceneCurrent(){var j=journey;var listener=client.getConnection();return j!=null&&j.selected()
            &&dev.veltraph.client.VeltraphBootstrap.moduleManager()==j.registry&&client.player==j.owner&&client.level==j.world
            &&listener==j.listener&&listener!=null&&listener.getConnection()==j.connection&&client.options==options&&client.gameMode==gameMode&&client.mouseHandler==mouse
            &&j.owner.input==originalInput&&j.owner.containerMenu==originalMenu&&j.owner.inventoryMenu==originalInventoryMenu&&j.owner.inventoryMenu.getCarried()==originalCarried
            &&Double.doubleToLongBits(j.owner.getX())==Double.doubleToLongBits(x)&&Double.doubleToLongBits(j.owner.getY())==Double.doubleToLongBits(y)&&Double.doubleToLongBits(j.owner.getZ())==Double.doubleToLongBits(z)
            &&Float.floatToIntBits(j.owner.getYRot())==Float.floatToIntBits(yaw)&&Float.floatToIntBits(j.owner.getXRot())==Float.floatToIntBits(pitch)&&j.owner.getPose()==posture
            &&Double.doubleToLongBits(j.owner.getDeltaMovement().x)==Double.doubleToLongBits(vx)&&Double.doubleToLongBits(j.owner.getDeltaMovement().y)==Double.doubleToLongBits(vy)&&Double.doubleToLongBits(j.owner.getDeltaMovement().z)==Double.doubleToLongBits(vz);}
        public boolean prepare(){
            if(prepared||!rawCurrent()||!actual.normalReturnedCurrent(client))return false;
            var next=actual.normalReturnedLifecycle();if(next==null)return false;
            if(journey!=null&&(!journey.registry.lifecycleSameHistory(lifecycle.before(),actual.beforeLifecycle())
                ||!journey.registry.lifecycleCurrent(next)||!travel.prepare()||!lifecycle.prepare(next)))return false;
            if(!actual.normalReturnedCurrent(client)||!rawCurrent())return false;prepared=true;return true;
        }
        /** After-snapshot observation only; does not read the still-uncommitted Service handle or grant effects. */
        public boolean prospectiveAlignmentCurrent(AlignmentCall exact){var j=journey;return prepared&&rawCurrent()&&actual.normalReturnedCurrent(client)
            &&j!=null&&exact!=null&&exact==originalCall&&exact==originalSelected&&exact==j.alignmentCall&&exact.adapter==savedReturnAdapter
            &&!exact.revoked&&!j.retiring&&!j.alignmentRetirementRefused&&nativeSceneCurrent()&&j.owner.isAlive()&&client.gui.screen()==null
            &&j.target.area()!=null&&j.target.area()!=GameArea.UNKNOWN&&j.target.dimension().equals(j.world.dimension().identifier().toString())
            &&j.registry.lifecycleCurrent(actual.normalReturnedLifecycle());}
        public boolean commit(){if(!prepared||!actual.passiveDeclarationCommitWindowCurrent()||!rawCurrent())return false;
            if(journey!=null&&(!travel.commit()||!lifecycle.commit()))return false;committed=true;return true;}
    }
    /** Private actual align episode. Public observations cannot construct a source or admit a queue. */
    public final class AlignmentCall {
        private final Journey journey;private final FarmMaintenanceCoordinator.SavedReturnAdapter adapter;
        private boolean admitting,revoked;
        private AlignmentCall(Journey journey,FarmMaintenanceCoordinator.SavedReturnAdapter adapter){this.journey=journey;this.adapter=adapter;}
        public FarmReturnPoint target(){return journey.target;}
        public boolean matchingDriver(McSavedAnchorRecovery exact){return exact==McSavedAnchorRecovery.this&&adapter==savedReturnAdapter;}
        public boolean admissionCurrent(){return admitting&&currentFinalNative();}
        public boolean currentFinalNative(){return !revoked&&adapter==savedReturnAdapter&&selectedAlignmentCall==this&&journey.alignmentCall==this&&journey.selected()
            &&!journey.retiring&&journey.current(journey.target.area());}
        public boolean cleanupFinalNative(){return !revoked&&adapter==savedReturnAdapter&&selectedAlignmentCall==this&&journey.alignmentCall==this&&journey.selected()
            &&journey.nativeCurrent()&&journey.owner.isAlive()&&client.gui.screen()==null&&client.getCameraEntity()==journey.owner
            &&journey.owner.containerMenu==journey.owner.inventoryMenu&&journey.owner.inventoryMenu.getCarried().isEmpty();}
        private void revoke(){revoked=true;if(selectedAlignmentCall==this)selectedAlignmentCall=null;}
    }

    private final class Journey {
        final long serial;
        final FarmReturnPoint target;
        final double arrivalRadius,viewTolerance;
        final LocalPlayer owner;
        final ClientLevel world;
        final ClientPacketListener listener;
        final Connection connection;
        final ModuleManager registry;
        final dev.veltraph.client.navigation.mc.PassiveTravelLifecycleSlot<ModuleManager.LifecycleSnapshot> lifecycle;
        final Object controlOwner;
        final McTravelNavigator travel;
        final GardenPlotReturnRuntime plotReturn;
        final SavedAnchorRecovery recovery;
        IssuedReturnTravelRequest issuedRequest;
        LoadedReturnCall loadedReturnCall;
        boolean issuedCleanupInProgress;
        dev.veltraph.client.service.mc.McRotationService.DirectPublicationReceipt issuedOwnAim;
        boolean flew,loadedWalkingReturn,rejectedPlot,failedTotal,plotAcknowledged,retiring;
        Vec3 expectedLanding;
        long expectedLandingAt,plotSettleSince=-1,started,lastTick;
        String lastTravelDiagnostic="none";
        String lastAlignmentDiagnostic="NOT_ATTEMPTED";
        AlignmentCall alignmentCall;
        dev.veltraph.client.service.mc.McRotationService.SavedReturnQueuedHandle alignmentHandle;
        boolean alignmentRetirementRefused;
        Journey(long serial,FarmReturnPoint target,double radius,double viewTolerance,LocalPlayer owner,ClientLevel world,
                ClientPacketListener listener,Connection connection,ModuleManager registry,ModuleManager.LifecycleSnapshot lifecycle,Object controlOwner){
            this.serial=serial;this.target=target;arrivalRadius=radius;this.viewTolerance=viewTolerance;this.owner=owner;this.world=world;
            this.listener=listener;this.connection=connection;this.registry=registry;this.lifecycle=new dev.veltraph.client.navigation.mc.PassiveTravelLifecycleSlot<>(lifecycle);this.controlOwner=controlOwner;
            travel=new McTravelNavigator(client,services,config);
            plotReturn=new GardenPlotReturnRuntime(this::sendPlot);
            recovery=new SavedAnchorRecovery(new SavedAnchorRecovery.Actions(){
                public boolean startWalk(){return issuedAction(IssuedOperation.WALK,Journey.this::startOriginalIssuedWalk);}
                public boolean startLoadedWalk(){return issuedAction(IssuedOperation.LOADED_WALK,Journey.this::startOriginalIssuedLoadedWalk);}
                public boolean startFlight(){return issuedAction(IssuedOperation.FLIGHT,Journey.this::startOriginalIssuedFlight);}
                public SavedAnchorRecovery.Travel travel(){return issuedAction(IssuedOperation.TICK,Journey.this::tickOriginalIssuedTravel);}
                public void align(){
                    if(issuedRequest!=null){issuedAction(IssuedOperation.ALIGN,Journey.this::trackOriginalIssuedView);return;}
                    boolean originalCurrent=current(target.area());if(!originalCurrent){lastAlignmentDiagnostic="ORIGINAL_JOURNEY_CURRENT_FALSE";return;}
                    if(savedReturnAdapter!=null){alignSavedReturn();return;}
                    // Every native producer resolves Root and callback-capable configuration first,
                    // then checks this immutable journey through its callback-free final leaf.
                    var originalRotation=services.rotation();Object returned=originalRotation.trackWithFinalNativeVeto(target.yaw(),target.pitch(),()->current(target.area()));
                    try{lastAlignmentDiagnostic="ORIGINAL_JOURNEY_CURRENT_TRUE | returned="+(returned==null?"NULL_NO_PUBLICATION":"NONNULL_NOT_OUTCOME_AUTHORITY")+" | target="+target.yaw()+"/"+target.pitch()
                        +(originalRotation instanceof dev.veltraph.client.service.mc.McRotationService actual?" | "+actual.cachedDirectScopeAuthority():" | NON_NATIVE_ROTATION_ADAPTER");}catch(Throwable ignored){/* Preserve original alignment return; diagnostics grant no permission. */}
                }
                public void release(){releaseOwnTravel();}
            },SavedAnchorRecovery.Settings.defaults());
        }
        private IssuedReturnTravelRequest.DeviceQueueContinuation captureOriginalIssuedDeviceQueue(
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission admission,
                dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg leg,Object originalInstruction){
            var request=issuedRequest;
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.filter(f->f.getDeclaringClass()!=Journey.class&&f.getDeclaringClass()!=IssuedReturnTravelRequest.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.Leg.class
                    &&first.getMethodName().equals("queueOwnedIssuedDeviceSource");
            });
            if(!caller||request==null||admission==null||leg==null||request.deviceQueueContinuation!=null
                ||admission.requestIdentity()!=request||admission.legIdentity()!=leg||!request.nativeCurrent())return null;
            var instruction=request.instruction;var transit=request.deviceTransit;
            if(instruction==null||instruction!=originalInstruction||instruction.failed||instruction.operation!=IssuedOperation.TICK
                ||transit==null||transit.admissionIdentity()!=admission||transit.originalLegIdentity()!=leg
                ||!transit.nativeCurrent()||request.originalReturnDeadlineMillis()<=0)return null;
            var capsule=request.new DeviceQueueContinuation(transit,instruction,admission,leg);request.deviceQueueContinuation=capsule;
            return capsule.dispatchCurrent()?capsule:null;
        }
        private boolean captureOriginalIssuedFinalEntryInstruction(McSuspendedFarmReturnCoordinator.FinalEntryContinuation exact,
                Object originalInstruction,dev.veltraph.client.navigation.mc.McSharedMovementNavigator shared,Vec3 goal,double radius){
            var request=issuedRequest;
            if(request==null||request.finalEntryContinuation!=null||request.finalEntryInstruction!=null
                ||!request.finalEntrySourceFieldsCurrent(exact,shared,goal,radius))return false;
            boolean originalCaller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.limit(40).filter(frame->frame.getDeclaringClass()!=Journey.class
                    &&frame.getDeclaringClass()!=IssuedReturnTravelRequest.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==dev.veltraph.client.navigation.mc.LegacyTravelPublicationLease.class
                    &&first.getMethodName().equals("beginAfterIssuedReturnEntry");
            });
            var actual=request.instruction;
            if(!originalCaller||actual==null||actual!=originalInstruction||actual.failed||actual.operation!=IssuedOperation.TICK
                ||!request.dispatching||!request.nativeCurrent()
                ||!exact.entryClaimSourceCurrent(request,actual,shared,goal,radius)||!request.nativeCurrent())return false;
            request.finalEntryContinuation=exact;request.finalEntryInstruction=actual;
            return request.currentFinalEntryInstructionCurrent(exact,actual,shared,goal,radius);
        }
        private IssuedReturnTravelRequest.DeviceTransit captureOriginalIssuedDeviceTransit(
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission actual){
            var request=issuedRequest;if(request==null||!request.originalSharedDeviceCaller(false,"begin")
                ||actual==null||actual.requestIdentity()!=request
                ||!request.ownsSharedChild(actual.issuerIdentity())||!request.nativeCurrent()||!actual.custodySourceCurrent())return null;
            var instruction=request.instruction;
            if(instruction==null||instruction.failed||!request.dispatching
                ||instruction.operation!=IssuedOperation.WALK&&instruction.operation!=IssuedOperation.TICK)return null;
            var prior=request.deviceTransit;
            if(prior!=null&&prior.admission==actual)return prior.custodySourceCurrent()?prior:null;
            if(prior!=null&&prior.custodySourceCurrent())return null;
            if(request.loadedSegment!=null&&request.loadedSegment.admittedDeviceTransit!=null)return null;
            var next=request.new DeviceTransit(actual,instruction);request.deviceTransit=next;
            if(request.loadedSegment!=null)
                request.loadedSegment.admittedDeviceTransit=next; // A failed native attempt remains a refusal, never an empty-field rearm.
            return next.custodySourceCurrent()&&request.nativeCurrent()?next:null;
        }
        private <T>T captureOriginalIssuedDeviceSelection(
                dev.veltraph.client.navigation.mc.McSharedMovementNavigator.ReturnDeviceAdmission actual,
                dev.veltraph.client.service.mc.McInventoryService inventory,int expected,int requested,boolean cleanup,
                java.util.function.Function<IssuedReturnTravelRequest.DeviceSelectionCall,T> action){
            var request=issuedRequest;var transit=request==null?null:request.deviceTransit;
            if(request==null||!request.originalSharedDeviceCaller(true,"selectSlotOwned","restoreSelectedSlotLoan")||transit==null||actual!=transit.admission||inventory==null||services.inventory()!=inventory
                ||action==null||request.deviceSelectionCall!=null||!transit.custodySourceCurrent()
                ||expected==requested||client.player.getInventory().getSelectedSlot()!=expected)return null;
            var instruction=request.instruction;
            if(instruction==null&&!(cleanup&&issuedCleanupInProgress&&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.nativeCurrent()))return null;
            var call=request.new DeviceSelectionCall(transit,instruction,inventory,expected,requested,cleanup);request.deviceSelectionCall=call;
            try{if(!(cleanup?call.cleanupCurrent():call.nativeCurrent()))return null;return action.apply(call);}
            finally{call.closed=true;if(request.deviceSelectionCall==call)request.deviceSelectionCall=null;}
        }
        private boolean trackOriginalIssuedView(){
            if(!current(target.area())||issuedRequest==null||!(services.rotation() instanceof dev.veltraph.client.service.mc.McRotationService nativeRotation))return false;
            var instruction=issuedRequest.publicationSourceSnapshot();
            if(instruction==null)return false;
            var actual=nativeRotation.trackIssuedReturnView(issuedRequest,instruction,target.yaw(),target.pitch());
            if(actual!=null&&actual.newPublication()&&actual.current()&&issuedRequest.nativeCurrent()&&issuedRequest.publicationSourceSnapshotCurrent(instruction))issuedOwnAim=actual;
            return actual!=null&&issuedRequest.publicationSourceSnapshotCurrent(instruction);
        }
        private boolean startOriginalIssuedWalk(){
                    if(!current(target.area())||rejectedPlot)return false;
                    var node=new LocalRoute.Node(destination(),target.yaw(),target.pitch());
                    boolean result=issuedRequest==null?travel.start(node,Math.min(.35,arrivalRadius),SharedMovementPolicy.Purpose.EXACT_RETURN)
                        :travel.startIssuedReturn(issuedRequest,node,Math.min(.35,arrivalRadius),SharedMovementPolicy.Purpose.EXACT_RETURN);
                    if(!current(target.area()))return false;flew=false;return result;
        }
        private boolean startOriginalIssuedLoadedWalk(){
                    if(!current(target.area())||rejectedPlot)return false;
                    var node=new LocalRoute.Node(destination(),target.yaw(),target.pitch());
                    if(loadedReturnCall!=null)throw new IllegalStateException("Original loaded return cannot be restarted");
                    var loadedCall=new LoadedReturnCall(this);loadedReturnCall=loadedCall;
                    boolean result=travel.startOriginalLoadedReturn(loadedCall,node,Math.min(.35,arrivalRadius));
                    if(!current(target.area()))return false;flew=false;loadedWalkingReturn=result;return result;
        }
        private boolean startOriginalIssuedFlight(){
                    if(arrivalRadius<.35||!current(target.area()))return false;
                    var mode=config.get(TravelConfig.TRAVEL_APPROACH_MODE);
                    if(!current(target.area())||!owner.getAbilities().mayfly||mode==ApproachMode.WALK_ONLY)return false;
                    var node=new LocalRoute.Node(LocalRoute.Kind.FLY,destination(),target.yaw(),target.pitch());
                    boolean result=issuedRequest==null?travel.start(node,.35,SharedMovementPolicy.Purpose.EXACT_RETURN)
                        :travel.startIssuedReturn(issuedRequest,node,.35,SharedMovementPolicy.Purpose.EXACT_RETURN);
                    if(!current(target.area()))return false;flew=result;return result;
        }
        private SavedAnchorRecovery.Travel tickOriginalIssuedTravel(){
                    if(!current(target.area()))return SavedAnchorRecovery.Travel.FAILED;
                    var result=loadedWalkingReturn&&loadedReturnCall!=null?travel.tickOriginalLoadedReturn(loadedReturnCall)
                        :issuedRequest==null?travel.tick():travel.tickIssuedReturn(issuedRequest);
                    if(!current(target.area()))return SavedAnchorRecovery.Travel.FAILED;
                    lastTravelDiagnostic=result+" | "+travel.status();
                    if(result==McTravelNavigator.State.ARRIVED&&flew){
                        var terrain=new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config);
                        boolean supported=GroundPathfinder.standable(terrain,GroundPathfinder.node(target.position()));
                        if(!supported||!current(target.area()))return SavedAnchorRecovery.Travel.FAILED;
                        // Native ability dispatch is allowed only after the final captured-session read.
                        owner.getAbilities().flying=false;owner.onUpdateAbilities();
                    }
                    return SavedAnchorRecovery.Travel.valueOf(result.name());
        }
        private <T>T issuedAction(IssuedOperation operation,java.util.function.Supplier<T> actual){return issuedRequest==null?actual.get():issuedRequest.dispatch(operation,actual);}
        boolean selected(){return activeJourney==this&&!generationExhausted&&generation==serial;}
        boolean nativeCurrent(){
            if(!selected())return false;
            var actual=client.getConnection();
            var before=new SavedAnchorNativeContext.Scene(registry,lifecycle.value(),serial,target,owner,world,listener,connection);
            var after=new SavedAnchorNativeContext.Scene(dev.veltraph.client.VeltraphBootstrap.moduleManager(),lifecycle.value(),generation,target,
                    client.player,client.level,actual,actual==null?null:actual.getConnection());
            return SavedAnchorNativeContext.current(before,after);
        }
        /** No public owner/getter/config/clock callback occurs in this final refusal leaf. */
        boolean current(GameArea area){return nativeCurrent()&&owner.isAlive()&&client.gui.screen()==null&&area!=null&&area!=GameArea.UNKNOWN
                &&target.area()==area&&target.dimension().equals(world.dimension().identifier().toString());}
        boolean cleanupCurrent(){
            if(!nativeCurrent()||controlOwner==null)return false;
            var authority=McOwnedRotation.authority(client);
            return authority==controlOwner&&nativeCurrent();
        }
        void alignSavedReturn(){
            if(alignmentRetirementRefused||!current(target.area()))return;
            var actualRotation=services.rotation();
            if(!(actualRotation instanceof dev.veltraph.client.service.mc.McRotationService rotation))throw new IllegalStateException("Native saved return rotation service required");
            if(alignmentHandle!=null){if(!alignmentHandle.current())throw new IllegalStateException("Original saved return queue changed");return;}
            if(alignmentCall!=null)throw new IllegalStateException("Original saved return admission cannot be retried");
            var call=new AlignmentCall(this,savedReturnAdapter);alignmentCall=call;selectedAlignmentCall=call;call.admitting=true;
            dev.veltraph.client.service.mc.McRotationService.SavedReturnQueuedHandle issued;
            try{var source=savedReturnAdapter.issue(call);if(source==null)throw new IllegalStateException("Original saved return source unavailable");
                issued=rotation.rotateToSavedReturnOwned(target.yaw(),target.pitch(),null,source);
                if(issued!=null)alignmentHandle=issued; // Preserve genuine cleanup custody before any later source read.
            }finally{call.admitting=false;}
            if(issued==null||!call.currentFinalNative())throw new IllegalStateException("Original saved return queue admission refused");
        }
        void retireSavedReturnAlignment(){
            var handle=alignmentHandle;var call=alignmentCall;if(handle==null){if(call!=null)call.revoke();return;}
            var actualRotation=services.rotation();
            if(!(actualRotation instanceof dev.veltraph.client.service.mc.McRotationService rotation)){alignmentRetirementRefused=true;throw new IllegalStateException("Original saved return rotation service changed");}
            var result=rotation.cancelSavedReturnOwned(handle);
            if(result==null||!result.current()||result.status()==dev.veltraph.client.service.mc.McRotationService.SavedReturnQueueRetirement.Status.REFUSED){alignmentRetirementRefused=true;throw new IllegalStateException("Original saved return queue retirement refused");}
            boolean metadataOnly=result.status()==dev.veltraph.client.service.mc.McRotationService.SavedReturnQueueRetirement.Status.METADATA_ONLY;
            alignmentHandle=null;if(call!=null)call.revoke();
            if(metadataOnly&&!retiring){alignmentRetirementRefused=true;throw new IllegalStateException("Saved return completion lacks original native queue retirement");}
        }
        private void performOriginalIssuedRelease(){
            try{travel.stop();}finally{try{if(cleanupCurrent())services.rotation().cancelRotation();}finally{if(cleanupCurrent())services.input().releaseAllMovement();}}
        }
        void releaseOwnTravel(){
            if(loadedReturnCall!=null){
                if(loadedReturnCall.retiringOriginalStartPredecessor){performOriginalTravelRelease();return;}
                loadedReturnCall.withOriginalJourneyStop(()->{performOriginalTravelRelease();return Boolean.TRUE;});return;
            }
            performOriginalTravelRelease();
        }
        private void performOriginalTravelRelease(){
            if(issuedRequest!=null){
                if(issuedCleanupInProgress&&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalJourney(this)){
                    performOriginalIssuedRelease();return;
                }
                var core=issuedRequest.original.coordinatorIdentity();var close=core.currentCloseCall();
                if(close!=null){retireOriginalIssuedReturn(close);return;}
                var call=core.currentReturnCleanupCall();
                if(call==null||call.returningIdentity()!=McSavedAnchorRecovery.this||call.sourceHeader()!=issuedRequest.sourceHeader())
                    throw new IllegalStateException("No actual original issued Journey retirement call");
                if(issuedCleanupInProgress)throw new IllegalStateException("Original issued Journey cleanup reentered");
                issuedCleanupInProgress=true;
                try {
                    var scope=new IssuedReturnCleanup(this,call);
                    if(!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.run(scope,this::performOriginalIssuedRelease))throw new IllegalStateException("Original issued Journey cleanup refused");
                } finally {issuedCleanupInProgress=false;}
                return;
            }
            if(savedReturnAdapter==null){
                try{travel.stop();}
                finally{try{if(cleanupCurrent())services.rotation().cancelRotation();}
                    finally{if(cleanupCurrent())services.input().releaseAllMovement();}}
                return;
            }
            try{travel.stop();}finally{if(!travel.loadedReturnCleanupRefused())retireSavedReturnAlignment();}
        }
        boolean sendPlot(int id){
            if(!current(target.area()))return false;
            // The fixed captured listener can never silently select a replacement listener.
            return SavedAnchorCommandFence.send(client,listener,connection,"tptoplot "+id,()->current(target.area()));
        }
        Vec3 destination(){return target.position();}
        boolean near(){return new Vec3(owner.getX(),owner.getY(),owner.getZ()).distanceTo(destination())<arrivalRadius;}
        boolean aligned(){return SavedAnchorViewTolerance.aligned(owner.getYRot(),owner.getXRot(),target.yaw(),target.pitch(),viewTolerance);}
        boolean stationary(){return owner.onGround()&&owner.getDeltaMovement().length()<.1;}
        boolean geometryLoaded(){return nativeCurrent()&&loadedGeometry()&&nativeCurrent();}
        /** Geometry is read only; the caller supplies no authority and must fence the whole read. */
        boolean loadedGeometry(){
            var feet=owner.blockPosition();var destination=target.position().toBlockPos();
            boolean currentLoaded=world.getChunkSource().hasChunk(feet.getX()>>4,feet.getZ()>>4);
            if(travel.loadedReturnMode())return currentLoaded&&travel.loadedReturnGeometry();
            boolean loaded=currentLoaded&&world.getChunkSource().hasChunk(destination.x()>>4,destination.z()>>4);
            return loaded&&(!loadedWalkingReturn||new dev.veltraph.client.navigation.mc.McNavigator(client,services.input(),config).standingClear(target.position()));
        }
        GardenPlotReturnRuntime.Observation observation(GameArea area){
            long now=services.clock().nowMillis();
            // The clock's callback must finish before the exact validity observation used by send.
            return new GardenPlotReturnRuntime.Observation(current(area),world,world==null?null:world.dimension().identifier().toString(),area,
                    owner==null?null:new Vec3(owner.getX(),owner.getY(),owner.getZ()),now);
        }
        SavedAnchorRecovery.State state(){
            if(travel.loadedReturnCleanupRefused())return SavedAnchorRecovery.State.FAILED;
            if(alignmentRetirementRefused||failedTotal||plotReturn.state()==GardenPlotReturnRuntime.State.FAILED)return SavedAnchorRecovery.State.FAILED;
            if(plotReturn.state()==GardenPlotReturnRuntime.State.CANCELLED)return SavedAnchorRecovery.State.CANCELLED;
            if(plotReturn.active()||plotReturn.state()==GardenPlotReturnRuntime.State.REJECTED)return SavedAnchorRecovery.State.WAITING_PLOT;
            return recovery.state();
        }
        boolean active(){return !travel.loadedReturnCleanupRefused()&&!failedTotal&&(plotReturn.active()||plotReturn.state()==GardenPlotReturnRuntime.State.REJECTED||recovery.active());}
        String status(){if(travel.loadedReturnCleanupRefused())return "Saved anchor: FAILED (original loaded cleanup custody retained)";return "Saved anchor: "+state()+(plotReturn.state()!=GardenPlotReturnRuntime.State.IDLE?" | "+plotReturn.status():active()?" | "+travel.status():state()==SavedAnchorRecovery.State.FAILED?" | "+lastTravelDiagnostic:"");}
    }

    private boolean originalIssuedStartAllowed(FarmReturnPoint target){
        var core=dev.veltraph.client.VeltraphBootstrap.suspendedFarmReturnCoordinator();
        if(core==null||!core.ownsReturningIdentity(this))return true;
        var selected=core.selectedMovementViewIdentity(this);
        if(selected==null)return !core.active();
        return target!=null&&target==selected.canonicalTargetPoint()&&selected.returningIdentity()==this
            &&selected.sourceHeader().farmServicesIdentity()==services&&selected.sourceHeader().configIdentity()==config
            &&originalCoreStartCaller()&&selected.current()&&selected.nativeCurrent();
    }
    public boolean start(RewarpPoint anchor,GameArea area){
        thread();if(!originalIssuedStartAllowed(anchor==null?null:FarmReturnPoint.fromSavedMarker(anchor)))return false;long before=generation;cancel();
        if(generationExhausted||before==Long.MAX_VALUE||generation!=before+1||anchor==null)return false;
        return startPose(FarmReturnPoint.fromSavedMarker(anchor),area,anchor.exactPosition()==null?.75:.35,SavedAnchorViewTolerance.DEFAULT);
    }
    public boolean start(FarmReturnPoint point,GameArea area){return start(point,area,.35);}
    public boolean start(FarmReturnPoint point,GameArea area,double radius){return start(point,area,radius,SavedAnchorViewTolerance.DEFAULT);}
    public boolean start(FarmReturnPoint point,GameArea area,double radius,double viewTolerance){
        thread();if(!originalIssuedStartAllowed(point))return false;long before=generation;cancel();
        if(generationExhausted||before==Long.MAX_VALUE||generation!=before+1)return false;
        if(!Double.isFinite(radius)||radius<=0||radius>.35||!SavedAnchorViewTolerance.valid(viewTolerance))return false;
        return startPose(point,area,radius,viewTolerance);
    }
    private boolean startPose(FarmReturnPoint point,GameArea area,double radius,double viewTolerance){
        if(point==null||!advance())return false;
        long entered=generation;
        var player=client.player;var world=client.level;var listener=client.getConnection();var connection=listener==null?null:listener.getConnection();
        var registry=dev.veltraph.client.VeltraphBootstrap.moduleManager();var lifecycle=registry==null?null:registry.lifecycleSnapshot();
        var control=McOwnedRotation.authority(client);
        // A callback-return value can never overwrite the newer journey it caused.
        if(generation!=entered||generationExhausted)return false;
        var journey=new Journey(entered,point,radius,viewTolerance,player,world,listener,connection,registry,lifecycle,control);activeJourney=journey;
        var issuedView=originalIssuedView(point);if(issuedView!=null)journey.issuedRequest=new IssuedReturnTravelRequest(journey,issuedView);
        long now=services.clock().nowMillis();
        if(!journey.current(area)){reject(journey);return false;}journey.started=journey.lastTick=now;
        var observed=journey.observation(area);
        if(!journey.current(area)){reject(journey);return false;}
        boolean plot=journey.plotReturn.start(point,observed);
        if(!journey.current(area)){reject(journey);return false;}
        if(plot){
            if(journey.cleanupCurrent())services.rotation().cancelRotation();
            if(journey.cleanupCurrent())services.input().releaseAllMovement();
            return journey.current(area)&&journey.plotReturn.active();
        }
        boolean result=journey.recovery.start(true,journey.owner.getAbilities().mayfly,now);
        if(!journey.current(area)){reject(journey);return false;}
        return result;
    }
    private void reject(Journey exact){if(activeJourney==exact)cancel();}
    public boolean arrived(GameArea area){
        thread();var j=activeJourney;return j!=null&&j.state()==SavedAnchorRecovery.State.ARRIVED&&j.current(area)
                &&j.geometryLoaded()&&j.near()&&j.aligned()&&j.stationary()&&j.current(area);
    }
    /** Private journey custody captured while ordinary arrival still has its original lifecycle. */
    final class ArrivalForOwnDisable {
        private final Journey journey;private final dev.veltraph.client.core.Module helper;
        private final SavedAnchorNativeContext.DisablePose pose;
        private final SavedAnchorNativeContext.FarmSettings farmSettings;
        private final dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot hardware;
        private final McOwnedRotation.InstallationSnapshot camera;
        private final long cameraEpoch,cameraSerial,cameraApplications;
        private final dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule.CompletedOwnDisable greenhouseCleanup;
        private ArrivalForOwnDisable(Journey journey,dev.veltraph.client.core.Module helper){
            this.journey=journey;this.helper=helper;pose=actualPose();farmSettings=SavedAnchorNativeContext.captureFarmSettings(config);
            hardware=dev.veltraph.client.service.mc.NormalIdleHardwareSnapshot.captureBeforeRelease(client);
            camera=McOwnedRotation.installationSnapshot(client);cameraEpoch=McOwnedRotation.epoch(client);
            cameraSerial=McOwnedRotation.publicationSerial(client);cameraApplications=McOwnedRotation.frameApplications(client);
            greenhouseCleanup=helper instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule greenhouse
                    ?greenhouse.captureCompletedOwnDisable(this):null;
        }
        /** Unchanged authentic publication history only. A cleanup effect needs its own private native retirement receipt. */
        private boolean publicationCurrent(){return (!(helper instanceof dev.veltraph.client.greenhouse.GreenhouseMaintenanceModule)||greenhouseCleanup!=null)
                &&farmSettings!=null&&farmSettings.current(config)&&hardware!=null&&hardware.current()&&camera!=null&&camera.current()
                &&cameraEpoch>=0&&cameraEpoch<Long.MAX_VALUE&&cameraSerial>=0&&cameraSerial<Long.MAX_VALUE
                &&cameraApplications>=0&&cameraApplications<Long.MAX_VALUE
                &&McOwnedRotation.epoch(client)==cameraEpoch&&McOwnedRotation.publicationSerial(client)==cameraSerial
                &&McOwnedRotation.frameApplications(client)==cameraApplications&&!McOwnedRotation.hasQueuedApplication(client);}
        private SavedAnchorNativeContext.DisablePose actualPose(){var p=journey.owner;
            return new SavedAnchorNativeContext.DisablePose(p.getX(),p.getY(),p.getZ(),p.getYRot(),p.getXRot(),p.getPose(),p.getInventory().getSelectedSlot());
        }
        private boolean nativeCurrentAfterDisable(GameArea area){
            var j=journey;if(activeJourney!=j||!j.selected()||area==null||area==GameArea.UNKNOWN||j.target.area()!=area)return false;
            var actual=client.getConnection();
            var before=new SavedAnchorNativeContext.Scene(j.registry,j.lifecycle.value(),j.serial,j.target,j.owner,j.world,j.listener,j.connection);
            var after=new SavedAnchorNativeContext.Scene(dev.veltraph.client.VeltraphBootstrap.moduleManager(),j.lifecycle.value(),generation,j.target,
                    client.player,client.level,actual,actual==null?null:actual.getConnection());
            return SavedAnchorNativeContext.arrivedAfterOwnDisable(before,after,helper)&&publicationCurrent()
                    &&(greenhouseCleanup==null||greenhouseCleanup.current(this,j.registry,j.lifecycle.value()))
                    &&SavedAnchorNativeContext.unchangedDisablePose(pose,actualPose())
                    &&j.owner.isAlive()&&client.gui.screen()==null
                    &&j.target.dimension().equals(j.world.dimension().identifier().toString());
        }
    }
    ArrivalForOwnDisable arrivalForOwnDisable(GameArea area,dev.veltraph.client.core.Module exactHelper){
        thread();var j=activeJourney;
        if(exactHelper==null||j==null||!arrived(area)||activeJourney!=j)return null;
        var original=new ArrivalForOwnDisable(j,exactHelper);
        return original.publicationCurrent()&&SavedAnchorNativeContext.unchangedDisablePose(original.pose,original.actualPose())
                &&j.current(area)&&activeJourney==j?original:null;
    }
    /** Retire only the exact arrived metadata after genuine helper disable, native restore9 and lease resume.
     * Pre-restore key/camera histories are deliberately not renewed; this issues no native cleanup effect. */
    boolean retireCompletedMaintenanceJourney(FarmMaintenanceCoordinator.SavedMaintenanceResumeCall exact){
        thread();if(exact==null||exact.returningDriverIdentity()!=this||!exact.normalReturnedCurrent())return false;
        Object retained=exact.originalArrivalIdentity();if(!(retained instanceof ArrivalForOwnDisable original))return false;
        var j=original.journey;var selected=selectedAlignmentCall;
        if(exact.helperIdentity()!=original.helper)return false;
        if(j==null||!CompletedMaintenanceJourneyIdentity.matches(j,activeJourney,j.serial,generation,generationExhausted)||j.state()!=SavedAnchorRecovery.State.ARRIVED||j.retiring
            ||j.alignmentHandle!=null||j.alignmentRetirementRefused||selected!=null&&selected!=j.alignmentCall
            ||j.alignmentCall!=null&&(!j.alignmentCall.revoked||j.alignmentCall.admitting))return false;
        var listener=client.getConnection();
        if(client.player!=j.owner||client.level!=j.world||listener!=j.listener||listener==null||listener.getConnection()!=j.connection
            ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=j.registry||j.target.area()!=GameArea.GARDEN
            ||!j.owner.isAlive()||client.gui.screen()!=null||!j.target.dimension().equals(j.world.dimension().identifier().toString())
            ||!SavedAnchorNativeContext.unchangedDisablePose(original.pose,original.actualPose()))return false;
        // The actual source proves its normally completed resume, not a current owner/Boolean or stale full Journey lifecycle.
        if(!exact.normalReturnedCurrent()||exact.returningDriverIdentity()!=this||exact.originalArrivalIdentity()!=original
            ||!CompletedMaintenanceJourneyIdentity.matches(j,activeJourney,j.serial,generation,generationExhausted)||j.state()!=SavedAnchorRecovery.State.ARRIVED||j.alignmentHandle!=null
            ||j.alignmentRetirementRefused||j.retiring||selectedAlignmentCall!=selected||exact.helperIdentity()!=original.helper
            ||j.alignmentCall!=null&&(!j.alignmentCall.revoked||j.alignmentCall.admitting)
            ||client.player!=j.owner||client.level!=j.world||client.getConnection()!=j.listener||j.listener.getConnection()!=j.connection
            ||dev.veltraph.client.VeltraphBootstrap.moduleManager()!=j.registry||!j.owner.isAlive()||client.gui.screen()!=null
            ||!SavedAnchorNativeContext.unchangedDisablePose(original.pose,original.actualPose()))return false;
        var detached=CompletedMaintenanceJourneyIdentity.detach(j,activeJourney,j.serial,generation,generationExhausted);
        if(detached==null||!detached.rawCurrent(activeJourney,generation,generationExhausted))return false;
        terminal=SavedAnchorRecovery.State.ARRIVED;terminalStatus="Saved anchor: ARRIVED";terminalDiagnostic=j.lastTravelDiagnostic;
        if(selected!=null)selectedAlignmentCall=null;
        activeJourney=detached.remaining();return true;
    }
    boolean arrivedAfterOwnDisable(ArrivalForOwnDisable original,GameArea area){
        thread();if(original==null||original.journey!=activeJourney)return false;var j=original.journey;
        return j.state()==SavedAnchorRecovery.State.ARRIVED&&original.nativeCurrentAfterDisable(area)
                &&j.loadedGeometry()&&j.near()&&j.aligned()&&j.stationary()&&original.nativeCurrentAfterDisable(area);
    }
    public void tick(GameArea area){
        thread();var j=activeJourney;if(j==null||!j.active())return;
        long now=services.clock().nowMillis();
        if(!j.current(area)){reject(j);return;}
        if(now<j.lastTick||now-j.started>=45000){j.failedTotal=true;j.plotReturn.cancel();j.recovery.cancel();return;}j.lastTick=now;
        if(j.plotReturn.active()){
            var observed=j.observation(area);
            if(!j.current(area)){reject(j);return;}j.plotReturn.tick(observed);
        }
        if(j.plotReturn.state()==GardenPlotReturnRuntime.State.READY_FOR_TRAVEL||j.plotReturn.state()==GardenPlotReturnRuntime.State.REJECTED){
            if(j.plotReturn.state()==GardenPlotReturnRuntime.State.READY_FOR_TRAVEL&&!j.plotAcknowledged&&j.expectedLanding==null){j.expectedLanding=new Vec3(j.owner.getX(),j.owner.getY(),j.owner.getZ());j.expectedLandingAt=now;}
            if(j.plotReturn.state()==GardenPlotReturnRuntime.State.READY_FOR_TRAVEL){
                boolean ready=j.current(area)&&j.owner.onGround()&&j.owner.getDeltaMovement().length()<.1&&j.geometryLoaded();
                if(!ready){j.plotSettleSince=-1;return;}if(j.plotSettleSince<0)j.plotSettleSince=now;if(now-j.plotSettleSince<250)return;
            }
            j.rejectedPlot=j.plotReturn.state()==GardenPlotReturnRuntime.State.REJECTED;j.plotReturn.cancel();
            boolean valid=j.current(area);j.recovery.start(valid,valid&&j.owner.getAbilities().mayfly,now);
            if(!j.current(area)){reject(j);return;}
        }
        if(!j.recovery.active())return;
        boolean near=j.near(),aligned=j.aligned(),stationary=j.stationary();
        long finalNow=services.clock().nowMillis();
        if(!j.current(area)){reject(j);return;}
        boolean geometry=j.geometryLoaded();
        if(!j.current(area)){reject(j);return;}
        j.recovery.tick(geometry,near,aligned,stationary,finalNow);
        if(!j.current(area))reject(j);
    }
    public boolean active(){var j=activeJourney;return j!=null&&j.active();}
    public SavedAnchorRecovery.State state(){var j=activeJourney;return j==null?terminal:j.state();}
    public String status(){var j=activeJourney;return j==null?terminalStatus:j.status();}
    public String diagnostics(){var j=activeJourney;return j==null?terminalStatus+" | "+terminalDiagnostic:j.status()+" | "+j.travel.diagnostics()+" | cachedOriginalAlignment="+j.lastAlignmentDiagnostic;}
    public void onServerMessage(String line){thread();var j=activeJourney;if(j!=null&&j.current(j.target.area()))j.plotReturn.onServerMessage(line);}
    public boolean consumeExpectedTeleport(Vec3 position,long now){
        thread();var j=activeJourney;if(j==null||j.failedTotal||j.state()==SavedAnchorRecovery.State.CANCELLED||j.state()==SavedAnchorRecovery.State.FAILED
                ||!j.current(j.target.area())||j.plotAcknowledged||position==null)return false;
        boolean pending=j.plotReturn.expectsTeleport(position,now);
        boolean observed=j.expectedLanding!=null&&now>=j.expectedLandingAt&&now-j.expectedLandingAt<500&&j.expectedLanding.distanceTo(position)<=.35;
        if((pending||observed)&&j.current(j.target.area())){j.plotAcknowledged=true;j.expectedLanding=null;return true;}return false;
    }
    /** Original logical membership only; no role, native effect, or new source is admitted. */
    public boolean hasOriginalIssuedReturn(McSuspendedFarmReturnCoordinator.SourceHeader exact){
        var j=activeJourney;return exact!=null&&j!=null&&j.issuedRequest!=null&&j.issuedRequest.sourceHeader()==exact&&generation==j.serial&&!generationExhausted;
    }
    private void performOriginalIssuedClose(Journey exact){exact.plotReturn.cancel();exact.recovery.cancel();exact.travel.stop();}
    private void performOriginalIssuedMetadataClose(Journey exact){exact.travel.stop();services.rotation().cancelRotation();}
    /** Genuine pre-revocation Root close; only the original private Journey can be claimed once. */
    public boolean retireOriginalIssuedReturn(McSuspendedFarmReturnCoordinator.CloseCall call){
        thread();var j=activeJourney;
        if(call==null||call.returningIdentity()!=this||j==null||j.issuedRequest==null||j.issuedRequest.sourceHeader()!=call.sourceHeader())return false;
        if(j.issuedCleanupInProgress)return false;
        j.issuedCleanupInProgress=true;
        try {
            var source=new IssuedReturnCleanup(j,call);
            // Freeze all original native/service/child fields before the cause/current callbacks.
            var captured=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.prepare(source);
            if(captured==null||activeJourney!=j||generation!=j.serial||!call.claimOriginalJourney(this,j))return false;
            boolean closed=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.runPrepared(captured,()->performOriginalIssuedClose(j));
            if(activeJourney==j&&generation==j.serial){terminal=SavedAnchorRecovery.State.CANCELLED;terminalStatus="Saved anchor: CANCELLED";terminalDiagnostic=j.lastTravelDiagnostic;activeJourney=null;advance();}
            return closed;
        } finally {j.issuedCleanupInProgress=false;}
    }
    private boolean originalMetadataDisposeCaller(){return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
        var first=stream.limit(48).filter(f->f.getDeclaringClass()!=McSavedAnchorRecovery.class).findFirst().orElse(null);
        return first!=null&&first.getDeclaringClass()==McSuspendedFarmReturnCoordinator.class
            &&java.util.Set.of("retireOriginalMetadata").contains(first.getMethodName());
    });}
    /** Old logical source retirement after lost provenance/PWC. It emits no native cleanup actions. */
    public boolean retireOriginalIssuedReturnMetadata(McSuspendedFarmReturnCoordinator.MetadataCloseCall exact){
        thread();var j=activeJourney;
        if(!originalMetadataDisposeCaller()||exact==null||exact.returningIdentity()!=this||!exact.currentMetadata())return false;
        if(j==null||j.issuedRequest==null||j.issuedRequest.sourceHeader()!=exact.sourceHeader()||j.issuedCleanupInProgress)return false;
        j.issuedCleanupInProgress=true;
        try {
            var source=new IssuedReturnCleanup(j,exact);
            boolean closed=dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.runMetadata(source,()->performOriginalIssuedMetadataClose(j));
            if(activeJourney==j&&generation==j.serial){terminal=SavedAnchorRecovery.State.CANCELLED;terminalStatus="Saved anchor: CANCELLED (native custody unknown)";terminalDiagnostic=j.lastTravelDiagnostic;activeJourney=null;advance();}
            return closed;
        } finally {j.issuedCleanupInProgress=false;}
    }
    public void cancel(){
        thread();var j=activeJourney;
        if(j==null){advance();return;}
        if(j.issuedRequest!=null)
            throw new IllegalStateException("Issued saved-anchor return requires its original typed close call");
        if(j.retiring){
            if(j.loadedReturnCall!=null)throw new IllegalStateException("Original loaded Journey cleanup reentered");
            if(activeJourney==j){activeJourney=null;advance();}return;
        }
        j.retiring=true;
        try{j.plotReturn.cancel();j.recovery.cancel();j.travel.stop();}
        finally{if(activeJourney==j){
            if(j.loadedReturnCall!=null&&j.travel.loadedReturnCleanupRefused()){
                j.loadedReturnCall.failed=true;terminal=SavedAnchorRecovery.State.FAILED;
                terminalStatus="Saved anchor: FAILED (original loaded cleanup custody retained)";terminalDiagnostic=j.lastTravelDiagnostic;
                j.retiring=false; // Only a later original cancel can observe the same retained cleanup; no native attempt is renewed.
            }else{terminal=j.alignmentRetirementRefused?SavedAnchorRecovery.State.FAILED:j.recovery.state();terminalStatus="Saved anchor: "+terminal;terminalDiagnostic=j.lastTravelDiagnostic;activeJourney=null;advance();}
        }}
    }
}
