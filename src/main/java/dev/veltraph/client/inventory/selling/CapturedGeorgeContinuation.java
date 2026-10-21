package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import java.util.Objects;

/** Exact one-use financial handoff evidence recorded only after the coordinator admits the native warp. */
final class CapturedGeorgeContinuation {
    private FarmMaintenanceLease.Token token;
    private GeorgeWarpPermit permit;
    private Scope previous,current;
    private boolean available;
    boolean record(FarmMaintenanceLease.Token token,GeorgeWarpPermit permit,Scope previous,Scope current) {
        clear();
        if(token==null||permit==null||permit.state()!=GeorgeWarpPermit.State.COMMITTED||previous==null||current==null)return false;
        var origin=permit.pending();var arrival=permit.cachedNativeReceipt().orElse(null);
        if(arrival==null||origin.player()!=previous.player()||origin.previousWorld()!=previous.world()||origin.connection()!=previous.connection()
                ||arrival.player()!=current.player()||arrival.world()!=current.world()||arrival.connection()!=current.connection())return false;
        this.token=token;this.permit=permit;this.previous=previous;this.current=current;available=true;return true;
    }
    boolean consume(FarmMaintenanceLease.Token token,GeorgeWarpPermit permit,Scope previous,Scope current) {
        if(!available||this.token!=token||this.permit!=permit||permit.state()!=GeorgeWarpPermit.State.COMMITTED
                ||!this.previous.same(previous)||!this.current.same(current))return false;
        available=false;return true;
    }
    void clear(){token=null;permit=null;previous=null;current=null;available=false;}
}
