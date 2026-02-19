package net.sf.l2j.dungeon;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.dungeon.data.template.DungeonTemplate;
import net.sf.l2j.dungeon.holder.SpawnTemplate;
import net.sf.l2j.dungeon.holder.StageTemplate;
import net.sf.l2j.dungeon.instancemanager.DungeonManager;
import net.sf.l2j.event.tournament.InstanceHolder;
import net.sf.l2j.event.tournament.InstanceManager;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.datatables.NpcTable;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.datatables.SpawnTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.network.serverpackets.AbstractNpcInfo.NpcInfo;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage.SMPOS;
import net.sf.l2j.gameserver.network.serverpackets.MagicSkillUse;

public class Dungeon
{
	
	private final DungeonTemplate _template;
	private final List<Player> _players;
	private final Map<L2MonsterInstance, SpawnTemplate> _mobToTemplate = new ConcurrentHashMap<>();
	private ScheduledFuture<?> _dungeonCancelTask;
	private ScheduledFuture<?> _nextTask;
	private ScheduledFuture<?> _timerTask;
	
	private long _stageBeginTime;
	private final InstanceHolder _instance;
	private StageTemplate _currentStage;
	private List<SpawnTemplate> _currentSpawns;
	private int _currentStageIndex = 0;
	
	private ScheduledFuture<?> monitorTask;
	
	public Dungeon(DungeonTemplate template, List<Player> players)
	{
		_template = template;
		_players = players;
		_instance = InstanceManager.getInstance().createInstance();
		beginTeleport();
		
	}
	
	private void beginTeleport()
	{
		if (!getNextStage())
		{
			broadcastScreenMessage("Failed to load dungeon stage!", 5);
			cancelDungeon();
			return;
		}
		
		broadcastScreenMessage("You will be teleported in a few seconds!", 5);
		
		L2Skill skill = SkillTable.getInstance().getInfo(1050, 1);
		
		for (Player player : _players)
		{
			player.setIsParalyzed(true);
			player.broadcastPacket(new MagicSkillUse(player, player, skill.getId(), skill.getLevel(), 9000, 5000), 1500);
			player.setInstance(_instance, true);
			player.setDungeon(this);
			
			ThreadPool.schedule(() -> player.setIsParalyzed(false), 10000);
		}
		
		_nextTask = ThreadPool.schedule(this::teleportPlayers, 10000);
	}
	
	private void teleportPlayers()
	{
		teleToStage();
		broadcastScreenMessage("Stage " + _currentStage._order + " begins in 10 seconds!", 5);
		_nextTask = ThreadPool.schedule(this::beginStage, 10 * 1000);
	}
	
