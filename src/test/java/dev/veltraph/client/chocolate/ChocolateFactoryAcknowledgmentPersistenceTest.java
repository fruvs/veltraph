package dev.veltraph.client.chocolate;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class ChocolateFactoryAcknowledgmentPersistenceTest {
    private static ChocolateFactoryModule.Journal journal(String status,String price){return new ChocolateFactoryModule.Journal(1,"a","2026-10-02",status,"capacity",price,"200","known receipt");}
    @Test void failedTerminalAckFlushRetainsExactSpendAndRestartBlocker(){
        var memory=new AtomicReference<>(ChocolateFactoryHistory.decode(""));var disk=new AtomicReference<>(memory.get());var flushes=new AtomicInteger();
        var ack=journal("ACKNOWLEDGED","100");
        boolean durable=ChocolateFactoryAcknowledgmentPersistence.confirm(ack,j->memory.set(ChocolateFactoryHistory.decode(memory.get().with(j,"terminal-receipt"))),()->{if(flushes.incrementAndGet()==1)return false;disk.set(memory.get());return true;});
        assertFalse(durable,"Failed terminal confirmation must keep module unreconciled even if fallback marker persists");assertEquals(2,flushes.get());assertEquals("ACKNOWLEDGED_UNPERSISTED",disk.get().profiles().get("a").status());assertTrue(ChocolateFactoryHistory.decode(new com.google.gson.Gson().toJson(disk.get())).pending());assertEquals(1,disk.get().receipts().size());assertEquals(ack,disk.get().receipts().getFirst().journal());
    }
    @Test void failedTerminalAckAndMarkerWritesLeavePriorPendingOnDisk(){var pending=ChocolateFactoryHistory.decode(ChocolateFactoryHistory.decode("").with(journal("PENDING","100")));var memory=new AtomicReference<>(pending);assertFalse(ChocolateFactoryAcknowledgmentPersistence.confirm(journal("ACKNOWLEDGED","100"),j->memory.set(ChocolateFactoryHistory.decode(memory.get().with(j,"terminal-receipt"))),()->false));assertTrue(pending.pending());assertTrue(memory.get().pending());assertEquals("200",memory.get().receipts().getFirst().journal().spent());}
    @Test void successfulTerminalAckFlushIsDurableOnce(){var memory=new AtomicReference<>(ChocolateFactoryHistory.decode(""));var calls=new AtomicInteger();assertTrue(ChocolateFactoryAcknowledgmentPersistence.confirm(journal("ACKNOWLEDGED","100"),j->memory.set(ChocolateFactoryHistory.decode(memory.get().with(j,"terminal-receipt"))),()->{calls.incrementAndGet();return true;}));assertEquals(1,calls.get());assertFalse(memory.get().pending());assertEquals(1,memory.get().receipts().size());}
    @Test void conflictingReceiptIdentityCannotBeAppendedOrDecoded(){var ack=journal("ACKNOWLEDGED","100");var first=ChocolateFactoryHistory.decode(ChocolateFactoryHistory.decode("").with(ack,"same-id"));assertThrows(IllegalArgumentException.class,()->first.with(journal("ACKNOWLEDGED","101"),"same-id"));var gson=new com.google.gson.Gson();String raw="{\"schema\":1,\"profiles\":{},\"receipts\":[{\"id\":\"same-id\",\"journal\":"+gson.toJson(ack)+"},{\"id\":\"same-id\",\"journal\":"+gson.toJson(journal("ACKNOWLEDGED","101"))+"}]}";assertThrows(IllegalArgumentException.class,()->ChocolateFactoryHistory.decode(raw));}
}
