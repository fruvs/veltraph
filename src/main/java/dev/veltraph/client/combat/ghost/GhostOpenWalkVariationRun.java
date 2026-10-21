package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.ghost.GhostSourceSampledRay.Point;
import java.util.IdentityHashMap;
import java.util.Objects;

/** One immutable seed per actual installed cursor; no per-tick sampling or original cursor mutation. */
public final class GhostOpenWalkVariationRun {
    private final Object cursorIdentity;
    private final long seed;
    private final IdentityHashMap<Object,GhostOpenWalkVariation> edges=new IdentityHashMap<>();
    private boolean revoked;
    private long edgeOrdinal;
    public GhostOpenWalkVariationRun(Object actualInstalledCursor,long oncePerCursorSeed){cursorIdentity=Objects.requireNonNull(actualInstalledCursor);seed=oncePerCursorSeed;}
    public GhostOpenWalkVariation.Result adapt(Object actualCursor,Object edgeIdentity,Point from,Point to,Point actualPosition,Point sourcePoint,boolean grounded,boolean sourceJump,GhostOpenWalkVariation.Geometry geometry){
        if(revoked||actualCursor!=cursorIdentity||!geometry.current())return new GhostOpenWalkVariation.Result(GhostOpenWalkVariation.Outcome.REVOKED,null);
        if(sourceJump||!grounded||from.y()!=to.y())return new GhostOpenWalkVariation.Result(GhostOpenWalkVariation.Outcome.ORIGINAL,sourcePoint);
        var plan=edges.get(edgeIdentity);
        if(plan==null){if(edges.size()>=6000)return new GhostOpenWalkVariation.Result(GhostOpenWalkVariation.Outcome.REVOKED,null);
            plan=GhostOpenWalkVariation.prepare(edgeIdentity,from,to,seed^(edgeOrdinal++*0x9e3779b97f4a7c15L),geometry);edges.put(edgeIdentity,plan);}
        if(!plan.sameEndpoints(from,to))return new GhostOpenWalkVariation.Result(GhostOpenWalkVariation.Outcome.REVOKED,null);
        return plan.adapt(edgeIdentity,actualPosition,sourcePoint,grounded,sourceJump,geometry);
    }
    public void revoke(){revoked=true;edges.clear();}
    public int cachedEdgeCount(){return edges.size();}
}
