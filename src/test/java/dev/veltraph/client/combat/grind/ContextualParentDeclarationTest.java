package dev.veltraph.client.combat.grind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContextualParentDeclarationTest {
    @Test void onlyExactKnownQuestCallerDeclaresSpiderAndTaraAlwaysStaysFixed(){
        assertEquals(CombatWeaponPolicy.Family.SLAYER_SPIDER,dev.veltraph.client.combat.slayer.SlayerModule.declaredWeaponFamily("Tarantula Broodfather",false));
        assertNull(dev.veltraph.client.combat.slayer.SlayerModule.declaredWeaponFamily("Tarantula Broodfather",true));
        for(String unknown:java.util.List.of("Primordial Broodfather","Conjoined Brood","Broodmother","Tarantula Broodfather boss","Revenant Horror","Sven Packmaster","slayer_combat","")){
            assertNull(dev.veltraph.client.combat.slayer.SlayerModule.declaredWeaponFamily(unknown,false));
            assertTrue(CombatWeaponPolicy.parse("malformed unrelated profile",dev.veltraph.client.combat.slayer.SlayerModule.declaredWeaponFamily(unknown,false)).valid());
        }
    }
}
