package net.sf.l2j.gameserver.datatables.xml;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.model.holder.EnchantSkillRateHolder;
import net.sf.l2j.gameserver.templates.StatsSet;

import org.w3c.dom.Document;

public class EnchantSkillRateData implements IXmlReader
{
	private final Map<Integer, EnchantSkillRateHolder> _rates = new ConcurrentHashMap<>();
	
	public EnchantSkillRateData()
	{
		load();
	}
	
	public void reload()
	{
		_rates.clear();
		load();
	}
	
	@Override
	public void load()
	{
		_rates.clear();
		parseFile("./data/xml/custom/enchantSkillRates.xml");
		LOGGER.info("Loaded {" + _rates.size() + "} enchant skill rate template(s).");
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "enchant", enchantNode -> {
			final StatsSet set = parseAttributes(enchantNode);
			final EnchantSkillRateHolder holder = new EnchantSkillRateHolder(set);
			_rates.put(holder.getId(), holder);
		}));
	}
	
	public EnchantSkillRateHolder getHolder(int id)
	{
		return _rates.get(id);
	}
	
	public int getRate(int id, int playerLevel)
	{
		final EnchantSkillRateHolder holder = getHolder(id);
		return (holder != null) ? holder.getRate(playerLevel) : -1;
	}
	
	public static EnchantSkillRateData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final EnchantSkillRateData INSTANCE = new EnchantSkillRateData();
	}
}