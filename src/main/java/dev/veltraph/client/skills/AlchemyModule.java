package dev.veltraph.client.skills;

import dev.veltraph.client.config.*;
import dev.veltraph.client.game.GameArea;
import dev.veltraph.client.service.FarmServices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ContainerInput;
import java.util.*;

/** Prepared brewer transaction: source PICKUP_ITEM -> PLACE_ITEM (13) -> WAIT_FINAL -> COLLECT/CLOSE.
 * Exact recipe/result names are local contracts; this does not guess the opaque brewer helper recipe. */
public final class AlchemyModule extends GuardedSkillModule {
    public static final String ID="alchemy_prepared_brew";
    private int ingredientBefore,outputBefore,ackBefore,ackQuantity;
    private SkillMenuSession.Item picked;
    public AlchemyModule(Minecraft client,ConfigService config,FarmServices services){super(ID,"Prepared Alchemy Brew",client,config,services);}
    @Override public Optional<GameArea> requiredArea(){return Optional.of(GameArea.ISLAND);}
    private String title(){return config.get(SkillAutomationConfig.ALCHEMY_MENU_TITLE);}
    private String inId(){return config.get(SkillAutomationConfig.ALCHEMY_INGREDIENT_ID);}
    private String inName(){return config.get(SkillAutomationConfig.ALCHEMY_INGREDIENT_NAME);}
    private String outId(){return config.get(SkillAutomationConfig.ALCHEMY_OUTPUT_ID);}
    private String outName(){return config.get(SkillAutomationConfig.ALCHEMY_OUTPUT_NAME);}
    private int bottles(){return config.get(SkillAutomationConfig.ALCHEMY_BOTTLES);}
    @Override protected boolean ownsCursor(SkillMenuSession.Item cursor){return picked!=null&&cursor.equals(picked);}
    @Override public boolean allowsScreen(String current){return SkillMenuSession.clean(current).equals(title());}
    @Override public Optional<String> preStartCheck(){return invalid(()->{
        if(!config.get(SkillAutomationConfig.ALCHEMY_ENABLED))throw new IllegalArgumentException("Enable prepared brewing explicitly");
        label(title());SkillContracts.identity(inId(),inName());SkillContracts.identity(outId(),outName());
        SkillContracts.identity(config.get(SkillAutomationConfig.ALCHEMY_BOTTLE_ID),config.get(SkillAutomationConfig.ALCHEMY_BOTTLE_NAME));
        SkillContracts.quantity(bottles(),Math.min(3,config.get(SkillAutomationConfig.SKILL_MAX_QUANTITY)));
        SkillContracts.value(config.get(SkillAutomationConfig.ALCHEMY_INGREDIENT_VALUE),config.get(SkillAutomationConfig.SKILL_MAX_RESOURCE_VALUE));
        if(outId().equals(inId())&&outName().equals(inName()))throw new IllegalArgumentException("Ingredient/result identities must differ");
        var menu=menus.read();if(menu==null||!menu.title().equals(title())||menu.cursor().count()!=0)throw new IllegalArgumentException("Open one prepared Brewing Stand with empty cursor");
        var target=menu.slot(config.get(SkillAutomationConfig.ALCHEMY_INGREDIENT_SLOT));
        if(target==null||target.player()||target.item().count()!=0)throw new IllegalArgumentException("Configured ingredient slot must be empty and container-owned");
        var sources=menu.matching(inId(),inName(),true).stream().filter(s->s.item().count()==1).toList();
        if(sources.size()!=1)throw new IllegalArgumentException("Prepare one unambiguous single-item ingredient stack");
        int prepared=menu.matching(config.get(SkillAutomationConfig.ALCHEMY_BOTTLE_ID),config.get(SkillAutomationConfig.ALCHEMY_BOTTLE_NAME),false).stream().mapToInt(s->s.item().count()).sum();
        if(prepared!=bottles()||!menu.matching(outId(),outName(),false).isEmpty())throw new IllegalArgumentException("Prepared bottle count/result state differs from recipe contract");
        if(!menus.capacity(bottles()))throw new IllegalArgumentException("Reserve empty inventory slots for each potion result");
        menus.count(inId(),inName());menus.count(outId(),outName());
    });}
    @Override public void onEnable(){begin();picked=null;var error=preStartCheck();if(error.isPresent()){fail(error.get());return;}var menu=menus.read();ownedMenu=menu.menu();ingredientBefore=menus.count(inId(),inName());outputBefore=menus.count(outId(),outName());phase="PICKUP_ITEM";}
    @Override public void onTick(){try{
        if(!context()){fail("Brewing player/world/config changed or timed out");return;}
        var menu=expected(title());if(menu==null){fail("Brewing menu replaced or closed");return;}
        switch(phase){
            case "PICKUP_ITEM"->{var source=menu.matching(inId(),inName(),true).stream().filter(s->s.item().count()==1).toList();if(source.size()!=1||menu.cursor().count()!=0){fail("Ingredient/cursor changed before pickup");return;}picked=source.getFirst().item();if(click(menu,source.getFirst().id(),ContainerInput.PICKUP))phase="WAIT_PICKUP_ACK";}
            case "WAIT_PICKUP_ACK"->{if(menu.revision()>revision&&menu.cursor().equals(picked)&&menus.count(inId(),inName())==ingredientBefore-1)phase="PLACE_ITEM";}
            case "PLACE_ITEM"->{var slot=menu.slot(config.get(SkillAutomationConfig.ALCHEMY_INGREDIENT_SLOT));if(!menu.cursor().equals(picked)||slot==null||slot.player()||slot.item().count()!=0){fail("Brewing placement cursor/target changed");return;}if(click(menu,slot.id(),ContainerInput.PICKUP))phase="WAIT_PLACE_ACK";}
            case "WAIT_PLACE_ACK"->{if(menu.revision()>revision&&menu.cursor().count()==0&&menus.count(inId(),inName())==ingredientBefore-1)phase="WAIT_FOR_FINAL_BREW";}
            case "WAIT_FOR_FINAL_BREW"->{if(menu.cursor().count()!=0){fail("Unexpected brewing cursor");return;}var outputs=menu.matching(outId(),outName(),false);int count=outputs.stream().mapToInt(s->s.item().count()).sum();if(count==bottles())phase="COLLECT_RESULTS";else if(count>bottles()){fail("Brewing result quantity exceeds contract");}}
            case "COLLECT_RESULTS"->{if(menu.cursor().count()!=0){fail("Unexpected result cursor");return;}var outputs=menu.matching(outId(),outName(),false);if(outputs.isEmpty()){if(menus.count(outId(),outName())==outputBefore+bottles()&&menus.count(inId(),inName())==ingredientBefore-1)complete("Exact brewed outputs collected and ingredient consumed");else fail("Brewing outputs disappeared without inventory outcome");return;}var output=outputs.getFirst();ackBefore=menus.count(outId(),outName());ackQuantity=output.item().count();if(click(menu,output.id(),ContainerInput.QUICK_MOVE))phase="WAIT_OUTPUT_ACK";}
            case "WAIT_OUTPUT_ACK"->{if(menu.revision()>revision&&menu.cursor().count()==0&&menus.count(outId(),outName())==ackBefore+ackQuantity)phase="COLLECT_RESULTS";}
            default->{fail("Invalid brewing phase");}
        }
    }catch(RuntimeException e){fail("Brewing guard: "+e.getMessage());}}
}
