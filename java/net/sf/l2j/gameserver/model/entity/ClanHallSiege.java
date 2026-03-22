package net.sf.l2j.gameserver.model.entity;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.instancemanager.ClanHallManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2SiegeClan;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.scriptings.Quest;

public abstract class ClanHallSiege extends Quest implements Siegable
{
	protected static final Logger _log = Logger.getLogger(ClanHallSiege.class.getName());

	private static final String SELECT_ATTACKERS = "SELECT attacker_id FROM clanhall_siege_attackers WHERE clanhall_id = ?";
	private static final String INSERT_ATTACKERS = "INSERT INTO clanhall_siege_attackers VALUES (?, ?)";
	private static final String DELETE_ATTACKERS = "DELETE FROM clanhall_siege_attackers WHERE clanhall_id = ?";

	public static final int FORTRESS_OF_RESISTANCE = 21;
	public static final int DEVASTATED_CASTLE = 34;
	public static final int BANDIT_STRONGHOLD = 35;
	public static final int RAINBOW_SPRINGS = 62;
	public static final int BEAST_FARM = 63;
	public static final int FORTRESS_OF_DEAD = 64;

	protected final List<L2SiegeClan> _attackers = new CopyOnWriteArrayList<>();
	protected final List<L2SiegeClan> _defenders = new CopyOnWriteArrayList<>();

	protected SiegableHall _hall;
	protected ScheduledFuture<?> _siegeTask;

	protected boolean _missionAccomplished;
	protected boolean _wasPreviouslyOwned;

