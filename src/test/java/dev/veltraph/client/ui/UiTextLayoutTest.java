package dev.veltraph.client.ui;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
class UiTextLayoutTest {
 @Test void largeSupplementaryValueHasBoundedMeasurementsAndNoSplit(){var calls=new AtomicInteger();var units=new AtomicInteger();String text="😀".repeat(32000)+"end";String result=UiTextLayout.suffix(text,20,s->{calls.incrementAndGet();units.addAndGet(s.length());return s.codePointCount(0,s.length());});assertTrue(result.startsWith("..."));assertTrue(result.endsWith("end"));assertTrue(result.codePointCount(0,result.length())<=20);assertFalse(Character.isLowSurrogate(result.charAt(3)));assertTrue(calls.get()<=13);assertTrue(units.get()<30000);}
 @Test void normalAndTinyValuesPreserveMeaning(){assertEquals("hello",UiTextLayout.suffix("hello",10,String::length));assertEquals("...ld",UiTextLayout.suffix("hello world",5,String::length));assertEquals("",UiTextLayout.suffix("hello",2,String::length));}
}
