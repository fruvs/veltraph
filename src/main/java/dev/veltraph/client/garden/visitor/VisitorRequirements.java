package dev.veltraph.client.garden.visitor;

import java.util.*;
import java.util.regex.Pattern;

/** Reads the bounded Required/Required Items section, never reward or decorative menu rows. */
public final class VisitorRequirements {
    private static final Pattern ITEM=Pattern.compile("^(.+?)\\s+(?:x|×)([0-9]+(?:,[0-9]{3})*)$",Pattern.CASE_INSENSITIVE);
    private static final Pattern PREFIX=Pattern.compile("^([0-9]+(?:,[0-9]{3})*)\\s*(?:x|×)\\s*(.+)$",Pattern.CASE_INSENSITIVE);
    private VisitorRequirements(){}
    public static Optional<List<VisitorItem>> read(List<String> lore){
        boolean required=false,found=false;var result=new ArrayList<VisitorItem>();var names=new HashSet<String>();
        for(String raw:lore){
            String line=BazaarPriceParser.stripColorCodes(raw).strip();
            if(header(line)){if(found)return Optional.empty();required=found=true;continue;}
            if(!required)continue;
            if(line.isBlank()){if(!result.isEmpty())break;continue;}
            if(line.endsWith(":"))break;
            var suffix=ITEM.matcher(line);var prefix=PREFIX.matcher(line);String name,amount;
            if(suffix.matches()){name=suffix.group(1);amount=suffix.group(2);}
            else if(prefix.matches()){name=prefix.group(2);amount=prefix.group(1);}
            else if(line.matches("[\\p{L}][\\p{L}\\p{N} '\\-]*")&&!line.toLowerCase(Locale.ROOT).contains(" x")){name=line;amount="1";}
            else return Optional.empty();
            try{
                int count=Integer.parseInt(amount.replace(",",""));name=name.strip();
                if(count<1||name.isBlank()||!names.add(name.toLowerCase(Locale.ROOT)))return Optional.empty();
                result.add(VisitorItem.of(name,count));
            }catch(NumberFormatException invalid){return Optional.empty();}
        }
        return found&&!result.isEmpty()?Optional.of(List.copyOf(result)):Optional.empty();
    }
    private static boolean header(String s){return s.equalsIgnoreCase("Required:")||s.equalsIgnoreCase("Required Items:")||s.equalsIgnoreCase("Items Required:");}
    public static boolean hasSection(List<String> lore){return lore.stream().map(s->BazaarPriceParser.stripColorCodes(s).strip()).anyMatch(VisitorRequirements::header);}
}
