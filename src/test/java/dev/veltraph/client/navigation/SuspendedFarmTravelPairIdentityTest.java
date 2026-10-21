package dev.veltraph.client.navigation;

import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** This pure fence supplies no native permission or private request factory. */
class SuspendedFarmTravelPairIdentityTest {
    private final Object[] ids=new Object[15];
    SuspendedFarmTravelPairIdentityTest(){for(int i=0;i<ids.length;i++)ids[i]=new Object();}
    private SuspendedFarmTravelPairIdentity.State frame(Object[] x,SharedMovementPolicy.Purpose purpose,
            long serial,long journey,boolean pair,boolean source,Vec3 target){
        return new SuspendedFarmTravelPairIdentity.State(x[0],x[1],x[2],x[3],x[4],x[5],x[6],x[7],
                x[8],x[9],x[10],x[11],x[12],x[13],target,purpose,serial,journey,pair,source);
    }
    private SuspendedFarmTravelPairIdentity.State valid(){return frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8));}
    @Test void exactIssuedSourceCanBeObservedWithoutRenewal(){assertTrue(SuspendedFarmTravelPairIdentity.sameSource(valid(),valid()));}
    @Test void eachContextSourceAndChildIdentityIsExact(){for(int i=0;i<14;i++){var changed=ids.clone();changed[i]=new Object();assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(changed,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8))),"identity "+i);}}
    @Test void nullAndUnknownPrivateSourceCannotQualify(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(null,valid()));for(int i=0;i<14;i++){var changed=ids.clone();changed[i]=null;assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(changed,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8))),"missing identity "+i);}}
    @Test void farmAndDriverMustBeDifferentActualObjects(){var changed=ids.clone();changed[5]=changed[4];assertFalse(SuspendedFarmTravelPairIdentity.sameSource(frame(changed,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8)),valid()));}
    @Test void unissuedOrChangedSerialCannotRenew(){for(long n:new long[]{-1,0,8})assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,n,9,true,true,new Vec3(3,70,8))));}
    @Test void newerJourneyCannotBorrowOlderPair(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,10,true,true,new Vec3(3,70,8))));}
    @Test void changedCanonicalPositionCannotUseOriginalSource(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8.01))));}
    @Test void generalCropOrCombatPurposeCannotBorrowReturn(){for(var p:SharedMovementPolicy.Purpose.values())if(p!=SharedMovementPolicy.Purpose.EXACT_RETURN)assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,p,7,9,true,true,new Vec3(3,70,8))));}
    @Test void thirdUnknownControllerOrMissingPairRefuses(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,false,true,new Vec3(3,70,8))));}
    @Test void closedFailedOrUnreturnedBodyRefuses(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,false,new Vec3(3,70,8))));}
    @Test void nonfiniteOrMissingTargetNeverFallsBack(){assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,null)));assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(ids,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(Double.NaN,70,8))));}
    @Test void equalDataDoesNotReplaceCanonicalPointIdentity(){var changed=ids.clone();changed[13]=new Object();assertFalse(SuspendedFarmTravelPairIdentity.sameSource(valid(),frame(changed,SharedMovementPolicy.Purpose.EXACT_RETURN,7,9,true,true,new Vec3(3,70,8))));}
}
