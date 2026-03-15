package net.sf.l2j.gameserver.model.zone;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.model.Location;

/**
 * Abstract zone with spawn locations
 * @author DS
 */
public abstract class L2SpawnZone extends L2ZoneType
{
	private List<Location> _spawnLocs = null;
	private List<Location> _chaoticSpawnLocs = null;
	
	public L2SpawnZone(int id)
	{
		super(id);
	}
	
	public final void addSpawn(int x, int y, int z)
	{
		if (_spawnLocs == null)
			_spawnLocs = new ArrayList<>();
		
		_spawnLocs.add(new Location(x, y, z));
	}
	
	public final void addChaoticSpawn(int x, int y, int z)
	{
		if (_chaoticSpawnLocs == null)
			_chaoticSpawnLocs = new ArrayList<>();
		
		_chaoticSpawnLocs.add(new Location(x, y, z));
	}
	
	public List<Location> getSpawns()
	{
		return _spawnLocs;
	}
	
	public final Location getSpawnLoc()
	{
		return Rnd.get(_spawnLocs);
	}
	
	public Location getChaoticSpawnLoc()
	{
		if (_chaoticSpawnLocs != null)
			return Rnd.get(_chaoticSpawnLocs);
		
		return getSpawnLoc();
	}
}