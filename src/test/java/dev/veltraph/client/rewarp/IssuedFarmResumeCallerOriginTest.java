package dev.veltraph.client.rewarp;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static dev.veltraph.client.rewarp.IssuedFarmResumeCallerOrigin.Operation.*;

class IssuedFarmResumeCallerOriginTest {
    static final class Producer {}
    static class FarmBase {}
    static final class Farm extends FarmBase { static final class World {} }
    static final class OtherFarm extends FarmBase {}
    static final class ForeignCallback {}
    static final class ShapeEngine {}
    private static IssuedFarmResumeCallerOrigin.Frame f(Class<?> type,String method){return new IssuedFarmResumeCallerOrigin.Frame(type,method);}
    private boolean matches(IssuedFarmResumeCallerOrigin.Operation operation,IssuedFarmResumeCallerOrigin.Frame... external){
        var frames=new java.util.ArrayList<IssuedFarmResumeCallerOrigin.Frame>();frames.add(f(Producer.class,"actualProducer"));frames.addAll(List.of(external));
        return IssuedFarmResumeCallerOrigin.matches(frames,Farm.class,FarmBase.class,ShapeEngine.class,Set.of(Producer.class),operation);
    }
    @Test void originalCropCallerIsAcceptedAsObservation(){assertTrue(matches(CROP_INSTALL,f(FarmBase.class,"installFarmTargetGuard"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void foreignCallbackCannotBorrowDeepOriginalCropStack(){assertFalse(matches(CROP_INSTALL,f(ForeignCallback.class,"replacement"),f(FarmBase.class,"installFarmTargetGuard"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void sameFarmPublicCallbackBeforeOriginalHookIsNotACropProducer(){assertFalse(matches(CROP_INSTALL,f(ForeignCallback.class,"wrappedOriginalRunnable"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void legacyResumeCannotBorrowAnOlderIssuedFrame(){assertFalse(matches(CROP_INSTALL,f(FarmBase.class,"installFarmTargetGuard"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeAfterRewarp"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void scheduledRestCannotBorrowIssuedResume(){assertFalse(matches(KEY_RELEASE,f(Farm.World.class,"release"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"rebindForScheduledRest"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void originalCameraCancellationCallerIsAccepted(){assertTrue(matches(CAMERA_RETIRE,f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void NestedCameraCallbackIsRefused(){assertFalse(matches(CAMERA_RETIRE,f(ForeignCallback.class,"cancel"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void originalPrivateFamilyWorldReleaseIsAccepted(){assertTrue(matches(KEY_RELEASE,f(Farm.World.class,"resumeAfterTeleport"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void originalShapeResumeReleaseIsAccepted(){assertTrue(matches(KEY_RELEASE,f(ShapeEngine.class,"releaseAll"),f(ShapeEngine.class,"resumeFromObservedPosition"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void ordinaryShapeStartCannotBorrowFamilyStack(){assertFalse(matches(KEY_RELEASE,f(ShapeEngine.class,"releaseAll"),f(ShapeEngine.class,"start"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void rawOnEnableCannotBorrowActualResumeStack(){assertFalse(matches(BREAKING_RETIRE,f(Farm.World.class,"setBreaking"),f(Farm.class,"onEnable"),f(Farm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void anotherFamilyDoesNotMatchTheActualFarm(){assertFalse(matches(CAMERA_RETIRE,f(OtherFarm.class,"onResumeIssuedReturn"),f(FarmBase.class,"resumeIssuedReturn")));}
    @Test void farmBaseRequiresActualIssuedResume(){assertFalse(matches(CROP_INSTALL,f(FarmBase.class,"installFarmTargetGuard"),f(Farm.class,"onResumeIssuedReturn")));}
    @Test void malformedAndOversizedObservationsRefuse(){assertFalse(IssuedFarmResumeCallerOrigin.matches(null,Farm.class,FarmBase.class,ShapeEngine.class,Set.of(Producer.class),KEY_RELEASE));assertFalse(IssuedFarmResumeCallerOrigin.matches(java.util.Collections.nCopies(49,f(Farm.class,"onResumeIssuedReturn")),Farm.class,FarmBase.class,ShapeEngine.class,Set.of(Producer.class),KEY_RELEASE));}
}
