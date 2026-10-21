package dev.veltraph.client.farming.module;

import dev.veltraph.client.service.InventoryService;

import java.util.List;
import java.util.Locale;

/**
 * Pure crop-tool selection. On farming start a module resolves the hotbar slot holding its tool:
 *
 * <ol>
 *   <li>If a tool slot is configured (0..8) and that slot is non-empty, use it.</li>
 *   <li>Otherwise scan the hotbar for an item whose (lowercased) name contains one of the module's
 *       tool keywords, using the first match.</li>
 *   <li>If neither resolves, fail with a clear, chat-ready message so start is blocked.</li>
 * </ol>
 *
 * <p>This never clicks inventory GUI slots — the caller just changes the held hotbar slot.
 */
public final class CropToolSelector {

    private CropToolSelector() {
    }

    public static ToolSelection select(InventoryService inventory, int configuredSlot,
                                       List<String> keywords, String moduleName) {
        List<String> names = inventory.hotbarItemNames();
        dev.veltraph.client.log.VeltraphLog.verbose("CropToolSelector.select",
                () -> moduleName + " scan: configuredSlot=" + configuredSlot + " keywords=" + keywords
                        + " hotbar=" + names);

        if (configuredSlot >= 0 && configuredSlot < names.size()) {
            if (!names.get(configuredSlot).isEmpty()) {
                dev.veltraph.client.log.VeltraphLog.details("CropToolSelector.select",
                        moduleName + " using configured slot " + (configuredSlot + 1)
                                + " (" + names.get(configuredSlot) + ")");
                return ToolSelection.ok(configuredSlot);
            }
            return ToolSelection.fail("Veltraph: " + moduleName + " — configured tool slot "
                    + (configuredSlot + 1) + " is empty. Put the farming tool there or set the slot to Auto.");
        }

        if (keywords != null) {
            for (int i = 0; i < names.size(); i++) {
                String name = names.get(i);
                if (name.isEmpty()) {
                    continue;
                }
                for (String kw : keywords) {
                    if (name.contains(kw.toLowerCase(Locale.ROOT))) {
                        int slot = i;
                        dev.veltraph.client.log.VeltraphLog.details("CropToolSelector.select",
                                moduleName + " matched '" + name + "' in slot " + (slot + 1)
                                        + " on keyword '" + kw + "'");
                        return ToolSelection.ok(slot);
                    }
                }
            }
        }

        String want = (keywords == null || keywords.isEmpty()) ? "tool" : String.join(" / ", keywords);
        dev.veltraph.client.log.VeltraphLog.details("CropToolSelector.select",
                moduleName + " found no tool (" + want + ") in the hotbar");
        return ToolSelection.fail("Veltraph: " + moduleName + " needs its tool (" + want
                + ") in the hotbar — none found. Hold it on the hotbar or set a tool slot.");
    }
}
