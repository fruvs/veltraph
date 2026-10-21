package dev.veltraph.client.garden.jacob;

import dev.veltraph.client.game.LocationText;
import dev.veltraph.client.scheduling.ScheduledContestObservation;
import java.util.List;
import java.util.regex.Pattern;

/** Source Jacob sidebar block: header, immediate crop/countdown row, and raw ranking rows. */
public record JacobContestObservation(boolean active, String crop, long remainingSeconds,
                                     List<String> rankingRows) {
    public static final String HEADER="Jacob's Contest";
    private static final Pattern CROP_CLOCK=Pattern.compile("^(Wheat|Carrot|Potato|Pumpkin|Melon|Sugar Cane|Mushroom|Cactus|Cocoa Beans|Nether Wart) ([0-9]{1,2})m([0-9]{1,2})s$");
    public JacobContestObservation { rankingRows=List.copyOf(rankingRows); }
    public static JacobContestObservation inactive(){return new JacobContestObservation(false,"",0,List.of());}
    public boolean clockKnown(){return active&&!crop.isEmpty()&&remainingSeconds>=0;}
    /** Preserve the scheduler's existing public value contract, with stronger row attribution. */
    public ScheduledContestObservation scheduled(){return new ScheduledContestObservation(active,remainingSeconds);}
    public static JacobContestObservation read(List<String> sidebar){
        if(sidebar==null)return inactive();
        var rows=sidebar.stream().map(LocationText::stripColorCodes).map(String::trim).toList();
        int header=rows.indexOf(HEADER);
        if(header<0)return inactive();
        if(header!=rows.lastIndexOf(HEADER)||header+1>=rows.size())return new JacobContestObservation(true,"",-1,List.of());
        var match=CROP_CLOCK.matcher(rows.get(header+1));
        if(!match.matches())return new JacobContestObservation(true,"",-1,List.of());
        int seconds=Integer.parseInt(match.group(3));
        if(seconds>=60)return new JacobContestObservation(true,"",-1,List.of());
        // These rows are evidence for display/end notification, never claim eligibility or proceeds.
        var ranking=rows.subList(Math.min(header+2,rows.size()),Math.min(header+4,rows.size()));
        return new JacobContestObservation(true,match.group(1),Long.parseLong(match.group(2))*60+seconds,ranking);
    }
}
