package dev.veltraph.client.notification;
import dev.veltraph.client.config.*;
import java.util.*;

/** Local event dispatch for the existing Visual settings; never forwards chat or screenshots. */
public final class LocalNotifications {
    public enum Event { INSANE_DROP, AUTO_SELL, PEST_RESULTS, FISHING_BOSS, BANNED }
    public interface Sink { void title(String title);void sound();void beep(); }
    private final ConfigService config;private final Sink sink;
    private final Map<Event,Long> last=new EnumMap<>(Event.class);
    public LocalNotifications(ConfigService config,Sink sink) { this.config=config;this.sink=sink; }
    public void emit(Event event,long now) {
        if(!config.get(NotificationConfig.NOTIFY_ENABLED)||!config.get(key(event))||now-last.getOrDefault(event,Long.MIN_VALUE/2)<3000) return;
        last.put(event,now);
        if(config.get(NotificationConfig.NOTIFY_TITLE)) sink.title(event.name().replace('_',' '));
        if(config.get(NotificationConfig.NOTIFY_SOUND)) sink.sound();
        if(config.get(NotificationConfig.NOTIFY_OS_BEEP)) sink.beep();
    }
    public static ConfigKey<Boolean> key(Event e) {
        return switch(e) {
            case INSANE_DROP -> NotificationConfig.NOTIFY_INSANE_DROP;
            case AUTO_SELL -> NotificationConfig.NOTIFY_AUTO_SELL;
            case PEST_RESULTS -> NotificationConfig.NOTIFY_PEST_RESULTS;
            case FISHING_BOSS -> NotificationConfig.NOTIFY_FISHING_BOSS;
            case BANNED -> NotificationConfig.NOTIFY_BANNED;
        };
    }
    /** Conservative anchored system messages; player-chat text is not interpreted as an event. */
    public void systemMessage(String line,long now) {
        if(line==null) return;
        String s=line.replaceAll("§.","").strip();
        if(s.startsWith("RARE DROP!")||s.startsWith("CRAZY RARE DROP!")||s.startsWith("INSANE DROP!")) emit(Event.INSANE_DROP,now);
        if(s.equals("You spot a Golden Fish surface from beneath the lava!")) emit(Event.FISHING_BOSS,now);
    }
}
