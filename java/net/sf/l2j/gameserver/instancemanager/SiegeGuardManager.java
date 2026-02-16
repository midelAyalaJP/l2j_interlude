package net.sf.l2j.gameserver.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Castle;

public class SiegeGuardManager
{
	private static final Logger _log = Logger.getLogger(SiegeGuardManager.class.getName());

	private final Castle _castle;

	private final List<L2Spawn> _defaultSpawns = new ArrayList<>();
	private final List<L2Spawn> _hiredSpawns = new ArrayList<>();

	private boolean _defaultLoaded = false;
	private boolean _hiredLoaded = false;

	public SiegeGuardManager(Castle castle)
	{
		_castle = castle;
	}

	// =======================================
	// Admin / Tickets API
	// =======================================

	public void addSiegeGuard(Player activeChar, int npcId)
	{
		if (activeChar == null)
			return;

		addSiegeGuard(activeChar.getX(), activeChar.getY(), activeChar.getZ(), activeChar.getHeading(), npcId);
	}

	public void addSiegeGuard(int x, int y, int z, int heading, int npcId)
	{
		saveSiegeGuard(x, y, z, heading, npcId, 0);
		_defaultLoaded = false;
	}

	public void hireMerc(Player activeChar, int npcId)
	{
		if (activeChar == null)
			return;

		hireMerc(activeChar.getX(), activeChar.getY(), activeChar.getZ(), activeChar.getHeading(), npcId);
	}

	public void hireMerc(int x, int y, int z, int heading, int npcId)
	{
		saveSiegeGuard(x, y, z, heading, npcId, 1);
		_hiredLoaded = false;
	}

	public void removeMerc(int npcId, int x, int y, int z)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(
				"DELETE FROM castle_siege_guards WHERE castleId=? AND npcId=? AND x=? AND y=? AND z=? AND isHired=1"))
		{
			ps.setInt(1, _castle.getCastleId());
			ps.setInt(2, npcId);
			ps.setInt(3, x);
			ps.setInt(4, y);
			ps.setInt(5, z);
			ps.execute();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error deleting hired siege guard at " + x + ',' + y + ',' + z + ": " + e.getMessage(), e);
		}
		finally
		{
			_hiredLoaded = false;
		}
	}

	public void removeMercs()
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(
				"DELETE FROM castle_siege_guards WHERE castleId=? AND isHired=1"))
		{
			ps.setInt(1, _castle.getCastleId());
			ps.execute();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error deleting hired siege guards for castle " + _castle.getName() + ": " + e.getMessage(), e);
		}
		finally
		{
			_hiredLoaded = false;
		}
	}

	// =======================================
	// Lifecycle (Peace / Siege)
	// =======================================

	/** Fora da siege (peace). Spawna guardas default (isHired=0) com respawn. */
	public void spawnDefaultGuards()
	{
		loadDefaultIfNeeded();

		for (L2Spawn sp : _defaultSpawns)
			spawnDefaultSafe(sp);
	}

	/** Durante siege. Spawna mercenários (isHired=1). Só se castelo tiver dono. */
	public void spawnHiredGuardsForSiege()
	{
		// BUG FIX: só faz sentido se tiver lord
		if (_castle.getOwnerId() <= 0)
			return;

		loadHiredIfNeeded();

		final int max = MercTicketManager.getInstance().getMaxAllowedMerc(_castle.getCastleId());
		int count = 0;

		for (L2Spawn sp : _hiredSpawns)
		{
			if (count >= max)
				break;

			spawnHiredSafe(sp); // spawn único
			count++;
		}
	}

	/** Remove TODOS os spawns (default + hired). */
	public void despawnAll()
	{
		despawnList(_defaultSpawns);
		despawnList(_hiredSpawns);
	}

	/** Remove apenas hired. */
	public void despawnHired()
	{
		despawnList(_hiredSpawns);
	}

	private static void despawnList(List<L2Spawn> list)
	{
		for (L2Spawn sp : list)
		{
			if (sp == null)
				continue;

			try
			{
				sp.setRespawnState(false);

				final L2Npc npc = sp.getNpc();
				if (npc != null)
					npc.deleteMe();

			 
			}
			catch (Exception e)
			{
				_log.log(Level.WARNING, "Error despawning siege guard: " + e.getMessage(), e);
			}
		}
	}

	private static void spawnDefaultSafe(L2Spawn sp)
	{
		if (sp == null)
			return;

		try
		{
			

			final L2Npc npc = sp.getNpc();
			sp.setRespawnState(true);
			if (npc == null)
				sp.doSpawn(false);
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error spawning default siege guard: " + e.getMessage(), e);
		}
	}

	private static void spawnHiredSafe(L2Spawn sp)
	{
		if (sp == null)
			return;

		try
		{
			sp.setRespawnState(false);

			final L2Npc npc = sp.getNpc();
			if (npc == null)
			{
				// hired: spawn único (sem respawn)
				sp.doSpawn(false);
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error spawning hired siege guard: " + e.getMessage(), e);
		}
	}

	// =======================================
	// Loaders
	// =======================================

	private void loadDefaultIfNeeded()
	{
		if (_defaultLoaded)
			return;

		_defaultSpawns.clear();
		loadFromDb(_defaultSpawns, 0);
		_defaultLoaded = true;
	}

	private void loadHiredIfNeeded()
	{
		if (_hiredLoaded)
			return;

		_hiredSpawns.clear();
		loadFromDb(_hiredSpawns, 1);
		_hiredLoaded = true;
	}

	private void loadFromDb(List<L2Spawn> out, int isHired)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(
				"SELECT npcId,x,y,z,heading,respawnDelay FROM castle_siege_guards WHERE castleId=? AND isHired=?"))
		{
			ps.setInt(1, _castle.getCastleId());
			ps.setInt(2, isHired);

			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final int npcId = rs.getInt("npcId");
					final NpcTemplate template = NpcTable.getInstance().getTemplate(npcId);
					if (template == null)
					{
						_log.warning("Missing npc template for id: " + npcId + " (castle " + _castle.getName() + ")");
						continue;
					}

					final L2Spawn spawn = new L2Spawn(template);
					spawn.setLoc(rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getInt("heading"));

					final int resp = rs.getInt("respawnDelay");
					spawn.setRespawnDelay(resp);

					out.add(spawn);
				}
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error loading siege guards for castle " + _castle.getName() + " hired=" + isHired + ": " + e.getMessage(), e);
		}
	}

	private void saveSiegeGuard(int x, int y, int z, int heading, int npcId, int isHire)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement(
				"INSERT INTO castle_siege_guards (castleId, npcId, x, y, z, heading, respawnDelay, isHired) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"))
		{
			ps.setInt(1, _castle.getCastleId());
			ps.setInt(2, npcId);
			ps.setInt(3, x);
			ps.setInt(4, y);
			ps.setInt(5, z);
			ps.setInt(6, heading);

			// default respawn, hired sem respawn
			ps.setInt(7, (isHire == 1 ? 0 : 600));
			ps.setInt(8, isHire);

			ps.execute();
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Error adding siege guard for castle " + _castle.getName() + ": " + e.getMessage(), e);
		}
	}

	public Castle getCastle()
	{
		return _castle;
	}
}
