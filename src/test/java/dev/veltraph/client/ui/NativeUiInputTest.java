package dev.veltraph.client.ui;
import com.mojang.blaze3d.platform.InputConstants;
import dev.veltraph.client.ui.widget.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
class NativeUiInputTest {
 @Test void actualNativeButtonsRespectPureContract(){assertEquals(0,NativeUiInput.widgetMouseButton(InputConstants.MOUSE_BUTTON_LEFT));assertEquals(1,NativeUiInput.widgetMouseButton(InputConstants.MOUSE_BUTTON_RIGHT));assertEquals(2,NativeUiInput.widgetMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE));assertEquals(-1,NativeUiInput.widgetMouseButton(0));assertEquals(-1,NativeUiInput.widgetMouseButton(99));}
 @Test void realLeftAndRightProduceDistinctCyclerDirections(){var value=new AtomicInteger(0);var widget=new EnumCycler<Integer>(0,0,100,20,"Three",List.of(0,1,2),value::get,value::set,Object::toString);assertTrue(widget.mouseClicked(5,5,NativeUiInput.widgetMouseButton(InputConstants.MOUSE_BUTTON_LEFT)));assertEquals(1,value.get());assertTrue(widget.mouseClicked(5,5,NativeUiInput.widgetMouseButton(InputConstants.MOUSE_BUTTON_RIGHT)));assertEquals(0,value.get());assertFalse(widget.mouseClicked(5,5,NativeUiInput.widgetMouseButton(0)));}
 @Test void keyboardFocusActivatesWithoutChangingMouseContracts(){var clicks=new AtomicInteger();var widget=TextButton.of(0,0,100,20,"Test",0,clicks::incrementAndGet);widget.focus();assertTrue(widget.focused());assertTrue(widget.activateKeyboard());assertEquals(1,clicks.get());widget.blur();assertFalse(widget.focused());assertTrue(widget.mouseClicked(5,5,0));assertFalse(widget.mouseClicked(5,5,1));assertEquals(2,clicks.get());}
}
