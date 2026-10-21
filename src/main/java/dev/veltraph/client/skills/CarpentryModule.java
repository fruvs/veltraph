package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import java.util.*;

/** One inventory-funded craft: source recipe-lore selection, normal pickup-style request and exact inventory ACK.
 * The result must be delivered by the server; a predicted cursor stack never counts as output inventory. */
public final class CarpentryModule extends GuardedSkillModule {
    public static final String ID="carpentry_inventory_craft";
    private int inputsBefore,outputsBefore;
    private SkillMenuSession.Item predictedCursor;
    public CarpentryModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Inventory Carpentry Craft",client,config,services);}
    private String title(){return config.get(SkillAutomationConfig.CARPENTRY_MENU_TITLE);}
    private String inId(){return config.get(SkillAutomationConfig.CARPENTRY_INPUT_ID);}
    private String inName(){return config.get(SkillAutomationConfig.CARPENTRY_INPUT_NAME);}
    private String outId(){return config.get(SkillAutomationConfig.CARPENTRY_OUTPUT_ID);}
    private String outName(){return config.get(SkillAutomationConfig.CARPENTRY_OUTPUT_NAME);}
    private int inputQuantity(){return config.get(SkillAutomationConfig.CARPENTRY_INPUT_QUANTITY);}
    private int outputQuantity(){return config.get(SkillAutomationConfig.CARPENTRY_OUTPUT_QUANTITY);}
    @Override protected boolean ownsCursor(SkillMenuSession.Item cursor){return predictedCursor!=null&&cursor.equals(predictedCursor);}
    private List<SkillMenuSession.Slot> recipes(SkillMenuSession.View menu){return menu.slots().stream().filter(s->!s.player()&&s.item().name().equals(config.get(SkillAutomationConfig.CARPENTRY_RECIPE_LABEL))&&s.lore().contains(config.get(SkillAutomationConfig.CARPENTRY_RECIPE_LORE))&&s.item().matches(outId(),outName())&&s.item().count()==outputQuantity()).toList();}
    @Override public boolean allowsScreen(String current){return SkillMenuSession.clean(current).equals(title());}
    @Override public Optional<String> preStartCheck(){return invalid(()->{
        if(!config.get(SkillAutomationConfig.CARPENTRY_ENABLED))throw new IllegalArgumentException("Enable inventory crafting explicitly");
        label(title());label(config.get(SkillAutomationConfig.CARPENTRY_RECIPE_LABEL));label(config.get(SkillAutomationConfig.CARPENTRY_RECIPE_LORE));SkillContracts.identity(inId(),inName());SkillContracts.identity(outId(),outName());
        SkillContracts.quantity(inputQuantity(),config.get(SkillAutomationConfig.SKILL_MAX_QUANTITY));SkillContracts.quantity(outputQuantity(),config.get(SkillAutomationConfig.SKILL_MAX_QUANTITY));
        SkillContracts.value(config.get(SkillAutomationConfig.CARPENTRY_CRAFT_VALUE),config.get(SkillAutomationConfig.SKILL_MAX_RESOURCE_VALUE));
        if(inId().equals(outId())&&inName().equals(outName()))throw new IllegalArgumentException("Craft input/output must differ");
        var menu=menus.read();if(menu==null||!menu.title().equals(title())||menu.cursor().count()!=0||recipes(menu).size()!=1)throw new IllegalArgumentException("Open one exact craft result with configured recipe lore and empty cursor");
        if(menus.count(inId(),inName())<inputQuantity()||!menus.capacity(1))throw new IllegalArgumentException("Missing exact materials or output capacity");menus.count(outId(),outName());
    });}
    @Override public void onEnable(){begin();predictedCursor=null;var invalid=preStartCheck();if(invalid.isPresent()){fail(invalid.get());return;}var menu=menus.read();ownedMenu=menu.menu();inputsBefore=menus.count(inId(),inName());outputsBefore=menus.count(outId(),outName());phase="CRAFT_ITEM";}
    @Override public void onTick(){try{
        if(!context()){fail("Craft player/world/config changed or timed out");return;}var menu=expected(title());if(menu==null){fail("Craft menu replaced");return;}
        if(phase.equals("CRAFT_ITEM")){var candidates=recipes(menu);if(menu.cursor().count()!=0||candidates.size()!=1||menus.count(inId(),inName())!=inputsBefore||menus.count(outId(),outName())!=outputsBefore){fail("Craft recipe/inventory/cursor changed");return;}predictedCursor=candidates.getFirst().item();if(click(menu,candidates.getFirst().id(),ContainerInput.PICKUP))phase="WAIT_CRAFT_ACK";}
        else if(phase.equals("WAIT_CRAFT_ACK")){int consumed=inputsBefore-menus.count(inId(),inName()),produced=menus.count(outId(),outName())-outputsBefore;
            reason="Awaiting server recipe outcome; revision="+revision+"->"+menu.revision()+", consumed="+consumed+", produced="+produced;
            if(menu.cursor().count()!=0&&!ownsCursor(menu.cursor())){fail("Unexpected craft cursor identity");return;}
            if(menu.revision()>revision&&menu.cursor().count()==0&&consumed==inputQuantity()&&produced==outputQuantity())complete("Exact server material consumption and delivered result inventory observed");
            else if(consumed<0||consumed>inputQuantity()||produced<0||produced>outputQuantity())fail("Craft inventory delta exceeds the declared contract");}
    }catch(RuntimeException e){fail("Craft guard: "+e.getMessage());}}
}
