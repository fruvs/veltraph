package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapturedGeorgePendingPermitTest {
    @Test void exactPermitIsAvailableForBindingBeforeTheCommandAndThroughSynchronousCommit(){
        class Port implements GeorgeSourceHubParent.Port {
            final GeorgeSourceHubParentTest.Native nativePort=new GeorgeSourceHubParentTest.Native(); GeorgeSourceHubParent parent;
            public GeorgeWarpPermit.Observation read(){return nativePort.read();}public boolean valid(){return true;}public boolean closed(){return true;}
            public boolean startNpcBatch(){return nativePort.startNpcBatch();}public FarmAutoSellRuntime.Progress npcBatch(){return nativePort.npcBatch();}public void cancelBatch(){}public boolean beginPhysicalReturn(){return nativePort.beginPhysicalReturn();}public FarmAutoSellRuntime.Progress physicalReturn(){return nativePort.physicalReturn();}
            void frame(){nativePort.tick++;parent.message(nativePort.area==GameArea.HUB?"Native Hub ready":"Native Garden ready");parent.tick(nativePort.tick);}
            GeorgeWarpPermit beforeSend,committed;
            @Override public boolean send(String command){beforeSend=parent.pendingWarpPermit().orElseThrow();assertEquals(GeorgeWarpPermit.State.WAITING,beforeSend.state());assertSame(nativePort.player,beforeSend.pending().player());assertSame(nativePort.world,beforeSend.pending().previousWorld());return nativePort.send(command);}
            @Override public boolean commitWarp(GeorgeWarpPermit permit,GeorgeWarpPermit.Observation arrival){assertSame(beforeSend,permit);assertSame(permit,parent.pendingWarpPermit().orElseThrow());committed=permit;return nativePort.commitWarp(permit,arrival);}
        }
        var p=new Port();p.parent=new GeorgeSourceHubParent(p);assertTrue(p.parent.pendingWarpPermit().isEmpty());
        assertTrue(p.parent.start(GeorgeSourceHubParentTest.profile(),new GeorgeWarpPermit.Destination(GameArea.GARDEN,"minecraft:overworld",GeorgeSourceHubParentTest.GARDEN,.35),0,80));
        var first=p.beforeSend;assertSame(first,p.parent.pendingWarpPermit().orElseThrow());p.frame();assertSame(first,p.committed);assertTrue(p.parent.pendingWarpPermit().isEmpty());
        p.frame();var second=p.beforeSend;assertNotSame(first,second);assertSame(second,p.parent.pendingWarpPermit().orElseThrow());p.frame();assertSame(second,p.committed);assertTrue(p.parent.pendingWarpPermit().isEmpty());p.frame();assertEquals(GeorgeSourceHubParent.State.COMPLETE,p.parent.state());assertTrue(p.parent.pendingWarpPermit().isEmpty());
    }
    @Test void terminalCancelAndFailureNeverPublishAStalePermit(){for(boolean rejected:new boolean[]{false,true}){var p=new GeorgeSourceHubParentTest.Native();var parent=p.start();var permit=parent.pendingWarpPermit().orElseThrow();if(rejected)parent.message("Native warp denied");else parent.cancel();assertEquals(GeorgeWarpPermit.State.CANCELLED,permit.state());assertTrue(parent.pendingWarpPermit().isEmpty());assertTrue(parent.cachedPendingNativeWarp().isEmpty());assertEquals(1,p.commands);}}
}
