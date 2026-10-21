package dev.veltraph.client.movement.device;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MovementDeviceEvidenceTest {
 private List<String> aotv(){return List.of("Ability: Instant Transmission  RIGHT CLICK","Teleport 8 blocks ahead of you and","gain +50 \uE022 Speed for 3 seconds.","Mana Cost: 23\uE003");}
 @Test void exactCapturedAotvHasObserved23CostAndNoEtherwarp(){var c=MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",aotv());assertEquals(1,c.size());assertEquals(MovementDeviceEvidence.Kind.INSTANT_TRANSMISSION,c.getFirst().kind());assertEquals(8,c.getFirst().range());assertEquals(23,c.getFirst().manaCost());assertFalse(c.getFirst().sneak());}
 @Test void capturedRogueUsesActualCost(){var c=MovementDeviceEvidence.parse("ROGUE_SWORD",List.of("Ability: Speed Boost  RIGHT CLICK","Grants +100\uE022 Speed for 30s.","Mana Cost: 50\uE003"));assertEquals(50,c.getFirst().manaCost());assertEquals(MovementDeviceEvidence.Kind.SPEED_BOOST,c.getFirst().kind());}
 @Test void loreOnWrongIdCannotGrantTeleport(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_DRAGON",aotv()).isEmpty());}
 @Test void dragonRageIsNotPlayerMovement(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_DRAGON",List.of("Ability: Dragon Rage  RIGHT CLICK","Teleport 8 blocks ahead of you and","Mana Cost: 100")).isEmpty());}
 @Test void unknownIdDenied(){assertTrue(MovementDeviceEvidence.parse("",aotv()).isEmpty());}
 @Test void missingCostDoesNotInferBaseMana(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",aotv().subList(0,3)).isEmpty());}
 @Test void missingRangeDoesNotInferDefaultEight(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",List.of(aotv().get(0),aotv().get(3))).isEmpty());}
 @Test void conflictingCostsDenied(){var lore=new ArrayList<>(aotv());lore.add("Mana Cost: 45");assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",lore).isEmpty());}
 @Test void duplicateAbilityDenied(){var lore=new ArrayList<>(aotv());lore.addAll(aotv());assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",lore).isEmpty());}
 @Test void sectionCostsDoNotLeakFromNextAbility(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",List.of(aotv().get(0),aotv().get(1),"Ability: Ether Transmission  SNEAK RIGHT CLICK","57 blocks away.","Mana Cost: 180")).stream().allMatch(c->c.kind()==MovementDeviceEvidence.Kind.ETHER_TRANSMISSION));}
 @Test void etherwarpRequiresExplicitSneak(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",List.of("Ability: Ether Transmission  RIGHT CLICK","57 blocks away.","Mana Cost: 180")).isEmpty());}
 @Test void unqualifiedLeapCannotInferRange(){assertTrue(MovementDeviceEvidence.parse("LEAPING_SWORD",List.of("Ability: Leap  RIGHT CLICK","Mana Cost: 100")).isEmpty());}
 @Test void oversizedMalformedTooltipDenied(){assertTrue(MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",List.of("x".repeat(513))).isEmpty());}
 @Test void colorsDoNotAlterCapturedNumbers(){var colored=aotv().stream().map(s->"§a"+s+"§7").toList();assertEquals(23,MovementDeviceEvidence.parse("ASPECT_OF_THE_VOID",colored).getFirst().manaCost());}
}
