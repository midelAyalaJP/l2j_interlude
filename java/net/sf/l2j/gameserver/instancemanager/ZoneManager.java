package net.sf.l2j.gameserver.instancemanager;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.L2WorldRegion;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.zone.L2SpawnZone;
import net.sf.l2j.gameserver.model.zone.L2ZoneType;
import net.sf.l2j.gameserver.model.zone.custom.ZoneDropCategory;
import net.sf.l2j.gameserver.model.zone.custom.ZoneDropItemData;
import net.sf.l2j.gameserver.model.zone.custom.ZoneNpcTemplateData;
import net.sf.l2j.gameserver.model.zone.form.ZoneCuboid;
import net.sf.l2j.gameserver.model.zone.form.ZoneCylinder;
import net.sf.l2j.gameserver.model.zone.form.ZoneNPoly;
import net.sf.l2j.gameserver.model.zone.type.L2ArenaZone;
import net.sf.l2j.gameserver.model.zone.type.L2BossZone;
import net.sf.l2j.gameserver.model.zone.type.L2OlympiadStadiumZone;
import net.sf.l2j.gameserver.model.zone.type.L2SpawnDropZone;
import net.sf.l2j.gameserver.model.zone.type.L2TimeFarmZone;
import net.sf.l2j.gameserver.xmlfactory.XMLDocumentFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This class manages the zones
 * @author durgus
 */
public class ZoneManager
{
	private static final Logger _log = Logger.getLogger(ZoneManager.class.getName());
	
	private static final String DELETE_GRAND_BOSS_LIST = "DELETE FROM grandboss_list";
	private static final String INSERT_GRAND_BOSS_LIST = "INSERT INTO grandboss_list (player_id,zone) VALUES (?,?)";
	
	private final Map<Class<? extends L2ZoneType>, Map<Integer, ? extends L2ZoneType>> _classZones = new HashMap<>();
	private int _lastDynamicId = 0;

	
	public static final ZoneManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	protected ZoneManager()
	{
		_log.info("ZoneManager: Loading zones...");
		
		load();
	}
	
	public void reload()
	{
		// save L2BossZone
		save();
		
		// remove zones from world
		int count = 0;
		for (L2WorldRegion[] worldRegion : L2World.getInstance().getWorldRegions())
		{
			for (L2WorldRegion element : worldRegion)
			{
				element.getZones().clear();
				count++;
			}
		}
		
		_log.info("ZoneManager: Removed zones in " + count + " regions.");
		
		// clear
		_classZones.clear();
		
		// load all zones
		load();
		
		// revalidate objects in zones
		for (L2Object o : L2World.getInstance().getObjects())
		{
			if (o instanceof Creature)
				((Creature) o).revalidateZone(true);
		}
	}
	
	private final void load()
	{
		// Get the world regions
		L2WorldRegion[][] worldRegions = L2World.getInstance().getWorldRegions();
		
		// Load the zone xml
		try
		{
			final File mainDir = new File("./data/xml/zones");
			if (!mainDir.isDirectory())
			{
				_log.warning("ZoneManager: Main directory " + mainDir.getAbsolutePath() + " hasn't been found.");
				return;
			}
			
			int fileCounter = 0;
			for (final File file : mainDir.listFiles())
			{
				if (file.isFile() && file.getName().endsWith(".xml"))
				{
					// Set dynamically the ID range of next XML loading file.
					_lastDynamicId = fileCounter++ * 1000;
					loadFileZone(file, worldRegions);
				}
			}
		}
		catch (Exception e)
		{
			_log.log(Level.SEVERE, "ZoneManager: Error while loading zones.", e);
			return;
		}
		
		// get size
		int size = 0;
		for (Map<Integer, ? extends L2ZoneType> map : _classZones.values())
			size += map.size();
		
		_log.info("ZoneManager: Loaded " + _classZones.size() + " zones classes and total " + size + " zones.");
	}
	
