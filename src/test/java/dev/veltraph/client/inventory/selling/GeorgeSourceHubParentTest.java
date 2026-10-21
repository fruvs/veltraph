package dev.veltraph.client.inventory.selling;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.world.Vec3;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GeorgeSourceHubParentTest {
    static final UUID UUID_A=UUID.fromString("00000000-0000-0000-0000-000000000001");static final Vec3 HUB=new Vec3(10,70,10),GARDEN=new Vec3(0,70,0);
    static GeorgeSourceHubProfile profile(){return new GeorgeSourceHubProfile("localhub","Native Hub ready","localgarden","Native Garden ready",List.of("Native warp denied"),new GeorgeWarpPermit.Destination(GameArea.HUB,"minecraft:overworld",HUB,.35),UUID_A,"Native George",20);}
    static final class Native implements GeorgeSourceHubParent.Port {
        final Object connection=new Object();Object player=new Object(),world=new Object();GameArea area=GameArea.GARDEN;Vec3 position=GARDEN,teleport;long tick,serial=1;boolean valid=true,npcFail,returnFail;int commands,admissions,batches,restores;GeorgeSourceHubParent parent;
        public GeorgeWarpPermit.Observation read(){return new GeorgeWarpPermit.Observation(UUID_A,player,world,connection,area,"minecraft:overworld",position,true,true,true,serial,teleport,tick);}
        public boolean valid(){return valid;}public boolean closed(){return true;}
        public boolean send(String command){commands++;player=new Object();world=new Object();serial++;if(command.equals("localhub")){area=GameArea.HUB;position=HUB;}else if(command.equals("localgarden")){area=GameArea.GARDEN;position=returnFail?new Vec3(40,70,40):GARDEN;}else throw new AssertionError(command);teleport=position;return true;}
        public boolean commitWarp(GeorgeWarpPermit permit,GeorgeWarpPermit.Observation arrival){assertEquals(GeorgeWarpPermit.State.COMMITTED,permit.state());assertSame(world,arrival.world());admissions++;return true;}
        public boolean startNpcBatch(){batches++;return true;}public FarmAutoSellRuntime.Progress npcBatch(){return npcFail?FarmAutoSellRuntime.Progress.FAILED:FarmAutoSellRuntime.Progress.COMPLETE;}public void cancelBatch(){}
        public boolean beginPhysicalReturn(){restores++;return true;}public FarmAutoSellRuntime.Progress physicalReturn(){return FarmAutoSellRuntime.Progress.COMPLETE;}
        GeorgeSourceHubParent start(){parent=new GeorgeSourceHubParent(this);assertTrue(parent.start(profile(),new GeorgeWarpPermit.Destination(GameArea.GARDEN,"minecraft:overworld",GARDEN,.35),0,80));return parent;}
        void frame(boolean receipt){tick++;if(receipt)parent.message(area==GameArea.HUB?"Native Hub ready":"Native Garden ready");parent.tick(tick);}
    }
    @Test void sameParentMakesExactlyTwoTypedWarpAdmissionsAndOnePhysicalReturn(){var nativePort=new Native();var parent=nativePort.start();for(int i=0;i<20&&parent.active();i++)nativePort.frame(true);assertEquals(GeorgeSourceHubParent.State.COMPLETE,parent.state(),parent.reason());assertEquals(2,nativePort.commands);assertEquals(2,nativePort.admissions);assertEquals(1,nativePort.batches);assertEquals(1,nativePort.restores);}
    @Test void nativeTeleportWithoutExactCommandReceiptNeverStartsNpcBatch(){var p=new Native();var parent=p.start();for(int i=0;i<30&&parent.active();i++)p.frame(false);assertEquals(GeorgeSourceHubParent.State.FAILED,parent.state());assertEquals(1,p.commands);assertEquals(0,p.admissions);assertEquals(0,p.batches);}
    @Test void failedNpcBatchNeverIssuesReturnOrClaimsRestoration(){var p=new Native();p.npcFail=true;var parent=p.start();for(int i=0;i<20&&parent.active();i++)p.frame(true);assertEquals(GeorgeSourceHubParent.State.FAILED,parent.state());assertEquals(1,p.commands);assertEquals(0,p.restores);}
    @Test void wrongReturnLandingCannotResumeCaller(){var p=new Native();p.returnFail=true;var parent=p.start();for(int i=0;i<20&&parent.active();i++)p.frame(true);assertEquals(GeorgeSourceHubParent.State.FAILED,parent.state());assertEquals(2,p.commands);assertEquals(1,p.admissions);assertEquals(0,p.restores);}
    @Test void stopRevokesPendingWarpAndDoesNotIssueRetry(){var p=new Native();var parent=p.start();parent.cancel();for(int i=0;i<20;i++)p.frame(true);assertEquals(GeorgeSourceHubParent.State.CANCELLED,parent.state());assertEquals(1,p.commands);assertTrue(parent.cachedPendingNativeWarp().isEmpty());assertEquals(0,p.admissions);}
}
