package net.sf.l2j.gameserver.model;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.actor.Player;

/**
 * Persistência de Talentos (trees/skills/levels + pontos disponíveis). - character_talents: (charId, treeId, skillId) -> level - character_talent_points: (charId, treeId) -> points Cache em memória por jogador para uso rápido (BBS, learnTalent, etc).
 */
public final class TalentMemoService
{
	// =========================
	// SQL
	// =========================
	private static final String SQL_LOAD_TALENTS = "SELECT treeId, skillId, level FROM character_talents WHERE charId=?";
	
	private static final String SQL_LOAD_POINTS = "SELECT treeId, points FROM character_talent_points WHERE charId=?";
	
	private static final String SQL_SAVE_TALENT = "INSERT INTO character_talents (charId, treeId, skillId, level) VALUES (?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE level=VALUES(level)";
	
	private static final String SQL_SAVE_POINTS = "INSERT INTO character_talent_points (charId, treeId, points) VALUES (?, ?, ?) " + "ON DUPLICATE KEY UPDATE points=VALUES(points)";
	
	private static final String SQL_DELETE_TREE_TALENTS = "DELETE FROM character_talents WHERE charId=? AND treeId=?";
	
	private static final String SQL_DELETE_TREE_POINTS = "DELETE FROM character_talent_points WHERE charId=? AND treeId=?";
	
	// =========================
	// Cache
	// =========================
	/**
	 * Cache de levels por jogador: charId -> (treeId -> (skillId -> level))
	 */
	private static final Map<Integer, Map<String, Map<Integer, Integer>>> TALENTS_CACHE = new ConcurrentHashMap<>();
	
	/**
	 * Cache de pontos por jogador: charId -> (treeId -> points)
	 */
	private static final Map<Integer, Map<String, Integer>> POINTS_CACHE = new ConcurrentHashMap<>();
	
	private TalentMemoService()
	{
		// utility
	}
	
	// =========================
	// Lifecycle
	// =========================
	
	public static void load(Player player)
	{
		if (player == null)
			return;
		
		final int charId = player.getObjectId();
		
		// Evita duplicar carga
		TALENTS_CACHE.computeIfAbsent(charId, k -> new ConcurrentHashMap<>());
		POINTS_CACHE.computeIfAbsent(charId, k -> new ConcurrentHashMap<>());
		
		loadTalentsFromDb(charId);
		loadPointsFromDb(charId);
	}
	
	public static void clear(Player player)
	{
		if (player == null)
			return;
		
		final int charId = player.getObjectId();
		TALENTS_CACHE.remove(charId);
		POINTS_CACHE.remove(charId);
	}
	
	// =========================
	// Getters (Memo)
	// =========================
	
	public static Map<Integer, Integer> getMemoTalents(Player player, String treeId)
	{
		if (player == null || treeId == null)
			return Collections.emptyMap();
		
		final int charId = player.getObjectId();
		final Map<String, Map<Integer, Integer>> perTree = TALENTS_CACHE.get(charId);
		if (perTree == null)
			return Collections.emptyMap();
		
		final Map<Integer, Integer> learned = perTree.get(treeId);
		if (learned == null || learned.isEmpty())
			return Collections.emptyMap();
		
		return new HashMap<>(learned);
	}
	
	public static int getMemoPoints(Player player, String treeId)
	{
		if (player == null || treeId == null)
			return 0;
		
		final int charId = player.getObjectId();
		final Map<String, Integer> perTree = POINTS_CACHE.get(charId);
		if (perTree == null)
			return 0;
		
		return perTree.getOrDefault(treeId, 0);
	}
	
	// =========================
	// Saves (DB + cache)
	// =========================
	
