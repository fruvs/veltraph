package dev.veltraph.client.inventory.selling;

import dev.veltraph.client.config.AutoSellMode;
import java.util.Locale;

/** Pure ordinary-command grammar. Inspection never activates or changes a selling contract. */
public record SellingInspectorRequest(boolean valid,String error,Kind kind,AutoSellMode channel,int index,String petPath) {
    public enum Kind { HELP, INSPECT, METADATA }
    public static final String USAGE="/vdebug selling inspect <npc|trash> <9..35> | inspect george <9..35> <observed-custom-data-path> | metadata <9..35>";
    private static SellingInspectorRequest bad(String why){return new SellingInspectorRequest(false,why+". "+USAGE,null,null,-1,"");}
    public static SellingInspectorRequest parse(String tail){
        String value=tail==null?"":tail.strip();if(value.isEmpty()||value.equalsIgnoreCase("help"))return new SellingInspectorRequest(true,"",Kind.HELP,null,-1,"");
        String[] args=value.split("\\s+");
        try{
            if(args[0].equalsIgnoreCase("metadata")&&args.length==2)return new SellingInspectorRequest(true,"",Kind.METADATA,null,index(args[1]),"");
            if(!args[0].equalsIgnoreCase("inspect")||args.length<3||args.length>4)return bad("Unknown inspection form");
            var channel=switch(args[1].toLowerCase(Locale.ROOT)){case "npc"->AutoSellMode.NPC;case "trash"->AutoSellMode.TRASH;
                case "george","george_pets"->AutoSellMode.GEORGE_PETS;default->throw new IllegalArgumentException("Choose npc, trash or george");};
            if(channel==AutoSellMode.GEORGE_PETS){if(args.length!=4||!validPath(args[3]))return bad("George needs an explicit observed metadata path; use metadata first");}
            else if(args.length!=3)return bad("NPC/trash inspection does not take a pet path");
            return new SellingInspectorRequest(true,"",Kind.INSPECT,channel,index(args[2]),args.length==4?args[3]:"");
        }catch(IllegalArgumentException e){return bad(e.getMessage());}
    }
    private static int index(String text){
        if(!text.matches("[0-9]{1,2}"))throw new IllegalArgumentException("Main inventory index must be an integer 9..35");
        int value=Integer.parseInt(text);if(value<9||value>35)throw new IllegalArgumentException("Only main inventory 9..35 is inspectable for selling");return value;
    }
    public static boolean validPath(String path){return path!=null&&path.length()<=128&&path.matches("[A-Za-z0-9_:-]+(?:\\.[A-Za-z0-9_:-]+){0,4}");}
}
