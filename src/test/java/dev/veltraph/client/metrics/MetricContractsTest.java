package dev.veltraph.client.metrics;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MetricContractsTest {
    @Test void emptyAndUnknownPricesRemainUnsetAndPlayerChatCannotBeABinding(){
        assertTrue(MetricContracts.parse("[]","[]").items().isEmpty());
        var c=MetricContracts.parse("[{\"fingerprint\":\"all-components\",\"label\":\"Observed Sorrow\",\"roles\":[\"SORROW\"],\"unitValue\":null}]","[]");assertNull(c.items().get("all-components").unitValue());
        assertThrows(IllegalArgumentException.class,()->new MetricContracts.Signal(MetricContracts.SignalKind.SKILL_XP_TOTAL,"MINING",MetricContracts.Channel.PLAYER_CHAT,"XP: ",""));
    }
    @Test void fullNumericBoundsAndDuplicateContractsRefuseAmbiguity(){
        var s=new MetricContracts.Signal(MetricContracts.SignalKind.RNG_PERCENT,"",MetricContracts.Channel.SYSTEM,"RNG: ","%");
        assertEquals(new BigDecimal("42.5"),s.parse("RNG: 42.5%").orElseThrow());
        for(String v:List.of("[Bob] RNG: 42.5%","RNG: 101%","RNG: NaN%","RNG: 1,2%","RNG: 2% trailing"))assertTrue(s.parse(v).isEmpty(),v);
        String one="{\"fingerprint\":\"full\",\"label\":\"Observed\"}";assertThrows(IllegalArgumentException.class,()->MetricContracts.parse("["+one+","+one+"]","[]"));
    }
    @Test void sourceSkillTableProjectionIsSeparateAndStrict(){
        var rows=SourceSkillXpProjection.tabRows(List.of("Skills:","Mining I: 50%","Foraging II: 25%","","Visitors"));assertEquals(2,rows.size());assertEquals(112,rows.getFirst().projectedTotal());
        assertEquals(174,SourceSkillXpProjection.project(1,100,60));
        assertTrue(SourceSkillXpProjection.tabRows(List.of("Skills:","Mining IIII: 20%")).isEmpty());
        assertTrue(SourceSkillXpProjection.tabRows(List.of("[Bob] Skills:","Mining I: 50%")).isEmpty());
        assertEquals(68972452,SourceSkillXpProjection.project(53,0,60));
        assertEquals(57322425,SourceSkillXpProjection.project(50,50,50));
        assertEquals(111672425,SourceSkillXpProjection.project(60,100,60));
        assertTrue(SourceSkillXpProjection.tabRows(List.of("Skills:","Mining I: 50%","Mining II: 20%")).isEmpty());
    }
}
