package dev.veltraph.client.ui.widget;

import dev.veltraph.client.ui.Theme;
import dev.veltraph.client.ui.render.UiRenderer;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A labeled selector that cycles through a fixed set of values (left-click = next, right-click =
 * previous). Used for layout/profile mode selectors and enum config rows. Backed by a getter/setter
 * so it reflects and mutates live state. Pure.
 *
 * @param <T> value type
 */
public final class EnumCycler<T> extends AbstractWidget {

    private final String label;
    private final List<T> values;
    private final Supplier<T> getter;
    private final Consumer<T> setter;
    private final Function<T, String> display;

    public EnumCycler(int x, int y, int width, int height,
                      String label, List<T> values,
                      Supplier<T> getter, Consumer<T> setter, Function<T, String> display) {
        super(x, y, width, height);
        this.label = label;
        this.values = List.copyOf(values);
        this.getter = getter;
        this.setter = setter;
        this.display = display;
    }

    /** Convenience for an enum class. */
    public static <E extends Enum<E>> EnumCycler<E> ofEnum(int x, int y, int width, int height,
                                                           String label, Class<E> type,
                                                           Supplier<E> getter, Consumer<E> setter,
                                                           Function<E, String> display) {
        return new EnumCycler<>(x, y, width, height, label, List.of(type.getEnumConstants()),
                getter, setter, display);
    }

    public T current() {
        return getter.get();
    }

    @Override
    public void render(UiRenderer r, int mouseX, int mouseY) {
        boolean hover = keyboardFocus || isMouseOver(mouseX, mouseY);
        if (hover) {
            r.roundedRect(x, y, width, height, 8, Theme.HOVER_OVERLAY);
        }
        int cy = y + (height - r.lineHeight()) / 2;

        // A pill that holds the current value, flanked by chevrons, in the accent color.
        String value = dev.veltraph.client.ui.TextLayout.fit(display.apply(getter.get()), Math.max(20,width/2-30),r::textWidth);
        int vw = r.textWidth(value);
        int pillW = vw + 30;
        int pillX = x + width - pillW - 4;
        r.text(dev.veltraph.client.ui.TextLayout.fit(label,pillX-x-16,r::textWidth),x+8,cy,Theme.TEXT_MUTED,false);
        int pillY = y + 2;
        int pillH = height - 4;
        r.roundedRect(pillX, pillY, pillW, pillH, pillH / 2, Theme.PANEL_RAISED);
        r.text("<", pillX + 7, cy, hover ? Theme.ACCENT_BRIGHT : Theme.ACCENT, false);
        r.text(value, pillX + (pillW - vw) / 2, cy, Theme.TEXT, false);
        r.text(">", pillX + pillW - 12, cy, hover ? Theme.ACCENT_BRIGHT : Theme.ACCENT, false);
    }

    private boolean keyboardFocus;
    @Override public boolean keyboardNavigable(){return true;}
    @Override public void focus(){keyboardFocus=true;}
    @Override public void blur(){keyboardFocus=false;}
    @Override public boolean focused(){return keyboardFocus;}
    @Override public boolean activateKeyboard(){return stepKeyboard(1);}
    @Override public boolean stepKeyboard(int direction){if(values.isEmpty())return false;int index=values.indexOf(getter.get());if(index<0)index=0;setter.accept(values.get((index+(direction<0?-1:1)+values.size())%values.size()));return true;}

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!isMouseOver(mouseX, mouseY) || values.isEmpty()) {
            return false;
        }
        int idx = values.indexOf(getter.get());
        if (idx < 0) {
            idx = 0;
        }
        if (button == 0) {
            idx = (idx + 1) % values.size();
        } else if (button == 1) {
            idx = (idx - 1 + values.size()) % values.size();
        } else {
            return false;
        }
        T value = values.get(idx);
        dev.veltraph.client.log.VeltraphLog.verbose("UI.cycler", "'" + label + "' -> " + display.apply(value));
        setter.accept(value);
        return true;
    }
}
