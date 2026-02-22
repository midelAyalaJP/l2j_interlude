package net.sf.l2j.gameserver.datatables.xml;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.xmlfactory.XMLDocumentFactory;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class IconTable
{
	private static final Logger _log = Logger.getLogger(IconTable.class.getName());

	private static Map<Integer, String> _icons = new ConcurrentHashMap<>();

	public static final IconTable getInstance()
	{
		return SingletonHolder._instance;
	}
	public void reload()
	{
		_icons.clear();
		load();;
	}
	

	protected IconTable()
	{

		load();
	}

	private static void load()
	{
		try
		{
			File f = new File("./data/xml/custom/icons.xml");
			Document doc = XMLDocumentFactory.getInstance().loadDocument(f);

			Node n = doc.getFirstChild();
			for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
			{
				if (d.getNodeName().equalsIgnoreCase("icon"))
				{
					NamedNodeMap attrs = d.getAttributes();

					int itemId = Integer.valueOf(attrs.getNamedItem("itemId").getNodeValue());
					String iconName = attrs.getNamedItem("iconName").getNodeValue();

					if (itemId == 0 && itemId < 0)
					{
						_log.log(Level.WARNING, "Icon Table: itemId=\"" + itemId + "\" is not item ID, Ignoring it!");
						continue;
					}                  
					_icons.put(itemId, iconName);
				}
			}
		}
		catch (Exception e)
		{
			_log.log(Level.WARNING, "Icon Table: Error loading from database: " + e.getMessage(), e);
		}

		_log.info("Icon Table: Loaded " + _icons.size() + " icons.");
	}

	public static String getIcon(int id)
	{
		if (_icons.get(id) == null)
			return "icon.NOIMAGE";

		return _icons.get(id);
	}

	private static class SingletonHolder
	{
		protected static final IconTable _instance = new IconTable();
	}

	
}