package dev.veltraph.client.metrics;

import java.util.*;
import java.util.regex.Pattern;

/** Source-derived estimates, kept separate from exact cumulative XP receipt contracts. */
public final class SourceSkillXpProjection {
    private SourceSkillXpProjection() {}
    // Exact stable4.0.30 literal, including its level53 68972452 entry; not silently corrected.
    private static final long[] TOTAL={0,50,175,375,675,1175,1925,2925,4425,6425,9925,14925,22425,32425,47425,67425,97425,147425,222425,322425,522425,822425,1222425,1722425,2322425,3022425,3822425,4722425,5722425,6822425,8022425,9322425,10722425,12222425,13822425,15522425,17322425,19222425,21222425,23322425,25522425,27822425,30222425,32722425,35322425,38072425,40972425,44072425,47472425,51172425,55172425,59472425,64072425,68972452,74172425,79672425,85472425,91572425,97972425,104672425,111672425};
    public record Estimate(String skill,int level,double percent,long projectedTotal) {}
    private static final Pattern ROW=Pattern.compile("^([A-Za-z]+) (0|[IVXLCDM]+): ([0-9]{1,3}(?:\\.[0-9]{1,4})?)%$");
    private static final Map<String,Integer> CAPS=Map.ofEntries(Map.entry("FARMING",60),Map.entry("MINING",60),Map.entry("COMBAT",60),Map.entry("FORAGING",60),Map.entry("FISHING",50),Map.entry("ENCHANTING",60),Map.entry("ALCHEMY",50),Map.entry("CARPENTRY",50),Map.entry("TAMING",50),Map.entry("HUNTING",50),Map.entry("SOCIAL",25),Map.entry("RUNECRAFTING",25));
    public static boolean isSkill(String skill){return CAPS.containsKey(skill);}
    public static long project(int level,double percent,int cap) {
        if(level<0||level>cap||cap>60||!Double.isFinite(percent)||percent<0||percent>100)throw new IllegalArgumentException("Invalid source level/percentage");
        if(level==60)return TOTAL[level];float fraction=(float)(percent/100.0);long n=(long)(TOTAL[level]+(TOTAL[level+1]-TOTAL[level])*(double)fraction);return fraction>=1?n-1:n;
    }
    public static List<Estimate> tabRows(List<String> rows) {
        if(rows.size()>128)return List.of();var out=new ArrayList<Estimate>();boolean header=false;var seen=new HashSet<String>();
        for(String raw:rows){String s=raw.replaceAll("§[0-9A-FK-ORa-fk-or]","").strip();if(!header){header=s.equals("Skills:");continue;}if(s.isEmpty()||s.startsWith("Visitors"))break;
            var m=ROW.matcher(s);if(!m.matches())return List.of();String skill=m.group(1).toUpperCase(Locale.ROOT);if(!CAPS.containsKey(skill)||!seen.add(skill))return List.of();
            try{int level=roman(m.group(2));double p=Double.parseDouble(m.group(3));out.add(new Estimate(skill,level,p,project(level,p,CAPS.get(skill))));}catch(IllegalArgumentException bad){return List.of();}
        }
        return List.copyOf(out);
    }
    private static int roman(String s){if(s.equals("0"))return 0;int n=0,last=0;for(int i=s.length()-1;i>=0;i--){int v=switch(s.charAt(i)){case 'I'->1;case 'V'->5;case 'X'->10;case 'L'->50;case 'C'->100;case 'D'->500;case 'M'->1000;default->throw new IllegalArgumentException();};n+=v<last?-v:v;last=v;}if(n<1||n>60||!encode(n).equals(s))throw new IllegalArgumentException("Noncanonical Roman level");return n;}
    private static String encode(int n){StringBuilder b=new StringBuilder();int[] v={50,40,10,9,5,4,1};String[] s={"L","XL","X","IX","V","IV","I"};for(int i=0;i<v.length;i++)while(n>=v[i]){b.append(s[i]);n-=v[i];}return b.toString();}
}
