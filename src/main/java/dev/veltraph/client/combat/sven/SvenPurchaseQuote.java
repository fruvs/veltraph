package dev.veltraph.client.combat.sven;

import java.util.List;
import java.util.OptionalLong;
import java.math.BigDecimal;
import java.math.RoundingMode;

/** Bound source price-line selection; an absent, ambiguous or malformed quote never authorizes a buy. */
public final class SvenPurchaseQuote {
    private SvenPurchaseQuote(){}
    public static OptionalLong price(List<String> lore,String prefix){
        if(lore==null||prefix==null||prefix.isBlank())return OptionalLong.empty();
        var lines=lore.stream().map(s->s.replaceAll("§.","").strip()).filter(s->s.startsWith(prefix)).toList();if(lines.size()!=1)return OptionalLong.empty();
        var matcher=java.util.regex.Pattern.compile("^([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) coins?$").matcher(lines.getFirst().substring(prefix.length()).strip());
        if(!matcher.matches())return OptionalLong.empty();try{long value=new BigDecimal(matcher.group(1).replace(",","")).setScale(0,RoundingMode.CEILING).longValueExact();return value>=0?OptionalLong.of(value):OptionalLong.empty();}catch(ArithmeticException|NumberFormatException invalid){return OptionalLong.empty();}
    }
}
