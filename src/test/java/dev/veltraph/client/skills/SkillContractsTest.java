package dev.veltraph.client.skills;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkillContractsTest {
    @Test void rejectsUnknownIdentityQuantityAndValuation(){
        assertThrows(IllegalArgumentException.class,()->SkillContracts.identity("",""));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.identity("../../data","Bottle"));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.quantity(65,64));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.value(0,100));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.value(101,100));
    }
    @Test void purchaseQuoteRequiresExactAmountSinglePriceAndIntegerCoins(){
        assertEquals(192,SkillContracts.quotedCost(List.of("Amount: 64x","Price: 192 Coins"),64).orElseThrow());
        assertTrue(SkillContracts.quotedCost(List.of("Amount: 63x","Price: 192 Coins"),64).isEmpty());
        assertTrue(SkillContracts.quotedCost(List.of("Amount: 64x","Price: 192 Coins","Price: 1 Coins"),64).isEmpty());
        assertTrue(SkillContracts.quotedCost(List.of("Amount: 64x","Price: 0.5 Coins"),64).isEmpty());
        assertTrue(SkillContracts.quotedCost(List.of("Amount: 64x","Price: -1 Coins"),64).isEmpty());
    }
    @Test void rejectsFractionalHugeAndMalformedCoordinates(){
        assertThrows(IllegalArgumentException.class,()->SkillContracts.position("1.5,70,0"));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.position("30000000,70,0"));
        assertThrows(IllegalArgumentException.class,()->SkillContracts.position("0,70"));
        assertEquals(-20,SkillContracts.position("-20,70,2").x());
    }
}
