package net.sf.l2j.gameserver.handler.skillhandlers;

import net.sf.l2j.event.bossevent.KTBEvent;
import net.sf.l2j.event.ctf.CTFEvent;
import net.sf.l2j.event.deathmath.DMEvent;
import net.sf.l2j.event.fortress.FOSEvent;
import net.sf.l2j.event.lastman.LMEvent;
import net.sf.l2j.event.tvt.TvTEvent;
import net.sf.l2j.gameserver.handler.ISkillHandler;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ConfirmDlg;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;
import net.sf.l2j.gameserver.util.Util;

/**
 * @authors BiTi, Sami
 */
public class SummonFriend implements ISkillHandler
{
	private static final L2SkillType[] SKILL_IDS =
	{
		L2SkillType.SUMMON_FRIEND
	};
	
	@Override
	public void useSkill(Creature activeChar, L2Skill skill, L2Object[] targets)
	{
		if (!(activeChar instanceof Player))
			return;
		
		final Player player = (Player) activeChar;
		
		// Check player status.
		if (!Player.checkSummonerStatus(player))
			return;
		
		if (KTBEvent.isPlayerParticipant(player.getObjectId()) && KTBEvent.isStarted() || LMEvent.isStarted() && LMEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You cant use this skill in event.");
			return;
		}
		if ((DMEvent.isPlayerParticipant(player.getObjectId()) && DMEvent.isStarted()))
		{
			player.sendMessage("You cant use this skill in event.");
			return;
		}
		if ((TvTEvent.isPlayerParticipant(player.getObjectId()) && TvTEvent.isStarted()))
		{
			player.sendMessage("You cant use this skill in event.");
			return;
		}
		if ((CTFEvent.isPlayerParticipant(player.getObjectId()) && CTFEvent.isStarted()))
		{
			player.sendMessage("You cant use this skill in event.");
			return;
		}
		if ((FOSEvent.isPlayerParticipant(player.getObjectId()) && FOSEvent.isStarted()))
		{
			player.sendMessage("You cant use this skill in event.");
			return;
		}
		for (L2Object obj : targets)
		{
			// The target must be a player.
			if (!(obj instanceof Player))
				continue;
			
			// Can't summon yourself.
			final Player target = ((Player) obj);
			if (activeChar == target)
				continue;
			
			// Check target status.
			if (!Player.checkSummonTargetStatus(target, player))
				continue;
			
			// Check target distance.
			if (Util.checkIfInRange(50, activeChar, target, false))
				continue;
			
			// Check target teleport request status.
			if (!target.teleportRequest(player, skill))
			{
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.S1_ALREADY_SUMMONED).addPcName(target));
				continue;
			}
			
			// Send a request for Summon Friend skill.
			if (skill.getId() == 1403)
			{
				final ConfirmDlg confirm = new ConfirmDlg(SystemMessageId.S1_WISHES_TO_SUMMON_YOU_FROM_S2_DO_YOU_ACCEPT.getId());
				confirm.addPcName(player);
				confirm.addZoneName(activeChar.getX(), activeChar.getY(), activeChar.getZ());
				confirm.addTime(30000);
				confirm.addRequesterId(player.getObjectId());
				target.sendPacket(confirm);
			}
			else
			{
				Player.teleToTarget(target, player, skill);
				target.teleportRequest(null, null);
			}
		}
	}
	
	@Override
	public L2SkillType[] getSkillIds()
	{
		return SKILL_IDS;
	}
}