package dev.veltraph.client.navigation;
/** Pure consistency only. Native admission additionally requires private actual carrier identity and exact revision evolution. */
public final class GroundOwnedSprintTransition {
    private GroundOwnedSprintTransition() {}
    private static boolean same(double a,double b){return Double.doubleToLongBits(a)==Double.doubleToLongBits(b);}
    private static boolean nativeSprint(GroundNativePhysicsProfile.State s){return s.movementModifiers().isEmpty()
        ||s.movementModifiers().size()==1&&same(s.movementModifiers().getFirst().amount(),GroundNativePhysicsProfile.CAPTURED_SPRINT_AMOUNT);}
    /** Pure opt-in family check only; actual native Journey custody remains mandatory. */
    public static boolean directionalVanillaProfileSupported(GroundNativePhysicsProfile.State state){
        return state!=null&&GroundNativePhysicsProfile.supportedState(state)
                &&(same(state.baseSpeed(),.1D)||same(state.baseSpeed(),(double).1F))
                &&state.jumpAmplifier()==-1&&state.effects().isEmpty()&&nativeSprint(state);
    }
    public static boolean directionalProfileSupported(GroundNativePhysicsProfile.State state){
        // Only the exact closed bootstrap empty list takes this pure family shortcut.
        // Captured family always rejects size zero; foreign/nonempty paths keep both validations.
        if(state!=null&&state.effects()!=null
                &&state.effects().getClass()==java.util.List.<GroundCapturedEffects.Effect>of().getClass()
                &&state.effects().isEmpty())return directionalVanillaProfileSupported(state);
        return CapturedProfileRouteFamily.supported(state)||directionalVanillaProfileSupported(state);
    }
    /** Metadata consistency only. This is used solely by a privately factory-bound directional Journey
     * after original frame/carrier/native revision/flag/modifier identity checks have succeeded. */
    public static boolean profileEvolutionDirectionalQualified(GroundNativePhysicsProfile.State before,
            GroundNativePhysicsProfile.State after,float travelSpeed,int actualCalls,boolean nativeArgument){
        if(CapturedProfileRouteFamily.supported(before))
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
    public static boolean profileEvolution(GroundNativePhysicsProfile.State before,GroundNativePhysicsProfile.State after,
            float travelSpeed,int actualCalls,boolean nativeArgument){
        if(before==null||after==null||GroundNativePhysicsProfile.classify(before).isEmpty()||GroundNativePhysicsProfile.classify(after).isEmpty()
            ||!(same(before.baseSpeed(),GroundNativePhysicsProfile.CAPTURED_360)||same(before.baseSpeed(),GroundNativePhysicsProfile.CAPTURED_400))
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
