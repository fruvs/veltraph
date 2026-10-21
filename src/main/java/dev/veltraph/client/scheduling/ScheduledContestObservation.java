package dev.veltraph.client.scheduling;

import dev.veltraph.client.game.LocationText;
import java.util.List;
import java.util.regex.Pattern;

/** Read-only Jacob sidebar evidence. An active header without a recognized clock is unready. */
public record ScheduledContestObservation(boolean active,long remainingSeconds) {
    private static final Pattern CLOCK=Pattern.compile("^(?:(?:Wheat|Carrot|Potato|Pumpkin|Melon|Sugar Cane|Mushroom|Cactus|Cocoa Beans|Nether Wart|Remaining|Time Left|Ends in)[: ]+)?([0-9]{1,2})m\\s*([0-9]{1,2})s$");
    public static ScheduledContestObservation read(List<String> sidebar){
        if(sidebar==null)return new ScheduledContestObservation(false,0);
        var clean=sidebar.stream().map(LocationText::stripColorCodes).map(String::trim).toList();
        if(clean.stream().noneMatch("Jacob's Contest"::equals))return new ScheduledContestObservation(false,0);
        long remaining=-1;
        for(String line:clean){var match=CLOCK.matcher(line);if(!match.matches())continue;
            long minutes=Long.parseLong(match.group(1)),seconds=Long.parseLong(match.group(2));
            if(seconds>=60)continue;long value=minutes*60+seconds;
            // Conflicting observed clocks cannot overstate the available contest window.
            remaining=remaining<0?value:Math.min(remaining,value);
        }
        return new ScheduledContestObservation(true,remaining);
    }
}
