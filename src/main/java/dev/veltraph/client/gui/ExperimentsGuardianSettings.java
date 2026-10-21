package dev.veltraph.client.gui;

import java.util.*;
import java.util.regex.Pattern;

/** Explicit frozen bindings; the recovered source does not decode these production strings. */
public record ExperimentsGuardianSettings(boolean enabled,String tabPrefix,String tabSuffix,String menuTitle,
        String command,String petName,String inactiveLore,String activeLore,String inactiveItemId,String activeItemId,
        String nextPageName,String nextPageItemId,int delayTicks,int timeoutTicks,int maxPages) {
    public ExperimentsGuardianSettings {
        tabPrefix=MenuSnapshot.clean(tabPrefix);tabSuffix=MenuSnapshot.clean(tabSuffix);menuTitle=MenuSnapshot.clean(menuTitle);
        command=command.strip();petName=MenuSnapshot.clean(petName);inactiveLore=MenuSnapshot.clean(inactiveLore);activeLore=MenuSnapshot.clean(activeLore);
        nextPageName=MenuSnapshot.clean(nextPageName);
        if(enabled) {
            if(List.of(tabPrefix,tabSuffix,menuTitle,command,petName,inactiveLore,activeLore,inactiveItemId,activeItemId).stream().anyMatch(String::isBlank))
                throw new IllegalArgumentException("Guardian preflight needs explicit tab prefix/suffix, pet menu command/title, pet name, item IDs and state lore");
            if(inactiveLore.equals(activeLore)||!command.matches("/?[a-zA-Z0-9 _:-]+")||delayTicks<1||delayTicks>40||timeoutTicks<=delayTicks||timeoutTicks>1200||maxPages<1||maxPages>20)
                throw new IllegalArgumentException("Guardian state lore must differ; command and bounded timing/page settings must be valid");
            if(maxPages>1&&(nextPageName.isBlank()||nextPageItemId.isBlank()))
                throw new IllegalArgumentException("Guardian pagination needs explicit next-page name and item ID");
        }
    }
    public record Page(int number,int total) {}
    /** Exact base title and source-shaped (page/total) suffix; malformed/partial titles are refused. */
    public Page page(String title) {
        String clean=MenuSnapshot.clean(title);
        if(clean.equals(menuTitle))return new Page(1,1);
        var match=Pattern.compile(Pattern.quote(menuTitle)+" \\(([0-9]+)/([0-9]+)\\)").matcher(clean);
        if(!match.matches())return null;
        try {
            int number=Integer.parseInt(match.group(1)),total=Integer.parseInt(match.group(2));
            return number>=1&&total>=number&&total<=maxPages?new Page(number,total):null;
        }catch(NumberFormatException invalid){return null;}
    }
    public boolean activeTab(String row) {String text=MenuSnapshot.clean(row);return text.startsWith(tabPrefix)&&text.endsWith(tabSuffix);}
}
