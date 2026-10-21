package dev.veltraph.client.ui;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.farming.domain.FarmMovementMode;
import dev.veltraph.client.farming.module.MushroomFarmModule;
import dev.veltraph.client.farming.module.WartCropsFarmModule;
import dev.veltraph.client.ui.content.AutomationsContent;
import dev.veltraph.client.ui.content.CardModel;
import dev.veltraph.client.ui.content.CategoryContent;
import dev.veltraph.client.ui.render.UiRenderer;
import dev.veltraph.client.ui.widget.EnumCycler;
import dev.veltraph.client.ui.widget.TextButton;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;









public final class AutomationsScreen extends VeltraphScreen {

    private static final int CARD_GAP = 8;

    private final AutomationsContent content;
    private final List<ModuleCategory> categories;
    private ModuleCategory selected = ModuleCategory.FARMING;
    /** Indices (within the current category's card list) whose detail drawer is expanded. */
    private final java.util.Set<Integer> expanded = new java.util.HashSet<>();

    public AutomationsScreen(ModuleManager modules, ConfigService config, FarmLayoutRegistry registry,
                             ModuleActivationGuard guard) {
        super("Automations", modules, config, registry, guard);
        this.content = AutomationsContent.build(registry);
        this.categories = content.categories();
    }

    @Override
    protected VeltraphScreen createSibling() {
        return new SettingsScreen(modules, config, registry, guard);
    }

    @Override
    protected boolean isAutomations() {
        return true;
    }

    @Override
    protected List<RailItem> railItems() {
        List<RailItem> items = new ArrayList<>();
        for (ModuleCategory cat : categories) {
            items.add(new RailItem(cat.displayName(), cat == selected));
        }
        return items;
    }

    @Override
    protected void onRailSelected(int index) {
        if (index < 0 || index >= categories.size()) {
            return;
        }
        ModuleCategory cat = categories.get(index);
        if (cat != selected) {
            selected = cat;
            expanded.clear(); // card indices are per-category
            dev.veltraph.client.log.VeltraphLog.verbose("UI.rail", "Automations category -> " + cat.displayName());
            refresh();
        }
    }

    @Override
    protected int layoutContent(int topY) {
        int x = contentX();
        int w = contentWidth();
        int y = topY;

        CategoryContent cc = content.forCategory(selected);
        List<CardModel> cards = cc.cards();
        for (int i = 0; i < cards.size(); i++) {
            y = addCard(x, y, w, cards.get(i), i);
        }
        return y;
    }

    private void toggleExpanded(int index) {
        if (!expanded.remove(index)) {
            expanded.add(index);
        }
        dev.veltraph.client.log.VeltraphLog.verbose("UI.card",
                "detail " + (expanded.contains(index) ? "expanded" : "collapsed") + " idx=" + index);
        rebuild(); // keep the scroll position; only the card heights change
    }

    /** Card height grows with the number of meta/info lines (and any mode cycler). */
    private int cardHeight(CardModel card) {
        int h = 48; // dedicated title row, then status + controls
        h += card.metaLines().size() * 11;
        if (card.hasSupportedCrops()) {
            h += 11;
        }
        if (card.hasBlocker()) {
            h += 11;
        }
        if (card.hasModeCycler()) {
            h += 22;
        }
        return Math.max(h + 8, 44);
    }

