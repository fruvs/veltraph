package dev.veltraph.client.ui;
import com.mojang.blaze3d.platform.InputConstants;
/** Native SDL mouse values are translated once to the existing pure widget 0/1/2 contract. */
public final class NativeUiInput {
    private NativeUiInput() {}
    public static int widgetMouseButton(int nativeButton){
        if(nativeButton==InputConstants.MOUSE_BUTTON_LEFT)return 0;
        if(nativeButton==InputConstants.MOUSE_BUTTON_RIGHT)return 1;
        if(nativeButton==InputConstants.MOUSE_BUTTON_MIDDLE)return 2;
        return -1;
    }
}
