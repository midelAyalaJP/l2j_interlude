package net.sf.l2j.gameserver.network.serverpackets;

import net.sf.l2j.Config;
import net.sf.l2j.event.bossevent.KTBEvent;
import net.sf.l2j.event.ctf.CTFEvent;
import net.sf.l2j.event.deathmath.DMEvent;
import net.sf.l2j.event.fortress.FOSEvent;
import net.sf.l2j.event.lastman.LMEvent;
import net.sf.l2j.event.tvt.TvTEvent;
import net.sf.l2j.gameserver.instancemanager.CastleManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2SiegeClan;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Castle;

public class Die extends L2GameServerPacket
{
	private final int _charObjId;
	private final boolean _fake;
	
	private boolean _sweepable;
	private boolean _allowFixedRes;
	private L2Clan _clan;
	Creature _activeChar;
	private boolean _canEvent;
	
	public Die(Creature cha)
	{
		_activeChar = cha;
		_charObjId = cha.getObjectId();
		_fake = !cha.isDead();
		
		if (cha instanceof Player)
		{
			Player player = (Player) cha;
			_allowFixedRes = player.getAccessLevel().allowFixedRes();
			_clan = player.getClan();
			_canEvent = !((cha.isInArenaEvent() || TvTEvent.isStarted() && TvTEvent.isPlayerParticipant(_charObjId) || CTFEvent.isStarted() && CTFEvent.isPlayerParticipant(_charObjId) || KTBEvent.isStarted() && KTBEvent.isPlayerParticipant(_charObjId) || LMEvent.isStarted() && LMEvent.isPlayerParticipant(_charObjId) || DMEvent.isStarted() && DMEvent.isPlayerParticipant(_charObjId) || FOSEvent.isStarted() && FOSEvent.isPlayerParticipant(_charObjId)));
			
		}
		else if (cha instanceof Attackable)
			_sweepable = ((Attackable) cha).isSpoiled();
	}
	
	@Override
	protected final void writeImpl()
	{
		if (_fake)
			return;
		
		writeC(0x06);
		writeD(_charObjId);
		
		if (_activeChar instanceof Player)
		{
			Player player = (Player) _activeChar;
			if(player.getDungeon() != null)
			{
				writeD(0x00);
				writeD(0x00); // to hide away
				writeD(0x00); // to castle
				writeD(0x00); // to siege HQ
				writeD(0x00); // sweepable (blue glow)
				writeD(0x00); // FIXED
				return;
			}
		}
		writeD(_canEvent ? 0x01 : 0); // 6d 00 00 00 00 - to nearest village
		
		if (_canEvent && _clan != null)
		{
			L2SiegeClan siegeClan = null;
			boolean isInDefense = false;
			
			Castle castle = CastleManager.getInstance().getCastle(_activeChar);
			if (castle != null && castle.getSiege().isInProgress())
			{
				// siege in progress
				siegeClan = castle.getSiege().getAttackerClan(_clan);
				if (siegeClan == null && castle.getSiege().checkIsDefender(_clan))
					isInDefense = true;
			}
			
			writeD(_clan.hasHideout() ? 0x01 : 0x00); // to hide away
			writeD(_clan.hasCastle() || isInDefense ? 0x01 : 0x00); // to castle
			writeD(siegeClan != null && !isInDefense && !siegeClan.getFlags().isEmpty() ? 0x01 : 0x00); // to siege HQ
		}
		else
		{
			writeD(0x00); // to hide away
			writeD(0x00); // to castle
			writeD(0x00); // to siege HQ
		}
		
		writeD(_sweepable ? 0x01 : 0x00); // sweepable (blue glow)
		if (Config.CUSTOM_TELEGIRAN_ON_DIE)
			writeD(_canEvent ? 0x01 : 0x00); // FIXED
		else
			writeD(_allowFixedRes ? 0x01 : 0x00); // FIXED
	}
}