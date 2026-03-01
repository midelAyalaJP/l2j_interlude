package net.sf.l2j.dungeon.data;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.dungeon.data.template.DungeonTemplate;
import net.sf.l2j.dungeon.enums.DungeonType;
import net.sf.l2j.dungeon.holder.EntryFee;
import net.sf.l2j.dungeon.holder.SpawnTemplate;
import net.sf.l2j.dungeon.holder.StageTemplate;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class DungeonData implements IXmlReader
{
	private final Map<Integer, DungeonTemplate> _dungeons = new HashMap<>();
	final AtomicLong cooldownMs = new AtomicLong(0);
	
	public DungeonData()
	{
		load();
	}
	
	public void reload()
	{
		_dungeons.clear();
		load();
	}
	
	@Override
	public void load()
	{
		parseFile("data/xml/custom/dungeon_event.xml");
		LOGGER.info("Loaded {" + _dungeons.size() + "} dungeons.");
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> forEach(listNode, "dungeon", this::parseDungeon));
	}
	
	private void parseDungeon(Node dungeonNode)
	{
		final NamedNodeMap attrs = dungeonNode.getAttributes();
		
		final int id = parseInteger(attrs, "id", -1);
		final String name = parseString(attrs, "name", null);
		
		final String typeAttr = parseString(attrs, "type", "solo");
		final DungeonType type = DungeonType.valueOf(typeAttr.toUpperCase());
		
		final boolean shared = parseBoolean(attrs, "sharedInstance", false);
		
		// grupo
		final int minPlayers = parseInteger(attrs, "minPlayers", (type == DungeonType.SOLO ? 1 : 2));
		final int maxPlayers = parseInteger(attrs, "maxPlayers", (type == DungeonType.SOLO ? 1 : 9));
		final boolean leaderOnly = parseBoolean(attrs, "leaderOnly", (type != DungeonType.SOLO));
		
		// requisitos
		final boolean vipOnly = parseBoolean(attrs, "vipOnly", false);
		final boolean heroOnly = parseBoolean(attrs, "heroOnly", false);
		
		final long[] cooldownHolder =
		{
			0L
		};
		final String[] storyHolder =
		{
			""
		};
		
		// fee (mutável no loop)
		final EntryFee[] feeHolder =
		{
			new EntryFee(0, 0, EntryFee.FeeMode.NONE)
		};
		
		final List<StageTemplate> stages = new ArrayList<>();
		final Map<Integer, List<SpawnTemplate>> stageSpawns = new HashMap<>();
		
		forEach(dungeonNode, node -> {
			switch (node.getNodeName())
			{
				case "entryFee":
				{
					final NamedNodeMap a = node.getAttributes();
					final int itemId = parseInteger(a, "itemId", 0);
					final long count = parseLong(a, "count", 0L);
					final String modeStr = parseString(a, "mode", "none").toUpperCase();
					
					EntryFee.FeeMode mode;
					try
					{
						mode = EntryFee.FeeMode.valueOf(modeStr);
					}
					catch (Exception e)
					{
						mode = EntryFee.FeeMode.NONE;
					}
					
					feeHolder[0] = new EntryFee(itemId, count, mode);
					break;
				}
				
				case "cooldown":
				{
					final NamedNodeMap cooldownAttrs = node.getAttributes();
					
					final int minutes = parseInteger(cooldownAttrs, "minutes", -1);
					final int hours = parseInteger(cooldownAttrs, "hours", -1);
					final int days = parseInteger(cooldownAttrs, "days", -1);
					
					if (minutes > 0)
						cooldownHolder[0] = TimeUnit.MINUTES.toMillis(minutes);
					else if (hours > 0)
						cooldownHolder[0] = TimeUnit.HOURS.toMillis(hours);
					else if (days > 0)
						cooldownHolder[0] = TimeUnit.DAYS.toMillis(days);
					
					break;
				}
				
				case "story":
				{
					final String text = node.getTextContent();
					if (text != null && !text.trim().isEmpty())
						storyHolder[0] = text.trim();
					break;
				}
				
				case "stage":
				{
					final NamedNodeMap stageAttrs = node.getAttributes();
					final int order = parseInteger(stageAttrs, "order", 0);
					final String[] loc = parseString(stageAttrs, "loc", "0,0,0").split(",");
					final int x = Integer.parseInt(loc[0]);
					final int y = Integer.parseInt(loc[1]);
					final int z = Integer.parseInt(loc[2]);
					final boolean teleport = parseBoolean(stageAttrs, "teleport", false);
					final int time = parseInteger(stageAttrs, "minutes", 0);
					
					stages.add(new StageTemplate(order, x, y, z, teleport, time));
					break;
				}
				
				case "spawns":
				{
					final int stageId = parseInteger(node, "stage", -1);
					final List<SpawnTemplate> spawns = new ArrayList<>();
					
					forEach(node, "spawn", spawnNode -> {
						final NamedNodeMap spawnAttrs = spawnNode.getAttributes();
						final int npcId = parseInteger(spawnAttrs, "npcId", 0);
						final String title = parseString(spawnAttrs, "title", "");
						final int respawnDelay = parseInteger(spawnAttrs, "respawnDelay", 0);
						final int count = parseInteger(spawnAttrs, "count", 1);
						final int range = parseInteger(spawnAttrs, "range", 0);
						final String[] loc = parseString(spawnAttrs, "loc", "0,0,0").split(",");
						final int x = Integer.parseInt(loc[0]);
						final int y = Integer.parseInt(loc[1]);
						final int z = Integer.parseInt(loc[2]);
						final String drops = parseString(spawnAttrs, "drops", null);
						
						spawns.add(new SpawnTemplate(npcId, title, respawnDelay, count, range, x, y, z, drops));
					});
					
					stageSpawns.put(stageId, spawns);
					break;
				}
			}
		});
		
		_dungeons.put(id, new DungeonTemplate(id, name, storyHolder[0], type, shared, cooldownHolder[0], minPlayers, maxPlayers, leaderOnly, vipOnly, heroOnly, feeHolder[0], stages, stageSpawns));
	}
	
	public DungeonTemplate getDungeon(int id)
	{
		return _dungeons.get(id);
	}
	
	public List<DungeonTemplate> getDungeonsSorted()
	{
		if (_dungeons.isEmpty())
			return Collections.emptyList();
		
		final List<DungeonTemplate> list = new ArrayList<>(_dungeons.values());
		list.sort(Comparator.comparingInt(DungeonTemplate::getId));
		return list;
	}
	
	public int getTotalPages(int pageSize)
	{
		final int total = _dungeons.size();
		if (total <= 0)
			return 1;
		
		return (total + pageSize - 1) / pageSize;
	}
	
	public int clampPage(int page, int pageSize)
	{
		final int totalPages = getTotalPages(pageSize);
		if (page < 1)
			return 1;
		if (page > totalPages)
			return totalPages;
		return page;
	}
	
	public String buildDungeonListHtml(int page, int pageSize)
	{
		final List<DungeonTemplate> all = getDungeonsSorted();
		if (all.isEmpty())
			return "<font color=\"LEVEL\">No dungeons available.</font>";
		
		page = clampPage(page, pageSize);
		
		final int from = (page - 1) * pageSize;
		final int to = Math.min(from + pageSize, all.size());
		
		final StringBuilder sb = new StringBuilder(2048);
		
		sb.append("<table width=295 cellspacing=0 cellpadding=0>");
		
		for (int i = from; i < to; i++)
		{
			final DungeonTemplate d = all.get(i);
			
			final int id = d._id;
			final String name = safeHtml(d._name);
			final String displayName = truncate(name, 18); // evita estourar linha
			final String typeLabel = d._type.name(); // SOLO/PARTY/etc
			
			sb.append("<tr>");
			
			// Nome (fixo)
			sb.append("<td width=115 height=25>").append("<font color=\"LEVEL\">").append(displayName).append("</font>").append("</td>");
			
			// History (por id)
			sb.append("<td width=90 height=25>").append("<button value=\"History\" action=\"bypass dungeon chat history ").append(id).append("\" width=90 height=25 back=\"anim90.Anim\" fore=\"anim90.Anim\">").append("</td>");
			
			// Enter (tipo como label)
			sb.append("<td width=90 height=25>").append("<button value=\"").append(typeLabel).append("\" action=\"bypass dungeon enter ").append(id).append("\" width=90 height=25 back=\"anim90.Anim\" fore=\"anim90.Anim\">").append("</td>");
			
			sb.append("</tr>");
			
		}
		
		sb.append("</table>");
		return sb.toString();
	}
	
	public String truncate(String s, int limit)
	{
		if (s == null)
			return "";
		if (s.length() <= limit)
			return s;
		if (limit <= 3)
			return s.substring(0, limit);
		return s.substring(0, limit - 3) + "...";
	}
	
	public String safeHtml(String s)
	{
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}
	
	public String buildDungeonPagesHtml(int page, int pageSize)
	{
		final int totalPages = getTotalPages(pageSize);
		page = clampPage(page, pageSize);
		
		if (totalPages <= 1)
			return "";
		
		final StringBuilder sb = new StringBuilder(512);
		
		// Prev
		if (page > 1)
			sb.append("<a action=\"bypass dungeon chat index ").append(page - 1).append("\"><< Prev</a>");
		else
			sb.append("<font color=\"666666\"><< Prev</font>");
		
		sb.append("&nbsp;|&nbsp;");
		
		for (int p = 1; p <= totalPages; p++)
		{
			if (p == page)
				sb.append("<font color=\"LEVEL\">[").append(p).append("]</font>");
			else
				sb.append("<a action=\"bypass dungeon chat index ").append(p).append("\">").append(p).append("</a>");
			
			if (p < totalPages)
				sb.append("&nbsp;");
		}
		
		sb.append("&nbsp;|&nbsp;");
		
		// Next
		if (page < totalPages)
			sb.append("<a action=\"bypass dungeon chat index ").append(page + 1).append("\">Next >></a>");
		else
			sb.append("<font color=\"666666\">Next >></font>");
		
		return sb.toString();
	}
	
	public static DungeonData getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		private static final DungeonData _instance = new DungeonData();
	}
}