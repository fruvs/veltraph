package dev.veltraph.client.ui;

import dev.veltraph.client.config.ConfigService;
import dev.veltraph.client.core.ModuleActivationGuard;
import dev.veltraph.client.core.ModuleManager;
import dev.veltraph.client.farming.domain.FarmLayoutRegistry;
import dev.veltraph.client.ui.render.DrawContextRenderer;
import dev.veltraph.client.ui.render.UiRenderer;
import dev.veltraph.client.ui.widget.TextButton;
import dev.veltraph.client.ui.widget.Widget;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.network.chat.Component;

/**
 * Base Veltraph screen with a custom (non-vanilla) skin: a dark veil, an indigo panel with a header
 * strip and accent underline, a left category rail with icons, and a scroll content viewport with a
 * thin scrollbar. Layout is fully responsive — sizes derive from the GUI-scaled window via
 * {@link UiLayout} (no fixed {@code 420x240}).
 *
 * <p>Content lives in a {@link ScrollViewport}: it is clipped to the viewport when drawn and clicks are
 * dispatched only to widgets actually inside the viewport, so scrolled-off rows never render or receive
 * input. Header tab widgets live outside the viewport.
 *
 * <p>{@code shouldPause()} is false so automations keep ticking while the menu is open.
 */
public abstract class VeltraphScreen extends Screen {

    protected final ModuleManager modules;
    protected final ConfigService config;
    protected final FarmLayoutRegistry registry;
    protected final ModuleActivationGuard guard;

    protected DrawContextRenderer renderer;
    protected LayoutMetrics layout;
    protected ScrollViewport viewport;

    private final List<Widget> tabWidgets = new ArrayList<>();
    private final List<Widget> contentWidgets = new ArrayList<>();
    private Widget focusedWidget;

    /** Persisted scroll offset (survives relayout / GUI-scale changes). */
    private int scroll;
    private boolean scrollbarDragging;
    private double scrollbarGrab;

    protected VeltraphScreen(String title, ModuleManager modules, ConfigService config,
                             FarmLayoutRegistry registry, ModuleActivationGuard guard) {
        super(Component.nullToEmpty("Veltraph — " + title));
        this.modules = modules;
        this.config = config;
        this.registry = registry;
        this.guard = guard;
    }

    protected abstract VeltraphScreen createSibling();

    protected abstract boolean isAutomations();

    /** Category rail entries for this screen (top to bottom). */
    protected abstract List<RailItem> railItems();

    /**
     * Lay out content widgets via {@link #addContent(Widget)}, stacking down from screen-space
     * {@code topY}. Return the Y just past the last widget (used to compute the scroll extent).
     */
    protected abstract int layoutContent(int topY);

    /**
     * Called when a rail category row is clicked (by its {@link #railItems()} index). Subclasses that
     * support category switching override this to change the displayed content. Default: no-op.
     */
    protected void onRailSelected(int index) {
    }

    /** Screen-space bounds of each drawn rail row, in {@link #railItems()} order. Rebuilt each draw. */
    private final List<int[]> railRowBounds = new ArrayList<>();

    private boolean loggedOpen;

    @Override
    protected void init() {
        this.renderer = new DrawContextRenderer();
        if (!loggedOpen) {
            loggedOpen = true;
            dev.veltraph.client.log.VeltraphLog.basic("UI", "GUI opened: " + (isAutomations() ? "Automations" : "Settings"));
        }
        relayout();
    }

    @Override
    public void removed() {
        clearFocus();scrollbarDragging=false;
        dev.veltraph.client.log.VeltraphLog.basic("UI", "GUI closed: " + (isAutomations() ? "Automations" : "Settings"));
        super.removed();
    }

    private void relayout() {
        clearFocus();scrollbarDragging=false;
        layout = UiLayout.compute(this.width, this.height);
        tabWidgets.clear();
        contentWidgets.clear();
        buildTabs();

        viewport = new ScrollViewport(layout.contentX(), layout.contentY(),
                layout.contentInnerW(), layout.contentH());
        viewport.setScroll(scroll);

        int topY = viewport.screenY(0);
        int bottomY = layoutContent(topY);
        viewport.setContentHeight(bottomY - topY);
        viewport.setScroll(scroll);
        scroll = viewport.scroll();

        // If the scroll got clamped (content shrank), reposition content at the corrected offset.
        if (viewport.screenY(0) != topY) {
            contentWidgets.clear();
            topY = viewport.screenY(0);
            layoutContent(topY);
        }
    }

