package dev.veltraph.client.ui.widget;

import dev.veltraph.client.ui.render.UiRenderer;

/**
 * A positioned, clickable UI element. Pure (renders through {@link UiRenderer}, no Minecraft
 * dependency) so widgets are unit-testable.
 */
public interface Widget {

    void render(UiRenderer r, int mouseX, int mouseY);

    /** Handle a click; return true if consumed. {@code button} 0 = left, 1 = right. */
    boolean mouseClicked(double mouseX, double mouseY, int button);

    /** Handle a key press while focused; return true if consumed. */
    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    /** Handle typed text while focused; return true if consumed. */
    default boolean charTyped(char chr, int modifiers) {
        return false;
    }

    /** Native Unicode codepoint, preserving the legacy char contract for BMP-only widgets. */
    default boolean codepointTyped(int codepoint,int modifiers){return codepoint>=0&&codepoint<=Character.MAX_VALUE&&charTyped((char)codepoint,modifiers);}
    default boolean keyboardNavigable(){return false;}
    default boolean textInputFocused(){return false;}
    default boolean preeditText(String text){return false;}
    /** Text value rectangle as GUI x0,y0,x1,y1 corners, or null for non-editors. */
    default int[] textInputBounds(){return null;}
    default void focus(){}
    default boolean activateKeyboard(){return false;}
    default boolean stepKeyboard(int direction){return false;}
    default boolean selectAllText(){return false;}
    default String selectedText(){return null;}
    default boolean replaceSelectedText(String value){return false;}
    default boolean deleteSelectedText(){return false;}

    /** Clear keyboard focus/editing state. */
    default void blur() {
    }

    /** Whether this widget is currently focused for keyboard input. */
    default boolean focused() {
        return false;
    }

    boolean isMouseOver(double mouseX, double mouseY);

    /** Screen-space bounds (used by the scroll viewport for culling and clip-aware dispatch). */
    int x();

    int y();

    int width();

    int height();
}
