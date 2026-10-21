package dev.veltraph.client.combat.ghost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import dev.veltraph.client.mixin.GhostSourceClickCountAccessor;
/** Actual source normal-key leftclick pulse under fresh Root child custody; same shared history across all child instances. */
public final class McGhostSourceLeavingClick {
    public enum Result { EMITTED, REFUSED, COUNT_LIMIT, SPACING, PENDING_CLICK, NATIVE_ACCESSOR_UNAVAILABLE }
    private static final GhostSourceSharedClickLimiter SHARED=new GhostSourceSharedClickLimiter(n->java.util.concurrent.ThreadLocalRandom.current().nextInt(n));
    private McGhostSourceLeavingClick(){}
    public static Result emit(Minecraft client,McGhostSourceLeavingChild actualChild){
        if(client==null||actualChild==null||!client.isSameThread()||!actualChild.current()||client.player==null||client.gameMode==null||client.gui.screen()!=null)return Result.REFUSED;
        long now=System.currentTimeMillis();var gate=SHARED.admit(now);
        if(gate==GhostSourceSharedClickLimiter.Result.COUNT_LIMIT)return Result.COUNT_LIMIT;if(gate==GhostSourceSharedClickLimiter.Result.SPACING)return Result.SPACING;if(gate!=GhostSourceSharedClickLimiter.Result.ADMITTED)return Result.REFUSED;
        // Source wrapper consumes history before keyenum checks pending vanilla click count; preserve this ordering.
        if(!(client.options.keyAttack instanceof GhostSourceClickCountAccessor access))return Result.NATIVE_ACCESSOR_UNAVAILABLE;
        if(access.veltraph$ghostPendingClickCount()!=0)return Result.PENDING_CLICK;
        if(!actualChild.current())return Result.REFUSED;
        KeyMapping.click(KeyMappingHelper.getBoundKeyOf(client.options.keyAttack));return Result.EMITTED;
    }
}
