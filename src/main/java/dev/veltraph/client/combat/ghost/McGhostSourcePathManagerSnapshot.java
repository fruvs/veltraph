package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.game.GameArea;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;

/** Source graph manager applicability from actual Root ownership/current issued SEARCH child, not feature defaults. */
public final class McGhostSourcePathManagerSnapshot {
    public record Flags(boolean minerManagerActive,boolean jumpBoostPresent,boolean skillManagerActiveAndPredicate,
                        boolean separateEndParentEnabledAndPointsNonempty,boolean specialManagerActive) {}
    private final Minecraft client;private final GhostGrindModule parent;private final GhostSourceIssuedSearchJob.Receipt issued;
    private final GhostSourceOwnedParentArea.Stamp stamp;private final Object child;private final int tick;
    private final boolean jumpBoost;private final long overrideRevision;
    private final GhostSourceOwnedParentArea.Receipt parentArea;
    private McGhostSourcePathManagerSnapshot(Minecraft client,GhostGrindModule parent,GhostSourceIssuedSearchJob.Receipt issued){
        this.client=client;this.parent=parent;this.issued=issued;stamp=issued.scope().parent();child=issued.scope().child();
        tick=client.player.tickCount;jumpBoost=client.player.getEffect(MobEffects.JUMP_BOOST)!=null;
        overrideRevision=GhostSourceGraphDeveloperAreaOverride.runtime().revision();
        parentArea=parent.sourceParentAreaReceipt(parent).orElseThrow(()->new IllegalStateException("Actual observed parent area required"));
    }
    public static McGhostSourcePathManagerSnapshot observe(Minecraft client,GhostGrindModule parent,GhostSourceIssuedSearchJob.Receipt issued){
        if(client==null||parent==null||issued==null||!client.isSameThread()||!ownedSearch(client,parent,issued))
            throw new IllegalStateException("Actual sole Root-owned Ghost SEARCH job required");
        var snapshot=new McGhostSourcePathManagerSnapshot(client,parent,issued);
        if(!snapshot.current())throw new IllegalStateException("Manager inputs changed during capture");return snapshot;
    }
    private static boolean ownedSearch(Minecraft client,GhostGrindModule parent,GhostSourceIssuedSearchJob.Receipt issued){
        var manager=VeltraphBootstrap.moduleManager();var context=VeltraphBootstrap.gameContext();
        return manager!=null&&context!=null&&context.current().area()==GameArea.DWARVEN_MINES
                &&manager.get(GhostGrindModule.ID).orElse(null)==parent&&parent.isEnabled()
                &&manager.inputOwner().orElse(null)==parent&&manager.enabledControllers().size()==1
                &&manager.enabledControllers().getFirst()==parent&&issued.scope().parent().parent()==parent
                &&issued.scopeCurrent()&&issued.scope().child() instanceof GhostNativeSearchPhase
                &&parent.sourceNativeChildIdentity(parent)==issued.scope().child();
    }
    public boolean current(){
        if(!client.isSameThread()||!ownedSearch(client,parent,issued))return false;
        var actual=parent.sourceParentAreaStamp(parent).orElse(null);
        return actual!=null&&stamp.same(actual)&&child==parent.sourceNativeChildIdentity(parent)
                &&client.player.tickCount==tick&&parentArea.current(actual)
                &&(client.player.getEffect(MobEffects.JUMP_BOOST)!=null)==jumpBoost
                &&GhostSourceGraphDeveloperAreaOverride.runtime().revision()==overrideRevision;
    }
    public Flags flags(){requireCurrent();
        // Active handler requires exact singleton identity. Actual current child is SEARCH, not any of these three managers.
        // Separate source End parent is not the sole active Ghost parent, so F19 short-circuits before reading its points map.
        return new Flags(false,jumpBoost,false,false,false);
    }
    /** Enabled source parent is TRUE here; F21 can nevertheless be proved absent by two actual nullable properties. */
    public boolean graphPreferredAreaKnownAbsent(){requireCurrent();return parentArea.observation()==GhostSourceOrderedStandingProvider.ParentAreaObservation.ABSENT
                &&GhostSourceGraphDeveloperAreaOverride.runtime().selected().isEmpty();}
    private void requireCurrent(){if(!current())throw new IllegalStateException("Actual native manager applicability frame changed");}
}
