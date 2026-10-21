package dev.veltraph.client.ui.widget;

import dev.veltraph.client.ui.Theme;
import dev.veltraph.client.ui.UiTextures;
import dev.veltraph.client.ui.render.UiRenderer;

import java.util.function.Supplier;

/**
 * A clickable text button. The label and fill color are supplied dynamically so the same button can
 * reflect live state (e.g. a Start/Stop toggle that shows ON/OFF). Pure.
 */
public final class TextButton extends AbstractWidget {

    private final Supplier<String> label;
    private final Supplier<Integer> fillColor;
    private final Runnable onClick;

    public TextButton(int x, int y, int width, int height,
                      Supplier<String> label, Supplier<Integer> fillColor, Runnable onClick) {
        super(x, y, width, height);
        this.label = label;
        this.fillColor = fillColor;
        this.onClick = onClick;
    }

    public static TextButton of(int x, int y, int width, int height, String label, int color, Runnable onClick) {
        return new TextButton(x, y, width, height, () -> label, () -> color, onClick);
    }

    @Override
    public void render(UiRenderer r, int mouseX, int mouseY) {
        boolean hover = keyboardFocus || isMouseOver(mouseX, mouseY);
        int radius = Math.max(6, height / 2);
        // Coloured base (carries Start/Stop/tab state colour + is the no-texture fallback).
        r.roundedRect(x, y, width, height, radius, fillColor.get());
        // Texture-backed frame + sheen over the coloured base.
        r.nineSlice(UiTextures.BUTTON, x, y, width, height,
                UiTextures.BUTTON_W, UiTextures.BUTTON_H, UiTextures.BUTTON_INSET, radius);
        if (hover) {
            r.roundedRect(x, y, width, height, radius, Theme.HOVER_OVERLAY);
        }
        String text = label.get();
        int tx = x + (width - r.textWidth(text)) / 2;
        int ty = y + (height - r.lineHeight()) / 2;
        r.text(text, tx, ty, Theme.TEXT, false);
    }

    private boolean keyboardFocus;
    @Override public boolean keyboardNavigable(){return true;}
    @Override public void focus(){keyboardFocus=true;}
    @Override public void blur(){keyboardFocus=false;}
    @Override public boolean focused(){return keyboardFocus;}
    @Override public boolean activateKeyboard(){onClick.run();return true;}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            dev.veltraph.client.log.VeltraphLog.verbose("UI.button", "click '" + label.get() + "'");
            onClick.run();
            return true;
        }
        return false;
    }
}
