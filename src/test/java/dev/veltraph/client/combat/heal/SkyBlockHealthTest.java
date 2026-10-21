package dev.veltraph.client.combat.heal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkyBlockHealthTest {
    @Test void acceptsFormattedHealthButNotManaOrStaleValues() {
        var health=new SkyBlockHealth();health.accept("§c1,234/2,000❤   §b200/300✎ Mana",1000);
        assertEquals(61.7,health.percent(1001).orElseThrow(),.001);
        health.accept("200/300 Mana",1100);assertEquals(61.7,health.percent(1200).orElseThrow(),.001);
        assertTrue(health.percent(3001).isEmpty());
        health.accept("50/0❤",4000);assertTrue(health.percent(4001).isEmpty());
        health.accept("50/100❤",5000);health.reset();assertTrue(health.percent(5001).isEmpty());
    }
}
