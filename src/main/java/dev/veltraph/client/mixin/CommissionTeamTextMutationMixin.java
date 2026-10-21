package dev.veltraph.client.mixin;
import dev.veltraph.client.mining.commission.*;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Actual native team prefix/suffix text may change tab fallback task grammar. */
@Mixin(PlayerTeam.class)
public abstract class CommissionTeamTextMutationMixin implements CommissionMutationCarrier {
    @Unique private long veltraph$commissionTeamTextVersion;
    @Override public long commissionNativeMutationVersion(){return veltraph$commissionTeamTextVersion;}
    @Unique private void veltraph$observeTeamText(){var c=Minecraft.getInstance();if(!c.isSameThread())return;if(veltraph$commissionTeamTextVersion<Long.MAX_VALUE)veltraph$commissionTeamTextVersion++;McCommissionObservation.nativeMutation(c);}
    @Inject(method={"setPlayerPrefix","setPlayerSuffix"},at=@At("HEAD"),require=1)
    private void before(Component text,CallbackInfo ci){veltraph$observeTeamText();}
    @Inject(method={"setPlayerPrefix","setPlayerSuffix"},at=@At("RETURN"),require=1)
    private void after(Component text,CallbackInfo ci){veltraph$observeTeamText();}
}
