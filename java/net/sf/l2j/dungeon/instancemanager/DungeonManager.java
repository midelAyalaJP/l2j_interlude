package net.sf.l2j.dungeon.instancemanager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

import net.sf.l2j.commons.lang.Tokenizer;
import net.sf.l2j.dungeon.Dungeon;
import net.sf.l2j.dungeon.data.DungeonData;
import net.sf.l2j.dungeon.data.template.DungeonTemplate;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.model.actor.Player;

public class DungeonManager
{
	private static Logger log = Logger.getLogger(DungeonManager.class.getName());
	
	private final List<Dungeon> running = new CopyOnWriteArrayList<>();
	
	public void handleEnterDungeonId(Player player, Tokenizer tokenizer)
	{
		int dungeonId = tokenizer.getAsInteger(2, 0);
		if (isPlayerEligibleForDungeon(player, dungeonId))
		{
			enterDungeon(dungeonId, player);
		}
		else
		{
			int playerId = player.getObjectId();
			
			long nextJoin = getNextJoinTime(dungeonId, playerId);
			
			if (nextJoin > 0)
			{
				long currentTime = System.currentTimeMillis();
				if (currentTime < nextJoin)
				{
					long remainingTime = nextJoin - currentTime;
					long minutes = (remainingTime / (60 * 1000)) % 60;
					long seconds = (remainingTime / 1000) % 60;
					player.sendMessage(String.format("You cannot enter this dungeon yet. Remaining time: %d min %d seg", minutes, seconds));
					return;
				}
			}
			
		}
	}
	
	private void enterDungeon(int dungeonId, Player player)
	{
		final DungeonTemplate template = DungeonData.getInstance().getDungeon(dungeonId);
		if (template == null)
		{
			player.sendMessage("Invalid dungeon ID.");
			return;
		}
		
		// Requisitos (VIP/HERO)
		if (template._vipOnly && !player.isVip()) // adapte pro seu método/flag de VIP
		{
			player.sendMessage("This dungeon is VIP only.");
			return;
		}
		
		if (template._heroOnly && !player.isHero())
		{
			player.sendMessage("This dungeon is HERO only.");
			return;
		}
		
		final List<Player> party = new CopyOnWriteArrayList<>();
		
		// Monta lista conforme o modo
		switch (template._type)
		{
			case SOLO:
			{
				if (player.isInParty())
				{
					player.sendMessage("This dungeon is solo. Leave your party to enter.");
					return;
				}
				party.add(player);
				break;
			}
			
			case PARTY:
			{
				if (!player.isInParty())
				{
					player.sendMessage("This dungeon requires a party.");
					return;
				}
				if (template._leaderOnly && !player.getParty().isLeader(player))
				{
					player.sendMessage("Only the party leader can enter this dungeon.");
					return;
				}
				
				for (Player member : player.getParty().getPartyMembers())
				{
					if (member == null)
						continue;
					
					if (!isPlayerEligibleForDungeon(member, dungeonId))
					{
						player.sendMessage(member.getName() + " is not eligible for the dungeon.");
						return;
					}
					party.add(member);
				}
				break;
			}
			
			case CLAN:
			{
				if (player.getClan() == null)
				{
					player.sendMessage("This dungeon requires a clan.");
					return;
				}
				
				 
				if (!player.isInParty())
				{
					player.sendMessage("This dungeon requires a party group from the same clan.");
					return;
				}
				if (template._leaderOnly && !player.getParty().isLeader(player))
				{
					player.sendMessage("Only the party leader can enter this dungeon.");
					return;
				}
				
				final int clanId = player.getClanId();
				for (Player member : player.getParty().getPartyMembers())
				{
					if (member == null)
						continue;
					
					if (member.getClanId() != clanId)
					{
						player.sendMessage(member.getName() + " is not in your clan.");
						return;
					}
					
					if (!isPlayerEligibleForDungeon(member, dungeonId))
					{
						player.sendMessage(member.getName() + " is not eligible for the dungeon.");
						return;
					}
					
					party.add(member);
				}
				break;
			}
		}
		
 
		final int size = party.size();
		if (size < template._minPlayers)
		{
			player.sendMessage("Not enough players. Minimum required: " + template._minPlayers);
			return;
		}
		if (size > template._maxPlayers)
		{
			player.sendMessage("Too many players. Maximum allowed: " + template._maxPlayers);
			return;
		}
		
		// Cooldown check (leader já checou, mas aqui mantemos segurança)
		final long now = System.currentTimeMillis();
		for (Player m : party)
		{
			final long nextJoin = getNextJoinTime(template._id, m.getObjectId());
			if (nextJoin > now)
			{
				long remaining = nextJoin - now;
				long minutes = (remaining / (60 * 1000)) % 60;
				long seconds = (remaining / 1000) % 60;
				player.sendMessage(m.getName() + " cannot enter yet. Remaining: " + minutes + " min " + seconds + " sec");
				return;
			}
		}
		
		// Cobrança de entrada
		if (template._entryFee != null && template._entryFee.isEnabled())
		{
			if (!chargeEntryFee(template, player, party))
				return; // mensagem já enviada
		}
		
		// Cria dungeon
		final Dungeon dungeon = new Dungeon(template, party);
		running.add(dungeon);
		
		// Salva cooldown
		for (Player m : party)
		{
			if (m == null)
				continue;
			
			savePlayerCooldown(template._id, m.getObjectId(), now, template._cooldown, m.getClient().getConnection().getInetAddress().getHostAddress(), 1);
		}
	}
	
