package net.sf.l2j.mods.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.mods.holder.SymbolHolder;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;



public class SymbolsData implements IXmlReader
{
	private final Map<String, SymbolHolder> _symbolsByClassId = new HashMap<>();
	
	public SymbolsData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_symbolsByClassId.clear();
		parseFile("data/mods/symbols.xml");
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		
		forEach(doc, "symbolSets", listNode ->
		{
			
			forEach(listNode, "symbol", privateSellNode ->
			{
				NamedNodeMap attrs = privateSellNode.getAttributes();
				
				String classId = attrs.getNamedItem("classId").getNodeValue();
				String symbolsStr = attrs.getNamedItem("symbols").getNodeValue();
				
				List<Integer> symbols = new ArrayList<>();
				for (String id : symbolsStr.split(";"))
				{
					try
					{
						symbols.add(Integer.parseInt(id.trim()));
					}
					catch (NumberFormatException e)
					{
						LOGGER.info("Invalid symbol ID: " + id);
					}
				}
				
				_symbolsByClassId.put(classId, new SymbolHolder(classId, symbols));
			});
		});
		
	}
	
	public SymbolHolder getSymbolSet(String classId)
	{
		return _symbolsByClassId.get(classId);
	}
	
	public static SymbolsData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final SymbolsData INSTANCE = new SymbolsData();
	}
}
