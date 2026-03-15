package net.sf.l2j.gameserver.model.zone.custom;

import java.util.ArrayList;
import java.util.List;

public class ZoneDropCategory
{
	private final String _name;
	private final int _chance;
	private final List<ZoneDropItemData> _items = new ArrayList<>();

	public ZoneDropCategory(String name, int chance)
	{
		_name = name;
		_chance = chance;
	}

	public String getName()
	{
		return _name;
	}

	public int getChance()
	{
		return _chance;
	}

	public List<ZoneDropItemData> getItems()
	{
		return _items;
	}

	public void addItem(ZoneDropItemData item)
	{
		_items.add(item);
	}
}