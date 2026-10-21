package dev.veltraph.client.combat.ghost;
import dev.veltraph.client.combat.sven.SvenBindingProfile;
import net.minecraft.client.Minecraft;
/** Read-only original localHUD predicate; original license/auth/account/network behavior is excluded. */
final class McGhostSourceCookieAccess {
    private static final GhostSourceCookiePredicate SOURCE_CACHE=new GhostSourceCookiePredicate();
    static GhostSourceJumpBoostGate.Observed observe(Minecraft client){
        if(client==null||!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null
                ||!(client.gui.hud.getTabList() instanceof dev.veltraph.client.mixin.PlayerListHudAccessor access))return GhostSourceJumpBoostGate.Observed.UNKNOWN;
        var player=client.player;var world=client.level;var connection=client.getConnection();int tick=player.tickCount;
        var footer=access.getFooter();var rows=access.veltraph$getPlayerInfos().stream().filter(i->i.getTabListDisplayName()!=null).map(i->i.getTabListDisplayName().getString()).toList();
        if(client.player!=player||client.level!=world||client.getConnection()!=connection||player.tickCount!=tick)return GhostSourceJumpBoostGate.Observed.UNKNOWN;
        // Original cached-positive lifetime is preserved. Invalid Root context grants no observation and does not clear source cache.
        return SOURCE_CACHE.observe(footer==null?null:footer.getString(),rows)?GhostSourceJumpBoostGate.Observed.YES:GhostSourceJumpBoostGate.Observed.NO;
    }
    // FrozenR compatibility only. Cookie grammar no longer comes from the separate menu/ingress profile.
    static GhostSourceJumpBoostGate.Observed observe(Minecraft client,SvenBindingProfile.Buff ignoredMenuBinding){return observe(client);}
}
