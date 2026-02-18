package net.sf.l2j.gameserver.network.clientpackets;

import net.sf.l2j.Config;
import net.sf.l2j.event.bossevent.KTBEvent;
import net.sf.l2j.event.ctf.CTFEvent;
import net.sf.l2j.event.deathmath.DMEvent;
import net.sf.l2j.event.fortress.FOSEvent;
import net.sf.l2j.event.lastman.LMEvent;
import net.sf.l2j.event.tournament.ArenaTask;
import net.sf.l2j.event.tvt.TvTEvent;
import net.sf.l2j.gameserver.instancemanager.SevenSignsFestival;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.olympiad.OlympiadManager;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;

public final class Logout extends L2GameClientPacket
{
	@Override
	protected void readImpl()
	{
	}
	
	@Override
	protected void runImpl()
	{
		final Player player = getClient().getActiveChar();
		if (player == null)
			return;
		
		if (player.getActiveEnchantItem() != null || player.isLocked())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		if (player.isArenaProtection() && ArenaTask.is_started())
		{
			player.sendMessage("You cannot logout while in Tournament Event!");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		// Check if player is in Event
		if (KTBEvent.isPlayerParticipant(player.getObjectId()) && KTBEvent.isStarted() || KTBEvent.isPlayerParticipant(player.getObjectId()) || LMEvent.isStarted() && LMEvent.isPlayerParticipant(player.getObjectId()) || LMEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't logout during Event.");
			return;
		}
		if(DMEvent.isStarted() && DMEvent.isPlayerParticipant(player.getObjectId()) || DMEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't logout during Event.");
			return;
		}
		if(TvTEvent.isStarted() && TvTEvent.isPlayerParticipant(player.getObjectId()) || TvTEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't logout during Event.");
			return;
		}
		if(CTFEvent.isStarted() && CTFEvent.isPlayerParticipant(player.getObjectId()) || CTFEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't logout during Event.");
			return;
		}
		if(FOSEvent.isStarted() && FOSEvent.isPlayerParticipant(player.getObjectId()) || FOSEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't logout during Event.");
			return;
		}
		if (player.isInsideZone(ZoneId.NO_RESTART))
		{
			player.sendPacket(SystemMessageId.NO_LOGOUT_HERE);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		if(Config.BLOCK_EXIT_OLY)
		{
			if(OlympiadManager.getInstance().isRegistered(player) || OlympiadManager.getInstance().isRegisteredInComp(player))
			{
				player.sendMessage("No Exit game or logout in registered Olympiads!");
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
		}
		if (AttackStanceTaskManager.getInstance().isInAttackStance(player))
		{
			player.sendPacket(SystemMessageId.CANT_LOGOUT_WHILE_FIGHTING);
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}

		if (player.isFestivalParticipant())
		{
			if (SevenSignsFestival.getInstance().isFestivalInitialized())
			{
				player.sendPacket(SystemMessageId.NO_LOGOUT_HERE);
				player.sendPacket(ActionFailed.STATIC_PACKET);
				return;
			}
			
			if (player.isInParty())
				player.getParty().broadcastToPartyMembers(SystemMessage.sendString(player.getName() + " has been removed from the upcoming festival."));
		}
		FOSEvent.onLogout(player);
		DMEvent.onLogout(player);
		LMEvent.onLogout(player);
		KTBEvent.onLogout(player);
		player.removeFromBossZone();
		player.logout();
	}
}