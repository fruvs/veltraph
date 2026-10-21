package dev.veltraph.client.ui;
import java.util.function.ToIntFunction;
/** Bounded codepoint-safe tail fitting for editable values, independent of native renderer state. */
public final class UiTextLayout {
    public static final int MAX_DISPLAY_UNITS=2048;
    private UiTextLayout() {}
    public static String suffix(String text,int width,ToIntFunction<String> measure){
        if(text==null||width<=0)return "";
        if(text.length()<=MAX_DISPLAY_UNITS&&measure.applyAsInt(text)<=width)return text;
        String ellipsis="...";if(measure.applyAsInt(ellipsis)>width)return "";
        int start=Math.max(0,text.length()-MAX_DISPLAY_UNITS);
        if(start>0&&Character.isLowSurrogate(text.charAt(start))&&Character.isHighSurrogate(text.charAt(start-1)))start++;
        String tail=text.substring(start);int count=tail.codePointCount(0,tail.length());int[] boundaries=new int[count+1];
        for(int i=1;i<=count;i++)boundaries[i]=tail.offsetByCodePoints(boundaries[i-1],1);
        int low=0,high=count;
        while(low<high){int mid=(low+high)/2;if(measure.applyAsInt(ellipsis+tail.substring(boundaries[mid]))<=width)high=mid;else low=mid+1;}
        return ellipsis+tail.substring(boundaries[low]);
    }
}
