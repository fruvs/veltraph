package dev.veltraph.client.combat.ghost;

/** Pure predicate over observations that the native owner reads from canonical Root, never caller arguments. */
public final class GhostSourceParentAuthority {
    private GhostSourceParentAuthority() {}
    public static boolean owned(Object parent,Object canonicalRegisteredParent,Object actualManagerInputOwner,
                                int enabledControllers,boolean enabled,Object actualCameraAuthority){
        return parent!=null&&canonicalRegisteredParent==parent&&actualManagerInputOwner==parent
                &&enabledControllers==1&&enabled&&actualCameraAuthority==parent;
    }
}
