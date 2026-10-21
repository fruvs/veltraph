package dev.veltraph.client.mining.vein;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Map;

/** Installed raw readers. The private capture rejects unknown classes before using these readers. */
public final class VeinWatchGraphFields {
    private VeinWatchGraphFields() {}
    public interface Literal { String veltraph$watchText(); }
    public interface Lore { List<?> veltraph$watchLines(); }
    public interface Custom { CompoundTag veltraph$watchTag(); }
    public interface Compound { Map<?,?> veltraph$watchTags(); }
    public interface NbtList { List<?> veltraph$watchList(); }
    public interface Bytes { byte[] veltraph$watchBytes(); }
    public interface Ints { int[] veltraph$watchInts(); }
    public interface Longs { long[] veltraph$watchLongs(); }
    public interface Color { int veltraph$watchColorValue(); String veltraph$watchColorName(); }
    public interface Font { Identifier veltraph$watchFontId(); }
    public interface NativeStyle {
        TextColor veltraph$watchColor(); Integer veltraph$watchShadow();
        Boolean veltraph$watchBold(); Boolean veltraph$watchItalic(); Boolean veltraph$watchUnderlined();
        Boolean veltraph$watchStrikethrough(); Boolean veltraph$watchObfuscated();
        ClickEvent veltraph$watchClick(); HoverEvent veltraph$watchHover(); String veltraph$watchInsertion(); FontDescription veltraph$watchFont();
    }
    public interface Enchantments { it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap<?> veltraph$watchEnchantments(); }
}
