package dev.veltraph.client.ui.widget;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToggleButtonTest {

    @Test
    void clickTogglesBackingValue() {
        AtomicBoolean value = new AtomicBoolean(false);
        ToggleButton t = new ToggleButton(0, 0, 120, 14, "Drop And Turn", value::get, value::set);

        assertTrue(t.mouseClicked(10, 7, 0));
        assertTrue(value.get());
        assertTrue(t.mouseClicked(10, 7, 0));
        assertFalse(value.get());
    }

    @Test
    void clickOutsideIsIgnored() {
        AtomicBoolean value = new AtomicBoolean(false);
        ToggleButton t = new ToggleButton(0, 0, 120, 14, "X", value::get, value::set);
        assertFalse(t.mouseClicked(500, 500, 0));
        assertFalse(value.get());
    }
}
