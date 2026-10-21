package dev.veltraph.client.builder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceResourceProfileTest {
    private String json(){return """
        {"sourcePolicy":{"mode":"BAZAAR","slotOrder":"RANDOM","moveCooldownMillis":100,
         "removeCooldownMillis":150,"clickDelayMinMillis":5,"clickDelayMaxMillis":15,
         "timeoutMillis":30000,"acknowledgementTimeoutMillis":3000,"inventoryTitle":"Owned inventory",
         "otherResources":[],"providerFlows":{"BAZAAR":[{"state":"BUY_RESOURCE","title":"Captured quote",
         "label":"Buy {requested}","receiptItem":"minecraft:dirt","receiptDelta":"requested"}]}}}
        """;}
    @Test void declaredSourceModeDispatchUsesRemainingCountInProviderRequest(){var p=SourceResourceProfile.decode(json()).orElseThrow();assertEquals(SourceResourcePolicy.Mode.BAZAAR,p.mode());String flow=p.purchaseFlow(57);assertTrue(flow.contains("Buy 57"));assertTrue(flow.contains("\"receiptDelta\":57"));assertFalse(flow.contains("3456"));}
    @Test void unknownSaltedTimingsAndUnboundModeFlowsAreExplicitContracts(){assertThrows(IllegalArgumentException.class,()->SourceResourceProfile.decode(json().replace("\"moveCooldownMillis\":100,","")));var p=SourceResourceProfile.decode(json().replace("\"mode\":\"BAZAAR\"","\"mode\":\"WOOL_WEAVER\"")).orElseThrow();assertThrows(IllegalArgumentException.class,()->p.purchaseFlow(57));assertTrue(SourceResourceProfile.decode("{}").isEmpty());}
}
