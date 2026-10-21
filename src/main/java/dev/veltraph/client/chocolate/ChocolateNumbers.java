package dev.veltraph.client.chocolate;
import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;
/** Captured full decimals/grouping and explicit K/M/B scale tokens; never parses mixed slash progress. */
public final class ChocolateNumbers {
    private ChocolateNumbers() {}
    private static final Pattern VALUE=Pattern.compile("^((?:[0-9]+|[1-9][0-9]{0,2}(?:,[0-9]{3})+)(?:\\.[0-9]+)?)([KMB]?)$");
    public static Optional<BigDecimal> read(String text,boolean compact){
        if(text==null)return Optional.empty();var m=VALUE.matcher(text);if(!m.matches()||!compact&&!m.group(2).isEmpty())return Optional.empty();
        try{var value=new BigDecimal(m.group(1).replace(",",""));var scale=switch(m.group(2)){case "K"->BigDecimal.valueOf(1000);case "M"->BigDecimal.valueOf(1_000_000);case "B"->BigDecimal.valueOf(1_000_000_000);default->BigDecimal.ONE;};var scaled=value.multiply(scale);if(scaled.precision()>30||scaled.scale()>12)return Optional.empty();return Optional.of(scaled);}catch(NumberFormatException e){return Optional.empty();}
    }
    public static int roman(String text){if(text==null||text.isEmpty())return -1;int value=0,previous=0;for(int i=text.length()-1;i>=0;i--){int n=switch(text.charAt(i)){case 'I'->1;case 'V'->5;case 'X'->10;case 'L'->50;case 'C'->100;case 'D'->500;case 'M'->1000;default->-1;};if(n<0)return -1;value+=n<previous?-n:n;previous=Math.max(previous,n);}return value>0&&value<=3999&&encodeRoman(value).equals(text)?value:-1;}
    private static String encodeRoman(int value){var out=new StringBuilder();int[] numbers={1000,900,500,400,100,90,50,40,10,9,5,4,1};String[] letters={"M","CM","D","CD","C","XC","L","XL","X","IX","V","IV","I"};for(int i=0;i<numbers.length;i++)while(value>=numbers[i]){out.append(letters[i]);value-=numbers[i];}return out.toString();}
}
