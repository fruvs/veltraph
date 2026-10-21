package dev.veltraph.client.capture.tool;

import net.minecraft.nbt.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the exact scalar reader used for chosen native paths, without granting native capture authority. */
class SelectedWateringNativeNumberTest {
    @Test void actualIntegerTagsRetainValuesRatherThanUnknownFieldSentinels(){assertEquals(37,SelectedToolNativeObservation.selectedNumber(IntTag.valueOf(37)).getAsInt());assertEquals(-7,SelectedToolNativeObservation.selectedNumber(ByteTag.valueOf((byte)-7)).getAsInt());assertEquals(32000,SelectedToolNativeObservation.selectedNumber(ShortTag.valueOf((short)32000)).getAsInt());assertEquals(Long.MAX_VALUE,SelectedToolNativeObservation.selectedNumber(LongTag.valueOf(Long.MAX_VALUE)).getAsLong());}
    @Test void actualFiniteNativeDecimalsRetainValues(){assertEquals(2.25,SelectedToolNativeObservation.selectedNumber(FloatTag.valueOf(2.25f)).getAsDouble());assertEquals(-4.125,SelectedToolNativeObservation.selectedNumber(DoubleTag.valueOf(-4.125)).getAsDouble());}
    @Test void nonfiniteNativeNumbersAreUnavailable(){assertNull(SelectedToolNativeObservation.selectedNumber(FloatTag.valueOf(Float.NaN)));assertNull(SelectedToolNativeObservation.selectedNumber(DoubleTag.valueOf(Double.POSITIVE_INFINITY)));assertNull(SelectedToolNativeObservation.selectedNumber(DoubleTag.valueOf(Double.NEGATIVE_INFINITY)));}
    @Test void stringsArraysAndObjectsAreNotNumericalObservations(){assertNull(SelectedToolNativeObservation.selectedNumber(StringTag.valueOf("8675309")));assertNull(SelectedToolNativeObservation.selectedNumber(new CompoundTag()));assertNull(SelectedToolNativeObservation.selectedNumber(new IntArrayTag(new int[]{1,2})));}
}
