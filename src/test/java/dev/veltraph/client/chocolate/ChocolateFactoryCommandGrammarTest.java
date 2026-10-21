package dev.veltraph.client.chocolate;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChocolateFactoryCommandGrammarTest {
    private static CommandDispatcher<FabricClientCommandSource> installedGrammar(){var dispatcher=new CommandDispatcher<FabricClientCommandSource>();ChocolateFactoryCommands.register(dispatcher,null,null);return dispatcher;}
    @Test void registeredSelectConsumesTwoIndependentIdsAndExactBudgetReserve(){var command="vchocolate select rabbit_bro,rabbit_barn 366784 33216";var parsed=installedGrammar().parse(command,null);assertTrue(parsed.getExceptions().isEmpty(),parsed.getExceptions().toString());assertFalse(parsed.getReader().canRead(),"Registered select left unread input: "+parsed.getReader().getRemaining());var context=parsed.getContext().build(command);assertEquals("rabbit_bro,rabbit_barn",context.getArgument("ids",String.class));assertEquals("366784",context.getArgument("budget",String.class));assertEquals("33216",context.getArgument("reserve",String.class));}
    @Test void registeredSingleUpgradeSyntaxRemainsValid(){var command="vchocolate select rabbit_bro 660 258";var parsed=installedGrammar().parse(command,null);assertTrue(parsed.getExceptions().isEmpty());assertFalse(parsed.getReader().canRead());assertEquals("rabbit_bro",parsed.getContext().build(command).getArgument("ids",String.class));}
    @Test void boundedCommaGrammarRefusesEmptyIdentifiersAndOverlongQueues(){for(var ids:java.util.List.of(",rabbit_bro","rabbit_bro,","rabbit_bro,,rabbit_barn","x".repeat(97),String.join(",",java.util.Collections.nCopies(17,"rabbit_bro")))){var command="vchocolate select "+ids+" 366784 33216";var parsed=installedGrammar().parse(command,null);assertFalse(parsed.getExceptions().isEmpty(),ids);}}

}
