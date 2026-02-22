package net.sf.l2j.gameserver.datatables;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import net.sf.l2j.Config;
import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.datatables.xml.IconTable;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance.ItemLocation;
import net.sf.l2j.gameserver.model.item.kind.Armor;
import net.sf.l2j.gameserver.model.item.kind.EtcItem;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.kind.Weapon;
import net.sf.l2j.gameserver.model.item.type.EtcItemType;
import net.sf.l2j.gameserver.skills.DocumentItem;

public class ItemTable
{
	private static final Logger _log = Logger.getLogger(ItemTable.class.getName());
	private static final Logger _logItems = Logger.getLogger("item");
	
	public static final Map<String, Integer> _slots = new HashMap<>();
	
	private Item[] _allTemplates;
	private static final Map<Integer, Armor> _armors = new HashMap<>();
	private static final Map<Integer, EtcItem> _etcItems = new HashMap<>();
	private static final Map<Integer, Weapon> _weapons = new HashMap<>();
	
	static
	{
		_slots.put("chest", Item.SLOT_CHEST);
		_slots.put("fullarmor", Item.SLOT_FULL_ARMOR);
		_slots.put("alldress", Item.SLOT_ALLDRESS);
		_slots.put("head", Item.SLOT_HEAD);
		_slots.put("hair", Item.SLOT_HAIR);
		_slots.put("face", Item.SLOT_FACE);
		_slots.put("hairall", Item.SLOT_HAIRALL);
		_slots.put("underwear", Item.SLOT_UNDERWEAR);
		_slots.put("back", Item.SLOT_BACK);
		_slots.put("neck", Item.SLOT_NECK);
		_slots.put("legs", Item.SLOT_LEGS);
		_slots.put("feet", Item.SLOT_FEET);
		_slots.put("gloves", Item.SLOT_GLOVES);
		_slots.put("chest,legs", Item.SLOT_CHEST | Item.SLOT_LEGS);
		_slots.put("rhand", Item.SLOT_R_HAND);
		_slots.put("lhand", Item.SLOT_L_HAND);
		_slots.put("lrhand", Item.SLOT_LR_HAND);
		_slots.put("rear;lear", Item.SLOT_R_EAR | Item.SLOT_L_EAR);
		_slots.put("rfinger;lfinger", Item.SLOT_R_FINGER | Item.SLOT_L_FINGER);
		_slots.put("none", Item.SLOT_NONE);
		_slots.put("wolf", Item.SLOT_WOLF); // for wolf
		_slots.put("hatchling", Item.SLOT_HATCHLING); // for hatchling
		_slots.put("strider", Item.SLOT_STRIDER); // for strider
		_slots.put("babypet", Item.SLOT_BABYPET); // for babypet
	}
	
	public static ItemTable getInstance()
	{
		return SingletonHolder._instance;
	}
	
	protected ItemTable()
	{
		load();
	}
	
	private static void hashFiles(String dirname, List<File> hash)
	{
		File dir = new File("./data/xml/" + dirname);
		if (!dir.isDirectory())
		{
			_log.config("Dir " + dir.getAbsolutePath() + " doesn't exist.");
			return;
		}
		
		File[] files = dir.listFiles();
		for (File f : files)
		{
			if (f.getName().endsWith(".xml"))
				hash.add(f);
		}
	}
	
	private void load()
	{
		List<File> files = new ArrayList<>();
		hashFiles("items", files);
		hashFiles("items/Acessorys", files);
		hashFiles("items/Armors", files);
		hashFiles("items/EtcItems", files);
		hashFiles("items/Jeweles", files);
		hashFiles("items/Weapons", files);
		int highest = 0;
		for (File file : files)
		{
			DocumentItem document = new DocumentItem(file);
			document.parse();
			
			for (Item item : document.getItemList())
			{
				if (highest < item.getItemId())
					highest = item.getItemId();
				
				if (item instanceof EtcItem)
					_etcItems.put(item.getItemId(), (EtcItem) item);
				else if (item instanceof Armor)
					_armors.put(item.getItemId(), (Armor) item);
				else
					_weapons.put(item.getItemId(), (Weapon) item);
			}
		}
		
		_log.info("ItemTable: Highest used itemID : " + highest);
		
		// Feed an array with all items templates.
		_allTemplates = new Item[highest + 1];
		
		for (Armor item : _armors.values())
			_allTemplates[item.getItemId()] = item;
		
		for (Weapon item : _weapons.values())
			_allTemplates[item.getItemId()] = item;
		
		for (EtcItem item : _etcItems.values())
			_allTemplates[item.getItemId()] = item;
	}
	
	/**
	 * @param id : int designating the item
	 * @return the item corresponding to the item ID.
	 */
	public Item getTemplate(int id)
	{
		if (id >= _allTemplates.length)
			return null;
		
		return _allTemplates[id];
	}
	
