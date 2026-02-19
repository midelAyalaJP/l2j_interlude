package net.sf.l2j.dungeon.holder;

public class StageTemplate
{
	public final int _order;
	public final int _x, _y, _z;
	public final boolean _teleport;
	public final int _timeLimit;

	public StageTemplate(int order, int x, int y, int z, boolean teleport, int timeLimit)
	{
		_order = order;
		_x = x;
		_y = y;
		_z = z;
		_teleport = teleport;
		_timeLimit = timeLimit;
	}
}