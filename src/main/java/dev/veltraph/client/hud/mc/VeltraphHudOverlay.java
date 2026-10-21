package dev.veltraph.client.hud.mc;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.config.HudConfig;
import dev.veltraph.client.core.MacroState;
import dev.veltraph.client.core.Module;
import dev.veltraph.client.core.ModuleCategory;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.game.GameContext;
import dev.veltraph.client.game.GameContextService;
import dev.veltraph.client.game.HudCategory;
import dev.veltraph.client.hud.HudCorner;
import dev.veltraph.client.hud.HudInputs;
import dev.veltraph.client.hud.HudLine;
import dev.veltraph.client.hud.HudModel;
import dev.veltraph.client.hud.HudModelBuilder;
import dev.veltraph.client.hud.HudPanelLayout;
import dev.veltraph.client.hydra.HydraAntiStaff;
import dev.veltraph.client.hydra.HydraEvent;
import dev.veltraph.client.log.VeltraphLog;
import dev.veltraph.client.rewarp.RewarpService;
import dev.veltraph.client.ui.Theme;
import dev.veltraph.client.ui.UiTextures;
import dev.veltraph.client.ui.render.DrawContextRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.List;

/**
 * In-world HUD overlay (Target 5). Registered on Fabric's classic {@link HudRenderCallback} (verified
 * present in {@code fabric-rendering-v1} 16.2.10; it hands a {@link GuiGraphicsExtractor} + tick counter after
 * the vanilla HUD). Each frame it samples the live services into a pure {@link HudInputs} snapshot, asks
 * {@link HudModelBuilder} what to draw, and — if visible — paints a compact Veltraph-skinned panel in
 * the configured corner using the shared {@link DrawContextRenderer}. Text uses the renderer's smooth
 * primary path (the same Minecraft {@code TextRenderer} the menu uses), so the HUD is no longer the
 * blocky bitmap font; panel geometry comes from the pure {@link HudPanelLayout}. All drawing is wrapped
 * in try/catch so a render hiccup can never crash the client.
 *
 * <p>Local rendering only: it reads existing services and draws to the local screen. No network, no
 * telemetry.
 */
public final class VeltraphHudOverlay {

    private static final int PAD = 5;
    private static final int LINE_GAP = 2;
    private static final int LOGO = 0xFF45D0C0; // accent square

    private final ModuleManager moduleManager;
    private final GameContextService gameContext;
    private final RewarpService rewarp;
    private final HydraAntiStaff hydra;
    private final ConfigService config;
    private final java.util.function.Supplier<List<HudLine>> metricsLines;
    private final DrawContextRenderer renderer = new DrawContextRenderer();

    private boolean lastVisible;

    public VeltraphHudOverlay(ModuleManager moduleManager,
                              GameContextService gameContext,
                              RewarpService rewarp,
                              HydraAntiStaff hydra,
                              ConfigService config) {
        this(moduleManager,gameContext,rewarp,hydra,config,List::of);
    }
    public VeltraphHudOverlay(ModuleManager moduleManager,GameContextService gameContext,RewarpService rewarp,
                             HydraAntiStaff hydra,ConfigService config,java.util.function.Supplier<List<HudLine>> metricsLines){
        this.moduleManager = moduleManager;
        this.gameContext = gameContext;
        this.rewarp = rewarp;
        this.hydra = hydra;
        this.config = config;
        this.metricsLines=java.util.Objects.requireNonNull(metricsLines);
    }

    /** Register the overlay on the HUD render callback. */
    public void register() {
        HudElementRegistry.addLast(net.minecraft.resources.Identifier.fromNamespaceAndPath("veltraph", "status"), (drawContext, tickCounter) -> onHudRender(drawContext));
    }

    private void onHudRender(GuiGraphicsExtractor drawContext) {
        try {
            HudModel model = HudModelBuilder.build(sample());
            if (model.visible() != lastVisible) {
                lastVisible = model.visible();
                VeltraphLog.basic("Hud", "overlay " + (model.visible() ? "visible" : "hidden"));
            }
            if (!model.visible() || model.lines().isEmpty()) {
                return;
            }
            draw(drawContext, model);
        } catch (Throwable t) {
            // A render hiccup must never crash the client. Swallow and skip this frame.
        }
    }

    /** Sample the live services into a pure snapshot the builder can consume. */
    private HudInputs sample() {
        boolean hudEnabled = config == null || config.get(HudConfig.HUD_ENABLED);
        GameContext ctx = gameContext == null ? GameContext.NONE : gameContext.current();

        HudInputs.ModuleInfo active = null;
        if (moduleManager != null) {
            List<Module> running = moduleManager.enabledModules();
            if (!running.isEmpty()) {
                Module m = running.get(0);
                MacroState state = m.state() == null ? MacroState.RUNNING : m.state();
                active = new HudInputs.ModuleInfo(m.displayName(), state, m.statusLine(),
                        hudCategoryFor(m.category()));
            }
        }

        HudInputs.HydraInfo hydraInfo = HudInputs.HydraInfo.disabled();
        if (hydra != null) {
            HydraEvent last = hydra.lastEvent();
            hydraInfo = new HudInputs.HydraInfo(hydra.enabled(), hydra.state(),
                    hydra.nearestPlayerName(), last == null ? null : last.type().name());
        }

        HudInputs.RewarpInfo rewarpInfo = HudInputs.RewarpInfo.none();
        if (rewarp != null) {
            rewarpInfo = new HudInputs.RewarpInfo(rewarp.savedCount(), rewarp.isReturnPending());
        }

        return new HudInputs(hudEnabled, ctx, active, hydraInfo, rewarpInfo);
    }