	/**
	 * Create the ItemInstance corresponding to the Item Identifier and quantitiy add logs the activity.
	 * @param process : String Identifier of process triggering this action
	 * @param itemId : int Item Identifier of the item to be created
	 * @param count : int Quantity of items to be created for stackable items
	 * @param actor : L2PcInstance Player requesting the item creation
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 * @return ItemInstance corresponding to the new item
	 */
	public ItemInstance createItem(String process, int itemId, int count, Player actor, L2Object reference)
	{
		// Create and Init the ItemInstance corresponding to the Item Identifier
		ItemInstance item = new ItemInstance(IdFactory.getInstance().getNextId(), itemId);
		
		if (process.equalsIgnoreCase("loot"))
		{
			if (reference instanceof Attackable && ((Attackable) reference).isRaid())
			{
				final Attackable raid = (Attackable) reference;
				if (raid.getFirstCommandChannelAttacked() != null && !Config.AUTO_LOOT_RAID)
					item.setDropProtection(raid.getFirstCommandChannelAttacked().getChannelLeader().getObjectId(), true);
			}
			else if (!Config.AUTO_LOOT)
				item.setDropProtection(actor.getObjectId(), false);
		}
		
		// Add the ItemInstance object to _allObjects of L2world
		L2World.getInstance().addObject(item);
		
		// Set Item parameters
		if (item.isStackable() && count > 1)
			item.setCount(count);
		
		if (Config.LOG_ITEMS)
		{
			LogRecord record = new LogRecord(Level.INFO, "CREATE:" + process);
			record.setLoggerName("item");
			record.setParameters(new Object[]
			{
				item,
				actor,
				reference
			});
			_logItems.log(record);
		}
		
		return item;
	}
	
	/**
	 * Dummy item is created by setting the ID of the object in the world at null value
	 * @param itemId : int designating the item
	 * @return ItemInstance designating the dummy item created
	 */
	public ItemInstance createDummyItem(int itemId)
	{
		final Item item = getTemplate(itemId);
		if (item == null)
			return null;
		
		return new ItemInstance(0, item);
	}
	
	/**
	 * Destroys the ItemInstance.
	 * @param process : String Identifier of process triggering this action
	 * @param item : ItemInstance The instance of object to delete
	 * @param actor : L2PcInstance Player requesting the item destroy
	 * @param reference : L2Object Object referencing current action like NPC selling item or previous item in transformation
	 */
	public void destroyItem(String process, ItemInstance item, Player actor, L2Object reference)
	{
		synchronized (item)
		{
			item.setCount(0);
			item.setOwnerId(0);
			item.setLocation(ItemLocation.VOID);
			item.setLastChange(ItemInstance.REMOVED);
			
			L2World.getInstance().removeObject(item);
			IdFactory.getInstance().releaseId(item.getObjectId());
			
			if (Config.LOG_ITEMS)
			{
				LogRecord record = new LogRecord(Level.INFO, "DELETE:" + process);
				record.setLoggerName("item");
				record.setParameters(new Object[]
				{
					item,
					actor,
					reference
				});
				_logItems.log(record);
			}
			
			// if it's a pet control item, delete the pet as well
			if (item.getItemType() == EtcItemType.PET_COLLAR)
			{
				try (Connection con = ConnectionPool.getConnection())
				{
					PreparedStatement statement = con.prepareStatement("DELETE FROM pets WHERE item_obj_id=?");
					statement.setInt(1, item.getObjectId());
					statement.execute();
					statement.close();
				}
				catch (Exception e)
				{
					_log.log(Level.WARNING, "could not delete pet objectid:", e);
				}
			}
		}
	}
	
	public void reload()
	{
		_armors.clear();
		_etcItems.clear();
		_weapons.clear();
		IconTable.getInstance().reload();
		load();
	}
	
	public List<Item> searchItemsByName(String name)
	{
		List<Item> result = new ArrayList<>();
		String lowerName = name.toLowerCase();
		
		for (Armor armor : _armors.values())
		{
			if (armor.getName().toLowerCase().contains(lowerName))
			{
				result.add(armor);
			}
		}
		
		for (EtcItem etcItem : _etcItems.values())
		{
			if (etcItem.getName().toLowerCase().contains(lowerName))
			{
				result.add(etcItem);
			}
		}
		
		for (Weapon weapon : _weapons.values())
		{
			if (weapon.getName().toLowerCase().contains(lowerName))
			{
				result.add(weapon);
			}
		}
		
		return result;
	}
	
	private static class SingletonHolder
	{
		protected static final ItemTable _instance = new ItemTable();
	}
	
	/**
	 * @return the list of all {@link Item} templates.
	 */
	public Item[] getTemplates()
	{
		return _allTemplates;
	}
	
	public int getArraySize()
	{
		return _allTemplates.length;
	}
}