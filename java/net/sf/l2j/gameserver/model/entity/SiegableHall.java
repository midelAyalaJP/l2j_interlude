package net.sf.l2j.gameserver.model.entity;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Calendar;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.instancemanager.ClanHallManager;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.L2DoorInstance;
import net.sf.l2j.gameserver.model.zone.type.L2SiegeZone;
import net.sf.l2j.gameserver.network.SystemMessageId;

public class SiegableHall extends ClanHall
{
	private static final String UPDATE_SIEGEABLE_CLANHALL = "UPDATE clanhall SET ownerId=?, endDate=? WHERE id=?";
	private static final String SELECT_OWNER_ID = "SELECT ownerId FROM clanhall WHERE id=?";

	private static final int CH_MINIMUM_CLAN_LEVEL = 4;
	private static final int CH_MAX_ATTACKERS_NUMBER = 10;

	private final long _siegeLength;
	private final int[] _scheduleConfig;

	private Calendar _nextSiege;
	private SiegeStatus _status = SiegeStatus.REGISTRATION_OPENED;

	private L2SiegeZone _siegeZone;
	private ClanHallSiege _siege;

	public SiegableHall(int id, String name, int ownerId, int lease, String desc, String location, long paidUntil, int grade, boolean paid, long siegeLength, int[] scheduleConfig)
	{
		super(id, name, ownerId, lease, desc, location, paidUntil, grade, paid);

		_siegeLength = siegeLength;
		_scheduleConfig = scheduleConfig;

		_nextSiege = Calendar.getInstance();
		_nextSiege.setTimeInMillis(System.currentTimeMillis());
	}

	@Override
	public void updateDb()
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			int ownerId = getOwnerId();

			// FIX: Fortress of Resistance no debe perder owner automáticamente al iniciar siege.
			// Si alguien intenta guardar ownerId=0 para el hall 21, conservamos el owner actual de DB.
			if (getId() == 21 && ownerId == 0)
			{
				try (PreparedStatement select = con.prepareStatement(SELECT_OWNER_ID))
				{
					select.setInt(1, getId());

					try (ResultSet rs = select.executeQuery())
					{
						if (rs.next())
						{
							final int dbOwnerId = rs.getInt("ownerId");
							if (dbOwnerId > 0)
								ownerId = dbOwnerId;
						}
					}
				}
			}

			try (PreparedStatement statement = con.prepareStatement(UPDATE_SIEGEABLE_CLANHALL))
			{
				statement.setInt(1, ownerId);
				statement.setLong(2, getNextSiegeTime());
				statement.setInt(3, getId());
				statement.execute();
			}
		}
		catch (Exception e)
		{
			_log.warning("Exception: SiegableHall.updateDb(): " + e.getMessage());
		}
	}

	public void setSiege(ClanHallSiege siege)
	{
		_siege = siege;
	}

	public ClanHallSiege getSiege()
	{
		return _siege;
	}

	public Calendar getSiegeDate()
	{
		return _nextSiege;
	}

	public long getSiegeLength()
	{
		return _siegeLength;
	}

	public long getNextSiegeTime()
	{
		return (_nextSiege != null) ? _nextSiege.getTimeInMillis() : 0L;
	}

	public void setNextSiegeDate(long date)
	{
		if (_nextSiege == null)
			_nextSiege = Calendar.getInstance();

		_nextSiege.setTimeInMillis(date);
	}

	public void setNextSiegeDate(Calendar cal)
	{
		_nextSiege = cal;
	}

	public void updateNextSiege()
	{
		Calendar c = Calendar.getInstance();
		c.add(Calendar.DAY_OF_YEAR, _scheduleConfig[0]);
		c.add(Calendar.MONTH, _scheduleConfig[1]);
		c.add(Calendar.YEAR, _scheduleConfig[2]);
		c.set(Calendar.HOUR_OF_DAY, _scheduleConfig[3]);
		c.set(Calendar.MINUTE, _scheduleConfig[4]);
		c.set(Calendar.SECOND, 0);
		c.set(Calendar.MILLISECOND, 0);

		setNextSiegeDate(c);
		updateDb();
	}

	public void addAttacker(L2Clan clan)
	{
		if (getSiege() != null)
			getSiege().addAttackerClan(clan);
	}

	public void removeAttacker(L2Clan clan)
	{
		if (getSiege() != null)
			getSiege().removeAttackerClan(clan);
	}

	public boolean isRegistered(L2Clan clan)
	{
		return getSiege() != null && getSiege().getAttackerClan(clan) != null;
	}

	public SiegeStatus getSiegeStatus()
	{
		return _status;
	}

	public boolean isRegistering()
	{
		return _status == SiegeStatus.REGISTRATION_OPENED;
	}

	public boolean isInSiege()
	{
		return _status == SiegeStatus.IN_PROGRESS;
	}

	public boolean isWaitingBattle()
	{
		return _status == SiegeStatus.REGISTRATION_OVER;
	}

	public void updateSiegeStatus(SiegeStatus status)
	{
		_status = status;
	}

	public L2SiegeZone getSiegeZone()
	{
		if (_siegeZone == null)
		{
			try
			{
				for (L2SiegeZone zone : ZoneManager.getInstance().getAllZones(L2SiegeZone.class))
				{
					if (zone != null && zone.getSiegeObjectId() == getId())
					{
						_siegeZone = zone;
						break;
					}
				}
			}
			catch (Exception e)
			{
				_log.warning("Could not resolve siege zone for clan hall id " + getId() + ": " + e.getMessage());
			}
		}
		return _siegeZone;
	}

	public void spawnDoors()
	{
		spawnDoors(false);
	}

	public void spawnDoors(boolean weak)
	{
		for (L2DoorInstance door : getDoors())
		{
			if (door == null)
				continue;

			if (door.isDead())
				door.doRevive();

			door.closeMe();
			door.setCurrentHpMp(weak ? door.getMaxHp() / 2 : door.getMaxHp(), door.getMaxMp());
		}
	}

	public void registerClan(L2Clan clan, Player player)
	{
		if (clan == null || player == null)
			return;

		if (clan.getLevel() < CH_MINIMUM_CLAN_LEVEL)
			player.sendPacket(SystemMessageId.ONLY_CLAN_LEVEL_4_ABOVE_MAY_SIEGE);
		else if (isWaitingBattle())
			player.sendPacket(SystemMessageId.DEADLINE_FOR_SIEGE_S1_PASSED);
		else if (isInSiege())
			player.sendPacket(SystemMessageId.NOT_SIEGE_REGISTRATION_TIME2);
		else if (getOwnerId() == clan.getClanId())
			player.sendPacket(SystemMessageId.CLAN_THAT_OWNS_CASTLE_IS_AUTOMATICALLY_REGISTERED_DEFENDING);
		else if (clan.hasHideout() || clan.getCastleId() > 0)
			player.sendPacket(SystemMessageId.CLAN_OWNING_CLANHALL_MAY_NOT_SIEGE_CLANHALL);
		else if (isRegistered(clan))
			player.sendPacket(SystemMessageId.ALREADY_REQUESTED_SIEGE_BATTLE);
		else if (ClanHallManager.getInstance().isClanParticipating(clan))
			player.sendMessage("Your clan is already registered on another clan hall siege.");
		else if (_siege != null && _siege.getAttackerClans().size() >= CH_MAX_ATTACKERS_NUMBER)
			player.sendPacket(SystemMessageId.ATTACKER_SIDE_FULL);
		else
			addAttacker(clan);
	}

	public void unregisterClan(L2Clan clan)
	{
		if (!isRegistering() || clan == null)
			return;

		removeAttacker(clan);
	}
}