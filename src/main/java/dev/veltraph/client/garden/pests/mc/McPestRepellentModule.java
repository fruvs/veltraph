package dev.veltraph.client.garden.pests.mc;

import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.*;
import dev.veltraph.client.gui.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.math.BigDecimal;
import java.util.*;

/** Source Skymart navigation/buy/confirm -> inventory relocation -> use, with copper/material/effect proof. */
public final class McPestRepellentModule extends GardenActionModule {
    public static final String ID="garden_pest_repellent";private PestBindings.Repellent b;private int route,before;private BigDecimal copper,price;
    private String pendingTitle;private MenuSnapshot previous;private boolean purchaseRequested,purchaseAck,useAck,productSelected;private Object purchaseMenu;
    public McPestRepellentModule(Minecraft c,FarmServices s,ConfigService cfg){super(ID,"Acquire and use Pest Repellent",c,s,cfg);}
    protected boolean allowsBarn(){return true;}
    protected boolean optedIn(){return config.get(PestConfig.PESTS_USE_REPELLENT);}
    protected Optional<String> ready(){b=PestBindings.read(config.get(PestConfig.PEST_REPELLENT_BINDINGS),PestBindings.Repellent.class);
        if(sidebar().contains(b.effectSidebarLine()))return Optional.of("Repellent effect already active");
        int count=PestItems.count(client,b.item(),false);if(count>0)return client.gui.screen()!=null?Optional.of("Close menus before using owned repellent"):Optional.empty();
        var m=menus.read();String first=b.route().isEmpty()?b.purchaseTitle():b.route().getFirst().title();
        return (m==null?!b.openCommand().equals("desk"):!m.title().equals(first))||balance(b.balancePrefix()).isEmpty()||config.get(PestConfig.PEST_SUPPLY_MAX_COST)<1||config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)<1
                ?Optional.of("Open configured Desk/Skymart path and positive purchase caps/readable copper"):Optional.empty();}
    protected boolean screenAllowed(String title){return b!=null&&(title.equals(b.purchaseTitle())||title.equals(b.confirmTitle())||b.route().stream().anyMatch(s->s.title().equals(title)||s.nextTitle().equals(title)));}
    protected void begin(){before=PestItems.count(client,b.item(),false);purchaseAck=useAck=purchaseRequested=productSelected=false;previous=null;pendingTitle=null;purchaseMenu=null;route=0;
        if(before>0){phase=Phase.RELOCATE;startLease();}else{phase=Phase.BUY;copper=balance(b.balancePrefix()).orElseThrow();if(client.gui.screen()==null&&!new dev.veltraph.client.service.mc.McServerCommandService(client).sendCommand(b.openCommand()))finish(Phase.FAILED,"Configured Desk opening request unavailable");}}
    private void startLease(){if(!lease.begin(b.item(),config.get(PestConfig.PEST_ACTION_HOTBAR_SLOT)))finish(Phase.FAILED,"Repellent inventory relocation refused");}
    protected void advance(){
        if(phase==Phase.RELOCATE){if(lease.ready()){before=PestItems.count(client,b.item(),false);phase=Phase.WAIT_EFFECT;useAck=false;
            if(!lease.use())finish(Phase.FAILED,"Repellent native item-use guard refused request");}return;}
        if(phase==Phase.WAIT_EFFECT){if(!lease.selectedSlotOwned()){finish(Phase.CANCELLED,"Repellent slot changed during effect acknowledgment");return;}if(client.gui.screen()!=null){finish(Phase.CANCELLED,"Menu opened while awaiting repellent effect");return;}
            int count=PestItems.count(client,b.item(),false);if(count<before-1){finish(Phase.FAILED,"Excess repellent consumption");return;}
            if(useAck&&count==before-1&&sidebar().contains(b.effectSidebarLine()))finish(Phase.COMPLETE,"Exact repellent consumption and server effect observed");return;}
        var menu=menu();
        if(purchaseRequested){
            int have=PestItems.count(client,b.item(),false);var amount=balance(b.balancePrefix()).orElse(null);
            if(purchaseAck&&have==before+1&&amount!=null&&amount.compareTo(copper.subtract(price))==0){closeOwned();phase=Phase.RELOCATE;startLease();}return;
        }
        if(menu==null||Objects.equals(menu,previous))return;
        if(pendingTitle!=null){
            if(menu.title().equals(pendingTitle)){pendingTitle=null;previous=null;}
            else if(previous!=null&&menu.id()==previous.id()&&menu.title().equals(previous.title()))return;
            else{finish(Phase.CANCELLED,"Unexpected menu after issued Skymart transition");return;}
        }
        if(PestItems.count(client,b.item(),false)!=before||balance(b.balancePrefix()).map(v->v.compareTo(copper)!=0).orElse(true)){finish(Phase.CANCELLED,"Inventory/copper changed before purchase");return;}
        if(route<b.route().size()){
            var step=b.route().get(route);if(!menu.title().equals(step.title())){finish(Phase.CANCELLED,"Skymart path changed");return;}
            var nav=button(menu,step.button());if(nav==null||nav.lore().stream().anyMatch(l->l.contains("Copper")||l.contains("Coins"))){finish(Phase.FAILED,"Missing/ambiguous/priced navigation button");return;}
            own(menu);if(!menus.click(menu,nav.id())){finish(Phase.FAILED,"Navigation menu guard refused request");return;}previous=menu;pendingTitle=step.nextTitle();route++;return;
        }
        if(!productSelected&&menu.title().equals(b.purchaseTitle())){
            var buy=button(menu,b.purchaseButton());price=buy==null?null:PestBindings.amount(buy.lore(),b.pricePrefix(),"Copper").orElse(null);
            if(!affordable(price)||!buy.lore().contains("Item: "+b.item().name())||!buy.lore().contains("Amount: 1")){finish(Phase.FAILED,"Repellent product/quantity/copper price unknown or over cap");return;}
            own(menu);if(!menus.click(menu,buy.id())){finish(Phase.FAILED,"Repellent purchase menu changed");return;}previous=menu;pendingTitle=b.confirmTitle();productSelected=true;return;
        }
        if(productSelected&&menu.title().equals(b.confirmTitle())&&price!=null){var confirm=button(menu,b.confirmButton());var current=confirm==null?null:PestBindings.amount(confirm.lore(),b.pricePrefix(),"Copper").orElse(null);
            if(!affordable(current)||current.compareTo(price)!=0||!confirm.lore().contains("Item: "+b.item().name())||!confirm.lore().contains("Amount: 1")){finish(Phase.FAILED,"Repellent confirmation price/product/quantity changed");return;}
            purchaseRequested=true;purchaseAck=false;purchaseMenu=owner.containerMenu;own(menu);
            if(!menus.click(menu,confirm.id()))finish(Phase.FAILED,"Repellent confirm guard refused request");return;}
        finish(Phase.CANCELLED,"Unexpected repellent menu");
    }
    private boolean affordable(BigDecimal cost){return cost!=null&&cost.signum()>0&&cost.compareTo(copper)<=0&&cost.compareTo(BigDecimal.valueOf(config.get(PestConfig.PEST_SUPPLY_MAX_COST)))<=0&&cost.compareTo(BigDecimal.valueOf(config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)))<=0;}
    @Override public void onServerMessage(String line){super.onServerMessage(line);if(!isEnabled()||line==null)return;String text=dev.veltraph.client.game.LocationText.stripColorCodes(line);
        if(purchaseRequested&&text.equals(b.purchaseAck()))purchaseAck=true;if(phase==Phase.WAIT_EFFECT&&text.equals(b.useAck()))useAck=true;}
}