	protected ClanHallSiege(String name, int hallId, String descr)
	{
		super(-1, name);

		_hall = ClanHallManager.getInstance().getSiegableHall(hallId);
		if (_hall == null)
		{
			_log.warning("Siegable hall " + hallId + " not found.");
			return;
		}

		_hall.setSiege(this);

		if (_hall.getId() != RAINBOW_SPRINGS)
			loadAttackers();

		long delay = _hall.getNextSiegeTime() - System.currentTimeMillis() - 3600000L;
		if (delay < 1000L)
			delay = 1000L;

		_siegeTask = ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				prepareSiege();
			}
		}, delay);
	}

	public abstract L2Clan getWinner();

	public abstract void spawnNpcs();

	public abstract void unspawnNpcs();

	@Override
	public L2SiegeClan getAttackerClan(int clanId)
	{
		for (L2SiegeClan sc : _attackers)
		{
			if (extractClanId(sc) == clanId)
				return sc;
		}
		return null;
	}

	@Override
	public L2SiegeClan getAttackerClan(L2Clan clan)
	{
		return (clan == null) ? null : getAttackerClan(clan.getClanId());
	}

	@Override
	public List<L2SiegeClan> getAttackerClans()
	{
		return _attackers;
	}

	@Override
	public boolean checkIsAttacker(L2Clan clan)
	{
		return getAttackerClan(clan) != null;
	}

	@Override
	public L2SiegeClan getDefenderClan(int clanId)
	{
		for (L2SiegeClan sc : _defenders)
		{
			if (extractClanId(sc) == clanId)
				return sc;
		}
		return null;
	}

	@Override
	public L2SiegeClan getDefenderClan(L2Clan clan)
	{
		return (clan == null) ? null : getDefenderClan(clan.getClanId());
	}

	@Override
	public List<L2SiegeClan> getDefenderClans()
	{
		return _defenders;
	}

	@Override
	public boolean checkIsDefender(L2Clan clan)
	{
		return getDefenderClan(clan) != null;
	}

	@Override
	public List<L2Npc> getFlag(L2Clan clan)
	{
		return Collections.emptyList();
	}

	@Override
	public Calendar getSiegeDate()
	{
		return (_hall != null) ? _hall.getSiegeDate() : null;
	}

	@Override
	public List<Player> getAttackersInZone()
	{
		return new ArrayList<>();
	}

	@Override
	public void startSiege()
	{
		_hall.updateSiegeStatus(SiegeStatus.IN_PROGRESS);
		spawnNpcs();
	}

	@Override
	public void endSiege()
	{
		final L2Clan winner = getWinner();

		if (_missionAccomplished && winner != null)
		{
			_hall.free();
			_hall.setOwner(winner);
		}

		_missionAccomplished = false;
		_hall.updateNextSiege();
		_hall.updateSiegeStatus(SiegeStatus.REGISTRATION_OPENED);

		_attackers.clear();
		_defenders.clear();

		unspawnNpcs();
	}

	protected void prepareSiege()
	{
		prepareSiege(_hall.getNextSiegeTime() - System.currentTimeMillis());
	}

	protected void prepareSiege(long delay)
	{
		if (_hall.getOwnerId() > 0)
		{
			final L2Clan clan = ClanTable.getInstance().getClan(_hall.getOwnerId());
			if (clan != null && !checkIsAttacker(clan))
				addAttackerClan(clan);

			_wasPreviouslyOwned = true;
		}
		else
			_wasPreviouslyOwned = false;

		_hall.free();
		_hall.banishForeigners();
		_hall.updateSiegeStatus(SiegeStatus.REGISTRATION_OVER);

		if (delay < 0L)
			delay = 0L;

		_siegeTask = ThreadPool.schedule(new Runnable()
		{
			@Override
			public void run()
			{
				startSiege();
			}
		}, delay);
	}

	public void cancelSiegeTask()
	{
		if (_siegeTask != null)
			_siegeTask.cancel(false);
	}

	public void loadAttackers()
	{
		if (_hall == null)
			return;

		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement ps = con.prepareStatement(SELECT_ATTACKERS);
			ps.setInt(1, _hall.getId());

			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				final L2Clan clan = ClanTable.getInstance().getClan(rs.getInt("attacker_id"));
				if (clan != null)
					addAttackerClan(clan);
			}
			rs.close();
			ps.close();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Could not load clan hall siege attackers.", e);
		}
	}

	public void saveAttackers()
	{
		if (_hall == null)
			return;

		try (Connection con = ConnectionPool.getConnection())
		{
			PreparedStatement delete = con.prepareStatement(DELETE_ATTACKERS);
			delete.setInt(1, _hall.getId());
			delete.execute();
			delete.close();

			if (!_attackers.isEmpty())
			{
				PreparedStatement insert = con.prepareStatement(INSERT_ATTACKERS);
				for (L2SiegeClan siegeClan : _attackers)
				{
					final int clanId = extractClanId(siegeClan);
					if (clanId <= 0)
						continue;

					insert.setInt(1, _hall.getId());
					insert.setInt(2, clanId);
					insert.addBatch();
				}
				insert.executeBatch();
				insert.close();
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Could not save clan hall siege attackers.", e);
		}
	}

	protected void addAttackerClan(L2Clan clan)
	{
		if (clan == null || checkIsAttacker(clan))
			return;

		final L2SiegeClan siegeClan = createSiegeClan(clan);
		if (siegeClan != null)
			_attackers.add(siegeClan);
	}
	
	protected void removeAttackerClan(L2Clan clan)
	{
	final L2SiegeClan siegeClan = getAttackerClan(clan);
	if (siegeClan != null)
		_attackers.remove(siegeClan);
	}

	protected int extractClanId(L2SiegeClan siegeClan)
	{
		if (siegeClan == null)
			return 0;

		try
		{
			Method m = siegeClan.getClass().getMethod("getClanId");
			Object obj = m.invoke(siegeClan);
			if (obj instanceof Integer)
				return ((Integer) obj).intValue();
		}
		catch (Exception e)
		{
		}

		return 0;
	}

	protected L2SiegeClan createSiegeClan(L2Clan clan)
	{
		if (clan == null)
			return null;

		try
		{
			Constructor<?>[] constructors = L2SiegeClan.class.getConstructors();
			for (Constructor<?> c : constructors)
			{
				Class<?>[] p = c.getParameterTypes();

				if (p.length == 1 && p[0] == L2Clan.class)
					return (L2SiegeClan) c.newInstance(clan);

				if (p.length == 1 && (p[0] == int.class || p[0] == Integer.class))
					return (L2SiegeClan) c.newInstance(Integer.valueOf(clan.getClanId()));
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Could not create L2SiegeClan.", e);
		}

		return null;
	}
}