	private static boolean chargeEntryFee(DungeonTemplate template, Player leader, List<Player> members)
	{
		final int itemId = template._entryFee.getItemId();
		final long count = template._entryFee.getCount();
		
		switch (template._entryFee.getMode())
		{
			case LEADER:
			{
				if (!hasAndConsume(leader, itemId, count))
				{
					leader.sendMessage("You need " + count + " of itemId " + itemId + " to enter.");
					return false;
				}
				return true;
			}
			
			case PER_PLAYER:
			{
				// Primeiro valida todo mundo, depois consome (evita consumir de alguns e falhar em outros)
				for (Player m : members)
				{
					if (m == null)
						continue;
					
					if (m.getInventory().getInventoryItemCount(itemId, -1) < count)
					{
						leader.sendMessage(m.getName() + " does not have the entry fee (" + count + " of itemId " + itemId + ").");
						return false;
					}
				}
				
				for (Player m : members)
				{
					if (m == null)
						continue;
					
					if (!hasAndConsume(m, itemId, count))
						return false;
				}
				return true;
			}
			
			default:
				return true;
		}
	}
	
	private static boolean hasAndConsume(Player player, int itemId, long count)
	{
		if (count <= 0 || itemId <= 0)
			return true;

		if (player.getInventory().getInventoryItemCount(itemId, -1) < count)
			return false;

		return player.destroyItemByItemId("DungeonFee", itemId, (int)count, player, true);
	}
	
	private static long getNextJoinTime(int dungeonId, int playerId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT next_join FROM dungeon_cooldowns WHERE dungeon_id = ? AND player_id = ?"))
		{
			
			ps.setInt(1, dungeonId);
			ps.setInt(2, playerId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return rs.getLong("next_join");
				}
			}
		}
		catch (Exception e)
		{
			log.severe("DungeonManager: Error retrieving next join time: " + e.getMessage());
			e.printStackTrace();
		}
		return 0;
	}
	
	public synchronized void removeDungeon(Dungeon dungeon)
	{
		running.remove(dungeon);
		
	}
	
	private boolean isPlayerEligibleForDungeon(Player player, int dungeonId)
	{
		if (isInDungeon(player) || player.isInOlympiadMode())
			return false;
		
		int savedStage = getPlayerSavedStage(dungeonId, player.getObjectId());
		DungeonTemplate template = DungeonData.getInstance().getDungeon(dungeonId);
		
		if (savedStage >= template._stages.size())
		{
			long nextJoin = getNextJoinTime(dungeonId, player.getObjectId());
			return System.currentTimeMillis() >= nextJoin;
		}
		
		return true;
	}
	
	public boolean isInDungeon(Player player)
	{
		return running.stream().anyMatch(dungeon -> dungeon.getPlayers().contains(player));
	}
	
	public void updateStage(int dungeonId, int playerId, int stage)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("UPDATE dungeon_cooldowns SET stage = ? WHERE dungeon_id = ? AND player_id = ?"))
		{
			ps.setInt(1, stage);
			ps.setInt(2, dungeonId);
			ps.setInt(3, playerId);
			ps.executeUpdate();
		}
		catch (Exception e)
		{
			log.severe("DungeonManager: Failed to update stage: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public void savePlayerCooldown(int dungeonId, int playerId, long lastJoin, long cooldownMillis, String ipAddress, int stage)
	{
		try (Connection con = ConnectionPool.getConnection();
			PreparedStatement ps = con.prepareStatement("INSERT INTO dungeon_cooldowns (dungeon_id, player_id, last_join, next_join, ip_address, stage) " + "VALUES (?, ?, ?, ?, ?, ?) " + "ON DUPLICATE KEY UPDATE last_join = VALUES(last_join), next_join = VALUES(next_join), ip_address = VALUES(ip_address), stage = VALUES(stage)"))
		{
			
			long nextJoin = lastJoin + cooldownMillis;
			ps.setInt(1, dungeonId);
			ps.setInt(2, playerId);
			ps.setLong(3, lastJoin);
			ps.setLong(4, nextJoin);
			ps.setString(5, ipAddress);
			ps.setInt(6, stage);
			
			ps.executeUpdate();
			
		}
		catch (Exception e)
		{
			log.severe("SystemDungeonManager: Error saving cooldown: " + e.getMessage());
			e.printStackTrace();
		}
	}
	
	public int getPlayerSavedStage(int dungeonId, int playerId)
	{
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement("SELECT stage FROM dungeon_cooldowns WHERE dungeon_id = ? AND player_id = ?"))
		{
			ps.setInt(1, dungeonId);
			ps.setInt(2, playerId);
			
			try (ResultSet rs = ps.executeQuery())
			{
				if (rs.next())
				{
					return rs.getInt("stage");
				}
			}
		}
		catch (Exception e)
		{
			log.severe("DungeonManager: Error retrieving saved stage: " + e.getMessage());
			e.printStackTrace();
		}
		return 1; // Default stage
	}
	
	public static DungeonManager getInstance()
	{
		return SingletonHolder.instance;
	}
	
	private static class SingletonHolder
	{
		protected static final DungeonManager instance = new DungeonManager();
	}
}