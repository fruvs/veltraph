package dev.veltraph.client.galatea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigPacketGapRegistryTest {
    @Test void scopesUseExactTransportIdentityAndNeverBorrowOtherConnectionHistory(){var journal=new FigPacketGapRegistry();Object a=new Object(),b=new Object();journal.received(a,1000);journal.received(a,1100);assertFalse(journal.observed(a,1101));assertTrue(journal.observed(b,1101));assertEquals(0,journal.snapshot(b).lastPacketAt());}
    @Test void networkingTimestampRemainsOriginalInsteadOfDelayedQueryTime(){var journal=new FigPacketGapRegistry();Object connection=new Object();journal.received(connection,1000);assertEquals(1000,journal.snapshot(connection).lastDetectedGapAt());assertFalse(journal.observed(connection,1100));assertFalse(journal.observed(connection,1500));assertTrue(journal.observed(connection,1501));assertEquals(1000,journal.snapshot(connection).lastPacketAt());}
    @Test void concurrentIngressAndRootQueriesShareOneAtomicTimestampPair()throws Exception{var journal=new FigPacketGapRegistry();Object connection=new Object();var worker=new Thread(()->{for(int i=1;i<=1000;i++)journal.received(connection,i);});worker.start();for(int i=0;i<1000;i++){var snapshot=journal.snapshot(connection);assertTrue(snapshot.lastDetectedGapAt()<=snapshot.lastPacketAt());}worker.join();assertEquals(1000,journal.snapshot(connection).lastPacketAt());}
}
