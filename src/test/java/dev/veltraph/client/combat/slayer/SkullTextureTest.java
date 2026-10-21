package dev.veltraph.client.combat.slayer;

import java.util.Base64;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SkullTextureTest {
    private final String hash="c0340923a6de4825a176813d133503eff186db0896e32b6704928c2a2bf68422";
    private String encoded(String json) {return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));}
    private String skin(String url) {return encoded("{\"textures\":{\"SKIN\":{\"url\":\""+url+"\"}}}");}
    @Test void acceptsOnlyExactMinecraftSkinTextureUrl() {
        assertEquals(hash,SkullTexture.hash(skin("http://textures.minecraft.net/texture/"+hash)));
        assertEquals(hash,SkullTexture.hash(skin("https://textures.minecraft.net/texture/"+hash)));
        assertEquals("",SkullTexture.hash(skin("https://example.org/texture/"+hash)));
        assertEquals("",SkullTexture.hash(skin("http://textures.minecraft.net/texture/"+hash+"?fake=1")));
    }
    @Test void embeddedDescriptionsOtherFieldsAndMalformedMetadataCannotSpoofIdentity() {
        assertEquals("",SkullTexture.hash(encoded("{\"name\":\""+hash+"\"}")));
        assertEquals("",SkullTexture.hash(encoded("{\"textures\":{\"CAPE\":{\"url\":\"http://textures.minecraft.net/texture/"+hash+"\"}}}")));
        assertEquals("",SkullTexture.hash("not base64"));assertEquals("",SkullTexture.hash(encoded("[]")));
        assertEquals("",SkullTexture.hash("A".repeat(8193)));
    }
    @Test void deepOrUnbalancedMetadataIsRejectedWithoutRecursingThroughTheClientTick() {
        assertEquals("",SkullTexture.hash(encoded("[".repeat(64)+"0"+"]".repeat(64))));
        assertEquals("",SkullTexture.hash(encoded("{\"textures\":{\"SKIN\":")));
    }
}
