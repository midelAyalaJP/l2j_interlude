package net.sf.l2j.gameserver.model.holder;

import net.sf.l2j.gameserver.templates.StatsSet;

public class GMHolder
{
	private final int objectId;
	private final int accessLevel;
	private final boolean enabled;
	private final String name;
	
	public GMHolder(StatsSet set)
	{
		objectId = set.getInteger("objectId");
		accessLevel = set.getInteger("accessLevel", 0);
		enabled = set.getBool("enabled", true);
		name = set.getString("name", "");
	}
	
	public int getObjectId()
	{
		return objectId;
	}
	
	public int getAccessLevel()
	{
		return accessLevel;
	}
	
	public boolean isEnabled()
	{
		return enabled;
	}
	
	public String getName()
	{
		return name;
	}
}