    /** Map a module category to the HUD-visibility category whose area gate the macro line respects. */
    static HudCategory hudCategoryFor(ModuleCategory category) {
        if (category == null) {
            return HudCategory.SCRIPT;
        }
        return switch (category) {
            case FARMING -> HudCategory.GARDEN;
            case FORAGING -> HudCategory.FORAGING;
            case FISHING -> HudCategory.FISHING;
            // Combat/mining/etc. have no confirmed area gate beyond "in SkyBlock while scripting".
            default -> HudCategory.SCRIPT;
        };
    }

    private void draw(GuiGraphicsExtractor drawContext, HudModel model) {
        renderer.begin(drawContext);

        // Measure the panel from the smooth primary font (the same TextRenderer the menu uses).
        int lineH = renderer.lineHeight();
        int textW = 0;
        for (HudLine line : model.lines()) {
            textW = Math.max(textW, renderer.textWidth(line.text()));
        }

        HudCorner corner = config == null ? HudCorner.TOP_LEFT : config.get(HudConfig.HUD_CORNER);
        if (corner == null) {
            corner = HudCorner.TOP_LEFT;
        }
        int margin = config == null ? 4 : Math.max(0, config.get(HudConfig.HUD_MARGIN));
        int screenW = drawContext.guiWidth();
        int screenH = drawContext.guiHeight();
        // Append cached lines only within the existing visible panel's measured vertical budget.
        int maximumLines=Math.max(1,(screenH-2*margin-2*PAD+LINE_GAP)/(lineH+LINE_GAP));
        int available=Math.max(0,maximumLines-model.lines().size());
        List<HudLine> metricRows=metricsLines.get();
        if(available>0&&metricRows!=null&&!metricRows.isEmpty()){
            var combined=new java.util.ArrayList<>(model.lines());int accepted=Math.min(available,metricRows.size());
            if(metricRows.size()>accepted&&accepted>1){combined.addAll(metricRows.subList(0,accepted-1));combined.add(HudLine.muted("Metrics: more available"));}
            else combined.addAll(metricRows.subList(0,accepted));model=HudModel.of(combined);
            for(var line:metricRows)textW=Math.max(textW,renderer.textWidth(line.text()));
        }

        HudPanelLayout.Rect rect = HudPanelLayout.compute(model.lines().size(), lineH, textW,
                PAD, LINE_GAP, screenW, screenH, margin, corner.isLeft(), corner.isTop());
        int x = rect.x();
        int y = rect.y();
        int panelW = rect.width();
        int panelH = rect.height();

        // Skinned panel: soft shadow + gradient body + nine-slice texture + border (texture degrades to
        // the procedural fill if the backend is unavailable).
        renderer.softShadow(x + 2, y + 3, panelW, panelH, 6, Theme.PANEL_SHADOW);
        renderer.gradientV(x, y, panelW, panelH, Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        renderer.nineSlice(UiTextures.PANEL, x, y, panelW, panelH,
                UiTextures.PANEL_W, UiTextures.PANEL_H, UiTextures.PANEL_INSET, 6);
        renderer.border(x, y, panelW, panelH, Theme.BORDER);
        // Accent strip down the left edge.
        renderer.fill(x, y, 2, panelH, Theme.ACCENT_DIM);

        int tx = rect.contentX();
        int ty = rect.contentY();
        for (HudLine line : model.lines()) {
            int color = colorFor(line.role());
            // Smooth text with a drop shadow via the shared renderer (not the bitmap font).
            String fitted=fitText(line.text(),rect.contentWidth());renderer.text(fitted, tx, ty, color, true);
            if (line.role() == HudLine.Role.TITLE) {
                renderer.fill(tx, ty + lineH, renderer.textWidth(fitted), 1, LOGO);
            }
            ty += lineH + LINE_GAP;
        }
    }

    private String fitText(String text,int width){
        if(renderer.textWidth(text)<=width)return text;String value=text;
        while(!value.isEmpty()&&renderer.textWidth(value+"…")>width)value=value.substring(0,value.offsetByCodePoints(value.length(),-1));
        return value.isEmpty()?"":value+"…";
    }

    private static int colorFor(HudLine.Role role) {
        return switch (role) {
            case TITLE -> Theme.TITLE;
            case ACTIVE -> Theme.ON;
            case WARNING -> Theme.WARNING;
            case STOPPED -> Theme.STOP;
            case MUTED -> Theme.TEXT_MUTED;
            case BODY -> Theme.TEXT;
        };
    }
}