    private void buildTabs() {
        int bh = Math.min(layout.headerH() - 8, 20);
        int by = layout.panelY() + (layout.headerH() - bh) / 2;
        int gap = 6;
        int avail = layout.panelW() - 150; // leave room for the title/logo on the left
        int bw = Math.max(72, Math.min(112, (avail - gap) / 2));
        int sx = layout.panelRight() - UiLayout.PAD - (2 * bw + gap);

        tabWidgets.add(new TextButton(sx, by, bw, bh,
                () -> "Automations", () -> isAutomations() ? Theme.TAB_ACTIVE : Theme.TAB_INACTIVE,
                () -> { if (!isAutomations()) switchSibling(); }));
        tabWidgets.add(new TextButton(sx + bw + gap, by, bw, bh,
                () -> "Settings", () -> isAutomations() ? Theme.TAB_INACTIVE : Theme.TAB_ACTIVE,
                () -> { if (isAutomations()) switchSibling(); }));
    }

    private void switchSibling() {
        Minecraft.getInstance().gui.setScreen(createSibling());
    }

    // ---- Subclass content helpers (screen-space) ----

    /** Left X where content begins (right of the category rail). */
    protected int contentX() {
        return layout.contentX();
    }

    /** Inner content width available to widgets (excludes the scrollbar gutter). */
    protected int contentWidth() {
        return viewport.width();
    }

    protected void addContent(Widget widget) {
        contentWidgets.add(widget);
    }

    /** Reset the scroll and rebuild all content/tab widgets (e.g. after switching rail category). */
    protected void refresh() {
        clearFocus();
        scroll = 0;
        relayout();
    }

    /**
     * Rebuild content/tab widgets in place <b>preserving the current scroll offset</b> — used for an
     * in-place content change like expanding/collapsing a card detail drawer, where resetting scroll to
     * the top (as {@link #refresh()} does) would be jarring.
     */
    protected void rebuild() {
        clearFocus();
        relayout();
    }

    // ---- Rendering ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderer.begin(context);

        // Custom backdrop only. Do not call Screen.renderBackground(): in 1.21.11 it may request the
        // vanilla blur pass, and other screen hooks can already have blurred this frame.
        renderer.fill(0, 0, this.width, this.height, Theme.BACKDROP_VEIL);

        // Soft feathered shadow + large anti-aliased texture panel. The procedural layers are fallbacks.
        renderer.softShadow(layout.panelX() + 6, layout.panelY() + 7, layout.panelW(), layout.panelH(), 18,
                Theme.PANEL_SHADOW);
        renderer.gradientV(layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(),
                Theme.PANEL_TOP, Theme.PANEL_BOTTOM);
        renderer.nineSlice(UiTextures.PANEL, layout.panelX(), layout.panelY(),
                layout.panelW(), layout.panelH(), UiTextures.PANEL_W, UiTextures.PANEL_H,
                UiTextures.PANEL_INSET, 18);
        renderer.border(layout.panelX(), layout.panelY(), layout.panelW(), layout.panelH(), Theme.BORDER);

        drawHeader();
        drawRail();
        drawContent(mouseX, mouseY);
        drawScrollbar(mouseX, mouseY);

