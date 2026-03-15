package net.sf.l2j.gameserver.model.zone.custom;

public class ZoneNpcTemplateData
{
	private final int _npcId;
	private final int _heading;
	private final int _respawnDelay;

	public ZoneNpcTemplateData(int npcId, int heading, int respawnDelay)
	{
		_npcId = npcId;
		_heading = heading;
		_respawnDelay = respawnDelay;
	}

	public int getNpcId()
	{
		return _npcId;
	}

	public int getHeading()
	{
		return _heading;
	}

	public int getRespawnDelay()
	{
		return _respawnDelay;
	}
}