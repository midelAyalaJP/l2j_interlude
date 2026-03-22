package net.sf.l2j.mods.holder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import net.sf.l2j.gameserver.templates.StatsSet;

public class ClanAllyCrestHolder
{
	private final int _id;
	private final String _name;
	private final int _level;
	private final int _reputationScore;
	
	private final int _allyId;
	private final String _allyName;
	
	private final String _clanCrestFile;
	private final String _allyCrestFile;
	
	private final Set<Integer> _warClanIds = new HashSet<>();
	
	public ClanAllyCrestHolder(StatsSet set)
	{
		_id = set.getInteger("id");
		_name = set.getString("name");
		_level = set.getInteger("level", 0);
		_reputationScore = set.getInteger("reputation", 0);
		
		_allyId = set.getInteger("allyId", 0);
		_allyName = set.getString("allyName", "");
		
		_clanCrestFile = set.getString("clanCrest", "");
		_allyCrestFile = set.getString("allyCrest", "");
	}
	
	public int getId()
	{
		return _id;
	}
	
	public String getName()
	{
		return _name;
	}
	
	public int getLevel()
	{
		return _level;
	}
	
	public int getReputationScore()
	{
		return _reputationScore;
	}
	
	public int getAllyId()
	{
		return _allyId;
	}
	
	public String getAllyName()
	{
		return _allyName;
	}
	
	public String getClanCrestFile()
	{
		return _clanCrestFile;
	}
	
	public String getAllyCrestFile()
	{
		return _allyCrestFile;
	}
	
	public String getClanCrestPath()
	{
		if (_clanCrestFile == null || _clanCrestFile.isEmpty())
			return null;
		
		return "data/mods/crest/" + _clanCrestFile;
	}
	
	public String getAllyCrestPath()
	{
		if (_allyCrestFile == null || _allyCrestFile.isEmpty())
			return null;
		
		return "data/mods/crest/" + _allyCrestFile;
	}
	
	public void addWarClanId(int clanId)
	{
		if (clanId > 0 && clanId != _id)
			_warClanIds.add(clanId);
	}
	
	public Set<Integer> getWarClanIds()
	{
		return Collections.unmodifiableSet(_warClanIds);
	}
	
	public boolean isAtWarWith(int clanId)
	{
		return _warClanIds.contains(clanId);
	}
	
	@Override
	public String toString()
	{
		return "ClanAllyCrestHolder{id=" + _id + ", name=" + _name + ", allyId=" + _allyId + ", allyName=" + _allyName + "}";
	}
}