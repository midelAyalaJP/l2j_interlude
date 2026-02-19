package net.sf.l2j.dungeon.data.template;

import java.util.List;
import java.util.Map;

import net.sf.l2j.dungeon.enums.DungeonType;
import net.sf.l2j.dungeon.holder.EntryFee;
import net.sf.l2j.dungeon.holder.SpawnTemplate;
import net.sf.l2j.dungeon.holder.StageTemplate;

public class DungeonTemplate
{
	public final int _id;
	public final String _name;
	public final String _story;
	
	public final DungeonType _type;
	public final boolean _sharedInstance;
	public final long _cooldown;
	
	// Grupo
	public final int _minPlayers;
	public final int _maxPlayers;
	public final boolean _leaderOnly;
	
	// Requisitos
	public final boolean _vipOnly;
	public final boolean _heroOnly;
	
	// Entrada paga
	public final EntryFee _entryFee;
	
	public final List<StageTemplate> _stages;
	public final Map<Integer, List<SpawnTemplate>> _spawns;
	
	public DungeonTemplate(int id, String name, String story, DungeonType type, boolean sharedInstance, long cooldown, int minPlayers, int maxPlayers, boolean leaderOnly, boolean vipOnly, boolean heroOnly, EntryFee entryFee, List<StageTemplate> stages, Map<Integer, List<SpawnTemplate>> spawns)
	{
		_id = id;
		_name = name;
		_story = story;
		
		_type = type;
		_sharedInstance = sharedInstance;
		_cooldown = cooldown;
		
		_minPlayers = Math.max(1, minPlayers);
		_maxPlayers = Math.max(_minPlayers, maxPlayers);
		_leaderOnly = leaderOnly;
		
		_vipOnly = vipOnly;
		_heroOnly = heroOnly;
		
		_entryFee = entryFee;
		
		_stages = stages;
		_spawns = spawns;
	}
	
	public int getId()
	{
		return _id;
	}
}
