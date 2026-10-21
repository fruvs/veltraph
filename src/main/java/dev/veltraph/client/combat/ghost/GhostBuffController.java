package dev.veltraph.client.combat.ghost;

import dev.veltraph.client.combat.sven.SvenBindingProfile;

import dev.veltraph.client.gui.MenuSnapshot;
import java.util.*;

/** Ghost use of the stable seven-case buff dispatcher, including remove/close exhaustion continuations. */
public final class GhostBuffController {
    public enum State { SWITCH_TO_SBMENU, OPEN_SBMENU, OPEN_COOKIE_MENU, OPEN_POTION_MENU, TOGGLE_POTIONS, GO_NEXT_PAGE, CLOSE_SBMENU, COMPLETE, FAILED }
    public enum Kind { NONE, SELECT_SLOT_EIGHT, USE_MENU_ITEM, CLICK, CLOSE, COMPLETE, FAILED }
    public record Action(Kind kind,MenuSnapshot menu,int slot,String reason){static Action none(){return new Action(Kind.NONE,null,-1,"");}}
    private final SvenBindingProfile.Buff binding;
    private final List<String> remaining;
    private final Set<String> desiredLoreObserved=new LinkedHashSet<>();
    private final Map<String,String> unresolved=new LinkedHashMap<>();
    private boolean nativeJumpBoostAtClose;
    private State state=State.SWITCH_TO_SBMENU;
    private boolean dispatched;
    private String pageTitle="",failure="",effect="";
    private int pages,attempts;
    private int effectRevision=Integer.MIN_VALUE,pageRevision=Integer.MIN_VALUE;
    public GhostBuffController(SvenBindingProfile.Buff binding){this.binding=binding;remaining=new ArrayList<>(binding.effectNames());}
    public State state(){return state;}
    public Set<String> desiredLoreObserved(){return Set.copyOf(desiredLoreObserved);}
    public Map<String,String> unresolved(){return Map.copyOf(unresolved);}
    public boolean nativeJumpBoostAtClose(){return nativeJumpBoostAtClose;}
    public Action tick(MenuSnapshot menu,boolean noScreen,boolean nativeJumpBoost){
        if(state==State.FAILED)return new Action(Kind.FAILED,null,-1,failure);
        if(state==State.COMPLETE)return new Action(Kind.COMPLETE,null,-1,"");
        switch(state){
            case SWITCH_TO_SBMENU -> {return new Action(Kind.SELECT_SLOT_EIGHT,null,8,"");}
            case OPEN_SBMENU -> {
                if(menu!=null&&menu.title().equals(binding.skyblockMenu())){enter(State.OPEN_COOKIE_MENU);return Action.none();}
                if(menu!=null)return failed("Unexpected screen while opening the source SkyBlock menu");
                if(!dispatched&&noScreen){dispatched=true;return new Action(Kind.USE_MENU_ITEM,null,-1,"");}
            }
            case OPEN_COOKIE_MENU -> {
                if(menu==null)return Action.none();if(menu.title().equals(binding.cookieMenu())){enter(State.OPEN_POTION_MENU);return Action.none();}
                if(!menu.title().equals(binding.skyblockMenu()))return failed("Source SkyBlock menu title changed");
                if(!dispatched)return click(menu,binding.cookieButton(),false);
            }
            case OPEN_POTION_MENU -> {
                if(menu==null)return Action.none();if(potionMenu(menu)){enter(State.TOGGLE_POTIONS);return Action.none();}
                if(!menu.title().equals(binding.cookieMenu()))return failed("Source Cookie menu title changed");
                if(!dispatched)return click(menu,binding.potionButton(),false);
            }
            case TOGGLE_POTIONS -> {
                if(menu==null)return Action.none();if(!potionMenu(menu))return failed("Source potion menu title changed");
                if(remaining.isEmpty()){enter(State.CLOSE_SBMENU);return Action.none();}
                for(String name:List.copyOf(remaining)){
                    var matches=menu.slots().stream().filter(s->eligible(s)&&s.name().toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT))).toList();
                    if(matches.isEmpty())continue;if(matches.size()!=1)return failed("Ambiguous configured source potion label: "+name);
                    var slot=matches.getFirst();if(!effect.equals(name)){effect=name;attempts=0;effectRevision=Integer.MIN_VALUE;}
                    if(slot.lore().stream().anyMatch(line->line.toLowerCase(Locale.ROOT).contains(binding.disabledLoreMarker().toLowerCase(Locale.ROOT)))){
                        desiredLoreObserved.add(name);remaining.remove(name);effect="";return Action.none();
                    }
                    if(effectRevision==menu.revision())return Action.none();
                    if(attempts>=5){unresolved.put(name,"Five native toggle attempts without desired-state lore");remaining.remove(name);effect="";return Action.none();}
                    attempts++;effectRevision=menu.revision();return new Action(Kind.CLICK,menu,slot.id(),"");
                }
                enter(State.GO_NEXT_PAGE);pageTitle=menu.title();
            }
            case GO_NEXT_PAGE -> {
                if(menu==null)return Action.none();if(!potionMenu(menu))return failed("Source potion pagination menu changed");
                if(!menu.title().equals(pageTitle)){enter(State.TOGGLE_POTIONS);return Action.none();}
                if(dispatched&&pageRevision==menu.revision())return Action.none();
                if(pages>=5){closeUnresolved("Five source page requests exhausted");return Action.none();}
                if(dispatched&&pageRevision!=menu.revision())dispatched=false;
                if(!dispatched){
                    var matches=menu.slots().stream().filter(s->eligible(s)&&s.name().contains(binding.nextPageButton())).toList();
                    if(matches.isEmpty()){closeUnresolved("Source next-page selector absent");return Action.none();}
                    if(matches.size()!=1)return failed("Ambiguous configured source next-page selector");
                    dispatched=true;pages++;pageRevision=menu.revision();return new Action(Kind.CLICK,menu,matches.getFirst().id(),"");
                }
            }
            case CLOSE_SBMENU -> {
                if(noScreen){nativeJumpBoostAtClose=nativeJumpBoost;state=State.COMPLETE;return new Action(Kind.COMPLETE,null,-1,"");}
                if(menu==null)return failed("Unowned non-container screen at source buff close");
                if(!potionMenu(menu)&&!menu.title().equals(binding.skyblockMenu())&&!menu.title().equals(binding.cookieMenu()))return failed("Unowned menu at source buff close");
                if(!dispatched){dispatched=true;return new Action(Kind.CLOSE,menu,-1,"");}
            }
            default -> {}
        }
        return Action.none();
    }
    public void slotEightObserved(boolean selected){if(state==State.SWITCH_TO_SBMENU){if(selected)enter(State.OPEN_SBMENU);else failed("Source selected-slot-eight acknowledgment unavailable");}}
    public void sourceCloseMessage(){if(state!=State.COMPLETE&&state!=State.FAILED)closeUnresolved("Source configured close-message prefix observed");}
    private void closeUnresolved(String reason){for(var name:remaining)unresolved.put(name,reason);enter(State.CLOSE_SBMENU);}
    private boolean potionMenu(MenuSnapshot menu){return menu.title().startsWith(binding.potionMenuPrefix());}
    private Action click(MenuSnapshot menu,String label,boolean insensitive){var matches=menu.slots().stream().filter(s->eligible(s)&&(insensitive?s.name().toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT)):s.name().contains(label))).toList();
        if(matches.size()!=1)return failed("Configured source buff selector missing/ambiguous: "+label);dispatched=true;return new Action(Kind.CLICK,menu,matches.getFirst().id(),"");}
    private static boolean eligible(MenuSnapshot.Slot slot){return !slot.playerInventory()&&!slot.locked()&&slot.count()>0;}
    private void enter(State next){state=next;dispatched=false;}
    private Action failed(String reason){failure=state+": "+reason;state=State.FAILED;return new Action(Kind.FAILED,null,-1,reason);}
}
