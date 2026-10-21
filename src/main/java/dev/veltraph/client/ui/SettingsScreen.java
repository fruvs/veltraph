package dev.veltraph.client.ui;

import dev.veltraph.client.config.ConfigKey;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.FarmingConfig;
import dev.veltraph.client.config.PersistableKeys;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.VeltraphBootstrap;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.rewarp.RewarpCommandHandler;
import dev.veltraph.client.rewarp.RewarpService;
import dev.veltraph.client.ui.content.CategoryContent;
import dev.veltraph.client.ui.content.RowModel;
import dev.veltraph.client.ui.content.SettingsContent;
import dev.veltraph.client.ui.render.UiRenderer;
import dev.veltraph.client.ui.widget.AbstractWidget;
import dev.veltraph.client.ui.widget.EnumCycler;
import dev.veltraph.client.ui.widget.TextButton;
import dev.veltraph.client.ui.widget.ToggleButton;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings UI: a clickable category rail (default Farming) over a pure {@link SettingsContent} model.
 * The Farming category renders the source-backed Farming + Rotation/Aim sections wired live to
 * {@link ConfigService}; the Scripting category exposes the local debug-log enable toggle + verbosity
 * cycler ({@code DebugConfig}). Every category that has live config (HUD, Anti-Staff, Fishing, Combat,
 * Foraging, …) renders real toggle/enum/value rows; categories with no ported config render an
 * inspectable disabled row so none is ever empty. The screen builds its widgets from the model; the
 * model is unit-testable without Minecraft.
 */
public final class SettingsScreen extends VeltraphScreen {

    private static final int ROW_H = 20;
    private static final int ROW_GAP = 3;

    private final SettingsContent content;
    private final List<String> categories;
    private final Map<String, ConfigKey<?>> keysByName = new HashMap<>();
    private String selected = "Farming";

    public SettingsScreen(ModuleManager modules, ConfigService config, FarmLayoutRegistry registry,
                          ModuleActivationGuard guard) {
        super("Settings", modules, config, registry, guard);
        this.content = SettingsContent.build();
        this.categories = content.categories();
        indexConfigKeys();
    }

    /**
     * Index every persisted {@code ConfigKey} by name so any Settings row can bind by name. Sourced
     * generically from {@link PersistableKeys#all()} (every holder class' keys), so a new config key
     * gets a working GUI binding the moment its content row is added — no per-key registration to keep
     * in sync (this removed the previous hand-maintained list and its "forgot to register" failure mode).
     */
    private void indexConfigKeys() {
        for (ConfigKey<?> k : PersistableKeys.all()) {
            keysByName.put(k.name(), k);
        }
    }

    @Override
    protected VeltraphScreen createSibling() {
        return new AutomationsScreen(modules, config, registry, guard);
    }

    @Override
    protected boolean isAutomations() {
        return false;
    }

    @Override
    protected List<RailItem> railItems() {
        List<RailItem> items = new java.util.ArrayList<>();
        for (String cat : categories) {
            items.add(new RailItem(cat, cat.equals(selected)));
        }
        return items;
    }

    @Override
    protected void onRailSelected(int index) {
        if (index < 0 || index >= categories.size()) {
            return;
        }
        String cat = categories.get(index);
        if (!cat.equals(selected)) {
            selected = cat;
            dev.veltraph.client.log.VeltraphLog.verbose("UI.rail", "Settings category -> " + cat);
            refresh();
        }
    }

    @Override
    protected int layoutContent(int topY) {
        int x = contentX();
        int w = contentWidth();
        int y = topY;

        CategoryContent cc = content.forCategory(selected);
        for (RowModel row : cc.rows()) {
            y = addRow(x, y, w, row);
        }
        if ("Farming".equals(selected)) {
            y += ROW_GAP;
            y = addRewarpRows(x, y, w);
        }
        return y;
    }

