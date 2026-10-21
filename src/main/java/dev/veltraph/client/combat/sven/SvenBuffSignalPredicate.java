package dev.veltraph.client.combat.sven;

import java.util.List;
import java.util.regex.Pattern;

/** Stable footer/ordered-tab-row helper grammar, with current salted strings explicitly bound. */
public final class SvenBuffSignalPredicate {
    private boolean cached;
    public boolean observe(String footer,List<String> orderedRows,SvenBindingProfile.Buff binding){
        String text=clean(footer);
        if(Pattern.compile(binding.footerPattern()).matcher(text).find()){
            try{int start=text.indexOf(binding.footerStart());if(start<0)throw new IllegalArgumentException();String tail=text.substring(start);
                int end=tail.indexOf(binding.footerEnd());if(end<0)throw new IllegalArgumentException();cached=!tail.substring(0,end).contains(binding.footerInactive());}
            catch(RuntimeException invalid){cached=false;}
        }
        if(!cached){for(int i=0;i<orderedRows.size();i++){
            String row=clean(orderedRows.get(i));if(!row.contains(binding.rowLabel()))continue;
            if(!row.contains(binding.rowNeighborMarker())){cached=true;break;}
            String next=i+1<orderedRows.size()?clean(orderedRows.get(i+1)):null;
            if(next!=null&&next.startsWith(binding.nextRowInactivePrefix()))break;
            cached=true;break;
        }}
        return cached;
    }
    public void reset(){cached=false;}
    private static String clean(String s){return s==null?"":s.replaceAll("§.","").strip();}
}
