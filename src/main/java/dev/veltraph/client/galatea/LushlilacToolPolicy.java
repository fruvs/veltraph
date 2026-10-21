package dev.veltraph.client.galatea;

import java.util.*;

/** Recovered tool-switch preference: slots 0..7; avoid right-click abilities; prefer an inert item
 * over empty (cost 5) or a block item in RIGHT mode (source cost 10). Placing items and
 * consumables are additionally rejected locally to avoid changing the target for another reason. */
public final class LushlilacToolPolicy {
    public record Tool(int slot,boolean empty,boolean rightClickAbility,boolean blockItem,boolean consumable) {}
    private LushlilacToolPolicy() {}
    public static int choose(List<Tool> tools,GalateaPlanner.ClickType type) {
        return tools.stream().filter(t->t.slot()>=0&&t.slot()<8)
                .filter(t->!t.consumable())
                .filter(t->type==GalateaPlanner.ClickType.LEFT||!t.rightClickAbility()&&!t.blockItem())
                .min(Comparator.comparingInt((Tool t)->(t.empty()?5:0)+(type==GalateaPlanner.ClickType.RIGHT&&t.blockItem()?10:0))
                        .thenComparingInt(Tool::slot)).map(Tool::slot).orElse(-1);
    }
    public static boolean rightClickAbility(List<String> lore) {
        return lore.stream().map(s->s.replaceAll("§.","").toLowerCase(Locale.ROOT))
                .anyMatch(s->s.contains("right click")||s.contains("right-click")||s.contains("right_click"));
    }
    public static boolean consumableLore(List<String> lore) {
        return lore.stream().map(s->s.replaceAll("§.","").toLowerCase(Locale.ROOT)).anyMatch(s->s.contains("to consume"));
    }
}
