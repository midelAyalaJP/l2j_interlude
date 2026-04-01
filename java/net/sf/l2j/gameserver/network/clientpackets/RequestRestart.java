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
import net.sf.l2j.gameserver.network.L2GameClient;
import net.sf.l2j.gameserver.network.L2GameClient.GameClientState;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CharSelectInfo;
import net.sf.l2j.gameserver.network.serverpackets.RestartResponse;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;
import net.sf.l2j.protection.hwid.HwidDAO;

public final class RequestRestart extends L2GameClientPacket
{
	private final HwidDAO dao = new HwidDAO();
	
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

		if (player.getActiveEnchantItem() != null || player.isLocked() || player.isInStoreMode())
		{
			sendPacket(RestartResponse.valueOf(false));
			return;
		}
		if (KTBEvent.isPlayerParticipant(player.getObjectId()) && KTBEvent.isStarted() || KTBEvent.isPlayerParticipant(player.getObjectId()) || LMEvent.isStarted() && LMEvent.isPlayerParticipant(player.getObjectId()) || LMEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't restart during Event.");
			sendPacket(RestartResponse.valueOf(false));
			return;
		}
		if(DMEvent.isStarted() && DMEvent.isPlayerParticipant(player.getObjectId()) || DMEvent.isPlayerParticipant(player.getObjectId()))
		{
			player.sendMessage("You can't restart during Event.");
			sendPacket(RestartResponse.valueOf(false));
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
			player.sendMessage("You can't restart during Event.");
			sendPacket(RestartResponse.valueOf(false));
			return;
		}
		if ((player.isInArenaEvent() || player.isArenaProtection()) && ArenaTask.is_started())
		{
			player.sendMessage("You cannot logout while in Tournament Event!");
			player.sendPacket(ActionFailed.STATIC_PACKET);
			sendPacket(RestartResponse.valueOf(false));
			return;
		}
		if (player.isInsideZone(ZoneId.NO_RESTART))
		{
			player.sendPacket(SystemMessageId.NO_RESTART_HERE);
			sendPacket(RestartResponse.valueOf(false));
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
			player.sendPacket(SystemMessageId.CANT_RESTART_WHILE_FIGHTING);
			sendPacket(RestartResponse.valueOf(false));
			return;
		}
		// delete box from the world
		if (player._active_boxes != -1)
		{
			player.decreaseBoxes();
		}
		if (player.isFestivalParticipant())
		{
			if (SevenSignsFestival.getInstance().isFestivalInitialized())
			{
				player.sendPacket(SystemMessageId.NO_RESTART_HERE);
				sendPacket(RestartResponse.valueOf(false));
				return;
			}
			
			if (player.isInParty())
				player.getParty().broadcastToPartyMembers(SystemMessage.sendString(player.getName() + " has been removed from the upcoming festival."));
		}
		dao.restartAndDisconnetion(player);
		
		player.removeFromBossZone();
		
		final L2GameClient client = getClient();
		
		// detach the client from the char so that the connection isnt closed in the deleteMe
		player.setClient(null);
		
		// removing player from the world
		player.deleteMe();
		
		client.setActiveChar(null);
		client.setState(GameClientState.AUTHED);
		
		sendPacket(RestartResponse.valueOf(true));
		
		// send char list
		final CharSelectInfo cl = new CharSelectInfo(client.getAccountName(), client.getSessionId().playOkID1);
		sendPacket(cl);
		client.setCharSelection(cl.getCharInfo());
	}
}