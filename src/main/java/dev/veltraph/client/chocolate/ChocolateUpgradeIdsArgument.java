package dev.veltraph.client.chocolate;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.LiteralMessage;
import java.util.*;

/** The advertised queue token permits commas while retaining exact, bounded upgrade identifiers. */
public final class ChocolateUpgradeIdsArgument implements ArgumentType<String> {
    private static final SimpleCommandExceptionType INVALID=new SimpleCommandExceptionType(new LiteralMessage("Select 1-16 comma-separated exact upgrade IDs, each 1-96 plain identifier characters"));
    @Override public String parse(StringReader reader) throws CommandSyntaxException {
        int start=reader.getCursor();
        while(reader.canRead()&&(StringReader.isAllowedInUnquotedString(reader.peek())||reader.peek()==','))reader.skip();
        String token=reader.getString().substring(start,reader.getCursor());var ids=token.split(",",-1);
        if(ids.length<1||ids.length>16||Arrays.stream(ids).anyMatch(id->id.isEmpty()||id.length()>96)){reader.setCursor(start);throw INVALID.createWithContext(reader);}
        return token;
    }
    @Override public Collection<String> getExamples(){return List.of("rabbit_bro","rabbit_bro,rabbit_barn");}
}
