package dev.veltraph.client.combat.ghost;
import net.minecraft.client.Minecraft;
import dev.veltraph.client.service.FarmServices;
/** Actual same-search-child buff helper. No warp, selection publication or unrelated preflight acknowledgment. */
final class McGhostSourceSearchJumpBoost {
    private final Minecraft client;private final GhostGrindModule parent;private final String binding;
    private final GhostSourceJumpBoostContinuation continuation;private final GhostNativePreflight menus;private final float yaw,pitch;
    private McGhostSourceSearchJumpBoost(Minecraft client,FarmServices services,GhostGrindModule parent,GhostSourceJumpBoostContinuation.Scope scope,GhostPreflightProfile profile,String binding){
        this.client=client;this.parent=parent;this.binding=binding;yaw=client.player.getYRot();pitch=client.player.getXRot();continuation=new GhostSourceJumpBoostContinuation(scope);
        if(!parent.sourceJumpBoostScopeCurrent(scope)||!binding.equals(dev.veltraph.client.VeltraphBootstrap.config().get(dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE)))
            throw new IllegalStateException("Actual owned source helper scope/profile required");
        if(!dev.veltraph.client.VeltraphBootstrap.config().get(dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_MODIFY_JUMP_BOOST))throw new IllegalStateException("Actual source modification setting required");
        menus=GhostNativePreflight.beginSearchBuffOnly(client,services,profile,GhostSourceJumpBoostClock.runtime().lastEntry());
    }
    static McGhostSourceSearchJumpBoost issue(Minecraft client,FarmServices services,GhostGrindModule parent,GhostSourceJumpBoostContinuation.Scope scope,GhostPreflightProfile profile,String binding){return new McGhostSourceSearchJumpBoost(client,services,parent,scope,profile,binding);}
    boolean current(){return parent.sourceActiveJumpBoostHelper()==this&&continuation.state()==GhostSourceJumpBoostContinuation.State.ACTIVE
        &&parent.sourceJumpBoostScopeCurrent(continuation.issued())&&viewCurrent()&&binding.equals(dev.veltraph.client.VeltraphBootstrap.config().get(dev.veltraph.client.config.GhostGrindConfig.GHOST_SOURCE_PREFLIGHT_PROFILE));}
    private boolean viewCurrent(){return client.player!=null&&Math.abs(dev.veltraph.client.rotation.SourceRotationStepper.wrap(client.player.getYRot()-yaw))<.001
        &&Math.abs(client.player.getXRot()-pitch)<.001&&(dev.veltraph.client.service.mc.McOwnedRotation.epoch(client)==continuation.issued().cameraEpoch()||menus.ownsNativeViewPause());}
    boolean ownsNativeViewPause(){return menus.ownsNativeViewPause();}
    void tick(){if(!current()){cancel();return;}menus.tick();if(menus.state()==GhostNativePreflight.State.FAILED||menus.state()==GhostNativePreflight.State.CANCELLED)continuation.revoke();}
    boolean failed(){return continuation.state()==GhostSourceJumpBoostContinuation.State.REVOKED;}
    boolean completed(){return menus.complete();}
    String status(){return menus.status();}
    boolean allowsScreen(String title){return current()&&menus.allowsScreen(title);}
    boolean consumeActualClose(){return current()&&continuation.consumeActualClose(continuation.issued(),menus.actualBuffCloseRestored());}
    Object continuationChild(){return continuation.issued().child();}
    GhostSourceJumpBoostContinuation.Scope issuedScope(){return continuation.issued();}
    GhostNativePreflight.MenuDiagnostics diagnostics(){return menus.menuDiagnostics();}
    void message(String text){if(current())menus.message(text);}
    void cancel(){continuation.revoke();menus.abandonSearchBuff();}
}
