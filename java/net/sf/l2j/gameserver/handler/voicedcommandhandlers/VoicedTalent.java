package net.sf.l2j.gameserver.handler.voicedcommandhandlers;

import net.sf.l2j.gameserver.communitybbs.Manager.TalentBBSManager;
import net.sf.l2j.gameserver.handler.IVoicedCommandHandler;
import net.sf.l2j.gameserver.model.actor.Player;

public class VoicedTalent implements IVoicedCommandHandler
{
	private static String[] VOICED_COMMANDS =
	{
		"talent"
	};

	@Override
	public boolean useVoicedCommand(String command, Player activeChar, String target)
	{
		if(command.startsWith("talent"))
		{
			TalentBBSManager.getInstance().parseCmd("_bbstalent show", activeChar);
		}
		return true;
	}

	@Override
	public String[] getVoicedCommandList()
	{
		return VOICED_COMMANDS;
	}
}
