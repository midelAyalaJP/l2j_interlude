package net.sf.l2j.dungeon.holder;

public final class EntryFee
{
	public enum FeeMode
	{
		NONE, // sem custo
		LEADER, // só o leader paga
		PER_PLAYER // cada player paga
	}
	
	private final int _itemId;
	private final long _count;
	private final FeeMode _mode;
	
	public EntryFee(int itemId, long count, FeeMode mode)
	{
		_itemId = itemId;
		_count = count;
		_mode = mode == null ? FeeMode.NONE : mode;
	}
	
	public int getItemId()
	{
		return _itemId;
	}
	
	public long getCount()
	{
		return _count;
	}
	
	public FeeMode getMode()
	{
		return _mode;
	}
	
	public boolean isEnabled()
	{
		return _mode != FeeMode.NONE && _itemId > 0 && _count > 0;
	}
}
