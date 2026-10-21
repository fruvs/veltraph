package dev.veltraph.client.fishing;

import java.util.OptionalInt;
import java.util.Optional;
import java.util.regex.Pattern;

/** Current plus overflow mana, as recovered from original MaxConstants3's action-bar parser. */
public final class FishingManaParser {
    private static final Pattern MANA=Pattern.compile("(?<![\\d,./+\\-])([\\d,]+)\\s*/\\s*([\\d,]+)\\s*✎(?:\\s*Mana)?");
    private static final Pattern OVERFLOW=Pattern.compile("([\\d,]+)[ʬ\uE017][ⓩⓄ]?");
    private static final Pattern NUMBER=Pattern.compile("(?:\\d+|\\d{1,3}(?:,\\d{3})+)");
    private FishingManaParser() {}
    public record Mana(int current,int maximum) {}
    public static Optional<Mana> detailed(String text){
        if(text==null)return Optional.empty();var normalized=text.replaceAll("§.","");
        var m=MANA.matcher(normalized);if(!m.find())return Optional.empty();
        String currentText=m.group(1),maximumText=m.group(2);int manaEnd=m.end();
        if(m.find()||!NUMBER.matcher(currentText).matches()||!NUMBER.matcher(maximumText).matches())return Optional.empty();
        try{
            int current=Integer.parseInt(currentText.replace(",","")),maximum=Integer.parseInt(maximumText.replace(",",""));
            int overflow=0;long glyphs=normalized.chars().filter(c->c=='ʬ'||c=='\uE017').count();
            if(glyphs!=0){
                var extra=OVERFLOW.matcher(normalized);
                if(glyphs!=1||!extra.find()||extra.start()<manaEnd||!normalized.substring(manaEnd,extra.start()).isBlank()||!NUMBER.matcher(extra.group(1)).matches())return Optional.empty();
                overflow=Integer.parseInt(extra.group(1).replace(",",""));
            }
            return maximum<1?Optional.empty():Optional.of(new Mana(Math.addExact(current,overflow),maximum));
        }catch(IllegalArgumentException|ArithmeticException ignored){return Optional.empty();}
    }
    public static OptionalInt parse(String text){var value=detailed(text);return value.isEmpty()?OptionalInt.empty():OptionalInt.of(value.get().current());}
}
