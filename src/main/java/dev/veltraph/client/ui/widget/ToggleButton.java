package dev.veltraph.client.ui.widget;

import dev.veltraph.client.ui.Theme;
import dev.veltraph.client.ui.UiTextures;
import dev.veltraph.client.ui.render.UiRenderer;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A labeled on/off toggle row backed by a getter/setter pair (e.g. a boolean {@code ConfigService}
 * key). Renders the label on the left and a pill switch on the right. Pure.
 */
public final class ToggleButton extends AbstractWidget {

    private final String label;
    private final BooleanSupplier getter;
    private final Consumer<Boolean> setter;

    public ToggleButton(int x, int y, int width, int height,
                        String label, BooleanSupplier getter, Consumer<Boolean> setter) {
        super(x, y, width, height);
        this.label = label;
        this.getter = getter;
        this.setter = setter;
    }

    private static final int PILL_W = 34;
    private static final int PILL_H = 16;

    @Override
    public void render(UiRenderer r, int mouseX, int mouseY) {
        boolean on = getter.getAsBoolean();
        if (keyboardFocus || isMouseOver(mouseX, mouseY)) {
            r.roundedRect(x, y, width, height, 8, Theme.HOVER_OVERLAY);
        }
        r.text(dev.veltraph.client.ui.TextLayout.fit(label,width-PILL_W-20,r::textWidth), x + 8, y + (height - r.lineHeight()) / 2, on ? Theme.TEXT : Theme.TEXT_MUTED, false);

        int pillX = x + width - PILL_W - 4;
        int pillY = y + (height - PILL_H) / 2;
        r.roundedRect(pillX, pillY, PILL_W, PILL_H, PILL_H / 2, on ? Theme.ON : Theme.OFF);
        // Texture-backed pill frame + sheen over the coloured track.
        r.nineSlice(UiTextures.TOGGLE, pillX, pillY, PILL_W, PILL_H,
                UiTextures.TOGGLE_W, UiTextures.TOGGLE_H, UiTextures.TOGGLE_INSET, PILL_H / 2);
        int knob = PILL_H - 4;
        int knobX = on ? pillX + PILL_W - knob - 2 : pillX + 2;
        r.roundedRect(knobX, pillY + 2, knob, knob, knob / 2, Theme.TEXT);
    }

    private boolean keyboardFocus;
    @Override public boolean keyboardNavigable(){return true;}
    @Override public void focus(){keyboardFocus=true;}
    @Override public void blur(){keyboardFocus=false;}
    @Override public boolean focused(){return keyboardFocus;}
    @Override public boolean activateKeyboard(){setter.accept(!getter.getAsBoolean());return true;}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver(mouseX, mouseY)) {
            boolean next = !getter.getAsBoolean();
            dev.veltraph.client.log.VeltraphLog.verbose("UI.toggle", "'" + label + "' -> " + next);
            setter.accept(next);
            return true;
        }
        return false;
    }
}
