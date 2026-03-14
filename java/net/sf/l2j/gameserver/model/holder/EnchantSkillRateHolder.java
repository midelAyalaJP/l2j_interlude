package net.sf.l2j.gameserver.model.holder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.sf.l2j.gameserver.templates.StatsSet;

public class EnchantSkillRateHolder
{
	private final int _id;
	private final List<IntIntHolder> _rates;
	
	public EnchantSkillRateHolder(StatsSet set)
	{
		_id = set.getInteger("id");
		_rates = Collections.unmodifiableList(new ArrayList<>(set.getIntIntHolderList("rates")));
	}
	
	public int getId()
	{
		return _id;
	}
	
	public List<IntIntHolder> getRates()
	{
		return _rates;
	}
	
	public int getRate(int playerLevel)
	{
		for (IntIntHolder holder : _rates)
		{
			if (holder.getId() == playerLevel)
				return holder.getValue();
		}
		return -1;
	}
}