package dev.veltraph.client.chocolate;
import dev.veltraph.client.garden.visitor.history.mc.McVisitorHistoryPacketObserver;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Modifier;
import static org.junit.jupiter.api.Assertions.*;
class OwnedGuiNativeSourceBoundaryTest {
    @Test void publicOutOfCallDataCannotMintActualNativeInputOrPacketCapsules(){assertTrue(McVisitorHistoryPacketObserver.existing(null).isEmpty());assertNull(McVisitorHistoryPacketObserver.beginGuiInput(new Object(),new Object(),new Object(),2,4,0,net.minecraft.world.inventory.ContainerInput.PICKUP));assertNull(McVisitorHistoryPacketObserver.beginSend(null,null,null,false));assertNull(McVisitorHistoryPacketObserver.beginReceive(new Object(),null));}
    @Test void ownedInspectionAndActualReceiptConstructorsAreNotPublicAuthorityDtos(){for(var type:new Class<?>[]{McVisitorHistoryPacketObserver.OwnedMenuInput.class,McVisitorHistoryPacketObserver.OwnedInputInspection.class,McVisitorHistoryPacketObserver.RequestReceipt.class,McVisitorHistoryPacketObserver.ContentReceipt.class,McVisitorHistoryPacketObserver.AppliedStateReceipt.class})for(var ctor:type.getDeclaredConstructors())assertTrue(Modifier.isPrivate(ctor.getModifiers()),type.getName());}
}