	private void loadFileZone(final File f, L2WorldRegion[][] worldRegions) throws Exception
	{
		final Document doc = XMLDocumentFactory.getInstance().loadDocument(f);
		for (Node n = doc.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("list".equalsIgnoreCase(n.getNodeName()))
			{
				NamedNodeMap attrs = n.getAttributes();
				Node attribute = attrs.getNamedItem("enabled");
				if (attribute != null && !Boolean.parseBoolean(attribute.getNodeValue()))
					continue;
				
				for (Node d = n.getFirstChild(); d != null; d = d.getNextSibling())
				{
					if ("zone".equalsIgnoreCase(d.getNodeName()))
					{
						final NamedNodeMap nnmd = d.getAttributes();
						
						// Generate dynamically zone's ID.
						int zoneId = _lastDynamicId++;
						
						// Dynamic id is replaced by handwritten id if existing.
						attribute = nnmd.getNamedItem("id");
						if (attribute != null)
							zoneId = Integer.parseInt(attribute.getNodeValue());
						
						final String zoneType = nnmd.getNamedItem("type").getNodeValue();
						final String zoneShape = nnmd.getNamedItem("shape").getNodeValue();
						final int minZ = Integer.parseInt(nnmd.getNamedItem("minZ").getNodeValue());
						final int maxZ = Integer.parseInt(nnmd.getNamedItem("maxZ").getNodeValue());
						
						// Create the zone
						Class<?> newZone;
						try
						{
							newZone = Class.forName("net.sf.l2j.gameserver.model.zone.type.L2" + zoneType);
						}
						catch (ClassNotFoundException e)
						{
							_log.warning("ZoneData: No such zone type: " + zoneType + " in file: " + f.getName());
							continue;
						}
						
						Constructor<?> zoneConstructor = newZone.getConstructor(int.class);
						L2ZoneType temp = (L2ZoneType) zoneConstructor.newInstance(zoneId);
						temp.setFileSource(f.getAbsolutePath());
						if(Config.ENABLE_TIME_ZONE_SYSTEM)
						{
							if (temp instanceof L2TimeFarmZone)
							{
								Node attr = nnmd.getNamedItem("name");
								if (attr != null)
								{
								    temp.setParameter("name", attr.getNodeValue());
								}
							    else
							    {
							        System.out.println("ZoneManager: Atributo 'name' não encontrado no XML para essa zona");
							    }
							}	
						}
						
						// Get the zone shape from sql
						try
						{
							List<int[]> rs = new ArrayList<>();
							
							// loading from XML first
							for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling())
							{
								if ("node".equalsIgnoreCase(cd.getNodeName()))
								{
									attrs = cd.getAttributes();
									int[] point = new int[2];
									point[0] = Integer.parseInt(attrs.getNamedItem("X").getNodeValue());
									point[1] = Integer.parseInt(attrs.getNamedItem("Y").getNodeValue());
									rs.add(point);
								}
							}
							
							int[][] coords = rs.toArray(new int[rs.size()][]);
							
							if (coords == null || coords.length == 0)
							{
								_log.warning("ZoneData: missing data for zone: " + zoneId + " on file: " + f.getName());
								continue;
							}
							
							// Create this zone. Parsing for cuboids is a
							// bit different than for other polygons
							// cuboids need exactly 2 points to be defined.
							// Other polygons need at least 3 (one per
							// vertex)
							if (zoneShape.equalsIgnoreCase("Cuboid"))
							{
								if (coords.length == 2)
									temp.setZone(new ZoneCuboid(coords[0][0], coords[1][0], coords[0][1], coords[1][1], minZ, maxZ));
								else
								{
									_log.warning("ZoneData: Missing cuboid vertex in sql data for zone: " + zoneId + " in file: " + f.getName());
									continue;
								}
							}
							else if (zoneShape.equalsIgnoreCase("NPoly"))
							{
								// nPoly needs to have at least 3 vertices
								if (coords.length > 2)
								{
									final int[] aX = new int[coords.length];
									final int[] aY = new int[coords.length];
									for (int i = 0; i < coords.length; i++)
									{
										aX[i] = coords[i][0];
										aY[i] = coords[i][1];
									}
									temp.setZone(new ZoneNPoly(aX, aY, minZ, maxZ));
								}
								else
								{
									_log.warning("ZoneData: Bad data for zone: " + zoneId + " in file: " + f.getName());
									continue;
								}
							}
							else if (zoneShape.equalsIgnoreCase("Cylinder"))
							{
								// A Cylinder zone requires a center point
								// at x,y and a radius
								attrs = d.getAttributes();
								final int zoneRad = Integer.parseInt(attrs.getNamedItem("rad").getNodeValue());
								if (coords.length == 1 && zoneRad > 0)
									temp.setZone(new ZoneCylinder(coords[0][0], coords[0][1], minZ, maxZ, zoneRad));
								else
								{
									_log.warning("ZoneData: Bad data for zone: " + zoneId + " in file: " + f.getName());
									continue;
								}
							}
							else
							{
								_log.warning("ZoneData: Unknown shape: " + zoneShape + " in file: " + f.getName());
								continue;
							}
						}
						catch (Exception e)
						{
							_log.log(Level.WARNING, "ZoneData: Failed to load zone " + zoneId + " coordinates: " + e.getMessage(), e);
						}
						
						// Check for additional parameters
						for (Node cd = d.getFirstChild(); cd != null; cd = cd.getNextSibling())
						{
							if ("stat".equalsIgnoreCase(cd.getNodeName()))
							{
								attrs = cd.getAttributes();
								final String name = attrs.getNamedItem("name").getNodeValue();
								final String val = attrs.getNamedItem("val").getNodeValue();

								temp.setParameter(name, val);
							}
							else if ("spawn".equalsIgnoreCase(cd.getNodeName()))
							{
								attrs = cd.getAttributes();
								final int spawnX = Integer.parseInt(attrs.getNamedItem("X").getNodeValue());
								final int spawnY = Integer.parseInt(attrs.getNamedItem("Y").getNodeValue());
								final int spawnZ = Integer.parseInt(attrs.getNamedItem("Z").getNodeValue());

								if (temp instanceof L2TimeFarmZone)
								{
									((L2TimeFarmZone) temp).addSpawn(new Location(spawnX, spawnY, spawnZ));
								}
								else if (temp instanceof L2SpawnZone)
								{
									final Node val = attrs.getNamedItem("isChaotic");
									if ((val != null) && Boolean.parseBoolean(val.getNodeValue()))
										((L2SpawnZone) temp).addChaoticSpawn(spawnX, spawnY, spawnZ);
									else
										((L2SpawnZone) temp).addSpawn(spawnX, spawnY, spawnZ);
								}
							}
							else if ((temp instanceof L2SpawnDropZone) && "revive".equalsIgnoreCase(cd.getNodeName()))
							{
								final L2SpawnDropZone zone = (L2SpawnDropZone) temp;
								attrs = cd.getAttributes();

								final int x = Integer.parseInt(attrs.getNamedItem("X").getNodeValue());
								final int y = Integer.parseInt(attrs.getNamedItem("Y").getNodeValue());
								final int z = Integer.parseInt(attrs.getNamedItem("Z").getNodeValue());

								zone.setReviveLoc(new Location(x, y, z));
							}
							else if ((temp instanceof L2SpawnDropZone) && "spawns".equalsIgnoreCase(cd.getNodeName()))
							{
								parseSpawnDropZoneSpawns((L2SpawnDropZone) temp, cd);
							}
							else if ((temp instanceof L2SpawnDropZone) && "drops".equalsIgnoreCase(cd.getNodeName()))
							{
								parseSpawnDropZoneDrops((L2SpawnDropZone) temp, cd);
							}
						}
						
						if (checkId(zoneId))
							_log.config("Caution: Zone (" + zoneId + ") from file: " + f.getName() + " overrides previos definition.");
						
						addZone(zoneId, temp);
						
						// Register the zone into any world region it
						// intersects with...
						// currently 11136 test for each zone :>
						for (int x = 0; x < worldRegions.length; x++)
						{
							for (int y = 0; y < worldRegions[x].length; y++)
							{
								if (temp.getZone().intersectsRectangle(L2World.getRegionX(x), L2World.getRegionX(x + 1), L2World.getRegionY(y), L2World.getRegionY(y + 1)))
								{
									if (Config.DEBUG)
										_log.info("Zone (" + zoneId + ") added to: " + x + " " + y);
									
									worldRegions[x][y].addZone(temp);
								}
							}
						}
					}
				}
			}
		}
	}
	
	public final void save()
	{
		try (Connection con = ConnectionPool.getConnection())
		{
			// clear table first
			PreparedStatement ps = con.prepareStatement(DELETE_GRAND_BOSS_LIST);
			ps.executeUpdate();
			ps.close();
			
			// store actual data
			ps = con.prepareStatement(INSERT_GRAND_BOSS_LIST);
			for (L2ZoneType zone : _classZones.get(L2BossZone.class).values())
			{
				for (int player : ((L2BossZone) zone).getAllowedPlayers())
				{
					ps.setInt(1, player);
					ps.setInt(2, zone.getId());
					ps.addBatch();
				}
			}
			ps.executeBatch();
			ps.close();
			
			_log.info("ZoneManager: Saved L2BossZone data.");
		}
		catch (SQLException e)
		{
			_log.log(Level.WARNING, "ZoneManager: Couldn't store boss zones to database: " + e.getMessage(), e);
		}
	}
	
	public boolean checkId(int id)
	{
		for (Map<Integer, ? extends L2ZoneType> map : _classZones.values())
		{
			if (map.containsKey(id))
				return true;
		}
		return false;
	}
	
	/**
	 * Add new zone
	 * @param id
	 * @param <T>
	 * @param zone
	 */
	@SuppressWarnings("unchecked")
	public <T extends L2ZoneType> void addZone(Integer id, T zone)
	{
		// _zones.put(id, zone);
		Map<Integer, T> map = (Map<Integer, T>) _classZones.get(zone.getClass());
		if (map == null)
		{
			map = new HashMap<>();
			map.put(id, zone);
			_classZones.put(zone.getClass(), map);
		}
		else
			map.put(id, zone);
	}
	private static void parseSpawnDropZoneDrops(L2SpawnDropZone zone, Node dropsNode)
	{
		for (Node cat = dropsNode.getFirstChild(); cat != null; cat = cat.getNextSibling())
		{
			if (!"category".equalsIgnoreCase(cat.getNodeName()))
				continue;

			final NamedNodeMap catAttrs = cat.getAttributes();

			final String name = catAttrs.getNamedItem("name").getNodeValue();
			final int chance = Integer.parseInt(catAttrs.getNamedItem("chance").getNodeValue());

			final ZoneDropCategory category = new ZoneDropCategory(name, chance);

			for (Node itemNode = cat.getFirstChild(); itemNode != null; itemNode = itemNode.getNextSibling())
			{
				if (!"item".equalsIgnoreCase(itemNode.getNodeName()))
					continue;

				final NamedNodeMap itemAttrs = itemNode.getAttributes();

				final int itemId = Integer.parseInt(itemAttrs.getNamedItem("id").getNodeValue());
				final int min = Integer.parseInt(itemAttrs.getNamedItem("min").getNodeValue());
				final int max = Integer.parseInt(itemAttrs.getNamedItem("max").getNodeValue());
				final int itemChance = Integer.parseInt(itemAttrs.getNamedItem("chance").getNodeValue());

				category.addItem(new ZoneDropItemData(itemId, min, max, itemChance));
			}

			zone.addDropCategory(category);
		}
	}
	private static void parseSpawnDropZoneSpawns(L2SpawnDropZone zone, Node spawnsNode)
	{
		for (Node n = spawnsNode.getFirstChild(); n != null; n = n.getNextSibling())
		{
			if ("monster".equalsIgnoreCase(n.getNodeName()))
			{
				final NamedNodeMap attrs = n.getAttributes();

				final int npcId = Integer.parseInt(attrs.getNamedItem("npcId").getNodeValue());
				final int heading = getInt(attrs, "heading", 0);
				final int respawn = getInt(attrs, "respawn", 60);

				zone.addMonsterTemplate(new ZoneNpcTemplateData(npcId, heading, respawn));
			}
			else if ("raidboss".equalsIgnoreCase(n.getNodeName()))
			{
				final NamedNodeMap attrs = n.getAttributes();

				final int npcId = Integer.parseInt(attrs.getNamedItem("npcId").getNodeValue());
				final int heading = getInt(attrs, "heading", 0);
				final int respawn = getInt(attrs, "respawn", 300);

				zone.addRaidBossTemplate(new ZoneNpcTemplateData(npcId, heading, respawn));
			}
			
		}
	}
	

	private static int getInt(NamedNodeMap attrs, String name, int defaultValue)
	{
		final Node node = attrs.getNamedItem(name);
		return (node == null) ? defaultValue : Integer.parseInt(node.getNodeValue());
	}
	
	/**
	 * Return all zones by class type
	 * @param <T>
	 * @param zoneType Zone class
	 * @return Collection of zones
	 */
	@SuppressWarnings("unchecked")
	public <T extends L2ZoneType> Collection<T> getAllZones(Class<T> zoneType)
	{
		return (Collection<T>) _classZones.get(zoneType).values();
	}
	
	/**
	 * Get zone by ID
	 * @param id
	 * @return
	 * @see #getZoneById(int, Class)
	 */
	public L2ZoneType getZoneById(int id)
	{
		for (Map<Integer, ? extends L2ZoneType> map : _classZones.values())
		{
			if (map.containsKey(id))
				return map.get(id);
		}
		return null;
	}
	
	/**
	 * Get zone by ID and zone class
	 * @param <T>
	 * @param id
	 * @param zoneType
	 * @return zone
	 */
	@SuppressWarnings("unchecked")
	public <T extends L2ZoneType> T getZoneById(int id, Class<T> zoneType)
	{
		return (T) _classZones.get(zoneType).get(id);
	}
	
	/**
	 * Returns all zones from where the object is located
	 * @param object
	 * @return zones
	 */
	public List<L2ZoneType> getZones(L2Object object)
	{
		return getZones(object.getX(), object.getY(), object.getZ());
	}
	
	/**
	 * Returns zone from where the object is located by type
	 * @param <T>
	 * @param object
	 * @param type
	 * @return zone
	 */
	public <T extends L2ZoneType> T getZone(L2Object object, Class<T> type)
	{
		if (object == null)
			return null;
		
		return getZone(object.getX(), object.getY(), object.getZ(), type);
	}
	
	/**
	 * Returns all zones from given coordinates (plane)
	 * @param x
	 * @param y
	 * @return zones
	 */
	public List<L2ZoneType> getZones(int x, int y)
	{
		final List<L2ZoneType> temp = new ArrayList<>();
		for (L2ZoneType zone : L2World.getInstance().getRegion(x, y).getZones())
		{
			if (zone.isInsideZone(x, y))
				temp.add(zone);
		}
		return temp;
	}
	
	/**
	 * Returns all zones from given coordinates
	 * @param x
	 * @param y
	 * @param z
	 * @return zones
	 */
	public List<L2ZoneType> getZones(int x, int y, int z)
	{
		final List<L2ZoneType> temp = new ArrayList<>();
		for (L2ZoneType zone : L2World.getInstance().getRegion(x, y).getZones())
		{
			if (zone.isInsideZone(x, y, z))
				temp.add(zone);
		}
		return temp;
	}
	
	/**
	 * Returns zone from given coordinates
	 * @param <T>
	 * @param x
	 * @param y
	 * @param z
	 * @param type
	 * @return zone
	 */
	@SuppressWarnings("unchecked")
	public <T extends L2ZoneType> T getZone(int x, int y, int z, Class<T> type)
	{
		for (L2ZoneType zone : L2World.getInstance().getRegion(x, y).getZones())
		{
			if (zone.isInsideZone(x, y, z) && type.isInstance(zone))
				return (T) zone;
		}
		return null;
	}
	
	public static final L2ArenaZone getArena(Creature character)
	{
		if (character == null)
			return null;
		
		for (L2ZoneType temp : ZoneManager.getInstance().getZones(character.getX(), character.getY(), character.getZ()))
		{
			if (temp instanceof L2ArenaZone && temp.isCharacterInZone(character))
				return ((L2ArenaZone) temp);
		}
		
		return null;
	}
	
	public static final L2OlympiadStadiumZone getOlympiadStadium(Creature character)
	{
		if (character == null)
			return null;
		
		for (L2ZoneType temp : ZoneManager.getInstance().getZones(character.getX(), character.getY(), character.getZ()))
		{
			if (temp instanceof L2OlympiadStadiumZone && temp.isCharacterInZone(character))
				return ((L2OlympiadStadiumZone) temp);
		}
		return null;
	}
	
	/**
	 * For testing purposes only
	 * @param <T>
	 * @param obj
	 * @param type
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends L2ZoneType> T getClosestZone(L2Object obj, Class<T> type)
	{
		T zone = getZone(obj, type);
		if (zone == null)
		{
			double closestdis = Double.MAX_VALUE;
			for (T temp : (Collection<T>) _classZones.get(type).values())
			{
				double distance = temp.getDistanceToZone(obj);
				if (distance < closestdis)
				{
					closestdis = distance;
					zone = temp;
				}
			}
		}
		return zone;
	}
	
	
	private static class SingletonHolder
	{
		protected static final ZoneManager _instance = new ZoneManager();
	}
	
	public void addSpawnToZoneXml(L2ZoneType zone, Location spawnLoc) throws Exception
	{
	    String xmlFile = zone.getFileSource();
	    if (xmlFile == null)
	    {
	        _log.warning("ZoneManager: No XML source file set for zone " + zone.getId());
	        return;
	    }

	    File file = new File(xmlFile);
	    Document doc = XMLDocumentFactory.getInstance().loadDocument(file);
	    NodeList zoneNodes = doc.getElementsByTagName("zone");

	    boolean zoneFound = false;

	    for (int i = 0; i < zoneNodes.getLength(); i++)
	    {
	        Node zoneNode = zoneNodes.item(i);
	        NamedNodeMap attrs = zoneNode.getAttributes();
	        Node idAttr = attrs.getNamedItem("id");

	        if (idAttr != null && Integer.parseInt(idAttr.getNodeValue()) == zone.getId())
	        {
	            zoneFound = true;

	            Element spawnElement = doc.createElement("spawn");
	            spawnElement.setAttribute("X", String.valueOf(spawnLoc.getX()));
	            spawnElement.setAttribute("Y", String.valueOf(spawnLoc.getY()));
	            spawnElement.setAttribute("Z", String.valueOf(spawnLoc.getZ()));

	            zoneNode.appendChild(doc.createTextNode("\n    "));
	            zoneNode.appendChild(spawnElement);
	            zoneNode.appendChild(doc.createTextNode("\n"));
	            break;
	        }
	    }

	    if (!zoneFound)
	    {
	        _log.warning("ZoneManager: Zone id " + zone.getId() + " not found in XML file " + xmlFile);
	        return;
	    }

	    // Salva com identação
	    TransformerFactory tf = TransformerFactory.newInstance();
	    Transformer transformer = tf.newTransformer();
	    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	    transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

	    // Transforma o XML em string
	    StringWriter writer = new StringWriter();
	    transformer.transform(new DOMSource(doc), new StreamResult(writer));
	    String rawXml = writer.toString();

	    // Limpa o conteúdo e adiciona espaçamento entre zonas
	    String[] lines = rawXml.split("\n");
	    StringBuilder cleaned = new StringBuilder();

	    for (int i = 0; i < lines.length; i++)
	    {
	        String line = lines[i].replaceAll("\\s+$", ""); // remove espaços no final da linha
	        boolean isZoneClose = line.trim().equals("</zone>");

	        if (line.trim().isEmpty())
	            continue;

	        cleaned.append(line).append("\n");

	        if (isZoneClose)
	        {
	            cleaned.append("\n"); // Adiciona linha em branco entre zonas
	        }
	    }

	    // Escreve o XML limpo de volta no arquivo
	    try (BufferedWriter bw = new BufferedWriter(new FileWriter(file)))
	    {
	        bw.write(cleaned.toString());
	    }

	    _log.info("ZoneManager: Spawn added to zone id " + zone.getId() + " in file " + xmlFile);
	}



	public L2ZoneType getZone(L2Object object)
	{
	    if (object == null)
	        return null;

	    // Percorre todas as zonas, e retorna a primeira que contenha o objeto
	    for (Map<Integer, ? extends L2ZoneType> zones : _classZones.values())
	    {
	        for (L2ZoneType zone : zones.values())
	        {
	            if (zone.isInsideZone(object))
	                return zone;
	        }
	    }
	    return null;
	}

}