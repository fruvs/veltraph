package dev.veltraph.client.skills;

import com.google.gson.*;
import java.math.BigDecimal;

/** Explicit unrecovered Bazaar and cookie-menu labels; no implicit destination or price. */
public record CarpentrySaleProtocol(String confirmationTitle,String confirmationLabel,Fallback fallback) {
    public record Fallback(CarpentryFlowProfile.Target skyblock,String cookieTitle,
            CarpentryFlowProfile.Item cookieButton,String rejectionMessage,String rejectionLabel,
            String activeCookieLine,BigDecimal unitProceeds,String quoteLabel,String receipt) {
        public Fallback {
            if(skyblock.command().isEmpty()||cookieTitle.isBlank()||cookieTitle.equals(skyblock.title())
                    ||rejectionMessage.isBlank()||rejectionLabel.isBlank()||activeCookieLine.isBlank()
                    ||unitProceeds.signum()<=0||unitProceeds.scale()>2||quoteLabel.isBlank()
                    ||!receipt.contains("{quantity}")||!receipt.contains("{item}")||!receipt.contains("{coins}"))
                throw new IllegalArgumentException("Exact observed cookie eligibility, uncommitted rejection and fallback sale required");
        }
    }
    public CarpentrySaleProtocol {
        if(confirmationTitle==null||confirmationTitle.isBlank()||confirmationLabel==null||confirmationLabel.isBlank())
            throw new IllegalArgumentException("Exact Bazaar confirmation title and quote required");
    }
    static CarpentrySaleProtocol parse(JsonObject sale) {
        if(!sale.has("confirmationTitle")||!sale.has("confirmationLabel"))throw new IllegalArgumentException("Carpentry output sale requires the Bazaar inventory confirmation contract; prior NPC-only profiles must be reconfigured");
        Fallback fallback=null;
        if(sale.has("fallback")) {
            var f=sale.getAsJsonObject("fallback");var t=f.getAsJsonObject("skyblock");var i=f.getAsJsonObject("cookieButton");
            fallback=new Fallback(new CarpentryFlowProfile.Target(t.get("title").getAsString(),null,"",t.get("command").getAsString()),
                f.get("cookieTitle").getAsString(),new CarpentryFlowProfile.Item(i.get("id").getAsString(),i.get("name").getAsString(),i.get("components").getAsString(),1),
                f.get("rejectionMessage").getAsString(),f.get("rejectionLabel").getAsString(),f.get("activeCookieLine").getAsString(),
                new BigDecimal(f.get("unitProceeds").getAsString()),f.get("quoteLabel").getAsString(),f.get("receipt").getAsString());
        }
        return new CarpentrySaleProtocol(sale.get("confirmationTitle").getAsString(),sale.get("confirmationLabel").getAsString(),fallback);
    }
}
