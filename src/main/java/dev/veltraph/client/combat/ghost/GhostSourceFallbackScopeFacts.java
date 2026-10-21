package dev.veltraph.client.combat.ghost;
/** Immutable construction area receipt plus actual per-call stamp/child comparisons; never caches permission. */
final class GhostSourceFallbackScopeFacts {
    private final GhostSourceOwnedParentArea.Stamp expected;private final GhostSourceOwnedParentArea.Receipt area;
    GhostSourceFallbackScopeFacts(GhostSourceOwnedParentArea.Stamp expected,GhostSourceOwnedParentArea.Receipt area){this.expected=java.util.Objects.requireNonNull(expected);this.area=java.util.Objects.requireNonNull(area);}
    boolean areaCurrent(GhostSourceOwnedParentArea.Stamp actual){return expected.same(actual)&&area.current(actual)&&area.observation()==GhostSourceOrderedStandingProvider.ParentAreaObservation.ABSENT;}
    boolean finalCurrent(GhostSourceOwnedParentArea.Stamp fresh,Object beforeChild,Object freshChild){return beforeChild!=null&&freshChild==beforeChild&&areaCurrent(fresh);}
    boolean finalCheck(java.util.function.BooleanSupplier actualRegistered,java.util.function.Supplier<GhostSourceOwnedParentArea.Stamp> actualFreshParent,
                       java.util.function.Function<GhostSourceOwnedParentArea.Stamp,Object> actualFieldChild,Object beforeChild){
        if(!actualRegistered.getAsBoolean())return false;var fresh=actualFreshParent.get();return finalCurrent(fresh,beforeChild,actualFieldChild.apply(fresh))&&actualRegistered.getAsBoolean();
    }

}
