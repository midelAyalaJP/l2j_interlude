package net.sf.l2j.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.entity.Auction;
import net.sf.l2j.gameserver.model.entity.ClanHall;
import net.sf.l2j.gameserver.model.entity.ClanHallSiege;
import net.sf.l2j.gameserver.model.entity.SiegableHall;
import net.sf.l2j.gameserver.model.zone.type.L2ClanHallZone;

/**
 * Clan hall manager with support for regular clan halls and siegable clan halls.
 */
public class ClanHallManager
{
	protected static final Logger _log = Logger.getLogger(ClanHallManager.class.getName());

	private static final int FORTRESS_OF_RESISTANCE_ID = 21;

	private final Map<String, List<ClanHall>> _allClanHalls;
	private final Map<Integer, ClanHall> _clanHall;
	private final Map<Integer, ClanHall> _freeClanHall;

	private boolean _loaded = false;

	public static ClanHallManager getInstance()
	{
		return SingletonHolder._instance;
	}

	public boolean loaded()
	{
		return _loaded;
	}

	protected ClanHallManager()
	{
		_allClanHalls = new HashMap<>();
		_clanHall = new HashMap<>();
		_freeClanHall = new HashMap<>();
		load();
	}

	private void load()
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement statement = con.prepareStatement("SELECT * FROM clanhall ORDER BY id");
			ResultSet rs = statement.executeQuery();

			while (rs.next())
			{
				final int id = rs.getInt("id");
				final String name = rs.getString("name");
				final int ownerId = rs.getInt("ownerId");
				final int lease = rs.getInt("lease");
				final String desc = rs.getString("desc");
				final String location = rs.getString("location");
				final long paidUntil = rs.getLong("paidUntil");
				final int grade = rs.getInt("Grade");
				final boolean paid = rs.getBoolean("paid");

				final ClanHall ch;

				if (id == FORTRESS_OF_RESISTANCE_ID)
				{
					ch = new SiegableHall(id, name, ownerId, lease, desc, location, paidUntil, grade, paid, 3600000L, new int[]
					{
						1, 0, 0, 20, 0
					});

					long nextSiege = 0L;
					try
					{
						nextSiege = rs.getLong("endDate");
					}
					catch (Exception e)
					{
					}

					if (nextSiege > System.currentTimeMillis())
					{
						final Calendar cal = Calendar.getInstance();
						cal.setTimeInMillis(nextSiege);
						((SiegableHall) ch).setNextSiegeDate(cal);
					}
					else
						((SiegableHall) ch).updateNextSiege();
				}
				else
					ch = new ClanHall(id, name, ownerId, lease, desc, location, paidUntil, grade, paid);

				if (!_allClanHalls.containsKey(location))
					_allClanHalls.put(location, new ArrayList<>());

				_allClanHalls.get(location).add(ch);

				if (ownerId > 0)
				{
					final L2Clan owner = ClanTable.getInstance().getClan(ownerId);
					if (owner != null)
					{
						_clanHall.put(id, ch);
						owner.setHideout(id);
						continue;
					}

					ch.free();
				}

				_freeClanHall.put(id, ch);

				if (!(ch instanceof SiegableHall))
				{
					final Auction auc = AuctionManager.getInstance().getAuction(id);
					if (auc == null && lease > 0)
						AuctionManager.getInstance().initNPC(id);
				}
			}

			rs.close();
			statement.close();

			_log.info("ClanHallManager: Loaded " + getClanHalls().size() + " owned clan halls.");
			_log.info("ClanHallManager: Loaded " + getFreeClanHalls().size() + " free clan halls.");
			_log.info("ClanHallManager: Loaded " + getSiegableHalls().size() + " siegable clan halls.");

