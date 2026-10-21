package dev.veltraph.client.mining.vein;
import dev.veltraph.client.world.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VeinAdmissionLifecycleTest {
 final Object p=new Object(),w=new Object(),c=new Object(),o=new Object(),camera=new Object();
 final VeinBreakSession.Scope scope=new VeinBreakSession.Scope(p,w,c,o,camera,"cfg","tool",0,1);
 VeinBreakSession.Frame frame(boolean ray,boolean safe,long time){return new VeinBreakSession.Frame(scope,new BlockPos(1,71,1),"minecraft:cyan_terracotta",true,true,ray,true,true,safe,true,true,false,0,time);}
 @Test void targetAdmissionDoesNotRequireAlreadyAimedCrosshair(){var session=new VeinBreakSession();assertTrue(session.begin(frame(false,true,1000),1000,12000));assertEquals(VeinBreakSession.State.AIMING,session.state());assertFalse(session.attackRequested());assertEquals(VeinBreakSession.State.AIMING,session.tick(frame(false,true,1050),1050));assertFalse(session.attackRequested());}
 @Test void attackStillRequiresFreshActualRay(){var session=new VeinBreakSession();assertTrue(session.begin(frame(false,true,1000),1000,12000));assertEquals(VeinBreakSession.State.BREAKING,session.tick(frame(true,true,1050),1050));assertTrue(session.attackRequested());}
 @Test void expiredOrUnsafeProofCannotBegin(){var session=new VeinBreakSession();assertFalse(session.begin(frame(false,false,1000),1000,12000));assertFalse(session.begin(frame(false,true,1000),1251,12000));}
 @Test void laterUnsafeContextCannotUseEarlierAdmittedGeometry(){var session=new VeinBreakSession();assertTrue(session.begin(frame(false,true,1000),1000,12000));assertEquals(VeinBreakSession.State.STOPPED,session.tick(frame(true,false,1050),1050));assertFalse(session.attackRequested());}
 @Test void scopeRevokedWhileObserversBindCannotPublishAttack(){var session=new VeinBreakSession();assertTrue(session.begin(frame(false,true,1000),1000,12000));var foreign=new VeinBreakSession.Scope(p,w,c,new Object(),camera,"cfg","tool",0,1);var next=new VeinBreakSession.Frame(foreign,new BlockPos(1,71,1),"minecraft:cyan_terracotta",true,true,true,true,true,true,true,true,false,0,1050);assertEquals(VeinBreakSession.State.STOPPED,session.tick(next,1050));assertFalse(session.attackRequested());}
 @Test void bindingWorkCannotExtendOriginalDeadline(){var session=new VeinBreakSession();assertTrue(session.begin(frame(false,true,1000),1000,100));assertEquals(VeinBreakSession.State.TIMED_OUT,session.tick(frame(true,true,1100),1100));assertFalse(session.attackRequested());}
}
