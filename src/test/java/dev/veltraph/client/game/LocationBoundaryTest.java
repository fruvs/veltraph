package dev.veltraph.client.game;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class LocationBoundaryTest {
    @Test void matchesHostBoundaryAndOptionalPort() {
        assertTrue(GameContextResolver.isHypixel(null,"mc.hypixel.net:25565"));
        assertFalse(GameContextResolver.isHypixel(null,"nothypixel.net"));
        assertFalse(GameContextResolver.isHypixel(null,"mc.hypixel.net.example.com"));
    }
    @Test void resolvesMistAndAvoidsSubstringAreas() {
        assertEquals(GameArea.DWARVEN_MINES,GameContextResolver.resolve(null,"mc.hypixel.net","SKYBLOCK",List.of("The Mist")).area());
        assertEquals(GameArea.UNKNOWN,GameContextResolver.resolve(null,"mc.hypixel.net","SKYBLOCK",List.of("Parker")).area());
        assertEquals(GameArea.DUNGEON_HUB,GameContextResolver.resolve(null,"mc.hypixel.net","SKYBLOCK",List.of("Dungeon Hub")).area());
    }
}
