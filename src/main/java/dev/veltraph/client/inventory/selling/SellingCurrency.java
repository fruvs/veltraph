package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.gui.MenuSnapshot;
import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/** Fail-closed parsing of one configured sidebar wallet line. */
public final class SellingCurrency {
    private SellingCurrency() {}
    public static BigDecimal parse(List<String> lines,String requiredPrefix){
        if(requiredPrefix==null||requiredPrefix.isBlank())return null;
        Pattern pattern=Pattern.compile("^"+Pattern.quote(MenuSnapshot.clean(requiredPrefix))+"\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)(?: Coins)?$");
        BigDecimal amount=null;
        for(String raw:lines){String line=MenuSnapshot.clean(raw);if(!line.startsWith(MenuSnapshot.clean(requiredPrefix)))continue;
            var match=pattern.matcher(line);if(!match.matches()||amount!=null)return null;
            try{amount=new BigDecimal(match.group(1).replace(",",""));}catch(NumberFormatException e){return null;}}
        return amount;
    }
}
