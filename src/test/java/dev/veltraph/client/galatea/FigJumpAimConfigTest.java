package dev.veltraph.client.galatea;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FigJumpAimConfigTest {
 @Test void sourceModeExistsAndDefaultsAverage() throws Exception {
  var key=(ConfigKey<?>)GalateaConfig.class.getField("FIG_JUMP_BOOST_AIMING").get(null);
  assertEquals("AVERAGE", key.defaultValue().toString());
  assertArrayEquals(new String[]{"STRONG","AVERAGE","LAZY"},java.util.Arrays.stream(key.defaultValue().getClass().getEnumConstants()).map(Object::toString).toArray(String[]::new));
 }
}
