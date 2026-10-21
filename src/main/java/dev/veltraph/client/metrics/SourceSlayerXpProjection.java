package dev.veltraph.client.metrics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.regex.Pattern;

/** Source widget grammar. Displayed compact XP is an observation, never an exact reward receipt. */
public final class SourceSlayerXpProjection {
    private SourceSlayerXpProjection() {}
    public enum State { OBSERVED, MAX, UNAVAILABLE }
    public record Widget(String selection,int level,long currentXp,long goalXp,boolean compact) {}
    public record Result(State state,Widget widget) {
        public Result {Objects.requireNonNull(state);if((state==State.OBSERVED)!=(widget!=null))throw new IllegalArgumentException();}
    }
    private static final Pattern TITLE=Pattern.compile("^([A-Za-z]+) Slayer ([0-9]{1,9}|[IVXLCDM]+)$");
    private static final Pattern NUMBER=Pattern.compile("^(?:[0-9]{1,18}|[0-9]{1,3}(?:,[0-9]{3}){1,5})(?:\\.[0-9]{1,8})?([kM]?)$");
    private static final Result UNAVAILABLE=new Result(State.UNAVAILABLE,null),MAX=new Result(State.MAX,null);
    /** Native tab accessor order; source removes empty display rows before applying +3/+4 offsets. */
    public static Result tabRows(List<String> rawRows){
        if(rawRows==null||rawRows.size()>128||rawRows.stream().anyMatch(Objects::isNull))return UNAVAILABLE;
        var rows=rawRows.stream().map(SourceSlayerXpProjection::clean).filter(s->!s.isEmpty()).toList();int header=-1;
        for(int i=0;i<rows.size();i++)if(rows.get(i).contains("Slayer:")){if(header!=-1)return UNAVAILABLE;header=i;}
        if(header<0||header+4>=rows.size())return UNAVAILABLE;
        var title=TITLE.matcher(rows.get(header+3));if(!title.matches())return UNAVAILABLE;
        try{
            int level=level(title.group(2));String xp=rows.get(header+4);
            // Source MAX is Integer.MAX_VALUE. It is a display sentinel, not earned XP.
            if(xp.contains("MAX"))return MAX;
            var pieces=xp.split("/",-1);if(pieces.length!=2)return UNAVAILABLE;
            String current=pieces[0].replace(" ",""),goal=pieces[1].replace(" ","");
            long n=number(current),g=number(goal);if(g<1||n>g)return UNAVAILABLE;
            boolean compact=current.endsWith("k")||current.endsWith("M")||goal.endsWith("k")||goal.endsWith("M");
            return new Result(State.OBSERVED,new Widget(title.group(1),level,n,g,compact));
        }catch(IllegalArgumentException|ArithmeticException invalid){return UNAVAILABLE;}
    }
    private static String clean(String s){return s.replaceAll("§[0-9A-FK-ORa-fk-or]","").strip();}
    private static long number(String s){
        var match=NUMBER.matcher(s);if(!match.matches())throw new IllegalArgumentException();
        String suffix=match.group(1),digits=suffix.isEmpty()?s:s.substring(0,s.length()-1);
        var n=new BigDecimal(digits.replace(",",""));
        if(suffix.isEmpty())return n.longValueExact();
        return n.multiply(BigDecimal.valueOf(suffix.equals("k")?1000L:1000000L)).setScale(0,RoundingMode.HALF_UP).longValueExact();
    }
    private static int level(String s){
        if(s.matches("[0-9]+")){int n=Integer.parseInt(s);if(!Integer.toString(n).equals(s))throw new IllegalArgumentException();return n;}
        if(!s.matches("M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})")||s.isEmpty())throw new IllegalArgumentException();
        int total=0,last=0;for(int i=s.length()-1;i>=0;i--){int v=switch(s.charAt(i)){case 'I'->1;case 'V'->5;case 'X'->10;case 'L'->50;case 'C'->100;case 'D'->500;case 'M'->1000;default->throw new IllegalArgumentException();};total+=v<last?-v:v;last=v;}return total;
    }
}
