package dev.veltraph.client.combat.ghost;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

/** Read-only source candidate eye: support collision top (fluid cube if empty) plus fixed1.62. */
public final class McGhostSourceCandidateEye {
    private McGhostSourceCandidateEye() {}
    public static GhostSourceSampledRay.Point observe(Minecraft client,BlockPos standingCell){
        if(!client.isSameThread()||client.player==null||client.level==null||client.getConnection()==null||standingCell==null)
            throw new IllegalStateException("Actual current native standing cell required");
        var support=standingCell.below();
        if(!client.level.isLoaded(standingCell)||!client.level.isLoaded(support))return null;
        var state=client.level.getBlockState(support);var shape=state.getCollisionShape(client.level,support);
        double top=shape.isEmpty()?(state.getFluidState().isEmpty()?support.getY():support.getY()+1):shape.bounds().maxY+support.getY();
        return new GhostSourceSampledRay.Point(standingCell.getX()+.5,top+1.62,standingCell.getZ()+.5);
    }
}
