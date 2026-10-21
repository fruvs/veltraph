package dev.veltraph.client.skills;

import java.util.*;
import java.math.BigDecimal;
import java.util.regex.Pattern;
import dev.veltraph.client.world.BlockPos;

/** Local contracts for source-unrecovered identities and quantities; no remote price/data dependency. */
public final class SkillContracts {
    private SkillContracts() {}
    public static void identity(String id,String name){if(id==null||!id.matches("[a-z0-9_.-]+:[a-z0-9_/.-]+")||name==null||name.isBlank())throw new IllegalArgumentException("Configure exact item registry ID and display name");}
    public static void quantity(int quantity,int cap){if(quantity<1||quantity>cap||cap>4096)throw new IllegalArgumentException("Quantity outside explicit cap");}
    public static void value(long value,long cap){if(value<1||cap<1||value>cap)throw new IllegalArgumentException("Resource value is unknown or exceeds cap");}
    public static BlockPos position(String text){
        if(text==null)throw new IllegalArgumentException("Configure XYZ position");var p=text.strip().split(",",-1);if(p.length!=3)throw new IllegalArgumentException("Position requires x,y,z");
        try{int x=Integer.parseInt(p[0].strip()),y=Integer.parseInt(p[1].strip()),z=Integer.parseInt(p[2].strip());if(Math.abs((long)x)>29999984||Math.abs((long)z)>29999984||y< -2048||y>2048)throw new IllegalArgumentException("Position outside bounds");return new BlockPos(x,y,z);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid integer XYZ");}
    }
    public static OptionalLong quotedCost(List<String> lore,int quantity){
        Long amount=null,cost=null;
        var amountPattern=Pattern.compile("(?i)^Amount: ([0-9,]+)x$");var pricePattern=Pattern.compile("(?i)^Price: ([0-9,]+(?:\\.[0-9]+)?) Coins$");
        try{for(var raw:lore){String line=raw.replaceAll("§.","").strip();var a=amountPattern.matcher(line);var p=pricePattern.matcher(line);
            if(a.matches()){if(amount!=null)return OptionalLong.empty();amount=Long.parseLong(a.group(1).replace(",",""));}
            if(p.matches()){if(cost!=null)return OptionalLong.empty();cost=new BigDecimal(p.group(1).replace(",","")).longValueExact();}}
        }catch(ArithmeticException|NumberFormatException invalid){return OptionalLong.empty();}
        return amount!=null&&amount==quantity&&cost!=null&&cost>0?OptionalLong.of(cost):OptionalLong.empty();
    }
}
