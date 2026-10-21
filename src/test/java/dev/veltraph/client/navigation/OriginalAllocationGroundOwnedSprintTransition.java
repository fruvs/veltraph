package dev.veltraph.client.navigation;
/** Pure consistency only. Native admission additionally requires private actual carrier identity and exact revision evolution. */
final class OriginalAllocationGroundOwnedSprintTransition {
    private OriginalAllocationGroundOwnedSprintTransition() {}
    private static boolean same(double a,double b){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b);}
    private static boolean nativeSprint(OriginalAllocationGroundNativePhysicsProfile.State s){return s.movementModifiers().isEmpty()
        ||s.movementModifiers().size()==1&&same(s.movementModifiers().getFirst().amount(),OriginalAllocationGroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT);}
    /** Pure opt-in family check only; actual native Journey custody remains mandatory. */
    public static boolean directionalVanillaProfileSupported(OriginalAllocationGroundNativePhysicsProfile.State state){
        return state!=null&&OriginalAllocationGroundNativePhysicsProfile.classify(state).isPresent()
                &&(same(state.baseSpeed(),.1D)||same(state.baseSpeed(),(double).1F))
                &&state.jumpAmplifier()==-1&&state.effects().isEmpty()&&nativeSprint(state);
    }
    public static boolean directionalProfileSupported(OriginalAllocationGroundNativePhysicsProfile.State state){
        return OriginalAllocationCapturedProfileRouteFamily.supported(state)||directionalVanillaProfileSupported(state);
    }
    /** Metadata consistency only. This is used solely by a privately factory-bound directional Journey
     * after original frame/carrier/native revision/flag/modifier identity checks have succeeded. */
    public static boolean profileEvolutionDirectionalQualified(OriginalAllocationGroundNativePhysicsProfile.State before,
            OriginalAllocationGroundNativePhysicsProfile.State after,float travelSpeed,int actualCalls,boolean nativeArgument){
        if(OriginalAllocationCapturedProfileRouteFamily.supported(before))
            return profileEvolution(before,after,travelSpeed,actualCalls,nativeArgument);
        if(!directionalVanillaProfileSupported(before)||!directionalVanillaProfileSupported(after)
                ||!same(before.baseSpeed(),after.baseSpeed())||!same(before.jumpStrength(),after.jumpStrength())
                ||!same(before.gravity(),after.gravity())||!same(before.frictionModifier(),after.frictionModifier())
                ||!same(before.airDragModifier(),after.airDragModifier())||!same(before.movementEfficiency(),after.movementEfficiency())
                ||Float.floatToIntBits(before.blockSpeedFactor())!=Float.floatToIntBits(after.blockSpeedFactor())
                ||!before.effects().equals(after.effects())
                ||Float.floatToIntBits(travelSpeed)!=Float.floatToIntBits(before.travelSpeed())
                ||actualCalls<0||actualCalls>1)return false;
        boolean previous=!before.movementModifiers().isEmpty(),next=!after.movementModifiers().isEmpty();
        return actualCalls==0?previous==next:nativeArgument==next;
    }
    public static boolean profileEvolution(OriginalAllocationGroundNativePhysicsProfile.State before,OriginalAllocationGroundNativePhysicsProfile.State after,
            float travelSpeed,int actualCalls,boolean nativeArgument){
        if(before==null||after==null||OriginalAllocationGroundNativePhysicsProfile.classify(before).isEmpty()||OriginalAllocationGroundNativePhysicsProfile.classify(after).isEmpty()
            ||!(same(before.baseSpeed(),OriginalAllocationGroundNativePhysicsProfile.CAPTURED_360)||same(before.baseSpeed(),OriginalAllocationGroundNativePhysicsProfile.CAPTURED_400))
            ||!nativeSprint(before)||!nativeSprint(after)||before.effects().size()!=7||before.jumpAmplifier()!=5||after.effects().size()!=7||after.jumpAmplifier()!=5
            ||!same(before.baseSpeed(),after.baseSpeed())||!same(before.jumpStrength(),after.jumpStrength())
            ||!same(before.gravity(),after.gravity())||!same(before.frictionModifier(),after.frictionModifier())
            ||!same(before.airDragModifier(),after.airDragModifier())||!same(before.movementEfficiency(),after.movementEfficiency())
            ||Float.floatToIntBits(before.blockSpeedFactor())!=Float.floatToIntBits(after.blockSpeedFactor())
            ||!before.effects().equals(after.effects())||Float.floatToIntBits(travelSpeed)!=Float.floatToIntBits(before.travelSpeed())
            ||actualCalls<0||actualCalls>1)return false;
        boolean previous=!before.movementModifiers().isEmpty(),next=!after.movementModifiers().isEmpty();
        return actualCalls==0?previous==next:nativeArgument==next;
    }
}
