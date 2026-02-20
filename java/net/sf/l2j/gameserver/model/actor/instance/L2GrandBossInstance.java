package net.sf.l2j.gameserver.model.actor.instance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import net.sf.l2j.Config;
import net.sf.l2j.clan.ranking.ClanRankingConfig;
import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.event.bossevent.KTBConfig;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.instancemanager.RaidBossPointsManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2Spawn;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.template.NpcTemplate;
import net.sf.l2j.gameserver.model.entity.Hero;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.itemcontainer.Inventory;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.ExShowScreenMessage;
import net.sf.l2j.gameserver.network.serverpackets.InventoryUpdate;
import net.sf.l2j.gameserver.network.serverpackets.PlaySound;
import net.sf.l2j.gameserver.network.serverpackets.SocialAction;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.util.Broadcast;
import net.sf.l2j.upgrade.UpgradeItem;
import net.sf.l2j.upgrade.UpgradeItemData;

/**
 * This class manages all Grand Bosses.
 */
public final class L2GrandBossInstance extends L2MonsterInstance
{
	/**
	 * Constructor for L2GrandBossInstance. This represent all grandbosses.
	 * @param objectId ID of the instance
	 * @param template L2NpcTemplate of the instance
	 */
	public L2GrandBossInstance(int objectId, NpcTemplate template)
	{
		super(objectId, template);
		setIsRaid(true);
	}
	
	@Override
	public void onSpawn()
	{
		setIsNoRndWalk(true);
		super.onSpawn();
	}
	
	@Override
	public boolean doDie(Creature killer)
	{
		if (!super.doDie(killer))
			return false;
		
		if (_maintenanceTask != null)
		{
			_maintenanceTask.cancel(false);
			_maintenanceTask = null;
		}
		
		final Player player = killer.getActingPlayer();
		if (player != null)
		{
			if (Config.ANNOUNCE_GRANDBOS_KILL)
			{
				if (player.getClan() != null)
					Broadcast.gameAnnounceToOnlinePlayers("Epic Boss: " + getName() + " was killed by " + player.getName() + " of the clan: " + player.getClan().getName());
				else
					Broadcast.gameAnnounceToOnlinePlayers("Epic Boss: " + getName() + " was killed by " + player.getName());
			}
			if (ClanRankingConfig.ENABLE_CLAN_RANKING)
			{
				if (ClanRankingConfig.CLAN_RANKING_BOSS_POINTS_MAIN_KILLER.containsKey(Integer.valueOf(getNpcId())))
				{
					L2Clan clan = player.getClan();
					if (clan != null)
					{
						int points = ClanRankingConfig.CLAN_RANKING_BOSS_POINTS_MAIN_KILLER.get(Integer.valueOf(getNpcId())).intValue();
						clan.addRankingBossPoints(points);
						
						if (ClanRankingConfig.ENABLE_ANNOUNCE_POINTS_EARNED_ONKILL)
							Broadcast.gameAnnounceToOnlinePlayers("Clan " + clan.getName() + " gained " + points + " points defeating a Raid!");
					}
				}
			}
			
			Inventory inv = player.getInventory();
			
			// Cria uma lista para juntar inventário + equipado
			List<ItemInstance> allItems = new ArrayList<>();
			
			// Adiciona itens do inventário
			allItems.addAll(inv.getItems());
			
			// Adiciona itens equipados
			for (ItemInstance equippedItem : player.getInventory().getPaperdollItems())
			{
				if (equippedItem != null)
					allItems.add(equippedItem);
			}
			
			// Ordena a lista pelo enchant level decrescente
			Collections.sort(allItems, new Comparator<ItemInstance>()
			{
				@Override
				public int compare(ItemInstance i1, ItemInstance i2)
				{
					return Integer.compare(i2.getEnchantLevel(), i1.getEnchantLevel()); // decrescente
				}
			});
			
			boolean upgraded = false;
			
			for (ItemInstance item : allItems)
			{
				if (upgraded)
					break;
				
				UpgradeItem upgrade = UpgradeItemData.getInstance().getUpgrade(item.getItemId(), getNpcId());
				if (upgrade == null)
					continue;
				
				if (Rnd.get(100) < upgrade.getChance())
				{
					int enchantLevel = item.getEnchantLevel();
					
					// Remove o item (do inventário ou equipado)
					inv.destroyItem("UpgradeSuccess", item, player, this);
					
					// Adiciona o novo item upgradeado
					ItemInstance newItem = inv.addItem("UpgradeSuccess", upgrade.getNewItemId(), 1, player, this);
					if (newItem != null)
					{
						newItem.setEnchantLevel(enchantLevel);
						
						InventoryUpdate iu = new InventoryUpdate();
						iu.addModifiedItem(newItem);
						player.sendPacket(iu);
						
						player.broadcastPacket(new SocialAction(player, 16));
						
						String playerName = player.getName();
						String itemName = newItem.getName();
						int newEnchantLevel = newItem.getEnchantLevel();
						
						String enchantText = (newEnchantLevel > 0) ? "+" + newEnchantLevel + " " : "";
						
						player.sendPacket(new ExShowScreenMessage("Player " + playerName + " successfully upgraded the item " + enchantText + itemName + "!", 7 * 1000, ExShowScreenMessage.SMPOS.TOP_CENTER, true));
						
					}
					else
					{
						player.sendMessage("Error: Could not add the upgraded item.");
					}
					
					upgraded = true; // já fez upgrade, sai do loop
				}
				else
				{
					player.sendMessage("Failed Upgrade Item :(");
					upgraded = true; // considera item processado mesmo falhando
				}
			}
			broadcastPacket(SystemMessage.getSystemMessage(SystemMessageId.RAID_WAS_SUCCESSFUL));
			broadcastPacket(new PlaySound("systemmsg_e.1209"));
			
			if (player.isInParty())
			{
				for (Player member : player.getParty().getPartyMembers())
				{
					RaidBossPointsManager.getInstance().addPoints(member, getNpcId(), (getLevel() / 2) + Rnd.get(-5, 5));
					if (member.isNoble())
						Hero.getInstance().setRBkilled(member.getObjectId(), getNpcId());
				}
			}
			else
			{
				RaidBossPointsManager.getInstance().addPoints(player, getNpcId(), (getLevel() / 2) + Rnd.get(-5, 5));
				if (player.isNoble())
					Hero.getInstance().setRBkilled(player.getObjectId(), getNpcId());
			}
		}
		
		return true;
	}
	
