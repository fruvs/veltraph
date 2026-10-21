package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.core.AbstractModule;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.metrics.FinancialActionReceipt;
import dev.veltraph.client.metrics.FinancialOutcomeBindings;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class GeorgeSaleIdentityTest {
    private static final String ACTIVATION="00000000-0000-0000-0000-000000000001";
    private static final UUID PET=UUID.fromString("00000000-0000-0000-0000-000000000002");
    @Test void actualSaleFinancialReceiptAcceptsCanonicalUuidButRejectsFormerCompoundId(){
        var identity=GeorgeSaleIdentity.acknowledged(ACTIVATION,PET);
        var module=new AbstractModule("george_identity","George identity",ModuleCategory.MISC){};
        var scope=new FinancialOutcomeBindings.Scope(new Object(),new Object(),new Object());
        var evidence=new FinancialActionReceipt.Evidence(new Object(),2,4,5,1,AutoSellMode.GEORGE_PETS.name());
        assertDoesNotThrow(()->new FinancialActionReceipt(identity.outcomeId(),module,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,new BigDecimal("5"),scope,List.of(evidence)));
        assertThrows(IllegalArgumentException.class,()->new FinancialActionReceipt(identity.sourceIdentity(),module,FinancialActionReceipt.Kind.SALE,FinancialActionReceipt.Denomination.COINS,new BigDecimal("5"),scope,List.of(evidence)));
        assertEquals(ACTIVATION+":"+PET,identity.sourceIdentity());assertEquals(UUID.fromString(identity.outcomeId()).toString(),identity.outcomeId());
    }
    @Test void repeatedViewsDedupSameActualOutcomeAndNewActivationCannotAlias(){var a=GeorgeSaleIdentity.acknowledged(ACTIVATION,PET);assertEquals(a,GeorgeSaleIdentity.acknowledged(ACTIVATION,PET));assertNotEquals(a.outcomeId(),GeorgeSaleIdentity.acknowledged("00000000-0000-0000-0000-000000000003",PET).outcomeId());assertNotEquals(a.outcomeId(),GeorgeSaleIdentity.acknowledged(ACTIVATION,UUID.fromString("00000000-0000-0000-0000-000000000004")).outcomeId());}
    @Test void changedNamespaceIdentityAndNoncanonicalActivationRefuse(){var id=GeorgeSaleIdentity.acknowledged(ACTIVATION,PET);assertThrows(IllegalArgumentException.class,()->new GeorgeSaleIdentity(id.activationUuid(),PET,ACTIVATION,id.sourceIdentity()));assertThrows(IllegalArgumentException.class,()->GeorgeSaleIdentity.acknowledged("1-1-1-1-1",PET));}
}
