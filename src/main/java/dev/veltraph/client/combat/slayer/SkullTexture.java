package dev.veltraph.client.combat.slayer;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Pattern;

/** Reads only the actual textures.SKIN.url field; incidental hashes do not identify a skull. */
public final class SkullTexture {
    private static final Pattern URL=Pattern.compile("https?://textures\\.minecraft\\.net/texture/([a-f0-9]{64})");
    private SkullTexture() {}
    public static String hash(String encoded) {
        if(encoded==null||encoded.length()>8192)return "";
        try {
            String json=new String(Base64.getDecoder().decode(encoded),StandardCharsets.UTF_8);
            if(!boundedStructure(json))return "";
            var root=JsonParser.parseString(json).getAsJsonObject();
            String url=root.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString();
            var match=URL.matcher(url);return match.matches()?match.group(1):"";
        } catch(RuntimeException ignored) {return "";}
    }
    private static boolean boundedStructure(String text) {
        int depth=0;boolean quoted=false,escaped=false;
        for(char c:text.toCharArray()) {
            if(quoted) {if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{'||c=='[') {if(++depth>32)return false;}
            else if(c=='}'||c==']') {if(--depth<0)return false;}
        }
        return depth==0&&!quoted;
    }
}