    private int addCard(int x, int y, int w, CardModel card, int index) {
        int h = cardHeight(card);
        boolean enabled = card.enabled();
        String moduleId = card.moduleId();
        boolean isExpanded = expanded.contains(index);

        addContent(new CardBackground(x, y, w, h, card,
                () -> enabled && moduleId != null ? stateLabel(moduleId) : "Coming Soon"));

        if (enabled && moduleId != null) {
            int btnW = 72;
            addContent(new TextButton(x + w - btnW - 12, y + 24, btnW, 20,
                    () -> moduleEnabled(moduleId) ? "Stop" : "galatea_source_reset".equals(moduleId) ? "Reset" : "Start",
                    () -> moduleEnabled(moduleId) ? Theme.STOP : Theme.ON,
                    () -> toggleAndCloseIfStarted(moduleId)));
            addModeCycler(x, y + h - 30, w, card);
        }

        // Details toggle (only when the card has structured detail). Placed left of Start/Stop on an
        // enabled card, or at the right edge on a disabled card (no Start/Stop there).
        if (card.hasDetail()) {
            int detW = 60;
            int detX = enabled && moduleId != null ? x + w - 72 - 12 - detW - 6 : x + w - detW - 12;
            addContent(new TextButton(detX, y + 24, detW, 20,
                    () -> isExpanded ? "Hide" : "Details",
                    () -> isExpanded ? Theme.ACCENT : Theme.ACCENT_DIM,
                    () -> toggleExpanded(index)));
        }

        int nextY = y + h + CARD_GAP;

        // Expanded detail drawer: a render-only panel (static recovered detail + live module state) plus a
        // local "Log State" debug action for live modules. Drawn just under the card, before the next one.
        if (isExpanded && card.hasDetail()) {
            int ph = detailPanelHeight(card, moduleId);
            addContent(new CardDetailPanel(x + 6, nextY, w - 12, ph, card, moduleId));
            int innerBottom = nextY + ph;
            if (enabled && moduleId != null) {
                addContent(TextButton.of(x + 6 + 10, innerBottom - 26, 110, 18, "Log State", Theme.ACCENT_DIM,
                        () -> logModuleState(moduleId)));
            }
            nextY = innerBottom + CARD_GAP;
        }
        return nextY;
    }

    /** Height of the expanded detail panel: the static detail lines + (for live modules) live state + action. */
    private int detailPanelHeight(CardModel card, String moduleId) {
        var d = card.detail();
        int lines = 0;
        if (!d.stateMachine().isEmpty()) {
            lines += 1 + wrappedLines(d.stateMachine());
        }
        if (!d.requirements().isEmpty()) {
            lines += 1 + wrappedLines(d.requirements());
        }
        if (!d.sources().isEmpty()) {
            lines += 1 + wrappedLines(d.sources());
        }
        boolean live = card.enabled() && moduleId != null;
        if (live) {
            lines += 2; // "Live state:" header + the status line
        }
        int h = 10 + lines * 11 + 6;
        if (live) {
            h += 24; // the Log State action button row
        }
        return Math.max(h, 30);
    }

    private int wrappedLines(List<String> lines) {
        return lines.stream().mapToInt(line -> TextLayout.wrap(line, Math.max(1,contentWidth()-42),
                text -> minecraft.font.width(text)).size()).sum();
    }

    /** Local-only debug action: write the live module state + diagnostics to the Veltraph debug log. */
    private void logModuleState(String moduleId) {
        modules.get(moduleId).ifPresent(m -> dev.veltraph.client.log.VeltraphLog.basic("UI.card",
                moduleId + " state=" + m.state() + " | " + m.diagnostics().replace('\n', ' ')));
    }

    /** Wire an in-card mode cycler back to the live module (Wart/Crops S↔V, Mushroom Classical↔Staircase). */
    private void addModeCycler(int x, int y, int w, CardModel card) {
        if (!card.hasModeCycler()) {
            return;
        }
        List<FarmMovementMode> modes = new ArrayList<>();
        for (CardModel.ModeOption opt : card.modeOptions()) {
            modes.add(FarmMovementMode.valueOf(opt.movementMode()));
        }
        if (WartCropsFarmModule.ID.equals(card.moduleId())) {
            WartCropsFarmModule mod = (WartCropsFarmModule) modules.get(WartCropsFarmModule.ID).orElse(null);
            if (mod != null) {
                addContent(new EnumCycler<>(x + 12, y, w - 24, 18, "Mode", modes,
                        mod::mode, v -> { if (!mod.isEnabled()) mod.setMode(v); }, AutomationsScreen::modeName));
            }
        } else if (MushroomFarmModule.ID.equals(card.moduleId())) {
            MushroomFarmModule mod = (MushroomFarmModule) modules.get(MushroomFarmModule.ID).orElse(null);
            if (mod != null) {
                addContent(new EnumCycler<>(x + 12, y, w - 24, 18, "Mode", modes,
                        mod::mode, v -> { if (!mod.isEnabled()) mod.setMode(v); }, AutomationsScreen::modeName));
            }
        }
    }

