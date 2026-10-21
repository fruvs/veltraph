package dev.veltraph.client.combat.heal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HealRawHealthObservationTest {
    @Test void rawHealthRetainedWithoutChangingExistingPercentageConsumers() {
        var health=new SkyBlockHealth();health.accept("1,234/20,000 ♥ 57/100 ✎ Mana",100000);
        assertEquals(1234,health.rawHealth(100000).orElseThrow());
        assertEquals(6.17,health.percent(100000).orElseThrow(),.0001);
        assertEquals(1234,health.rawHealth(102000).orElseThrow());
        assertTrue(health.rawHealth(102001).isEmpty());assertTrue(health.rawHealth(99999).isEmpty());
    }
    @Test void rejectedTextAndResetCannotManufactureFreshRawHealth() {
        var health=new SkyBlockHealth();health.accept("10/100 ✎ Mana",100000);
        assertTrue(health.rawHealth(100000).isEmpty());
        health.accept("500/2,000 ♥",100000);health.accept("unknown",102001);
        assertTrue(health.rawHealth(102001).isEmpty());
        health.reset();assertTrue(health.rawHealth(100000).isEmpty());
    }
}
