package dev.veltraph.client.garden.visitor;
import java.math.*;
import java.util.*;
import java.util.regex.Pattern;

/** Explicit per-unit quotes only. Ambiguous total-price lore cannot authorize serving. */
public final class VisitorUnitQuote {
    private static final Pattern QUOTE=Pattern.compile("^(?:Unit Price: ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) Coins|Price: ([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]+)?) Coins each)$",Pattern.CASE_INSENSITIVE);
    private VisitorUnitQuote(){}
    public static OptionalLong read(List<String> lore){
        Long quote=null;
        for(String line:lore){
            var m=QUOTE.matcher(BazaarPriceParser.stripColorCodes(line).trim());if(!m.matches())continue;
            try{
                long amount=new BigDecimal((m.group(1)==null?m.group(2):m.group(1)).replace(",",""))
                        .setScale(0,RoundingMode.CEILING).longValueExact();
                if(quote!=null)return OptionalLong.empty();quote=amount;
            }catch(ArithmeticException|NumberFormatException invalid){return OptionalLong.empty();}
        }
        return quote==null?OptionalLong.empty():OptionalLong.of(quote);
    }
}
