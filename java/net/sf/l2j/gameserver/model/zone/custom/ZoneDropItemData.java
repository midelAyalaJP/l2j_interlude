package net.sf.l2j.gameserver.model.zone.custom;

public class ZoneDropItemData
{
	private final int _itemId;
	private final int _min;
	private final int _max;
	private final int _chance;

	public ZoneDropItemData(int itemId, int min, int max, int chance)
	{
		_itemId = itemId;
		_min = min;
		_max = max;
		_chance = chance;
	}

	public int getItemId()
	{
		return _itemId;
	}

	public int getMin()
	{
		return _min;
	}

	public int getMax()
	{
		return _max;
	}

	public int getChance()
	{
		return _chance;
	}
}