			_loaded = true;
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Exception: ClanHallManager.load(): " + e.getMessage(), e);
		}
	}

	public final Map<Integer, ClanHall> getFreeClanHalls()
	{
		return _freeClanHall;
	}

	public final Map<Integer, ClanHall> getClanHalls()
	{
		return _clanHall;
	}

	public final List<ClanHall> getClanHallsByLocation(String location)
	{
		if (!_allClanHalls.containsKey(location))
			return null;

		return _allClanHalls.get(location);
	}

	public final boolean isFree(int chId)
	{
		return _freeClanHall.containsKey(chId);
	}

	public final synchronized void setFree(int chId)
	{
		final ClanHall hall = _clanHall.get(chId);
		if (hall == null)
			return;

		_freeClanHall.put(chId, hall);

		final L2Clan clan = ClanTable.getInstance().getClan(hall.getOwnerId());
		if (clan != null)
			clan.setHideout(0);

		hall.free();
		_clanHall.remove(chId);
	}

	public final synchronized void setOwner(int chId, L2Clan clan)
	{
		if (clan == null)
			return;

		if (!_clanHall.containsKey(chId))
		{
			_clanHall.put(chId, _freeClanHall.get(chId));
			_freeClanHall.remove(chId);
		}
		else
			_clanHall.get(chId).free();

		final L2Clan realClan = ClanTable.getInstance().getClan(clan.getClanId());
		if (realClan != null)
			realClan.setHideout(chId);

		_clanHall.get(chId).setOwner(clan);
	}

	public final ClanHall getClanHallById(int clanHallId)
	{
		if (_clanHall.containsKey(clanHallId))
			return _clanHall.get(clanHallId);

		if (_freeClanHall.containsKey(clanHallId))
			return _freeClanHall.get(clanHallId);

		_log.warning("ClanHall (id: " + clanHallId + ") isn't found in clanhall table.");
		return null;
	}

	public final ClanHall getNearbyClanHall(int x, int y, int maxDist)
	{
		L2ClanHallZone zone = null;

		for (Map.Entry<Integer, ClanHall> ch : _clanHall.entrySet())
		{
			zone = ch.getValue().getZone();
			if (zone != null && zone.getDistanceToZone(x, y) < maxDist)
				return ch.getValue();
		}

		for (Map.Entry<Integer, ClanHall> ch : _freeClanHall.entrySet())
		{
			zone = ch.getValue().getZone();
			if (zone != null && zone.getDistanceToZone(x, y) < maxDist)
				return ch.getValue();
		}

		return null;
	}

	public final ClanHall getClanHallByOwner(L2Clan clan)
	{
		if (clan == null)
			return null;

		for (Map.Entry<Integer, ClanHall> ch : _clanHall.entrySet())
		{
			if (clan.getClanId() == ch.getValue().getOwnerId())
				return ch.getValue();
		}
		return null;
	}

	public final SiegableHall getSiegableHall(int id)
	{
		final ClanHall hall = getClanHallById(id);
		return (hall instanceof SiegableHall) ? (SiegableHall) hall : null;
	}

	public final List<SiegableHall> getSiegableHalls()
	{
		final List<SiegableHall> list = new ArrayList<>();

		for (ClanHall hall : _clanHall.values())
		{
			if (hall instanceof SiegableHall)
				list.add((SiegableHall) hall);
		}

		for (ClanHall hall : _freeClanHall.values())
		{
			if (hall instanceof SiegableHall)
				list.add((SiegableHall) hall);
		}

		return list;
	}

	public final ClanHallSiege getActiveSiege(Creature creature)
	{
		if (creature == null)
			return null;

		for (SiegableHall hall : getSiegableHalls())
		{
			if (hall.getSiege() == null || hall.getSiegeZone() == null)
				continue;

			if (hall.getSiegeZone().isActive() && hall.getSiegeZone().isInsideZone(creature))
				return hall.getSiege();
		}
		return null;
	}

	public final boolean isClanParticipating(L2Clan clan)
	{
		if (clan == null)
			return false;

		for (SiegableHall hall : getSiegableHalls())
		{
			if (hall.getSiege() != null && hall.getSiege().getAttackerClan(clan) != null)
				return true;
		}
		return false;
	}

	public final void saveSiegeAttackers()
	{
		for (SiegableHall hall : getSiegableHalls())
		{
			if (hall.getSiege() != null)
				hall.getSiege().saveAttackers();
		}
	}

	private static class SingletonHolder
	{
		protected static final ClanHallManager _instance = new ClanHallManager();
	}
}