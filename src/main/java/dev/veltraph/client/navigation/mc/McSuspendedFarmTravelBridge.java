package dev.veltraph.client.navigation.mc;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.input.ScriptingInputLock;
import dev.veltraph.client.navigation.SharedMovementPolicy;
import dev.veltraph.client.navigation.DynamicRouteConfigStamp;
import dev.veltraph.client.navigation.SuspendedFarmTravelPairIdentity;
import dev.veltraph.client.rewarp.mc.McSavedAnchorRecovery;
import dev.veltraph.client.service.FarmServices;
import dev.veltraph.client.service.mc.McOwnedRotation;
import dev.veltraph.client.world.Vec3;
import net.minecraft.client.Minecraft;

/** Additive exact-pair source adapter. No global role/count exception, public Boolean
 * issuer, Root getter adoption or fresh Farm admission exists here. The request is
 * privately issued by the actual original saved-anchor Journey/child dispatch. */
public final class McSuspendedFarmTravelBridge {
    private McSuspendedFarmTravelBridge() {}
    public static final class Binding {
        private final Minecraft client;
        private final McSavedAnchorRecovery.IssuedReturnTravelRequest request;
        private final Object child;
        private final McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegment;
        private final dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.MovementView sourceView;
        private final ConfigService config;
        private final FarmServices services;
        private final DynamicRouteConfigStamp stamp;
        private final ModuleManager root;
        private final ModuleManager.LifecycleSnapshot lifecycle;
        private final ScriptingInputLock.ControlSnapshot control;
        private final SuspendedFarmTravelPairIdentity.State original;
        private Binding(Minecraft client,McSavedAnchorRecovery.IssuedReturnTravelRequest request,
                Object child,ConfigService config,FarmServices services,
                DynamicRouteConfigStamp stamp,McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegment) {
            this.client=client;this.request=request;this.child=child;this.config=config;this.loadedSegment=loadedSegment;
            this.services=services;this.stamp=stamp;sourceView=request.initialView();root=VeltraphBootstrap.moduleManager();
            lifecycle=root==null?null:root.lifecycleSnapshot();control=ScriptingInputLock.controlSnapshot();
            original=observe();
        }
        /** Callbacks finish before the callback-free private source/native leaf. */
        public boolean current() {
            try {
                if(!nativeCurrent()||!(request.current()||request.nativeContinuationCurrent())||!stamp.current(config))return false;
                var controllers=root.enabledControllers();Object input=root.inputOwner().orElse(null);
                Object camera=McOwnedRotation.authority(client);
                if(controllers.size()!=2||!controllers.contains(sourceView.farm())
                        ||!controllers.contains(request.driverIdentity())||input!=request.driverIdentity()
                        ||camera!=request.driverIdentity())return false;
                return nativeCurrent();
            }catch(RuntimeException unavailable){return false;}
        }
        /** No role/authority/config/clock/provider callback; no epoch/serial adoption. */
        public boolean nativeCurrent() {
            if(!client.isSameThread()||root==null||lifecycle==null||!lifecycle.known()
                    ||VeltraphBootstrap.moduleManager()!=root||VeltraphBootstrap.config()!=config
                    ||!root.lifecycleCurrent(lifecycle)||control==null||!control.current()
                    ||!ScriptingInputLock.isActive()
                    ||!request.driverIdentity().id().equals(ScriptingInputLock.ownerModuleId())
                    ||request.sourceHeader().registryIdentity()!=root
                    ||request.sourceHeader().configIdentity()!=config
                    ||request.sourceHeader().farmServicesIdentity()!=services
                    ||!childCurrent())return false;
            boolean source=request.nativeCurrent()||request.nativeContinuationCurrent();
            if(!source||!SuspendedFarmTravelPairIdentity.sameSource(original,observe()))return false;
            return root.lifecycleCurrent(lifecycle)&&control.current()
                    &&childCurrent()
                    &&(request.nativeCurrent()||request.nativeContinuationCurrent());
        }
        public boolean cancellationCurrent(LegacyTravelPublicationLease.Leg leg) {
            if(leg==null||!client.isSameThread()||root==null||lifecycle==null||!lifecycle.known()
                    ||VeltraphBootstrap.moduleManager()!=root||VeltraphBootstrap.config()!=config
                    ||!root.ownCleanupLifecycleCurrent(lifecycle,request.driverIdentity())
                    ||!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalRequest(request,leg)
                    ||!dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.nativeCurrent())return false;
            return childCurrent()&&exactRetiringPair()
                    &&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.ownsOriginalRequest(request,leg)
                    &&dev.veltraph.client.service.mc.McIssuedFarmReturnCleanup.nativeCurrent();
        }
        private boolean exactRetiringPair() {
            boolean farm=false;
            for(var module:root.all()) {
                if(!(module instanceof AbstractModule stored))return false;
                if(!stored.isEnabled())continue;
                if(module==sourceView.farm())farm=true;
                else if(module!=request.driverIdentity())return false;
            }
            return farm;
        }
        private boolean childCurrent() {
            if(loadedSegment!=null&&(loadedSegment.requestIdentity()!=request||loadedSegment.sharedChildIdentity()!=child
                ||request.currentLoadedSegmentIdentity()!=loadedSegment))return false;
            return child instanceof McSharedMovementNavigator shared?request.ownsSharedChild(shared)
                    :child instanceof McTravelNavigator travel&&request.travelIdentity()==travel;
        }
        private boolean exactStoredPair() {
            if(root==null)return false;boolean farm=false,driver=false;
            for(var module:root.all()) {
                if(!(module instanceof AbstractModule stored))return false;
                if(!stored.isEnabled())continue;
                if(module==sourceView.farm())farm=true;
                else if(module==request.driverIdentity())driver=true;
                else return false; // Unknown enabled observers are not fabricated controller proof.
            }
            return farm&&driver;
        }
        private SuspendedFarmTravelPairIdentity.State observe() {
            var view=sourceView;
            return new SuspendedFarmTravelPairIdentity.State(root,
                    view==null?null:view.coordinatorIdentity(),request.sourceHeader(),request.checkpointIdentity(),
                    sourceView.farm(),request.driverIdentity(),request.journeyIdentity(),
                    request.travelIdentity(),child,client.player,client.level,client.getConnection(),config,
                    view==null?null:view.canonicalTargetPoint(),
                    request.targetPosition(),request.purpose(),view==null?-1:view.serial(),request.journeySerial(),
                    exactStoredPair(),request.nativeCurrent()||request.nativeContinuationCurrent());
        }
        /** Retired-parent context data only, reached solely by the original private Leg continuation consumer. */
        public boolean retiredLoadedContextCurrent(LegacyTravelPublicationLease.Leg exactRetired,
                McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment successor){
            boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
                var first=stream.filter(f->f.getDeclaringClass()!=Binding.class).findFirst().orElse(null);
                return first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.Leg.class
                    &&first.getMethodName().equals("closedOwnParentContext");
            });
            if(!caller||loadedSegment==null||successor==null||successor==loadedSegment||exactRetired==null
                ||exactRetired.loadedIssuedBinding(loadedSegment)!=this||loadedSegment.requestIdentity()!=request
                ||successor.requestIdentity()!=request||loadedSegment.sharedChildIdentity()!=child||successor.sharedChildIdentity()!=child
                ||request.currentLoadedSegmentIdentity()!=successor||!request.currentLoadedSegmentFieldsCurrent(successor.sourceIdentity())
                ||loadedSegment.sourceIdentity().actualRetiredLegIdentity()!=exactRetired||successor.predecessorIdentity()!=exactRetired
                ||loadedSegment.sourceIdentity().actualSegmentCleanupIdentity()!=successor.predecessorRetirementIdentity()
                ||exactRetired.retirementOutcome()!=successor.predecessorRetirementIdentity()
                ||exactRetired.retirementOutcome()==null||!exactRetired.retirementOutcome().cleanSuccess()
                ||exactRetired.retirementOutcome().removal()!=dev.veltraph.client.navigation.LegacyTravelRetirement.Removal.REMOVED_OWN
                ||!loadedSegment.sourceIdentity().retiredLineageCurrent())return false;
            if(!client.isSameThread()||root==null||lifecycle==null||!lifecycle.known()
                ||VeltraphBootstrap.moduleManager()!=root||VeltraphBootstrap.config()!=config||!root.lifecycleCurrent(lifecycle)
                ||control==null||!control.current()||!ScriptingInputLock.isActive()
                ||!request.driverIdentity().id().equals(ScriptingInputLock.ownerModuleId())
                ||request.sourceHeader().registryIdentity()!=root||request.sourceHeader().configIdentity()!=config
                ||request.sourceHeader().farmServicesIdentity()!=services||!request.nativeCurrent()
                ||!SuspendedFarmTravelPairIdentity.sameSource(original,observe()))return false;
            return request.currentLoadedSegmentIdentity()==successor&&request.currentLoadedSegmentFieldsCurrent(successor.sourceIdentity())
                &&exactRetired.loadedIssuedBinding(loadedSegment)==this&&loadedSegment.sourceIdentity().retiredLineageCurrent()
                &&exactRetired.retirementOutcome()==successor.predecessorRetirementIdentity()
                &&root.lifecycleCurrent(lifecycle)&&control.current()&&request.nativeCurrent();
        }
        public McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment loadedSegmentIdentity(){return loadedSegment;}
        public Object driverIdentity(){return request.driverIdentity();}
        public Object sourceIdentity(){return request.sourceHeader();}
        public Object journeyIdentity(){return request.journeyIdentity();}
    }
    public static Binding capture(Minecraft client,McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            McSharedMovementNavigator child,ConfigService config,FarmServices services,
            DynamicRouteConfigStamp stamp,SharedMovementPolicy.Purpose purpose,Vec3 goal) {
        return captureChild(client,request,child,config,services,stamp,purpose,goal);
    }
    public static Binding capture(Minecraft client,McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            McTravelNavigator child,ConfigService config,FarmServices services,
            DynamicRouteConfigStamp stamp,SharedMovementPolicy.Purpose purpose,Vec3 goal) {
        return captureChild(client,request,child,config,services,stamp,purpose,goal);
    }
    /** Genuine original selected source only; the suspended pair still records the unchanged canonical saved target. */
    public static Binding captureLoadedChild(Minecraft client,McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment selected,McSharedMovementNavigator child,
            ConfigService config,FarmServices services,DynamicRouteConfigStamp stamp,SharedMovementPolicy.Purpose purpose,Vec3 goal){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McSuspendedFarmTravelBridge.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.class
                &&first.getMethodName().equals("beginIssuedLoadedSelected");
        });
        if(!caller||client==null||!client.isSameThread()||request==null||selected==null||child==null
            ||config==null||services==null||stamp==null||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN
            ||request.purpose()!=purpose||selected.requestIdentity()!=request||selected.sharedChildIdentity()!=child
            ||request.currentLoadedSegmentIdentity()!=selected||!request.currentLoadedSegmentFieldsCurrent(selected.sourceIdentity())
            ||!request.nativeCurrent()||request.currentView()==null||!request.ownsSharedChild(child)
            ||!selected.sourceIdentity().initialSharedStartCurrent(child)||!SharedMovementPolicy.finite(goal)
            ||!selected.segmentPosition().equals(goal)||!request.targetPosition().equals(selected.canonicalTarget())
            ||selected.sourceIdentity().canonicalTargetPoint()!=request.canonicalTargetPoint()
            ||selected.sourceIdentity().configIdentity()!=config||selected.sourceIdentity().returnCallIdentity().servicesIdentity()!=services
            ||selected.predecessorIdentity()==null||selected.predecessorRetirementIdentity()==null
            ||selected.predecessorIdentity().retirementOutcome()!=selected.predecessorRetirementIdentity()
            ||!selected.predecessorRetirementIdentity().cleanSuccess())return null;
        try{var binding=new Binding(client,request,child,config,services,stamp,selected);
            return binding.current()&&selected.nativeCurrent()?binding:null;
        }catch(RuntimeException unavailable){return null;}
    }
    /** Same original selected Binding after one actual clean predecessor retirement; no scene is recaptured. */
    public static Binding retainLoadedChild(Binding original,McSavedAnchorRecovery.IssuedReturnTravelRequest.LoadedSegment selected,
            McSharedMovementNavigator child,LegacyTravelPublicationLease.Leg predecessor){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McSuspendedFarmTravelBridge.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.class
                &&first.getMethodName().equals("beginIssuedLoadedSelected");
        });
        if(!caller||original==null||selected==null||child==null||predecessor==null
            ||original.loadedSegment!=selected||original.request!=selected.requestIdentity()||original.child!=child
            ||selected.sharedChildIdentity()!=child||predecessor.loadedIssuedBinding(selected)!=original
            ||predecessor.retirementOutcome()==null||!predecessor.retirementOutcome().cleanSuccess()
            ||predecessor.retirementOutcome().removal()!=dev.veltraph.client.navigation.LegacyTravelRetirement.Removal.REMOVED_OWN
            ||!original.request.currentLoadedSegmentFieldsCurrent(selected.sourceIdentity())
            ||!original.request.nativeCurrent()||!selected.nativeCurrent())return null;
        var context=predecessor.retirementContinuationContext();
        if(context==null||context.activeLeg()!=null||context.root()!=original.root
            ||context.parent()!=original.request.driverIdentity()||context.inputOwner()!=original.request.driverIdentity()
            ||context.cameraAuthority()!=original.request.driverIdentity()||context.player()!=original.client.player
            ||context.world()!=original.client.level||context.connection()!=original.client.getConnection()
            ||context.config()!=original.config||context.journey()!=predecessor.journeyGeneration()
            ||context.epoch()!=predecessor.cameraEpoch()||context.requestGeneration()!=predecessor.requestGeneration())return null;
        return original.current()&&original.nativeCurrent()&&selected.nativeCurrent()
            &&predecessor.loadedIssuedBinding(selected)==original&&predecessor.retirementContinuationContext()!=null?original:null;
    }
    /** Retains the SAME authentic source Binding only after the request's real typed entry claim. */
    public static Binding captureIssuedReturnEntry(Binding original,
            dev.veltraph.client.rewarp.mc.McSuspendedFarmReturnCoordinator.FinalEntryContinuation cap,
            McSavedAnchorRecovery.IssuedReturnTravelRequest request,McSharedMovementNavigator shared,Object instruction,Vec3 goal,double radius){
        boolean caller=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{
            var first=stream.filter(f->f.getDeclaringClass()!=McSuspendedFarmTravelBridge.class).findFirst().orElse(null);
            return first!=null&&first.getDeclaringClass()==LegacyTravelPublicationLease.class
                &&first.getMethodName().equals("beginAfterIssuedReturnEntry");
        });
        if(!caller||original==null||cap==null||request==null||original.request!=request||original.child!=shared
            ||original.loadedSegment!=cap.loadedSegmentIdentity()
            ||cap.requestIdentity()!=request||cap.sourceHeader()!=request.sourceHeader()||cap.checkpointIdentity()!=request.checkpointIdentity()
            ||cap.driverIdentity()!=request.driverIdentity()||cap.canonicalTargetPoint()!=request.canonicalTargetPoint()
            ||cap.predecessorIdentity()==null||cap.predecessorIdentity().retirementContinuationContext()==null
            ||!request.currentFinalEntryInstructionCurrent(cap,instruction,shared,goal,radius)||!request.nativeCurrent())return null;
        return original.current()&&original.nativeCurrent()&&request.currentFinalEntryInstructionCurrent(cap,instruction,shared,goal,radius)
            &&cap.predecessorIdentity().retirementContinuationContext()!=null?original:null;
    }
    /** Actual typed request and child affiliation, never a caller's frame or eligibility flag. */
    private static Binding captureChild(Minecraft client,McSavedAnchorRecovery.IssuedReturnTravelRequest request,
            Object child,ConfigService config,FarmServices services,
            DynamicRouteConfigStamp stamp,SharedMovementPolicy.Purpose purpose,Vec3 goal) {
        if(client==null||!client.isSameThread()||request==null||child==null||config==null||services==null
                ||stamp==null||purpose!=SharedMovementPolicy.Purpose.EXACT_RETURN
                ||request.purpose()!=purpose||!SharedMovementPolicy.finite(goal)
                ||!goal.equals(request.targetPosition())||request.currentView()==null
                ||!request.nativeChildAdmissionCurrent(child))return null;
        try {var binding=new Binding(client,request,child,config,services,stamp,null);
            return binding.current()?binding:null;
        }catch(RuntimeException unavailable){return null;}
    }
}
