package dev.veltraph.client.combat.ghost;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import dev.veltraph.client.config.*;
class GhostSourceSettingsObservationTest {
    @Test void immutableKeyListStillReadsChangedActualValuesAtEveryBoundary(){var c=new MapConfigService();var key=ConfigKey.ofInt("step",1);var declared=new ArrayList<ConfigKey<?>>();declared.add(key);var area=new Object();var snapshot=GhostSourceSettingsObservation.capture(c,declared,area);declared.clear();assertTrue(snapshot.current(c,area));c.set(key,2);assertFalse(snapshot.current(c,area));}
    @Test void sameTypedScalarAndOldStringRepresentationEquivalenceArePreserved(){var c=new MapConfigService();var key=ConfigKey.ofInt("scalar",1);var snapshot=GhostSourceSettingsObservation.capture(c,List.of(key),null);c.set(key,Integer.valueOf(1));assertTrue(snapshot.current(c,null));setRaw(c,key,1L);assertTrue(snapshot.current(c,null));setRaw(c,key,2L);assertFalse(snapshot.current(c,null));}
    @SuppressWarnings({"rawtypes","unchecked"}) void setRaw(MapConfigService c,ConfigKey key,Object value){c.set(key,value);}
    @Test void lastDeclaredDuplicateKeyMatchesPriorTreeMapSignature(){var c=new MapConfigService();var first=ConfigKey.ofInt("same",1);var last=ConfigKey.ofInt("same",2);var snapshot=GhostSourceSettingsObservation.capture(c,List.of(first,last),null);c.set(last,2);assertTrue(snapshot.current(c,null));c.set(last,3);assertFalse(snapshot.current(c,null));}
    @Test void observedAreaChangeInvalidatesEvenWhenValuesIdentical(){var c=new MapConfigService();var area=new Object();var snapshot=GhostSourceSettingsObservation.capture(c,List.of(),area);assertFalse(snapshot.current(c,new Object()));}
    @Test void mutableUnknownValuesRetainOldTextDriftDetection(){var c=new MapConfigService();var key=new ConfigKey<StringBuilder>("future",new StringBuilder("a"));var snapshot=GhostSourceSettingsObservation.capture(c,List.of(key),null);key.defaultValue().append("b");assertFalse(snapshot.current(c,null));}
    @Test void revertBetweenObservationsRetainsExistingUnboundAbaQualification(){var c=new MapConfigService();var key=ConfigKey.ofInt("step",1);var snapshot=GhostSourceSettingsObservation.capture(c,List.of(key),null);c.set(key,2);c.set(key,1);assertTrue(snapshot.current(c,null)); /* ConfigService has no mutation revision; no claim of detecting unobserved ABA. */}
}
