package dev.veltraph.client.skills;

import com.google.gson.*;
import dev.veltraph.client.config.AutoSellMode;
import dev.veltraph.client.inventory.selling.*;
import java.math.BigDecimal;
import java.util.*;

/** Explicit local binding of the readable stand → SkyBlock → cookie → potion-sale parent.
 * Original encrypted titles, prices, receipt text and cookie labels are not assumed. */
public record AlchemySaleProfile(int openerSlot,AlchemyFlowProfile.Item opener,String skyblockTitle,
                                 int cookieSlot,AlchemyFlowProfile.Item cookieButton,List<String> cookieLore,
                                 String saleTitle,String quoteLabel,BigDecimal unitProceeds,String receiptTemplate,
                                 String pursePrefix,String activeCookieLine,String cookieGrammar,int delayTicks,int timeoutTicks) {
    public AlchemySaleProfile {
        cookieLore=List.copyOf(cookieLore);
        for(String text:List.of(skyblockTitle,saleTitle,quoteLabel,pursePrefix))if(text==null||text.isBlank()||text.length()>256)throw new IllegalArgumentException("Explicit alchemy sale menu/quote/purse labels required");
        if(openerSlot<0||openerSlot>8||cookieSlot<0||cookieSlot>53||opener==null||cookieButton==null||cookieLore.isEmpty()||cookieLore.size()>32||cookieLore.stream().anyMatch(s->s==null||s.length()>512)||skyblockTitle.equals(saleTitle))throw new IllegalArgumentException("Distinct alchemy ingress contracts required");
        if(unitProceeds==null||unitProceeds.signum()<=0||unitProceeds.scale()>2||unitProceeds.precision()>18||timeoutTicks>600||delayTicks<1)throw new IllegalArgumentException("Bounded explicit alchemy proceeds and time required");
        if(activeCookieLine==null||cookieGrammar==null||activeCookieLine.isBlank()&&cookieGrammar.isBlank())throw new IllegalArgumentException("Fresh native cookie observation contract required");
        if(!cookieGrammar.isBlank())CookieEvidence.Grammar.parse(cookieGrammar);
        new SellingRuntime.Settings(AutoSellMode.NPC,saleTitle,"","","",-1,-1,receiptTemplate,3,unitProceeds,unitProceeds.multiply(BigDecimal.valueOf(3)),BigDecimal.ZERO,delayTicks,timeoutTicks);
    }
    public SellingRuntime.Settings settings(){return new SellingRuntime.Settings(AutoSellMode.NPC,saleTitle,"","","",-1,-1,receiptTemplate,3,unitProceeds,unitProceeds.multiply(BigDecimal.valueOf(3)),BigDecimal.ZERO,delayTicks,timeoutTicks);}
    public SellingRule rule(SellingObservation.Stack stack){return new SellingRule(AutoSellMode.NPC,stack.inventoryIndex(),stack.registryId(),stack.name(),stack.components(),stack.count(),unitProceeds,quoteLabel);}
    public static AlchemySaleProfile parse(JsonObject p){
        var lore=new ArrayList<String>();for(var l:p.getAsJsonArray("cookieLore"))lore.add(l.getAsString());
        return new AlchemySaleProfile(p.get("openerSlot").getAsInt(),item(p.getAsJsonObject("opener")),text(p,"skyblockTitle"),p.get("cookieSlot").getAsInt(),item(p.getAsJsonObject("cookieButton")),lore,text(p,"saleTitle"),text(p,"quoteLabel"),new BigDecimal(p.get("unitProceeds").getAsString()),text(p,"receiptTemplate"),text(p,"pursePrefix"),p.has("activeCookieLine")?p.get("activeCookieLine").getAsString():"",p.has("cookieGrammar")?p.get("cookieGrammar").getAsString():"",p.get("delayTicks").getAsInt(),p.get("timeoutTicks").getAsInt());
    }
    private static String text(JsonObject p,String key){return AlchemyFlowMenus.clean(p.get(key).getAsString());}
    private static AlchemyFlowProfile.Item item(JsonObject p){return new AlchemyFlowProfile.Item(text(p,"id"),text(p,"name"),p.get("components").getAsString(),1);}
}