    private int addRow(int x, int y, int w, RowModel row) {
        switch (row.kind()) {
            case SECTION -> {
                addContent(new SectionLabel(x, y, w, row.label()));
                return y + ROW_H + ROW_GAP;
            }
            case TOGGLE -> {
                if (dev.veltraph.client.config.DeveloperConfig.DEV_MODE_ENABLED.name().equals(row.configKey())) {
                    addDevModeToggle(x, y, w, row.label());
                } else if (dev.veltraph.client.config.HydraConfig.STAFF_ALERT_ENABLED.name()
                        .equals(row.configKey())) {
                    addAntiStaffToggle(x, y, w, row.label());
                } else {
                    addBoolToggle(x, y, w, row.label(), boolKey(row.configKey()));
                }
                return y + ROW_H + ROW_GAP;
            }
            case ENUM -> {
                addEnumCycler(x, y, w, row);
                return y + ROW_H + ROW_GAP;
            }
            case VALUE -> {
                addValueEditor(x, y, w, row.label(), row.configKey());
                return y + ROW_H + ROW_GAP;
            }
            case BUTTON -> {
                addDevButton(x, y, w, row.label(), row.actionId());
                return y + ROW_H + ROW_GAP;
            }
            case INFO -> {
                addContent(new DevInfoRow(x, y, w, row.actionId()));
                return y + ROW_H + ROW_GAP;
            }
            case COMING_SOON -> {
                addContent(new ComingSoonRow(x, y, w, row));
                return y + ROW_H + ROW_GAP;
            }
            default -> {
                return y;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private ConfigKey<Boolean> boolKey(String name) {
        ConfigKey<?> k = keysByName.get(name);
        if (k == null) {
            throw new IllegalStateException("Unknown boolean config key: " + name);
        }
        return (ConfigKey<Boolean>) k;
    }

    private void addBoolToggle(int x, int y, int w, String label, ConfigKey<Boolean> key) {
        addContent(new ToggleButton(x, y, w, ROW_H, label,
                () -> config.get(key), v -> config.set(key, v)));
    }

    /**
     * Anti-Staff watcher toggle: routes the set through {@link dev.veltraph.client.hydra.HydraAntiStaff#setEnabled}
     * (when Hydra is initialized) so disabling it immediately resets the detector, response queue/state, and
     * the smooth look + movement reaction (releasing held input) instead of just flipping the config flag —
     * the adapter's {@code settings.enabled()==false} early-return would otherwise skip that cleanup while a
     * macro is running. Falls back to a plain {@code config.set} only if Hydra is not available yet.
     */
    private void addAntiStaffToggle(int x, int y, int w, String label) {
        ConfigKey<Boolean> key = dev.veltraph.client.config.HydraConfig.STAFF_ALERT_ENABLED;
        addContent(new ToggleButton(x, y, w, ROW_H, label,
                () -> config.get(key),
                v -> {
                    dev.veltraph.client.hydra.HydraAntiStaff hydra = VeltraphBootstrap.hydra();
                    if (hydra != null) {
                        hydra.setEnabled(v); // resets detector + response + responder on a change
                    } else {
                        config.set(key, v);
                    }
                    VeltraphBootstrap.saveConfig();
                }));
    }

    /** Developer Mode toggle: routes the set through the controller so verbose logging is applied/restored. */
    private void addDevModeToggle(int x, int y, int w, String label) {
        addContent(new ToggleButton(x, y, w, ROW_H, label,
                () -> config.get(dev.veltraph.client.config.DeveloperConfig.DEV_MODE_ENABLED),
                v -> {
                    dev.veltraph.client.dev.DeveloperModeController dm = VeltraphBootstrap.developerMode();
                    if (dm != null) {
                        dm.setEnabled(v);
                    } else {
                        config.set(dev.veltraph.client.config.DeveloperConfig.DEV_MODE_ENABLED, v);
                    }
                    VeltraphBootstrap.saveConfig();
                    refresh();
                }));
    }

    /** A dev-test button. Colour reflects the action's risk; clicking runs it through the shared registry. */
    private void addDevButton(int x, int y, int w, String label, String actionId) {
        dev.veltraph.client.dev.DevTestRegistry registry = VeltraphBootstrap.devTests();
        dev.veltraph.client.dev.DevTestAction action = registry == null ? null : registry.byId(actionId);
        int color = Theme.ACCENT_DIM;
        if (action != null) {
            color = switch (action.risk()) {
                case LOCAL_ONLY -> Theme.ACCENT_DIM;
                case DRY_RUN -> Theme.ACCENT;
                case SERVER_BOUND -> Theme.STOP;
            };
        }
        addContent(TextButton.of(x, y, w, ROW_H, label, color, () -> runDevAction(actionId)));
    }

    /** Run a dev-test action via the shared registry + live MC context, surfacing the result locally. */
    private void runDevAction(String actionId) {
        dev.veltraph.client.dev.DevTestRegistry registry = VeltraphBootstrap.devTests();
        dev.veltraph.client.dev.DeveloperModeController dm = VeltraphBootstrap.developerMode();
        dev.veltraph.client.dev.DevTestContext ctx = VeltraphBootstrap.devTestContext();
        if (registry == null || dm == null) {
            return;
        }
        // SERVER_BOUND actions only send when the user has explicitly enabled the Allow toggle.
        dev.veltraph.client.dev.DevTestResult res =
                registry.run(actionId, ctx, dm.enabled(), dm.allowServerBound());
        dev.veltraph.client.log.VeltraphLog.basic("UI.dev", actionId + " -> " + res.status() + ": " + res.message());
        if (VeltraphBootstrap.chat() != null) {
            VeltraphBootstrap.chat().send("[Veltraph] dev " + actionId + " -> " + res.status() + ": " + res.message());
        }
        refresh();
    }

    private void addValueEditor(int x, int y, int w, String label, String name) {
        ConfigKey<?> key = keysByName.get(name);
        if (key == null) {
            throw new IllegalStateException("Unknown value config key: " + name);
        }
        addContent(new ConfigValueRow(x, y, w, ROW_H, label, key));
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> void addEnumCycler(int x, int y, int w, RowModel row) {
        String name = row.configKey();
        ConfigKey<?> rawKey = keysByName.get(name);
        if (rawKey == null) {
            throw new IllegalStateException("Unknown enum config key: " + name);
        }
        // The enum class travels on the (pure) RowModel, so the screen needs no hand-maintained
        // key-name -> enum-class map (the model is the single source of truth).
        Class<E> type = (Class<E>) row.enumType();
        ConfigKey<E> key = (ConfigKey<E>) rawKey;
        addContent(EnumCycler.ofEnum(x, y, w, ROW_H, row.label(), type,
                () -> config.get(key), v -> config.set(key, v),
                value -> dev.veltraph.client.game.LocationText.titleCase(value.name().replace('_',' ').toLowerCase(java.util.Locale.ROOT))));
    }

    /** UI-backed wrapper around the same local rewarp handler used by /veltraph farming rewarp. */
    private int addRewarpRows(int x, int y, int w) {
        addContent(new SectionLabel(x, y, w, "Rewarper"));
        y += ROW_H + ROW_GAP;

        addContent(new RewarpStatusRow(x, y, w));
        y += ROW_H + ROW_GAP;

        int gap = 5;
        int buttonW = Math.max(64, (w - gap * 3) / 4);
        addContent(TextButton.of(x, y, buttonW, ROW_H, "Add Rewarp", Theme.ON,
                () -> applyRewarpUi("add")));
        addContent(TextButton.of(x + buttonW + gap, y, buttonW, ROW_H, "Replace", Theme.ACCENT_DIM,
                () -> applyRewarpUi("replace")));
        addContent(TextButton.of(x + (buttonW + gap) * 2, y, buttonW, ROW_H, "List", Theme.ACCENT_DIM,
                () -> applyRewarpUi("list")));
        addContent(TextButton.of(x + (buttonW + gap) * 3, y, buttonW, ROW_H, "Clear", Theme.STOP,
                () -> applyRewarpUi("clear")));
        y += ROW_H + ROW_GAP;

        addContent(TextButton.of(x, y, buttonW * 2 + gap, ROW_H, "Warp Garden", Theme.ACCENT,
                () -> applyRewarpUi("warp")));
        addContent(new RewarpCommandRow(x + (buttonW + gap) * 2, y, w - (buttonW + gap) * 2, ROW_H));
        return y + ROW_H + ROW_GAP;
    }

    private void applyRewarpUi(String action) {
        RewarpCommandHandler handler = VeltraphBootstrap.rewarpCommand();
        if (handler == null) {
            dev.veltraph.client.log.VeltraphLog.basic("UI.rewarp", "rewarp handler unavailable for " + action);
            return;
        }
        dev.veltraph.client.log.VeltraphLog.basic("UI.rewarp", action);
        handler.apply(action);
        VeltraphBootstrap.saveConfig();
        refresh();
    }

    /** Render-only live status for the saved rewarp store. */
    private static final class RewarpStatusRow extends AbstractWidget {
        RewarpStatusRow(int x, int y, int w) {
            super(x, y, w, ROW_H);
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            RewarpService service = VeltraphBootstrap.rewarp();
            String text;
            if (service == null || service.savedCount() == 0) {
                text = "Saved rewarps: none";
            } else {
                String last = service.lastPoint()
                        .map(p -> p.pos().x() + ", " + p.pos().y() + ", " + p.pos().z()
                                + " (" + p.area().displayName() + ")")
                        .orElse("unknown");
                text = "Saved rewarps: " + service.savedCount() + " | Last: " + last;
            }
            r.text(text, x + 8, y + (height - r.lineHeight()) / 2, Theme.TEXT_MUTED, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /** Render-only current command preview for the physical rewarp action. */
    private final class RewarpCommandRow extends AbstractWidget {
        RewarpCommandRow(int x, int y, int w, int h) {
            super(x, y, w, h);
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            String command = dev.veltraph.client.rewarp.RewarpWarpExecutor.normalizeCommand(
                    config.get(FarmingConfig.REWARP_COMMAND));
            r.text("Command: /" + command, x + 8, y + (height - r.lineHeight()) / 2,
                    Theme.TEXT_MUTED, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /** Render-only section header with an accent underline. */
    private static final class SectionLabel extends AbstractWidget {
        private final String text;

        SectionLabel(int x, int y, int w, String text) {
            super(x, y, w, ROW_H);
            this.text = text;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            r.text(text, x + 2, y + 4, Theme.ACCENT, false);
            r.fill(x + 2, y + ROW_H - 2, width - 4, 1, 0x66323B4F);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /**
     * Render-only disabled placeholder so a category with no live config is never empty. Names the
     * recovered config group ({@link RowModel#label()}) and, where present, the blocker explaining why
     * it is not yet active — so the placeholder is inspectable rather than a bare "Coming Soon".
     */
    private static final class ComingSoonRow extends AbstractWidget {
        private final RowModel row;

        ComingSoonRow(int x, int y, int w, RowModel row) {
            super(x, y, w, ROW_H);
            this.row = row;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            String label = row.label();
            if (row.hasBlocker()) {
                int top = y + (height - r.lineHeight() * 2 - 1) / 2;
                r.text(label, x + 8, top, Theme.TEXT_MUTED, false);
                r.text("Blocked: " + row.blocker(), x + 8, top + r.lineHeight() + 1, Theme.TEXT_DISABLED, false);
            } else {
                r.text(label, x + 8, y + (height - r.lineHeight()) / 2, Theme.TEXT_DISABLED, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /** Read-only live status rows; rendering never arms or advances a workflow. */
    private static final class DevInfoRow extends AbstractWidget {
        private final String infoId;

        DevInfoRow(int x, int y, int w, String infoId) {
            super(x, y, w, ROW_H);
            this.infoId = infoId;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            String text;
            if ("logStatus".equals(infoId)) {
                dev.veltraph.client.dev.DeveloperModeController dm = VeltraphBootstrap.developerMode();
                text = dm != null ? dm.status() : "Developer Mode unavailable";
            } else if ("timerStatus".equals(infoId)) {
                var scheduler=VeltraphBootstrap.scriptScheduler();
                if(scheduler==null)text="Stop timer unavailable";
                else if(!VeltraphBootstrap.config().get(dev.veltraph.client.config.SchedulingConfig.TIMER_ENABLED))
                    text="Stop timer: off";
                else text=switch(scheduler.timerState()) {
                    case IDLE -> "Stop timer: waiting for a macro";
                    case CANCELLED -> "Stop timer: cancelled";
                    case EXPIRED -> "Stop timer: finished";
                    case ARMED -> {
                        long seconds=(Math.max(0,scheduler.timerRemainingTicks())+19L)/20;
                        yield "Stop timer: "+String.format(Locale.ROOT,"%d:%02d:%02d remaining",seconds/3600,seconds/60%60,seconds%60);
                    }
                };
            } else {
                text = infoId;
            }
            r.text(text, x + 8, y + (height - r.lineHeight()) / 2, Theme.TEXT_MUTED, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /** Editable string/number config row. Click value, type, Enter commits, Escape cancels. */
    private final class ConfigValueRow extends AbstractWidget {
        private static final int KEY_BACKSPACE = InputConstants.KEY_BACKSPACE;
        private static final int KEY_ENTER = InputConstants.KEY_RETURN;
        private static final int KEY_KP_ENTER = InputConstants.KEY_NUMPADENTER;
        private static final int KEY_ESCAPE = InputConstants.KEY_ESCAPE;

        private final String label;
        private final ConfigKey<?> key;
        private boolean editing;
        private boolean invalid;
        private final UiTextBuffer buffer=new UiTextBuffer();
        private long editingRevision=-1;
        private String preedit="";

        ConfigValueRow(int x, int y, int w, int h, String label, ConfigKey<?> key) {
            super(x, y, w, h);
            this.label = label;
            this.key = key;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            boolean hover = isMouseOver(mouseX, mouseY);
            if (hover || editing) {
                r.roundedRect(x, y, width, height, 8, editing ? 0x2639C6D6 : Theme.HOVER_OVERLAY);
            }
            int cy = y + (height - r.lineHeight()) / 2;
            int boxW = Math.max(86, width / 2);
            int boxX = x + width - boxW - 4;
            r.text(TextLayout.fit(label,boxX-x-16,r::textWidth),x+8,cy,Theme.TEXT_MUTED,false);
            int boxY = y + 2;
            int boxH = height - 4;
            r.roundedRect(boxX, boxY, boxW, boxH, 6, Theme.PANEL_RAISED);
            r.border(boxX, boxY, boxW, boxH, invalid ? Theme.STOP : (editing ? Theme.ACCENT : Theme.BORDER));

            String raw = editing ? buffer.preview(preedit) : formatValue(currentValue(key));
            String display = raw == null || raw.isBlank() ? "<blank>" : raw;
            r.text(fitLeft(r, display, boxW - 12), boxX + 6, cy, invalid ? Theme.STOP : Theme.TEXT, false);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0 || !isMouseOver(mouseX, mouseY)) {
                return false;
            }
            // Clicking an already active editor keeps its draft, revision and native composition.
            if (!editing) focus();
            return editing;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!editing) {
                return false;
            }
            boolean extend=(modifiers&InputConstants.MOD_SHIFT)!=0;
            if(keyCode==KEY_BACKSPACE){buffer.backspace();invalid=false;return true;}
            if(keyCode==InputConstants.KEY_DELETE){buffer.delete();invalid=false;return true;}
            if(keyCode==InputConstants.KEY_LEFT){buffer.move(-1,extend);return true;}
            if(keyCode==InputConstants.KEY_RIGHT){buffer.move(1,extend);return true;}
            if(keyCode==InputConstants.KEY_HOME){buffer.home(extend);return true;}
            if(keyCode==InputConstants.KEY_END){buffer.end(extend);return true;}
            if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
                commit();
                return true;
            }
            if (keyCode == KEY_ESCAPE) {
                blur();
                return true;
            }
            return true;
        }

        @Override
        public boolean charTyped(char chr, int modifiers) {
            if (!editing) {
                return false;
            }
            if(chr>=32&&chr!=127){if(!buffer.insert(String.valueOf(chr)))invalid=true;else invalid=false;}
            return true;
        }

        @Override public boolean keyboardNavigable(){return true;}
        @Override public boolean textInputFocused(){return editing;}
        @Override public int[] textInputBounds(){int boxW=Math.max(86,width/2);return new int[]{x+width-boxW-4,y+2,x+width-4,y+height-2};}
        @Override public void focus(){
            String value=formatValue(currentValue(key));
            if(value.length()>UiTextBuffer.MAX_LENGTH){editing=false;invalid=true;return;}
            editingRevision=config.revision(key).orElse(-1);buffer.set(value);preedit="";editing=true;invalid=false;
        }
        @Override public boolean preeditText(String text){if(!editing||text==null||text.length()>UiTextBuffer.MAX_LENGTH)return false;preedit=text;return true;}
        @Override public boolean codepointTyped(int codepoint,int modifiers){if(!editing)return false;preedit="";if(!Character.isValidCodePoint(codepoint))return false;return replaceSelectedText(new String(Character.toChars(codepoint)));}
        @Override public boolean selectAllText(){if(!editing)return false;buffer.selectAll();return true;}
        @Override public String selectedText(){return editing&&buffer.selected()?buffer.selection():null;}
        @Override public boolean replaceSelectedText(String value){if(!editing)return false;invalid=!buffer.insert(value);return true;}
        @Override public boolean deleteSelectedText(){if(!editing)return false;buffer.deleteSelection();invalid=false;return true;}
        @Override public void blur(){editing=false;invalid=false;editingRevision=-1;preedit="";buffer.set("");}

        @Override
        public boolean focused() {
            return editing;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private Object currentValue(ConfigKey<?> rawKey) {
            return config.get((ConfigKey) rawKey);
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        private void commit() {
            if(editingRevision<0||config.revision(key).orElse(-1)!=editingRevision){invalid=true;return;}
            Object parsed = parseValue(key.defaultValue(), buffer.text());
            if (parsed == null) {
                invalid = true;
                return;
            }
            config.set((ConfigKey) key, parsed);
            blur();
        }

        private Object parseValue(Object def, String raw) {
            String s = raw == null ? "" : raw.trim();
            try {
                if (def instanceof Integer) {
                    return Integer.parseInt(s);
                }
                if (def instanceof Long) {
                    return Long.parseLong(s);
                }
                if (def instanceof Double) {
                    double value=Double.parseDouble(s);return Double.isFinite(value)?value:null;
                }
                return raw == null ? "" : raw;
            } catch (RuntimeException bad) {
                return null;
            }
        }

        private String formatValue(Object value) {
            if (value instanceof Double d) {
                String s = String.format(Locale.ROOT, "%.4f", d);
                return s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return value == null ? "" : value.toString();
        }

        private String fitLeft(UiRenderer r,String s,int maxW){return UiTextLayout.suffix(s,maxW,r::textWidth);}
    }
}
