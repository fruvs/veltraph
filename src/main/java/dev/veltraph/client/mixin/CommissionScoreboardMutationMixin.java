package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.commission.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Actual native tab-comparator membership changes, including whole-team removal. Observation only. */
@Mixin(Scoreboard.class)
public abstract class CommissionScoreboardMutationMixin implements CommissionMutationCarrier {
    @Unique private long veltraph$commissionTeamMembershipVersion;
    @Override public long commissionNativeMutationVersion(){return veltraph$commissionTeamMembershipVersion;}
    @Unique private void veltraph$observeMembership(){var c=Minecraft.getInstance();if(!c.isSameThread()||c.level==null||c.level.getScoreboard()!=(Object)this)return;if(veltraph$commissionTeamMembershipVersion<Long.MAX_VALUE)veltraph$commissionTeamMembershipVersion++;McCommissionObservation.nativeMutation(c);}
    @Inject(method="addPlayerToTeam",at=@At("HEAD"),require=1)
    private void beforeAdd(String name,PlayerTeam team,CallbackInfoReturnable<Boolean> ci){veltraph$observeMembership();}
    @Inject(method="addPlayerToTeam",at=@At("RETURN"),require=1)
    private void afterAdd(String name,PlayerTeam team,CallbackInfoReturnable<Boolean> ci){veltraph$observeMembership();}
    @Inject(method="removePlayerFromTeam(Ljava/lang/String;)Z",at=@At("HEAD"),require=1)
    private void beforeRemove(String name,CallbackInfoReturnable<Boolean> ci){veltraph$observeMembership();}
    @Inject(method="removePlayerFromTeam(Ljava/lang/String;)Z",at=@At("RETURN"),require=1)
    private void afterRemove(String name,CallbackInfoReturnable<Boolean> ci){veltraph$observeMembership();}
    @Inject(method="removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V",at=@At("HEAD"),require=1)
    private void beforeRemoveFrom(String name,PlayerTeam team,CallbackInfo ci){veltraph$observeMembership();}
    @Inject(method="removePlayerFromTeam(Ljava/lang/String;Lnet/minecraft/world/scores/PlayerTeam;)V",at=@At("RETURN"),require=1)
    private void afterRemoveFrom(String name,PlayerTeam team,CallbackInfo ci){veltraph$observeMembership();}
    @Inject(method="removePlayerTeam",at=@At("HEAD"),require=1)
    private void beforeRemoveTeam(PlayerTeam team,CallbackInfo ci){veltraph$observeMembership();}
    @Inject(method="removePlayerTeam",at=@At("RETURN"),require=1)
    private void afterRemoveTeam(PlayerTeam team,CallbackInfo ci){veltraph$observeMembership();}
}