	public static void save(Player player, String treeId, int skillId, int level)
	{
		if (player == null || treeId == null || treeId.isEmpty() || skillId <= 0 || level < 0)
			return;
		
		final int charId = player.getObjectId();
		
		// Cache
		TALENTS_CACHE.computeIfAbsent(charId, k -> new ConcurrentHashMap<>());
		final Map<String, Map<Integer, Integer>> perTree = TALENTS_CACHE.get(charId);
		perTree.computeIfAbsent(treeId, k -> new ConcurrentHashMap<>());
		
		final Map<Integer, Integer> learned = perTree.get(treeId);
		learned.put(skillId, level);
		
		// DB
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SQL_SAVE_TALENT))
		{
			ps.setInt(1, charId);
			ps.setString(2, treeId);
			ps.setInt(3, skillId);
			ps.setInt(4, level);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			// Em L2J, você pode trocar por LOGGER.error(...)
			e.printStackTrace();
		}
	}
	
	public static void savePoints(Player player, String treeId, int points)
	{
		if (player == null || treeId == null || treeId.isEmpty())
			return;
		
		final int charId = player.getObjectId();
		final int safe = Math.max(0, points);
		
		// Cache
		POINTS_CACHE.computeIfAbsent(charId, k -> new ConcurrentHashMap<>());
		POINTS_CACHE.get(charId).put(treeId, safe);
		
		// DB
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SQL_SAVE_POINTS))
		{
			ps.setInt(1, charId);
			ps.setString(2, treeId);
			ps.setInt(3, safe);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// =========================
	// Reset / Maintenance (opcional)
	// =========================
	
	public static void resetTree(Player player, String treeId)
	{
		if (player == null || treeId == null || treeId.isEmpty())
			return;
		
		final int charId = player.getObjectId();
		
		// Cache
		final Map<String, Map<Integer, Integer>> perTree = TALENTS_CACHE.get(charId);
		if (perTree != null)
			perTree.remove(treeId);
		
		final Map<String, Integer> perPoints = POINTS_CACHE.get(charId);
		if (perPoints != null)
			perPoints.remove(treeId);
		
		// DB
		try (Connection con = ConnectionPool.getConnection())
		{
			try (PreparedStatement ps = con.prepareStatement(SQL_DELETE_TREE_TALENTS))
			{
				ps.setInt(1, charId);
				ps.setString(2, treeId);
				ps.executeUpdate();
			}
			
			try (PreparedStatement ps = con.prepareStatement(SQL_DELETE_TREE_POINTS))
			{
				ps.setInt(1, charId);
				ps.setString(2, treeId);
				ps.executeUpdate();
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	// =========================
	// Internal DB loaders
	// =========================
	
	private static void loadTalentsFromDb(int charId)
	{
		final Map<String, Map<Integer, Integer>> perTree = TALENTS_CACHE.get(charId);
		if (perTree == null)
			return;
		
		// limpa e recarrega
		perTree.clear();
		
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SQL_LOAD_TALENTS))
		{
			ps.setInt(1, charId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final String treeId = rs.getString("treeId");
					final int skillId = rs.getInt("skillId");
					final int level = rs.getInt("level");
					
					if (treeId == null || treeId.isEmpty() || skillId <= 0 || level <= 0)
						continue;
					
					perTree.computeIfAbsent(treeId, k -> new ConcurrentHashMap<>());
					perTree.get(treeId).put(skillId, level);
					
					final Player player = L2World.getInstance().getPlayer(charId);
					
					if (player != null)
					{
						final L2Skill sk = SkillTable.getInstance().getInfo(skillId, level);
						if (sk != null)
						{
							player.addSkill(sk, false);
							player.sendSkillList();
						}
					}
					
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	private static void loadPointsFromDb(int charId)
	{
		final Map<String, Integer> perTree = POINTS_CACHE.get(charId);
		if (perTree == null)
			return;
		
		perTree.clear();
		
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(SQL_LOAD_POINTS))
		{
			ps.setInt(1, charId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				while (rs.next())
				{
					final String treeId = rs.getString("treeId");
					final int points = rs.getInt("points");
					
					if (treeId == null || treeId.isEmpty())
						continue;
					
					perTree.put(treeId, Math.max(0, points));
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}