	public void healFull()
	{
		super.setCurrentHp(super.getMaxHp());
		super.setCurrentMp(super.getMaxMp());
	}
	
	@Override
	protected void startMaintenanceTask()
	{
		// System.out.println("startMaintenanceTask() foi chamado!");
		
		super.startMaintenanceTask();
		
		if (_maintenanceTask != null)
		{
			_maintenanceTask.cancel(false);
			_maintenanceTask = null;
		}
		
		_maintenanceTask = ThreadPool.scheduleAtFixedRate(this::checkAndReturnToSpawn, 1_000L, // delay inicial em milissegundos
			1_000L // intervalo entre execuções
		);
	}
	
	protected void checkAndReturnToSpawn()
	{
		final int npcId = getNpcId();
		
		// Verificações iniciais
		if (isDead() || isMovementDisabled())
			return;
		
		// Verifica se o NPC está na lista de exceções
		if (npcId == 29095 || npcId == Config.SOLO_BOSS_ID_ONE || npcId == Config.SOLO_BOSS_ID_TWO || npcId == Config.SOLO_BOSS_ID_THREE || npcId == Config.SOLO_BOSS_ID_FOUR || npcId == Config.SOLO_BOSS_ID_FIVE || npcId == 29066 || npcId == 29067 || npcId == 29068 || npcId == 29028 ||
			
			(KTBConfig.LIST_KTB_EVENT_BOSS_ID != null && KTBConfig.LIST_KTB_EVENT_BOSS_ID.contains(npcId)))
		{
			return;
		}
		
		final L2Spawn spawn = getSpawn();
		if (spawn == null)
			return;
		
		// Verifica se está dentro do raio permitido — se sim, nem continua
		if (isInsideRadius(spawn.getLocX(), spawn.getLocY(), spawn.getLocZ(), Config.RANGE_EPICBOSS, true, false))
			return;
			
		// Fora do range: faz o teleport e cura se necessário
		// System.out.println("NPC ID " + npcId + " está fora do range. Teleportando para o spawn.");
		
		teleToLocation(spawn.getLoc(), 0);
		
		if (Config.ENABLE_FULL_HP_RECALL_EPIC)
		{
			healFull();
		}
	}
}