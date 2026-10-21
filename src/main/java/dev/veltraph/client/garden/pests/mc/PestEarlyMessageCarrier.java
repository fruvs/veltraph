package dev.veltraph.client.garden.pests.mc;

import java.util.List;

/** Passive readonly actual normal native receive history. No supplied text or receipt insertion API. */
public interface PestEarlyMessageCarrier {
    interface Receipt {Object receiver();Object observationScope();long serial();long observedMillis();String message();}
    boolean earlyLoadoutMessageHistoryKnown();
    List<Receipt> earlyLoadoutReceivedMessages();
}
