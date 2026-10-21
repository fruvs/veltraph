package dev.veltraph.client.ui;
/** Pure bounded Unicode editing state; it never writes configuration or clipboard. */
public final class UiTextBuffer {
    public static final int MAX_LENGTH=65536;
    private String text="";private int cursor,anchor;
    public String text(){return text;}
    /** Noncommitting composition preview at the current selection/cursor. */
    public String preview(String composition){
        if(composition==null||composition.isEmpty())return text;
        if(composition.length()>MAX_LENGTH)throw new IllegalArgumentException("UI composition too long");
        return text.substring(0,Math.min(cursor,anchor))+composition+text.substring(Math.max(cursor,anchor));
    }
    public void set(String value){if(value==null)value="";if(value.length()>MAX_LENGTH)throw new IllegalArgumentException("UI text too long");text=value;cursor=anchor=text.length();}
    public boolean selected(){return cursor!=anchor;}
    public String selection(){return text.substring(Math.min(cursor,anchor),Math.max(cursor,anchor));}
    public void selectAll(){anchor=0;cursor=text.length();}
    public boolean insert(String value){
        if(value==null||value.length()>MAX_LENGTH)return false;var clean=new StringBuilder();value.codePoints().filter(cp->Character.isValidCodePoint(cp)&&(cp<0xd800||cp>0xdfff)&&!Character.isISOControl(cp)&&cp!=127).forEach(clean::appendCodePoint);
        int from=Math.min(cursor,anchor),to=Math.max(cursor,anchor);if((long)text.length()-(to-from)+clean.length()>MAX_LENGTH)return false;
        text=text.substring(0,from)+clean+text.substring(to);cursor=anchor=from+clean.length();return true;
    }
    public void backspace(){if(selected()){deleteSelection();return;}if(cursor>0){int before=text.offsetByCodePoints(cursor,-1);text=text.substring(0,before)+text.substring(cursor);cursor=anchor=before;}}
    public void delete(){if(selected()){deleteSelection();return;}if(cursor<text.length()){int after=text.offsetByCodePoints(cursor,1);text=text.substring(0,cursor)+text.substring(after);anchor=cursor;}}
    public void deleteSelection(){int from=Math.min(cursor,anchor),to=Math.max(cursor,anchor);text=text.substring(0,from)+text.substring(to);cursor=anchor=from;}
    public void move(int direction,boolean extend){
        if(!extend&&selected()){cursor=direction<0?Math.min(cursor,anchor):Math.max(cursor,anchor);anchor=cursor;return;}
        if(direction<0&&cursor>0)cursor=text.offsetByCodePoints(cursor,-1);else if(direction>0&&cursor<text.length())cursor=text.offsetByCodePoints(cursor,1);
        if(!extend)anchor=cursor;
    }
    public void home(boolean extend){cursor=0;if(!extend)anchor=cursor;}
    public void end(boolean extend){cursor=text.length();if(!extend)anchor=cursor;}
}
