package net.sf.l2j.gameserver.datatables;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2RaidBossInstance;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.zone.custom.ZoneNpcTemplateData;
import net.sf.l2j.gameserver.model.zone.type.L2SpawnDropZone;

public class SpawnDropZoneManager
{
	private final Map<Integer, L2SpawnDropZone> _npcZoneByObjectId = new ConcurrentHashMap<>();
	private final Map<L2Spawn, L2SpawnDropZone> _zoneBySpawn = new ConcurrentHashMap<>();
	private final Map<Integer, List<L2Npc>> _spawnedByZoneId = new ConcurrentHashMap<>();
	
	public static SpawnDropZoneManager getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	public void spawnZone(L2SpawnDropZone zone)
	{
		if (zone == null)
			return;
		
		despawnZone(zone);
		
		final List<L2Npc> list = new ArrayList<>();
		_spawnedByZoneId.put(zone.getId(), list);
		
		for (Location loc : zone.getSpawns())
		{
			if (zone.getMonsterTemplates().isEmpty())
				break;
			
			final ZoneNpcTemplateData tpl = zone.getMonsterTemplates().get(list.size() % zone.getMonsterTemplates().size());
			spawnNpc(zone, tpl, loc);
		}
	}
	
	public void despawnZone(L2SpawnDropZone zone)
	{
		if (zone == null)
			return;
		
		final List<L2Npc> list = _spawnedByZoneId.remove(zone.getId());
		if (list == null)
			return;
		
		for (L2Npc npc : list)
		{
			_npcZoneByObjectId.remove(npc.getObjectId());

			if (npc.getSpawn() != null)
			{
				_zoneBySpawn.remove(npc.getSpawn());

				if (npc.getSpawn().getRespawnState())
				{
					npc.getSpawn().setRespawnState(false);
				}
			}

			npc.getSpawn().getNpc().deleteMe();
		}
	}
	
	private void spawnNpc(L2SpawnDropZone zone, ZoneNpcTemplateData tpl, Location loc)
	{
		try
		{
			final NpcTemplate template = NpcTable.getInstance().getTemplate(tpl.getNpcId());
			if (template == null)
				return;

			final L2Spawn spawn = new L2Spawn(template);
			spawn.setLoc(loc.getX(), loc.getY(), loc.getZ(), Rnd.get(65535));
			spawn.setRespawnDelay(tpl.getRespawnDelay());
			spawn.setRespawnState(true);

			final L2Npc npc = spawn.doSpawn(false);
			if (npc == null)
				return;

			registerZoneSpawn(zone, spawn);
			registerZoneNpc(zone, npc);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	public void registerZoneNpc(L2SpawnDropZone zone, L2Npc npc)
	{
		if (zone == null || npc == null)
			return;

		_npcZoneByObjectId.put(npc.getObjectId(), zone);
		_spawnedByZoneId.computeIfAbsent(zone.getId(), k -> new ArrayList<>()).add(npc);

		if (npc.getSpawn() != null)
			_zoneBySpawn.put(npc.getSpawn(), zone);
	}
	
	public void registerZoneSpawn(L2SpawnDropZone zone, L2Spawn spawn)
	{
		if (zone == null || spawn == null)
			return;

		_zoneBySpawn.put(spawn, zone);
	}
	public void replaceExistingZoneNpcs(L2SpawnDropZone zone)
	{
		if (zone == null || !zone.isReplaceExistingSpawns())
			return;
		
		final List<Location> monsterLocs = new ArrayList<>();
		final List<Location> raidLocs = new ArrayList<>();
		final List<L2Npc> oldNpcs = new ArrayList<>();
		
		for (L2Object obj : L2World.getInstance().getObjects())
		{
			if (!(obj instanceof L2Npc))
				continue;
			
			final L2Npc npc = (L2Npc) obj;
			
			if (!zone.isInsideZone(npc.getX(), npc.getY(), npc.getZ()))
				continue;
			
			if ((npc instanceof L2MonsterInstance) && !(npc instanceof L2RaidBossInstance) && zone.isReplaceMonsters())
			{
				monsterLocs.add(new Location(npc.getX(), npc.getY(), npc.getZ()));
				oldNpcs.add(npc);
			}
			else if ((npc instanceof L2RaidBossInstance) && zone.isReplaceRaidBosses())
			{
				raidLocs.add(new Location(npc.getX(), npc.getY(), npc.getZ()));
				oldNpcs.add(npc);
			}
		}
		
		for (L2Npc npc : oldNpcs)
		{
			_npcZoneByObjectId.remove(npc.getObjectId());

			if (npc.getSpawn() != null)
			{
				_zoneBySpawn.remove(npc.getSpawn());

				if (npc.getSpawn().getRespawnState())
				{
					npc.getSpawn().setRespawnState(false);
				}
			}

			npc.getSpawn().getNpc().deleteMe();
		}
		
		_spawnedByZoneId.remove(zone.getId());
		
		spawnTemplatesAtLocations(zone, zone.getMonsterTemplates(), monsterLocs);
		spawnTemplatesAtLocations(zone, zone.getRaidBossTemplates(), raidLocs);
	}
	
	private void spawnTemplatesAtLocations(L2SpawnDropZone zone, List<ZoneNpcTemplateData> templates, List<Location> locs)
	{
		if (templates == null || templates.isEmpty() || locs == null || locs.isEmpty())
			return;
		
		int index = 0;
		for (Location loc : locs)
		{
			final ZoneNpcTemplateData tpl = templates.get(index % templates.size());
			spawnNpc(zone, tpl, loc);
			index++;
		}
	}
	
	public L2SpawnDropZone getZoneByNpc(L2Npc npc)
	{
		if (npc == null)
			return null;

		L2SpawnDropZone zone = _npcZoneByObjectId.get(npc.getObjectId());
		if (zone != null)
			return zone;

		final L2Spawn spawn = npc.getSpawn();
		if (spawn != null)
		{
			zone = _zoneBySpawn.get(spawn);
			if (zone != null)
			{
				_npcZoneByObjectId.put(npc.getObjectId(), zone);
				return zone;
			}
		}

		return null;
	}
	
	public void unregisterZoneNpc(L2Npc npc)
	{
		if (npc == null)
			return;

		_npcZoneByObjectId.remove(npc.getObjectId());

		final L2SpawnDropZone zone = (npc.getSpawn() != null) ? _zoneBySpawn.get(npc.getSpawn()) : null;
		if (zone == null)
			return;

		final List<L2Npc> list = _spawnedByZoneId.get(zone.getId());
		if (list != null)
			list.remove(npc);
	}
	
	private static class SingletonHolder
	{
		private static final SpawnDropZoneManager INSTANCE = new SpawnDropZoneManager();
	}
}