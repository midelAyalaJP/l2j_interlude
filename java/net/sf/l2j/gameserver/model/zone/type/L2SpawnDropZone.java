package net.sf.l2j.gameserver.model.zone.type;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.datatables.SpawnDropZoneManager;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.zone.L2SpawnZone;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.model.zone.custom.ZoneDropCategory;
import net.sf.l2j.gameserver.model.zone.custom.ZoneNpcTemplateData;
import net.sf.l2j.gameserver.model.zone.custom.ZoneRewardTargetType;

public class L2SpawnDropZone extends L2SpawnZone
{
	private int _townId;
	private boolean _isPeaceZone = true;
	private Location _reviveLoc;
	private ZoneRewardTargetType _rewardTarget = ZoneRewardTargetType.SOLO;
	
	private final List<ZoneNpcTemplateData> _monsterTemplates = new ArrayList<>();
	private final List<ZoneNpcTemplateData> _raidBossTemplates = new ArrayList<>();
	private final List<ZoneDropCategory> _dropCategories = new ArrayList<>();
	
	private boolean _replaceExistingSpawns;
	private boolean _replaceMonsters = true;
	private boolean _replaceRaidBosses = true;
	
	public L2SpawnDropZone(int id)
	{
		super(id);
	}
	
	@Override
	protected void onEnter(Creature character)
	{
		if (character instanceof Player)
		{
			final Player player = (Player) character;
			
			if (_isPeaceZone && Config.ZONE_TOWN != 2)
				player.setInsideZone(ZoneId.PEACE, true);
			
			player.setInsideZone(ZoneId.DROP_ZONE, true);
			player.setLastSpawnDropZone(this);
		}
		else
		{
			character.setInsideZone(ZoneId.DROP_ZONE, true);
		}
	}
	
	@Override
	protected void onExit(Creature character)
	{
		if (character instanceof Player)
		{
			final Player player = (Player) character;
			
			if (_isPeaceZone)
				player.setInsideZone(ZoneId.PEACE, false);
			
			player.setInsideZone(ZoneId.DROP_ZONE, false);
			
			if (player.getLastSpawnDropZone() == this)
				player.setLastSpawnDropZone(null);
		}
		else
		{
			character.setInsideZone(ZoneId.DROP_ZONE, false);
		}
	}
	
	@Override
	public void onDieInside(Creature character)
	{
		if (character instanceof Player)
			((Player) character).setDeathSpawnDropZone(this);
		
		if (character instanceof L2Npc)
		{
			final L2Npc npcs = (L2Npc) character;
			
			SpawnDropZoneManager.getInstance().registerZoneNpc(this, npcs);
		}
	}
	
	@Override
	public void onReviveInside(Creature character)
	{
		if (character instanceof Player)
			((Player) character).setDeathSpawnDropZone(null);
	}
	
	@Override
	public void setParameter(String name, String value)
	{
		switch (name)
		{
			case "townId":
				_townId = Integer.parseInt(value);
				break;
			
			case "isPeaceZone":
				_isPeaceZone = Boolean.parseBoolean(value);
				break;
			
			case "replaceExistingSpawns":
				_replaceExistingSpawns = Boolean.parseBoolean(value);
				break;
			
			case "replaceMonsters":
				_replaceMonsters = Boolean.parseBoolean(value);
				break;
			
			case "replaceRaidBosses":
				_replaceRaidBosses = Boolean.parseBoolean(value);
				break;
			
			case "reviveX":
			{
				final int x = Integer.parseInt(value);
				final int y = (_reviveLoc != null) ? _reviveLoc.getY() : 0;
				final int z = (_reviveLoc != null) ? _reviveLoc.getZ() : 0;
				_reviveLoc = new Location(x, y, z);
				break;
			}
			case "reviveY":
			{
				final int x = (_reviveLoc != null) ? _reviveLoc.getX() : 0;
				final int y = Integer.parseInt(value);
				final int z = (_reviveLoc != null) ? _reviveLoc.getZ() : 0;
				_reviveLoc = new Location(x, y, z);
				break;
			}
			case "reviveZ":
			{
				final int x = (_reviveLoc != null) ? _reviveLoc.getX() : 0;
				final int y = (_reviveLoc != null) ? _reviveLoc.getY() : 0;
				final int z = Integer.parseInt(value);
				_reviveLoc = new Location(x, y, z);
				break;
			}
			case "rewardTarget":
				_rewardTarget = ZoneRewardTargetType.valueOf(value.toUpperCase());
				break;
			
			default:
				super.setParameter(name, value);
				break;
		}
	}
	
	public void setReviveLoc(Location loc)
	{
		_reviveLoc = loc;
	}
	
	public void addMonsterTemplate(ZoneNpcTemplateData template)
	{
		if (template != null)
			_monsterTemplates.add(template);
	}
	
	public void addRaidBossTemplate(ZoneNpcTemplateData template)
	{
		if (template != null)
			_raidBossTemplates.add(template);
	}
	
	public void addDropCategory(ZoneDropCategory category)
	{
		if (category != null)
			_dropCategories.add(category);
	}
	
	public int getTownId()
	{
		return _townId;
	}
	
	public boolean isPeaceZone()
	{
		return _isPeaceZone;
	}
	
	public Location getReviveLoc()
	{
		return _reviveLoc;
	}
	
	public ZoneRewardTargetType getRewardTarget()
	{
		return _rewardTarget;
	}
	
	public List<ZoneNpcTemplateData> getMonsterTemplates()
	{
		return _monsterTemplates;
	}
	
	public List<ZoneNpcTemplateData> getRaidBossTemplates()
	{
		return _raidBossTemplates;
	}
	
	public List<ZoneDropCategory> getDropCategories()
	{
		return _dropCategories;
	}
	
	public boolean isReplaceExistingSpawns()
	{
		return _replaceExistingSpawns;
	}
	
	public boolean isReplaceMonsters()
	{
		return _replaceMonsters;
	}
	
	public boolean isReplaceRaidBosses()
	{
		return _replaceRaidBosses;
	}
	
	public void replaceExistingSpawnsNow()
	{
		SpawnDropZoneManager.getInstance().replaceExistingZoneNpcs(this);
	}
}