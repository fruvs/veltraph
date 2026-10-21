package dev.veltraph.client.navigation;
import dev.veltraph.client.combat.slayer.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SlayerTitleSignalTest {
    @Test void titlesAreScopedToTheWorldThatReceivedThem() {
        Object first=new Object(),second=new Object();
        SlayerTitleSignal.receive(first,"§8§lASHEN");
        var before=SlayerTitleSignal.read(first);
        assertEquals(DaggerAttunement.Aspect.ASHEN,before.aspect());
        assertNull(SlayerTitleSignal.read(second).aspect());
        SlayerTitleSignal.receive(second,"CRYSTAL");
        assertNull(SlayerTitleSignal.read(first).aspect());
        assertEquals(DaggerAttunement.Aspect.CRYSTAL,SlayerTitleSignal.read(second).aspect());
        assertTrue(SlayerTitleSignal.read(second).serial()>before.serial());
    }
    @Test void unrelatedTextCannotAcknowledgeASwitch() {
        Object world=new Object();SlayerTitleSignal.receive(world,"SPIRIT");
        var before=SlayerTitleSignal.read(world);
        SlayerTitleSignal.receive(world,"Strike using ASHEN!");
        SlayerTitleSignal.receive(null,"ASHEN");
        assertEquals(before,SlayerTitleSignal.read(world));
    }
}
