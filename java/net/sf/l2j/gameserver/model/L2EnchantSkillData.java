package net.sf.l2j.gameserver.model;

import net.sf.l2j.gameserver.datatables.xml.EnchantSkillRateData;

public final class L2EnchantSkillData
{
	private final int _costExp;
	private final int _costSp;
	private final int _itemId;
	private final int _itemCount;
	private final int _rate76;
	private final int _rate77;
	private final int _rate78;
	
	public L2EnchantSkillData(int costExp, int costSp, int itemId, int itemCount, int rate76, int rate77, int rate78)
	{
		_costExp = costExp;
		_costSp = costSp;
		_itemId = itemId;
		_itemCount = itemCount;
		_rate76 = rate76;
		_rate77 = rate77;
		_rate78 = rate78;
	}
	
	public int getCostExp()
	{
		return _costExp;
	}
	
	public int getCostSp()
	{
		return _costSp;
	}
	
	public int getItemId()
	{
		return _itemId;
	}
	
	public int getItemCount()
	{
		return _itemCount;
	}
	
	public int getRate(int level)
	{
		final int xmlRate = EnchantSkillRateData.getInstance().getRate(1, level);
		if (xmlRate >= 0)
			return xmlRate;
		
		switch (level)
		{
			case 76:
				return _rate76;
			case 77:
				return _rate77;
			case 78:
			case 79:
			case 80:
				return _rate78;
			default:
				return 0;
		}
	}
}