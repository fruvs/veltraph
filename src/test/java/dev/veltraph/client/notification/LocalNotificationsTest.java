package dev.veltraph.client.notification;
import dev.veltraph.client.config.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class LocalNotificationsTest {
    @Test void honorsEventMechanismAndMasterTogglesWithDeduplication() {
        var config=new MapConfigService();var calls=new ArrayList<String>();
        var notifications=new LocalNotifications(config,new LocalNotifications.Sink() {
            public void title(String text) { calls.add("title"); }public void sound() { calls.add("sound"); }public void beep() { calls.add("beep"); }
        });
        notifications.systemMessage("Player: RARE DROP!",1000);assertTrue(calls.isEmpty());
        notifications.systemMessage("RARE DROP! item",1000);notifications.systemMessage("RARE DROP! item",1100);
        assertEquals(List.of("title","sound"),calls);
        config.set(NotificationConfig.NOTIFY_ENABLED,false);notifications.systemMessage("RARE DROP! item",5000);assertEquals(2,calls.size());
    }
}