        for (Widget w : tabWidgets) {
            w.render(renderer, mouseX, mouseY);
        }
        if (focusedWidget != null && focusedWidget.textInputFocused() && focusedWidget.focused()
                && Minecraft.getInstance().gui.screen() == this) {
            int[] bounds = focusedWidget.textInputBounds();
            if (bounds != null) {
                int x0 = Math.max(bounds[0], viewport.x()), y0 = Math.max(bounds[1], viewport.y());
                int x1 = Math.min(bounds[2], viewport.x() + viewport.width());
                int y1 = Math.min(bounds[3], viewport.y() + viewport.height());
                if (x1 > x0 && y1 > y0)
                    Minecraft.getInstance().textInputManager().setTextInputArea(x0, y0, x1, y1);
            }
        }
    }

    private void drawHeader() {
        renderer.gradientV(layout.panelX() + 2, layout.panelY() + 2, layout.panelW() - 4, layout.headerH(),
                Theme.HEADER_TOP, Theme.HEADER_BOTTOM);
        renderer.fill(layout.panelX() + 12, layout.headerBottom() - 1, layout.panelW() - 24, 1, 0x5545D0C0);

        // Bundled logo texture, with a procedural accent square as the fallback if textures are off.
        int logo = 24;
        int lx = layout.panelX() + 12;
        int ly = layout.panelY() + Math.max(1, (layout.headerH() - logo) / 2);
        renderer.roundedRect(lx, ly, logo, logo, 7, 0x3345D0C0);
        renderer.texture(UiTextures.LOGO, lx, ly, logo, logo, UiTextures.LOGO_W, UiTextures.LOGO_H);
        // Smooth, slightly enlarged title via the matrix-scaled TextRenderer path (not the bitmap font).
        float titleScale = 1.4f;
        int th = renderer.lineHeightScaled(titleScale);
        int cy = layout.panelY() + Math.max(1, (layout.headerH() - th) / 2);
        renderer.textScaled("Veltraph", lx + logo + 8, cy, Theme.TITLE, true, titleScale);
    }

    private void drawRail() {
        renderer.gradientV(layout.railX() + 2, layout.railY() + 2, layout.railW() - 4, layout.railH() - 4,
                Theme.RAIL_TOP, Theme.RAIL_BOTTOM);
        renderer.fill(layout.railRight(), layout.railY() + 8, 1, layout.railH() - 16, 0x552A3243);

        List<RailItem> items = railItems();
        railRowBounds.clear();
        int rowH = TextLayout.railRowHeight(layout.railH(),items.size());
        int y = layout.railY() + 10;
        int rowX = layout.railX() + 7;
        int rowW = layout.railW() - 14;
        for (int i = 0; i < items.size(); i++) {
            RailItem item = items.get(i);
            if (y + rowH > layout.panelBottom() - 2) {
                break;
            }
            railRowBounds.add(new int[]{rowX, y - 2, rowW, rowH});
            if (item.active()) {
                renderer.roundedRect(layout.railX() + 7, y - 2, layout.railW() - 14, rowH, 9, Theme.RAIL_SELECTED);
                renderer.nineSlice(UiTextures.RAIL, layout.railX() + 7, y - 2, layout.railW() - 14, rowH,
                        UiTextures.RAIL_W, UiTextures.RAIL_H, UiTextures.RAIL_INSET, 9);
                renderer.roundedRect(layout.railX() + 8, y + 3, 3, rowH - 6, 2, Theme.ACCENT);
            }
            int glyph = 13;
            int iconY = y + (renderer.lineHeight() - glyph) / 2;
            drawCategoryIcon(layout.railX() + 13, iconY, glyph, item.active(), i);
            int textColor = item.active() ? Theme.TEXT : Theme.TEXT_DISABLED;
            renderer.text(TextLayout.fit(item.label(),layout.railW()-42,renderer::textWidth), layout.railX() + 13 + glyph + 7, y + 1, textColor, false);
            y += rowH;
        }
    }

    /** Texture-backed category icon from the bundled atlas (with a tiny procedural dot fallback). */
    private void drawCategoryIcon(int x, int y, int size, boolean active, int index) {
        int u = (index % UiTextures.ICON_COUNT) * UiTextures.ICON_SIZE;
        // Fallback marker under the texture (shows if the atlas is unavailable).
        renderer.fill(x + size / 2 - 1, y + size / 2 - 1, 2, 2, active ? Theme.ACCENT : Theme.TEXT_DISABLED);
        renderer.textureRegion(UiTextures.ICONS, x, y, size, size,
                u, 0, UiTextures.ICON_SIZE, UiTextures.ICON_SIZE, UiTextures.ICONS_ATLAS_W, UiTextures.ICON_SIZE);
    }

    private void drawContent(int mouseX, int mouseY) {
        renderer.pushScissor(viewport.x(), viewport.y(), viewport.width(), viewport.height());
        for (Widget w : contentWidgets) {
            if (viewport.rowVisible(w.y(), w.height())) {
                w.render(renderer, mouseX, mouseY);
            }
        }
        renderer.popScissor();
    }

    private void drawScrollbar(int mouseX, int mouseY) {
        if (!viewport.canScroll()) {
            return;
        }
        int sx = layout.scrollbarX();
        renderer.fill(sx, viewport.y(), layout.scrollbarW(), viewport.height(), Theme.SCROLL_TRACK);
        int[] thumb = viewport.thumb(sx, layout.scrollbarW());
        if (thumb != null) {
            boolean hover = mouseX >= sx && mouseX < sx + layout.scrollbarW()
                    && mouseY >= thumb[1] && mouseY < thumb[1] + thumb[3];
            renderer.roundedRect(thumb[0], thumb[1], thumb[2], thumb[3], thumb[2] / 2,
                    hover ? Theme.SCROLL_THUMB_HOVER : Theme.SCROLL_THUMB);
            // Texture-backed thumb (nine-slice pill) over the procedural fallback.
            renderer.nineSlice(UiTextures.THUMB, thumb[0], thumb[1], thumb[2], thumb[3],
                    UiTextures.THUMB_W, UiTextures.THUMB_H, UiTextures.THUMB_INSET, thumb[2] / 2);
            if (hover) {
                renderer.roundedRect(thumb[0], thumb[1], thumb[2], thumb[3], thumb[2] / 2, 0x3045D0C0);
            }
        }
    }

    // ---- Input ----

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button=NativeUiInput.widgetMouseButton(click.button());
        if(layout==null||viewport==null||Minecraft.getInstance().gui.screen()!=this)return false;
        if(button<0)return super.mouseClicked(click,doubled);
        if(button==0&&viewport.canScroll()&&mouseX>=layout.scrollbarX()&&mouseX<layout.scrollbarX()+layout.scrollbarW()
            &&mouseY>=viewport.y()&&mouseY<viewport.y()+viewport.height()){
            clearFocus();int[] thumb=viewport.thumb(layout.scrollbarX(),layout.scrollbarW());
            scrollbarGrab=mouseY>=thumb[1]&&mouseY<thumb[1]+thumb[3]?mouseY-thumb[1]:thumb[3]/2.0;
            setScrollbarPosition(mouseY-scrollbarGrab);scrollbarDragging=true;return true;
        }

        for (Widget w : tabWidgets) {
            if (w.mouseClicked(mouseX, mouseY, button)) {
                clearFocus();
                return true;
            }
        }
        // Category rail: clicking a row switches the displayed content (left button only).
        if (button == 0) {
            for (int i = 0; i < railRowBounds.size(); i++) {
                int[] b = railRowBounds.get(i);
                if (mouseX >= b[0] && mouseX < b[0] + b[2] && mouseY >= b[1] && mouseY < b[1] + b[3]) {
                    clearFocus();
                    onRailSelected(i);
                    return true;
                }
            }
        }
        // Content clicks are gated by the viewport: scrolled-off / clipped rows never receive input.
        for (Widget w : contentWidgets) {
            if (viewport.clickReaches(w, mouseX, mouseY) && w.mouseClicked(mouseX, mouseY, button)) {
                if(Minecraft.getInstance().gui.screen()!=this||!contentWidgets.contains(w)){clearFocus();return true;}
                if(w.keyboardNavigable()){if(focusedWidget!=null&&focusedWidget!=w)focusedWidget.blur();if(!w.focused())w.focus();focusedWidget=w.focused()?w:null;}
                else if(focusedWidget!=null&&focusedWidget!=w)clearFocus();
                syncTextInput();return true;
            }
        }
        clearFocus();
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if(layout==null||viewport==null||Minecraft.getInstance().gui.screen()!=this)return false;
        if(input.isCycleFocus()&&moveKeyboardFocus(input.hasShiftDown()?-1:1))return true;
        if(focusedWidget!=null){
            if(input.isSelectAll()&&focusedWidget.selectAllText())return true;
            if(input.isCopy()||input.isCut()){String selection=focusedWidget.selectedText();if(selection!=null){Minecraft.getInstance().keyboardHandler.setClipboard(selection);if(input.isCut())focusedWidget.deleteSelectedText();return true;}}
            if(input.isPaste()&&focusedWidget.replaceSelectedText(Minecraft.getInstance().keyboardHandler.getClipboard()))return true;
            if(input.isConfirmation()&&focusedWidget.activateKeyboard())return true;
            if(input.isLeft()&&focusedWidget.stepKeyboard(-1)||input.isRight()&&focusedWidget.stepKeyboard(1))return true;
        }
        if (focusedWidget != null && focusedWidget.keyPressed(input.key(), input.keycode(), input.modifiers())) {
            if (!focusedWidget.focused()) {
                focusedWidget = null;
            }
            syncTextInput();return true;
        }
        if(input.isUp()&&moveKeyboardFocus(-1)||input.isDown()&&moveKeyboardFocus(1))return true;
        return super.keyPressed(input);
    }

    private boolean moveKeyboardFocus(int direction){
        var targets=new ArrayList<Widget>();for(var w:tabWidgets)if(w.keyboardNavigable())targets.add(w);
        for(var w:contentWidgets)if(w.keyboardNavigable()&&viewport.contains(w.x()+w.width()/2.0,w.y()+w.height()/2.0))targets.add(w);
        if(targets.isEmpty())return false;int old=targets.indexOf(focusedWidget),next=old<0?(direction<0?targets.size()-1:0):(old+(direction<0?-1:1)+targets.size())%targets.size();
        clearFocus();Widget target=targets.get(next);target.focus();if(target.focused())focusedWidget=target;syncTextInput();return true;
    }

    // One synchronous SDK resubmission belongs to the retired editor's composition.
    private Widget textInputWidget;
    private boolean suppressFocusPreedit;

    @Override
    public boolean preeditUpdated(PreeditEvent input) {
        if (Minecraft.getInstance().gui.screen() != this || focusedWidget == null
                || !focusedWidget.textInputFocused() || !focusedWidget.focused()) return false;
        if (suppressFocusPreedit) return false;
        return focusedWidget.preeditText(input == null ? "" : input.fullText());
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if(Minecraft.getInstance().gui.screen()!=this)return false;
        if (focusedWidget != null && input.isAllowedChatCharacter()
                && focusedWidget.codepointTyped(input.codepoint(), 0)) {
            return true;
        }
        return super.charTyped(input);
    }

    public void clearFocus() {
        if (focusedWidget != null) {
            focusedWidget.blur();
            focusedWidget = null;
        }
        textInputWidget = null;
        Minecraft.getInstance().onTextInputFocusChange(this, false);
    }

    private void syncTextInput() {
        var client = Minecraft.getInstance();
        Widget desired = client.gui.screen() == this && focusedWidget != null
                && focusedWidget.textInputFocused() && focusedWidget.focused() ? focusedWidget : null;
        if (desired == textInputWidget) return;
        // Stop only this screen's SDK owner, so SDL clears composition before another editor starts.
        client.onTextInputFocusChange(this, false);
        textInputWidget = desired;
        if (desired != null) {
            // Minecraft synchronously resubmits KeyboardHandler's last event on start. It may belong
            // to the old editor; accept only fresh subsequent callbacks for this new draft.
            suppressFocusPreedit = true;
            try { client.onTextInputFocusChange(this, true); }
            finally { suppressFocusPreedit = false; }
        }
    }

    private void setScrollbarPosition(double top){
        int[] thumb=viewport.thumb(layout.scrollbarX(),layout.scrollbarW());if(thumb==null)return;
        int travel=viewport.height()-thumb[3];int next=travel<=0?0:(int)Math.round((top-viewport.y())*viewport.maxScroll()/travel);
        scroll=Math.max(0,Math.min(viewport.maxScroll(),next));relayout();
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){
        if(Minecraft.getInstance().gui.screen()==this&&scrollbarDragging&&Double.isFinite(event.y())&&NativeUiInput.widgetMouseButton(event.button())==0){
            setScrollbarPosition(event.y()-scrollbarGrab);scrollbarDragging=true;return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event){
        if(scrollbarDragging&&NativeUiInput.widgetMouseButton(event.button())==0){scrollbarDragging=false;return true;}
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if(Minecraft.getInstance().gui.screen()!=this)return false;
        if (viewport != null && viewport.canScroll()&&Double.isFinite(verticalAmount)&&Double.isFinite(mouseX)&&Double.isFinite(mouseY)) {
            scroll = Math.max(0, Math.min(viewport.maxScroll(), scroll - (int) (verticalAmount * 16)));
            relayout();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** A category rail entry. */
    public record RailItem(String label, boolean active) {
    }
}
