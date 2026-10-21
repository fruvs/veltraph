package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.metrics.FinancialOutcomeBindings.Scope;
import dev.veltraph.client.rewarp.FarmMaintenanceLease;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class CapturedGeorgeContinuationTest {
    private static final UUID PLAYER=new UUID(0,1);
    private record Handoff(FarmMaintenanceLease.Token token,GeorgeWarpPermit permit,Scope before,Scope after) {}
    private static Handoff handoff(){var before=new Scope(new Object(),new Object(),new Object());var after=new Scope(new Object(),new Object(),before.connection());
        var lease=new FarmMaintenanceLease();var token=lease.prepare(new Object(),new Object(),0,10000);assertTrue(lease.commit(token));
        var origin=new GeorgeWarpPermit.Observation(PLAYER,before.player(),before.world(),before.connection(),GameArea.GARDEN,"minecraft:overworld",new Vec3(.5,70,.5),true,true,true,1,new Vec3(.5,70,.5),0);
        var target=new GeorgeWarpPermit.Destination(GameArea.HUB,"minecraft:overworld",new Vec3(6.5,70,.5),.4);var permit=GeorgeWarpPermit.issue(origin,target,80);
        var arrival=new GeorgeWarpPermit.Observation(PLAYER,after.player(),after.world(),after.connection(),GameArea.HUB,"minecraft:overworld",target.position(),true,true,true,2,target.position(),1);
        assertTrue(permit.observe(arrival));assertTrue(permit.commit(arrival).isPresent());return new Handoff(token,permit,before,after);}
    @Test void exactNativeAdmissionContinuesOnlyOnce(){var h=handoff();var c=new CapturedGeorgeContinuation();assertTrue(c.record(h.token(),h.permit(),h.before(),h.after()));assertTrue(c.consume(h.token(),h.permit(),h.before(),h.after()));assertFalse(c.consume(h.token(),h.permit(),h.before(),h.after()));}
    @Test void differentLeasePermitOrScopeCannotConsumeTrueHandoff(){var h=handoff();var other=handoff();var c=new CapturedGeorgeContinuation();assertTrue(c.record(h.token(),h.permit(),h.before(),h.after()));
        assertFalse(c.consume(other.token(),h.permit(),h.before(),h.after()));assertFalse(c.consume(h.token(),other.permit(),h.before(),h.after()));assertFalse(c.consume(h.token(),h.permit(),new Scope(new Object(),h.before().world(),h.before().connection()),h.after()));assertFalse(c.consume(h.token(),h.permit(),h.before(),new Scope(h.after().player(),new Object(),h.after().connection())));assertTrue(c.consume(h.token(),h.permit(),h.before(),h.after()));}
    @Test void priorScopeMustComeFromCaptureBeforeWorldReplacement(){var h=handoff();var c=new CapturedGeorgeContinuation();assertFalse(c.record(h.token(),h.permit(),h.after(),h.after()));assertFalse(c.consume(h.token(),h.permit(),h.before(),h.after()));assertTrue(c.record(h.token(),h.permit(),h.before(),h.after()));}
    @Test void cancellationRevokesRecordedAdmission(){var h=handoff();var c=new CapturedGeorgeContinuation();assertTrue(c.record(h.token(),h.permit(),h.before(),h.after()));h.permit().cancel();assertFalse(c.consume(h.token(),h.permit(),h.before(),h.after()));assertFalse(c.record(h.token(),h.permit(),h.before(),h.after()));c.clear();assertFalse(c.consume(h.token(),h.permit(),h.before(),h.after()));}
}
