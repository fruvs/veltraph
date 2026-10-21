package dev.veltraph.client.garden.pests.mc;
import dev.veltraph.client.config.*;
import dev.veltraph.client.garden.pests.PestBindings;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import java.util.Optional;

/** One source custom-amount acquisition with exact supply/currency proof. */
public final class McPestSupplyRefillModule extends GardenActionModule {
    public static final String ID="pest_supply_refill";private final PestSupplyAcquire acquire=new PestSupplyAcquire(this);private PestBindings.Supply binding;
    public McPestSupplyRefillModule(Minecraft c,FarmServices s,ConfigService cfg){super(ID,"Refill Garden supply",c,s,cfg);}
    protected boolean allowsBarn(){return true;}
    protected boolean optedIn(){return config.get(PestConfig.PEST_SUPPLY_REFILL_ENABLED);}
    protected Optional<String> ready(){binding=PestBindings.read(config.get(PestConfig.PEST_SUPPLY_BINDINGS),PestBindings.Supply.class);
        return client.gui.screen()!=null||config.get(PestConfig.PEST_SUPPLY_MAX_COST)<1||config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)<1
                ||balance(binding.balancePrefix()).isEmpty()?Optional.of("Close menus and configure readable purse plus positive total/unit caps"):Optional.empty();}
    protected void begin(){phase=Phase.BUY;if(!acquire.begin(binding,config.get(PestConfig.SPRAYONATOR_TRY_SACKS_FIRST),config.get(PestConfig.PEST_SUPPLY_MAX_COST),config.get(PestConfig.PEST_SUPPLY_MAX_UNIT_COST)))finish(Phase.FAILED,acquire.status());}
    protected void advance(){acquire.tick();reason=acquire.status();if(acquire.state()==PestSupplyAcquire.State.COMPLETE)finish(Phase.COMPLETE,reason);else if(acquire.state()==PestSupplyAcquire.State.FAILED)finish(Phase.FAILED,reason);}
    protected boolean screenAllowed(String title){return acquire.allows(title);}
    @Override public void onServerMessage(String line){super.onServerMessage(line);if(isEnabled())acquire.message(dev.veltraph.client.game.LocationText.stripColorCodes(line));}
}
