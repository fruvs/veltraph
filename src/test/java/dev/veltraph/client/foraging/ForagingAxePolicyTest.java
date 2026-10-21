package dev.veltraph.client.foraging;
import dev.veltraph.client.config.ForagingConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ForagingAxePolicyTest {
 @Test void modernAutoReadsTotalSweepAndDoesNotApplyOldNameCooldown() {
  var p=ForagingAxePolicy.profile("Treecapitator",List.of("Sweep: +25"),List.of("§a∮ Sweep: 105.5"),ForagingConfig.AxeMode.AUTO,2000);
  assertEquals(105.5,p.sweep());assertTrue(p.multiLog());assertEquals(0,p.cooldownMs());
  assertEquals(ForagingAxePolicy.Activation.ATTACK,p.activation());
  assertEquals(2000,ForagingAxePolicy.cooldown("Treecapitator",ForagingConfig.AxeMode.TREECAPITATOR,2000));
 }
 @Test void throwingIsExplicitUseWithObservedLoreCooldown() {
  var lore=List.of("Sweep: +25","Ability: Throwing Axe RIGHT CLICK","Cooldown: 1s");
  var p=ForagingAxePolicy.profile("Treecapitator",lore,List.of(),ForagingConfig.AxeMode.THROWING,2000);
  assertTrue(ForagingAxePolicy.hasThrowingAbility(lore));assertEquals(ForagingAxePolicy.Activation.USE,p.activation());assertEquals(1000,p.cooldownMs());
  assertFalse(ForagingAxePolicy.hasThrowingAbility(List.of("Sweep: +25")));
 }
 @Test void missingSweepStaysUnknownAndPickaxeIsNotAForagingTool() {
  assertTrue(Double.isNaN(ForagingAxePolicy.sweep(List.of("Sweeping Edge III"))));
  assertFalse(ForagingAxePolicy.isAxe("Diamond Pickaxe"));assertTrue(ForagingAxePolicy.isAxe("Diamond Axe"));
 }
}
