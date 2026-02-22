package net.sf.l2j.gameserver.datatables.xml;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.model.holder.GMHolder;
import net.sf.l2j.gameserver.templates.StatsSet;

import org.w3c.dom.Document;

public class GmData implements IXmlReader
{
	private static final Logger LOGGER = Logger.getLogger(GmData.class.getName());
	
	private static final String FILE = "./data/xml/custom/adminaccesslevel/gm_list.xml";
	
	private final Map<Integer, GMHolder> _entries = new ConcurrentHashMap<>();
	
	public GmData()
	{
		load();
	}
	
	public void reload()
	{
		_entries.clear();
		load();
	}
	
	@Override
	public void load()
	{
		parseFile(FILE);
		LOGGER.info("Loaded " + _entries.size() + " GM whitelist entries.");
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "gmList", listNode -> forEach(listNode, "gm", gmNode -> {
			final StatsSet attrs = parseAttributes(gmNode);
			final GMHolder holder = new GMHolder(attrs);
			_entries.put(holder.getObjectId(), holder);
		}));
	}
	
	public GMHolder getEntry(int objectId)
	{
		return _entries.get(objectId);
	}
	
	public boolean isAllowed(int objectId)
	{
		final GMHolder e = _entries.get(objectId);
		return e != null && e.isEnabled() && e.getAccessLevel() > 0;
	}
	
	public static GmData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final GmData INSTANCE = new GmData();
	}
}