	private void beginStage()
	{
		for (SpawnTemplate spawn : _currentSpawns)
		{
			for (int i = 0; i < spawn._count; i++)
			{
				NpcTemplate template = NpcTable.getInstance().getTemplate(spawn._npcId);
				try
				{
					L2Spawn spawns = new L2Spawn(template);
					
					int x = spawn._x;
					int y = spawn._y;
					int z = spawn._z;
					
					if (spawn._count > 1)
					{
						final int radius = spawn._range;
						final double angle = Rnd.nextDouble() * 2 * Math.PI;
						x += (int) (Math.cos(angle) * Rnd.get(0, radius));
						y += (int) (Math.sin(angle) * Rnd.get(0, radius));
					}
					
					Location loc = new Location(x, y, z);
					
					spawns.setLoc(loc.getX(), loc.getY(), loc.getZ(), 0);
					
					spawns.doSpawn(false);
					((L2MonsterInstance) spawns.getNpc()).setDungeon(this);
					spawns.getNpc().setInstance(_instance, false);
					spawns.getNpc().setTitle(spawn._title);
					SpawnTable.getInstance().addNewSpawn(spawns, false);
					for (Player player : _players)
					{
						player.sendPacket(new NpcInfo(spawns.getNpc(), player));
					}
					
					
					
					_mobToTemplate.put(((L2MonsterInstance) spawns.getNpc()), spawn);
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		}
		
		broadcastScreenMessage("You have " + _currentStage._timeLimit + " minutes to finish stage " + _currentStage._order + "!", 5);
		_stageBeginTime = System.currentTimeMillis();
		updatePlayerStage(_currentStage._order);
		_timerTask = ThreadPool.scheduleAtFixedRate(this::broadcastTimer, 5 * 1000, 1000);
		_dungeonCancelTask = ThreadPool.schedule(this::cancelDungeon, 1000 * 60 * _currentStage._timeLimit);
		monitorTask = ThreadPool.scheduleAtFixedRate(this::monitorDungeon, 5000, 5000);
		
	}
	
	private void monitorDungeon()
	{
		
		boolean allDead = true;
		
		for (Player player : _players)
		{
			if (player == null)
				continue;
			
			if (!player.isDead())
				allDead = false;
		}
		
		if (allDead)
		{
			cancelDungeon();
		}
	}
	
	private boolean advanceToNextStage()
	{
		if (!getNextStage())
			return false;
		
		if (_currentStage != null)
		{
			ThreadPool.schedule(this::teleToStage, 5 * 1000);
			return true;
		}
		return false;
	}
	
	private void teleToStage()
	{
		if (_currentStage._teleport)
		{
			for (Player player : _players)
			{
				player.teleToLocation(new Location(_currentStage._x, _currentStage._y, _currentStage._z), 75);
			}
		}
	}
	
	public void cancelDungeon()
	{
		for (Attackable mob : _mobToTemplate.keySet())
		{
			deleteMob(mob);
		}
		if (_timerTask != null)
			_timerTask.cancel(true);
		
		broadcastScreenMessage("You have failed to complete the dungeon. You will be teleported back in 5 seconds.", 5);
		ThreadPool.schedule(this::teleToTown, 5 * 1000);
		
	}
	
	private static void deleteMob(Attackable mob)
	{
		L2Spawn spawn = mob.getSpawn();
		spawn.setRespawnDelay(0);
		spawn.getNpc().deleteMe();
		SpawnTable.getInstance().deleteSpawn(spawn, false);
		
	}
	
	private void teleToTown()
	{
		
		for (Player player : _players)
		{
			
			if (player.isDead())
				player.doRevive();
			
			player.teleToLocation(new Location(81664, 149056, -3472), 150);
			
			player.broadcastCharInfo();
			player.broadcastUserInfo();
			player.setDungeon(null);
			player.setInstance(InstanceManager.getInstance().getInstance(0), true);
			
		}
		
		cleanupDungeon();
	}
	
	private void cleanupDungeon()
	{
		InstanceManager.getInstance().deleteInstance(_instance.getId());
		DungeonManager.getInstance().removeDungeon(this);
		
		cancelScheduledTasks();
	}
	
	private void completeDungeon()
	{
		ThreadPool.schedule(this::teleToTown, 5 * 1000);
		broadcastScreenMessage("You have completed the dungeon!", 5);
		
	}
	
	private void cancelScheduledTasks()
	{
		if (_dungeonCancelTask != null)
			_dungeonCancelTask.cancel(true);
		
		if (monitorTask != null)
			monitorTask.cancel(true);
		
		if (_timerTask != null)
			_timerTask.cancel(true);
		
		if (_nextTask != null)
			_nextTask.cancel(true);
		
	}
	
	private void broadcastTimer()
	{
		int secondsLeft = (int) ((_stageBeginTime + (1000 * 60 * _currentStage._timeLimit)) - System.currentTimeMillis()) / 1000;
		int minutes = secondsLeft / 60;
		int seconds = secondsLeft % 60;
		
		ExShowScreenMessage packet = new ExShowScreenMessage(String.format("%02d:%02d", minutes, seconds), 1010, SMPOS.BOTTOM_RIGHT, false);
		for (Player player : _players)
		{
			player.sendPacket(packet);
		}
	}
	
	private void broadcastScreenMessage(String msg, int seconds)
	{
		ExShowScreenMessage packet = new ExShowScreenMessage(msg, seconds * 1000, SMPOS.TOP_CENTER, false);
		for (Player player : _players)
		{
			player.sendPacket(packet);
		}
	}
	
	private boolean getNextStage()
	{
		if (_template._stages.isEmpty())
			return false;
		
		if (_currentStageIndex >= _template._stages.size())
		{
			_currentStage = null;
			return false;
		}
		
		_currentStage = _template._stages.get(_currentStageIndex);
		
		List<SpawnTemplate> stageSpawns = _template._spawns.get(_currentStage._order);
		if (stageSpawns == null || stageSpawns.isEmpty())
			return false;
		
		_currentSpawns = stageSpawns;
		
		_stageBeginTime = System.currentTimeMillis();
		_currentStageIndex++;
		return true;
	}
	
	public synchronized void onMobKill(Attackable attackable)
	{
		
		SpawnTemplate spawnTemplate = _mobToTemplate.remove(attackable);
		if (spawnTemplate == null)
			return;
		
		List<DropData> drops = parseDrops(spawnTemplate._drops);
		
		for (Player player : _players)
		{
			if (drops != null && !drops.isEmpty())
			{
				for (DropData drop : drops)
				{
					if (Rnd.get(1000000) < drop._chance)
					{
						int totalAmount = Rnd.get(drop._min, drop._max);
						
						if (player.isInParty())
						{
							List<Player> members = player.getParty().getPartyMembers();
							int size = members.size();
							
							int baseAmount = totalAmount / size;
							int remainder = totalAmount % size;
							
							for (Player member : members)
							{
								int amount = baseAmount;
								if (remainder > 0)
								{
									amount++;
									remainder--;
								}
								
								if (amount > 0)
									member.addItem("", drop._itemId, amount, attackable, true);
							}
						}
						else
						{
							player.addItem("", drop._itemId, totalAmount, attackable, true);
						}
					}
				}
			}
		}
		
		deleteMob(attackable);
		
		if (_mobToTemplate.isEmpty())
		{
			cancelScheduledTasks();
			
			if (advanceToNextStage())
			{
				broadcastScreenMessage("You have completed stage " + (_currentStage._order - 1) + "! Next stage begins in 10 seconds.", 5);
				_nextTask = ThreadPool.schedule(this::beginStage, 10 * 1000);
			}
			else
			{
				completeDungeon();
			}
		}
	}
	
	private List<DropData> parseDrops(String dropString)
	{
		List<DropData> drops = new ArrayList<>();
		if (dropString == null || dropString.isEmpty())
			return drops;
		
		String[] entries = dropString.split(";");
		for (String entry : entries)
		{
			String[] parts = entry.split("-");
			if (parts.length < 3)
				continue;
			
			int itemId = Integer.parseInt(parts[0]);
			int min = Integer.parseInt(parts[1]);
			int max = Integer.parseInt(parts[2]);
			int chance = parts.length > 3 ? Integer.parseInt(parts[3]) : 1000000;
			
			drops.add(new DropData(itemId, min, max, chance));
		}
		return drops;
	}
	
	public class DropData
	{
		public final int _itemId;
		public final int _min;
		public final int _max;
		public final int _chance;
		
		public DropData(int itemId, int min, int max, int chance)
		{
			_itemId = itemId;
			_min = min;
			_max = max;
			_chance = chance;
		}
	}
	
	public void updatePlayerStage(int stage)
	{
		for (Player player : _players)
		{
			DungeonManager.getInstance().updateStage(_template._id, player.getObjectId(), stage);
		}
	}
	
	public List<Player> getPlayers()
	{
		return _players;
	}
	
}