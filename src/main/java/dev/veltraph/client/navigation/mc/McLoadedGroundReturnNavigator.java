package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.navigation.*;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;
import java.util.*;

/** Physical loaded walking horizons; no force-load, view-distance change, teleport or flight. */
public final class McLoadedGroundReturnNavigator {
    private final Minecraft client;private final FarmServices services;private final McNavigator walk;
    private final LoadedGroundReturnController controller;private final GroundPublicationActor actor;
    private final McTravelNavigator.LoadedReturnDispatch dispatch;
    private SelectedLoadedSegment selectedSource;
    /** Concrete native witness minted only by this wrapper's genuine controller-selected callback. */
    public final class SelectedLoadedSegment {
        private final LoadedGroundReturnController.SelectedSegment selected;
        private final McTravelNavigator.LoadedReturnDispatch originalDispatch;
        private final LegacyTravelPublicationLease.Leg originalLeg;
        private LegacyTravelPublicationLease.Leg retiredLeg;
        private LegacyTravelRetirement.RetirementOutcome retiredOutcome;
        private LegacyTravelPublicationLease.Leg admittedLeg;
        private boolean admissionAttempted;
        private SelectedLoadedSegment(LoadedGroundReturnController.SelectedSegment selected){
            this.selected=selected;originalDispatch=dispatch;originalLeg=dispatch.originalLegIdentity();
        }
        public McLoadedGroundReturnNavigator wrapperIdentity(){return McLoadedGroundReturnNavigator.this;}
        public LoadedGroundReturnController controllerIdentity(){return controller;}
        public McTravelNavigator.LoadedReturnDispatch dispatchIdentity(){return originalDispatch;}
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.LoadedReturnCall returnCallIdentity(){return originalDispatch.returnCallIdentity();}
        public dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery.IssuedReturnTravelRequest issuedRequestIdentity(){return returnCallIdentity().issuedRequestIdentity();}
        public dev.veltraph.client.rewarp.FarmReturnPoint canonicalTargetPoint(){return returnCallIdentity().canonicalTargetPoint();}
        public Vec3 canonicalTarget(){return selected.canonicalTarget();}public Vec3 segmentPosition(){return selected.segmentPosition();}
        public double terminalRadius(){return selected.terminalRadius();}public boolean canonicalFinalSegment(){return selected.canonicalFinalSegment();}
        public long originalDeadlineMillis(){return selected.originalDeadline();}public long originalStartedAtMillis(){return selected.originalStartedAt();}
        public long originalJourneyDeadlineMillis(){return returnCallIdentity().originalJourneyDeadlineMillis();}
        public int searchNodes(){return selected.searchNodes();}public int searchRadius(){return selected.searchRadius();}public int rankedAttempt(){return selected.rankedAttempt();}
        public Object playerIdentity(){return selected.selectionObservation().player();}public Object worldIdentity(){return selected.selectionObservation().world();}
        public Object connectionIdentity(){return selected.selectionObservation().connection();}
        public ConfigService configIdentity(){return returnCallIdentity().configIdentity();}
        public Object parentIdentity(){return originalLeg==null?null:originalLeg.parent();}
        public Object cameraOwnerIdentity(){return originalLeg==null?null:originalLeg.cameraAuthority();}
        public McSharedMovementNavigator sharedChildIdentity(){return originalDispatch.sharedChildIdentity();}
        public LegacyTravelPublicationLease.Leg originalLegIdentity(){return originalLeg;}
        public LegacyTravelRetirement.RetirementOutcome actualRetirementIdentity(){return originalLeg==null?null:originalLeg.retirementOutcome();}
        public boolean selectingOriginalStart(){return selected.controllerIdentity()==controller&&selected.selectingOriginalStart();}
        /** Cached original source/geometry provenance only; the actual new Shared Leg owns all native effects. */
        public boolean nativeRealmCurrent(){return selectedSource==this&&dispatch==originalDispatch&&selected.controllerIdentity()==controller
            &&selected.selectionCurrent()&&originalDispatch.nativeRealmCurrent()&&returnCallIdentity().canonicalTarget().equals(canonicalTarget())
            &&client.player==playerIdentity()&&client.level==worldIdentity()&&client.getConnection()==connectionIdentity()
            &&originalDeadlineMillis()>=0&&controller.observedClockMillis()>=originalStartedAtMillis()
            &&controller.observedClockMillis()<originalDeadlineMillis();}
        public boolean current(){return nativeRealmCurrent()&&(returnCallIdentity().nativeDispatchCurrent(originalDispatch.travelIdentity())
            ||returnCallIdentity().nativeContinuationCurrent(originalDispatch.travelIdentity()));}
        public boolean nativeSourceCurrent(){return current();}
        public boolean initialSharedStartCurrent(McSharedMovementNavigator exact){return exact==sharedChildIdentity()&&selectingOriginalStart()
            &&originalDispatch.selectingCurrent(this)&&nativeRealmCurrent()&&originalSelectionHistoryCurrent();}
        public int originalSlot(){return returnCallIdentity().originalSlot();}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory originalSelectionHistory(){return returnCallIdentity().originalSelectionHistory();}
        public dev.veltraph.client.garden.pests.mc.McPestInventoryRestorationObserver.SelectionHistory expectedSelectionHistory(){return returnCallIdentity().expectedSelectionHistory();}
        public boolean originalSelectionHistoryCurrent(){return returnCallIdentity().originalSelectionHistoryCurrent();}
        /** One actual named Shared factory binds its genuine newly admitted private native Leg. */
        public boolean bindActualSharedLeg(McSharedMovementNavigator exact,LegacyTravelPublicationLease.Leg candidate){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var first=stream.filter(f->f.getDeclaringClass()!=SelectedLoadedSegment.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==McSharedMovementNavigator.class&&first.getMethodName().equals("admitOriginalLoadedMovementLeg");});
            if(!caller||exact!=sharedChildIdentity()||admissionAttempted||admittedLeg!=null||!initialSharedStartCurrent(exact))return false;
            admissionAttempted=true;
            if(candidate==null||candidate==originalLeg||candidate.parent()!=parentIdentity()||!candidate.loadedSegmentSourceFieldsCurrent(this)
                ||!nativeRealmCurrent()||!originalSelectionHistoryCurrent())return false;
            admittedLeg=candidate;return true;
        }
        public LegacyTravelPublicationLease.Leg initialAdmittedLegIdentity(){return admittedLeg;}
        public boolean cleanupSourceCurrent(){return selected.cleanupProvenanceCurrent()&&originalDispatch.cleanupSourceCurrent(this)
            &&client.player==playerIdentity()&&client.level==worldIdentity()&&client.getConnection()==connectionIdentity();}
        void recordActualRetirement(LegacyTravelPublicationLease.Leg leg,LegacyTravelRetirement.RetirementOutcome outcome){
            if(!cleanupSourceCurrent()||leg==null||outcome==null||leg.retirementOutcome()!=outcome||!outcome.cleanSuccess())
                throw new IllegalStateException("No actual loaded segment retirement outcome");
            retiredLeg=leg;retiredOutcome=outcome;
        }
        /** Exact retired lineage is data for next admission only; it cannot publish old movement/device use. */
        public boolean retiredLineageCurrent(){return retiredLeg!=null&&retiredOutcome!=null&&retiredLeg.retirementOutcome()==retiredOutcome
            &&retiredOutcome.cleanSuccess()&&originalDispatch.retiredLineageCurrent(this)
            &&returnCallIdentity().canonicalTarget().equals(canonicalTarget())&&client.player==playerIdentity()
            &&client.level==worldIdentity()&&client.getConnection()==connectionIdentity();}
        public LegacyTravelPublicationLease.Leg actualRetiredLegIdentity(){return retiredLeg;}
        public LegacyTravelRetirement.RetirementOutcome actualSegmentCleanupIdentity(){return retiredOutcome;}
    }
    public McLoadedGroundReturnNavigator(Minecraft client,FarmServices services,ConfigService config){this(client,services,config,null);}
    public McLoadedGroundReturnNavigator(Minecraft client,FarmServices services,ConfigService config,LeaseGroundAdapter publication){this(client,services,config,publication,null);}
    public McLoadedGroundReturnNavigator(Minecraft client,FarmServices services,ConfigService config,LeaseGroundAdapter publication,McTravelNavigator.LoadedReturnDispatch dispatch){
        this.client=client;this.services=services;actor=publication;this.dispatch=dispatch;
        walk=publication==null?new McNavigator(client,services.input(),config):new McNavigator(client,publication,config,publication);
        controller=new LoadedGroundReturnController(new LoadedGroundReturnController.Driver(){
            public boolean ready(Vec3 goal){return geometrySourceCurrent()&&walk.standingClear(goal)&&geometrySourceCurrent();}
            public List<Vec3> loadedProgressCandidates(Vec3 origin,Vec3 goal){
                var values=new ArrayList<Vec3>();if(!geometrySourceCurrent())return List.of();double dx=goal.x()-origin.x(),dz=goal.z()-origin.z(),length=Math.hypot(dx,dz);
                if(length<.001)return values;double ux=dx/length,uz=dz/length;
                // Deterministic local placement, qualified separately from opaque source candidate ranking.
                for(int step=16;step>=8;step--)for(int lateral:new int[]{0,1,-1,2,-2,3,-3}){
                    double x=origin.x()+ux*step-uz*lateral,z=origin.z()+uz*step+ux*lateral;
                    double y=origin.y()+(goal.y()-origin.y())*Math.min(1,step/length);
                    var surface=walk.surfacePosition(new Vec3(x,y,z));
                    if(surface.distanceTo(origin)<=LoadedGroundReturnController.HOP_DISTANCE&&walk.standingClear(surface))values.add(surface);
                }
                return geometrySourceCurrent()?List.copyOf(values):List.of();
            }
            public boolean start(Vec3 goal,int maxNodes,int radius,double terminalRadius){return (actor==null||actor.current())&&walk.start(goal,maxNodes,radius,terminalRadius)&&(actor==null||actor.current());}
            public boolean startSelected(LoadedGroundReturnController.SelectedSegment actual,int maxNodes,int radius,double terminalRadius){
                if(dispatch==null)return LoadedGroundReturnController.Driver.super.startSelected(actual,maxNodes,radius,terminalRadius);
                if(actual==null||actual.controllerIdentity()!=controller||!actual.selectingOriginalStart()
                    ||maxNodes!=2500||radius!=32||Double.doubleToLongBits(terminalRadius)!=Double.doubleToLongBits(actual.terminalRadius())
                    ||!actual.canonicalTarget().equals(dispatch.returnCallIdentity().canonicalTarget()))return false;
                var source=new SelectedLoadedSegment(actual);selectedSource=source;
                return dispatch.startSelected(McLoadedGroundReturnNavigator.this,source);
            }
            public LoadedGroundReturnController.Progress tick(){return dispatch==null?switch(walk.tick()){
                case WALKING->LoadedGroundReturnController.Progress.ACTIVE;case ARRIVED->LoadedGroundReturnController.Progress.ARRIVED;default->LoadedGroundReturnController.Progress.FAILED;
            }:dispatch.tickSelected(selectedSource);}
            public void stop(){if(dispatch==null)walk.stop();else if(selectedSource!=null)dispatch.stopSelected(selectedSource);}
        });
    }
    private boolean geometrySourceCurrent(){return dispatch==null?actor==null||actor.current():dispatch.nativeRealmCurrent();}
    private LoadedGroundReturnController.Observation observation(){var p=client.player;boolean valid=p!=null&&client.level!=null&&client.getConnection()!=null&&p.level()==client.level&&p.isAlive()
            &&geometrySourceCurrent()&&client.gui.screen()==null&&p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty()&&!p.getAbilities().flying&&!p.isPassenger()&&!p.isInLava();
        return new LoadedGroundReturnController.Observation(p,client.level,client.getConnection(),p==null?null:new Vec3(p.getX(),p.getY(),p.getZ()),valid,services.clock().nowMillis());}
    public boolean start(Vec3 target,double radius){return controller.start(target,radius,observation());}
    public McTravelNavigator.State tick(){return switch(controller.tick(observation())){case MOVING->McTravelNavigator.State.ACTIVE;case ARRIVED->McTravelNavigator.State.ARRIVED;default->McTravelNavigator.State.FAILED;};}
    /** Intermediate liveness never asserts that the final unloaded endpoint is already safe. */
    public boolean geometryLoaded(){return controller.segment()!=null&&walk.standingClear(controller.segment());}
    public void stop(){controller.stop();}
    public String status(){return controller.status()+" | "+walk.status();}
    public String diagnostics(){return controller.status()+" | "+walk.diagnostics();}
}
