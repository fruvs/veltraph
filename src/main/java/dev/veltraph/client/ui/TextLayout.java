package dev.veltraph.client.ui;
import java.util.*;
import java.util.function.ToIntFunction;

/** Width-aware plain text for cards, controls and detail drawers. */
public final class TextLayout {
    private TextLayout() {}
    public static String fit(String text,int width,ToIntFunction<String> measure) {
        if(text==null||width<=0) return "";
        if(measure.applyAsInt(text)<=width) return text;
        String tail="...";
        if(measure.applyAsInt(tail)>width) return "";
        int end=text.length();
        while(end>0&&measure.applyAsInt(text.substring(0,end)+tail)>width) end=text.offsetByCodePoints(end,-1);
        return text.substring(0,end)+tail;
    }
    public static List<String> wrap(String text,int width,ToIntFunction<String> measure) {
        if(text==null||text.isEmpty()) return List.of("");
        List<String> lines=new ArrayList<>();String remaining=text;
        while(!remaining.isEmpty()) {
            if(measure.applyAsInt(remaining)<=width) { lines.add(remaining);break; }
            int end=0,lastSpace=-1;
            while(end<remaining.length()) {
                int next=remaining.offsetByCodePoints(end,1);
                if(measure.applyAsInt(remaining.substring(0,next))>Math.max(1,width)) break;
                if(remaining.charAt(end)==' ') lastSpace=end;
                end=next;
            }
            if(end==0) end=remaining.offsetByCodePoints(0,1);
            if(lastSpace>0) end=lastSpace;
            lines.add(remaining.substring(0,end));remaining=remaining.substring(end).stripLeading();
        }
        return List.copyOf(lines);
    }
    public static int railRowHeight(int railHeight,int rows) { return Math.max(11,Math.min(20,(railHeight-14)/Math.max(1,rows))); }
}
