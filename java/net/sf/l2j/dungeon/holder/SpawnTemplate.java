package net.sf.l2j.dungeon.holder;

public class SpawnTemplate
{
	public final int _npcId;
	public final String _title;
	public final int _count;
	public final int _range;
	public final int _x, _y, _z;
	
	public final String _drops;
	
	public SpawnTemplate(int npcId, String title, int count, int range, int x, int y, int z, String drops)
	{
		_npcId = npcId;
		_title = title;
		_count = count;
		_range = range;
		_x = x;
		_y = y;
		_z = z;
		_drops = drops;
	}
}