    private boolean moduleEnabled(String id) {
        return modules.get(id).map(Module::isEnabled).orElse(false);
    }

    private dev.veltraph.client.VeltraphBootstrap.RootOriginalMenuInstallation originalRootMenu;
    /** Existing completed original factory identity only, never a constructor or gameplay grant. */
    public dev.veltraph.client.VeltraphBootstrap.RootOriginalMenuInstallation originalRootMenuForRoot(){return originalRootMenu;}
    /** Passive source registration only from this exact original factory return. */
    final void observeOriginalFactoryCompletion(){
        try{boolean factory=StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream->{var caller=stream.skip(1).findFirst().orElse(null);
            return caller!=null&&caller.getDeclaringClass()==VeltraphScreens.class&&caller.getMethodName().equals("automations");});
            if(factory&&originalRootMenu==null)originalRootMenu=dev.veltraph.client.VeltraphBootstrap.observeOriginalAutomationsMenu(this,modules,config,registry,guard);
        }catch(RuntimeException|Error unavailable){}
    }
    private void toggleAndCloseIfStarted(String id) {
        if("galatea_source_reset".equals(id)&&!moduleEnabled(id)) {
            Minecraft.getInstance().gui.setScreen(null);
            boolean requested=dev.veltraph.client.VeltraphBootstrap.requestGalateaReset();
            dev.veltraph.client.VeltraphBootstrap.chat().send(requested
                    ?"[Veltraph] Galatea reset requested; awaiting ordinary guarded handoff."
                    :"[Veltraph] Galatea reset unavailable: check idle parent, permission, exact bindings and active owners.");
            return;
        }
        dev.veltraph.client.VeltraphBootstrap.RootOriginalMenuRequest original=null;
        try{original=dev.veltraph.client.VeltraphBootstrap.beginOriginalAutomationsRequest(originalRootMenu,id);}catch(RuntimeException|Error unavailable){}
        try{
            boolean wasEnabled = moduleEnabled(id);
            boolean nowEnabled = guard.toggle(id);
            try{dev.veltraph.client.VeltraphBootstrap.observeOriginalAutomationsGuardReturn(original,wasEnabled,nowEnabled);}catch(RuntimeException|Error unavailable){}
            if (!wasEnabled && nowEnabled) {
                dev.veltraph.client.log.VeltraphLog.basic("UI", "closing Automations after starting macro " + id);
                try{dev.veltraph.client.VeltraphBootstrap.beginOriginalAutomationsNativeClose(original);}catch(RuntimeException|Error unavailable){}
                Minecraft.getInstance().gui.setScreen(null);
                try{dev.veltraph.client.VeltraphBootstrap.finishOriginalAutomationsNativeClose(original);}catch(RuntimeException|Error unavailable){}
            }
        }finally{try{dev.veltraph.client.VeltraphBootstrap.closeOriginalAutomationsRequest(original);}catch(RuntimeException|Error unavailable){}}
    }

    private String stateLabel(String id) {
        return modules.get(id).map(m -> m.isEnabled() ? "RUNNING — " + m.state() : "Idle").orElse("—");
    }

    private static String modeName(FarmMovementMode m) {
        return switch (m) {
            case S_SHAPE -> "S-Shape";
            case VERTICAL -> "Vertical";
            case CLASSICAL -> "Classical";
            case STAIRCASE -> "Staircase";
            default -> m.name();
        };
    }

    /** Render-only card background driven by the content model; consumes no clicks. */
    private static final class CardBackground extends dev.veltraph.client.ui.widget.AbstractWidget {
        private final CardModel card;
        private final java.util.function.Supplier<String> status;

        CardBackground(int x, int y, int w, int h, CardModel card,
                       java.util.function.Supplier<String> status) {
            super(x, y, w, h);
            this.card = card;
            this.status = status;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            r.softShadow(x + 3, y + 4, width - 2, height - 1, 14, 0x33000000);
            r.gradientV(x, y, width, height, Theme.CARD_TOP, Theme.CARD_BOTTOM);
            r.nineSlice(UiTextures.CARD, x, y, width, height,
                    UiTextures.CARD_W, UiTextures.CARD_H, UiTextures.CARD_INSET, 12);
            int accent = card.comingSoon() ? Theme.TEXT_DISABLED : Theme.ACCENT_DIM;
            r.roundedRect(x + 1, y + 8, 3, height - 16, 2, accent);

            int titleColor = card.comingSoon() ? Theme.TEXT_MUTED : Theme.TEXT;
            r.text(TextLayout.fit(card.title(), width - 28, r::textWidth), x + 14, y + 10, titleColor, false);
            r.text(TextLayout.fit(status.get(), Math.max(0, width - (card.enabled() ? 170 : 92)), r::textWidth), x + 14, y + 29, Theme.TEXT_MUTED, false);

            int ty = y + 50;
            for (String line : card.metaLines()) {
                r.text(TextLayout.fit(line, width - 28, r::textWidth), x + 14, ty, Theme.TEXT_MUTED, false);
                ty += 11;
            }
            if (card.hasSupportedCrops()) {
                r.text(TextLayout.fit("Supported crops: " + String.join(", ", card.supportedCrops()) + " (auto-detected)", width - 28, r::textWidth),
                        x + 14, ty, Theme.TEXT_MUTED, false);
                ty += 11;
            }
            if (card.hasBlocker()) {
                r.text(TextLayout.fit("Blocked: " + card.blocker(), width - 28, r::textWidth), x + 14, ty, Theme.TEXT_DISABLED, false);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }

    /**
     * Render-only detail drawer for an expanded card: the recovered state machine, requirements, and
     * source citations (from the pure {@link CardModel#detail()}), plus — for a live module — its current
     * state and one-line diagnostics read live at render time. Consumes no clicks (the "Log State"
     * debug-action button is a separate widget). Non-static so it can read {@link #modules}.
     */
    private final class CardDetailPanel extends dev.veltraph.client.ui.widget.AbstractWidget {
        private final CardModel card;
        private final String moduleId;

        CardDetailPanel(int x, int y, int w, int h, CardModel card, String moduleId) {
            super(x, y, w, h);
            this.card = card;
            this.moduleId = moduleId;
        }

        @Override
        public void render(UiRenderer r, int mouseX, int mouseY) {
            r.roundedRect(x, y, width, height, 10, Theme.PANEL_RAISED);
            r.roundedRect(x + 1, y + 6, 3, height - 12, 2, Theme.ACCENT_DIM);
            int tx = x + 12;
            int ty = y + 6;
            ty = section(r, tx, ty, "State machine", card.detail().stateMachine());
            ty = section(r, tx, ty, "Requires", card.detail().requirements());
            ty = section(r, tx, ty, "Sources", card.detail().sources());

            if (card.enabled() && moduleId != null) {
                r.text("Live state", tx, ty, Theme.ACCENT_DIM, false);
                ty += 11;
                String live = modules.get(moduleId)
                        .map(m -> (m.isEnabled() ? "RUNNING " + m.state() : "Idle") + " — " + m.statusLine())
                        .orElse("—");
                r.text(fit(r, live, width - 24), tx, ty, Theme.TEXT_MUTED, false);
            }
        }

        private int section(UiRenderer r, int tx, int ty, String header, List<String> lines) {
            if (lines.isEmpty()) {
                return ty;
            }
            r.text(header, tx, ty, Theme.ACCENT_DIM, false);
            ty += 11;
            for (String line : lines) {
                for(String wrapped:TextLayout.wrap(line,width-30,r::textWidth)) {
                    r.text(wrapped,tx+6,ty,Theme.TEXT_MUTED,false);ty+=11;
                }
            }
            return ty;
        }

        private String fit(UiRenderer r, String s, int maxW) {
            if (r.textWidth(s) <= maxW) {
                return s;
            }
            String tail = "...";
            int end = s.length();
            while (end > 0 && r.textWidth(s.substring(0, end) + tail) > maxW) {
                end--;
            }
            return s.substring(0, Math.max(0, end)) + tail;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return false;
        }
    }
}
