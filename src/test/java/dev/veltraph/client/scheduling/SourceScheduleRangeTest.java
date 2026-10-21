package dev.veltraph.client.scheduling;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SourceScheduleRangeTest {
    @Test void hoursAndMinutesAreDistinctAndRestTruncatesWithOneMinuteFloor(){var random=new SplittableRandom(41);assertTrue(SourceScheduleRange.parse("1.5:2.25").cooldownMillis(random)>=5_400_000);for(int i=0;i<100;i++)assertEquals(60_000,SourceScheduleRange.parse("0.05:0.99").restMillis(random));assertEquals(600_000,SourceScheduleRange.parse("10.01:10.99").restMillis(random));}
    @Test void samplerRetainsHalfOpenRange(){var range=SourceScheduleRange.parse("10:30");var random=new SplittableRandom(7);for(int i=0;i<1000;i++){double value=range.sample(random);assertTrue(value>=10&&value<30);}}
    @Test void invalidRangeCannotSilentlyBecomeImmediateRest(){for(String value:new String[]{"", "1", "1:1", "3:2", "-1:3", "NaN:3", "1:Infinity", "1:2:3"})assertThrows(IllegalArgumentException.class,()->SourceScheduleRange.parse(value));}
}
