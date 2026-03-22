package net.sf.l2j.mods.data;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.cache.CrestCache;
import net.sf.l2j.gameserver.cache.CrestCache.CrestType;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.idfactory.IdFactory;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2ClanMember;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.network.serverpackets.CharInfo;
import net.sf.l2j.gameserver.network.serverpackets.PledgeShowMemberListAll;
import net.sf.l2j.gameserver.network.serverpackets.UserInfo;
import net.sf.l2j.gameserver.templates.StatsSet;
import net.sf.l2j.mods.actor.FakePlayer;
import net.sf.l2j.mods.holder.ClanAllyCrestHolder;
import net.sf.l2j.mods.manager.FakePlayerManager;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;

public class ClanAllyCrestData implements IXmlReader
{
	private static final Logger _log = Logger.getLogger(ClanAllyCrestData.class.getName());
	
	private final Map<Integer, ClanAllyCrestHolder> _clansById = new ConcurrentHashMap<>();
	private final Map<String, ClanAllyCrestHolder> _clansByName = new ConcurrentHashMap<>();
	
	public ClanAllyCrestData()
	{
		load();
	}
	
	@Override
	public void load()
	{
		_clansById.clear();
		_clansByName.clear();
		
		parseFile("data/mods/clanCrest.xml");
		
		validateWars();
		validateCrests();
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> {
			forEach(listNode, "clan", clanNode -> {
				final NamedNodeMap attrs = clanNode.getAttributes();
				final StatsSet set = new StatsSet();
				
				set.set("id", parseInteger(attrs, "id"));
				set.set("name", parseString(attrs, "name"));
				set.set("level", parseInteger(attrs, "level", 0));
				set.set("reputation", parseInteger(attrs, "reputation", 0));
				
				set.set("allyId", parseInteger(attrs, "allyId", 0));
				set.set("allyName", parseString(attrs, "allyName", ""));
				set.set("clanCrest", parseString(attrs, "clanCrest", ""));
				set.set("allyCrest", parseString(attrs, "allyCrest", ""));
				
				final ClanAllyCrestHolder holder = new ClanAllyCrestHolder(set);
				
				if (holder.getId() <= 0)
				{
					_log.warning(getClass().getSimpleName() + ": Skipped clan with invalid id: " + holder);
					return;
				}
				
				if (holder.getName() == null || holder.getName().isEmpty())
				{
					_log.warning(getClass().getSimpleName() + ": Skipped clan with empty name. id=" + holder.getId());
					return;
				}
				
				if (_clansById.containsKey(holder.getId()))
				{
					_log.warning(getClass().getSimpleName() + ": Duplicate clan id " + holder.getId() + ", skipped.");
					return;
				}
				
				if (_clansByName.containsKey(holder.getName().toLowerCase()))
				{
					_log.warning(getClass().getSimpleName() + ": Duplicate clan name " + holder.getName() + ", skipped.");
					return;
				}
				
				forEach(clanNode, "war", warNode -> {
					final NamedNodeMap warAttrs = warNode.getAttributes();
					final int targetClanId = parseInteger(warAttrs, "clanId", 0);
					
					if (targetClanId > 0)
						holder.addWarClanId(targetClanId);
				});
				
				_clansById.put(holder.getId(), holder);
				_clansByName.put(holder.getName().toLowerCase(), holder);
			});
		});
	}
	
	private void validateWars()
	{
		for (ClanAllyCrestHolder holder : _clansById.values())
		{
			for (int warClanId : holder.getWarClanIds())
			{
				if (!_clansById.containsKey(warClanId))
					_log.warning(getClass().getSimpleName() + ": Clan " + holder.getName() + " has war with unknown clanId=" + warClanId);
			}
		}
	}
	
	private void validateCrests()
	{
		for (ClanAllyCrestHolder holder : _clansById.values())
		{
			checkCrest(holder.getClanCrestPath(), holder.getName(), "clan");
			checkCrest(holder.getAllyCrestPath(), holder.getName(), "ally");
		}
	}
	
	private void checkCrest(String path, String clanName, String type)
	{
		if (path == null || path.isEmpty())
			return;
		
		final Path file = Paths.get(path);
		if (!Files.exists(file))
			_log.warning(getClass().getSimpleName() + ": Missing " + type + " crest for clan " + clanName + ": " + path);
	}
	
	public ClanAllyCrestHolder getClan(int id)
	{
		return _clansById.get(id);
	}
	
	public ClanAllyCrestHolder getClanByName(String name)
	{
		if (name == null)
			return null;
		
		return _clansByName.get(name.toLowerCase());
	}
	
	public Collection<ClanAllyCrestHolder> getClans()
	{
		return _clansById.values();
	}
	
	public boolean hasClan(int id)
	{
		return _clansById.containsKey(id);
	}
	
	public void siegeStart(Siege siege)
	{
		final List<FakePlayer> eligible = new ArrayList<>();
		
		for (Player player : L2World.getInstance().getPlayers())
		{
			final FakePlayer fake = FakePlayerManager.getInstance().getPlayer(player.getObjectId());
			if (fake == null)
				continue;
			
			if (fake.getClassId() == null || fake.getClassId().level() != 3)
				continue;
			
			if (fake.getClan() != null)
				continue;
			
			eligible.add(fake);
			
		}
		
		if (eligible.isEmpty())
		{
			_log.info("ClanAllyCrestData: No eligible fake players for siege clan creation.");
			return;
		}
		
		final List<ClanAllyCrestHolder> allHolders = new ArrayList<>(getClans());
		if (allHolders.isEmpty())
		{
			_log.warning("ClanAllyCrestData: No fake clan templates loaded from XML.");
			return;
		}
		
		// 1) descobrir holders ainda nao usados por clans phantom existentes
		final List<ClanAllyCrestHolder> freeHolders = new ArrayList<>();
		for (ClanAllyCrestHolder holder : allHolders)
		{
			final L2Clan existing = ClanTable.getInstance().getClan(holder.getId());
			if (existing == null)
				freeHolders.add(holder);
		}
		
		// 2) pegar clans phantom existentes que ainda possuem vaga
		final List<L2Clan> availableClans = new ArrayList<>();
		for (ClanAllyCrestHolder holder : allHolders)
		{
			final L2Clan clan = ClanTable.getInstance().getClan(holder.getId());
			if (clan != null && getClanMemberCount(clan) < 40)
				availableClans.add(clan);
		}
		
		// 3) criar novos clans: 1 fake vira lider de cada holder livre
		final int clansToCreate = Math.min(eligible.size(), freeHolders.size());
		
		for (int i = 0; i < clansToCreate; i++)
		{
			final FakePlayer leader = eligible.remove(0);
			final ClanAllyCrestHolder holder = freeHolders.get(i);
			
			final L2Clan clan = ClanTable.getInstance().createPhantomClan(leader, holder);
			if (clan == null)
			{
				_log.warning("ClanAllyCrestData: Failed to create phantom clan for holder " + holder.getName());
				continue;
			}
			
			applyClanCrest(clan, holder);
			applyAllyCrest(clan, holder);
			
			availableClans.add(clan);
			if (siege != null)
				siege.approveSiegeDefenderClan(clan.getClanId());
			
			_log.info("ClanAllyCrestData: Created phantom clan " + clan.getName() + " with leader " + leader.getName() + ".");
		}
		
		if (availableClans.isEmpty())
		{
			_log.warning("ClanAllyCrestData: No phantom clan available to receive remaining fake players.");
			return;
		}
		
		// 4) distribuir o restante nos clans com menor quantidade de membros
		for (FakePlayer member : eligible)
		{
			final L2Clan targetClan = findClanWithFreeSlot(availableClans);
			if (targetClan == null)
			{
				_log.warning("ClanAllyCrestData: All phantom clans are full (40 members). Remaining fake player: " + member.getName());
				break;
			}
			
			addPhantomMemberToClan(targetClan, member);
		}
		
		applyFakeWars();
		
		for (L2Clan clan : availableClans)
		{
			_log.info("ClanAllyCrestData: Phantom clan " + clan.getName() + " now has " + getClanMemberCount(clan) + " members.");
			
		}
	}
	
	private static int getClanMemberCount(L2Clan clan)
	{
		if (clan == null || clan.getMembers() == null)
			return 0;
		
		return clan.getMembers().length;
	}
	
	private static L2Clan findClanWithFreeSlot(List<L2Clan> clans)
	{
		L2Clan selected = null;
		int lowestCount = Integer.MAX_VALUE;
		
		for (L2Clan clan : clans)
		{
			final int count = getClanMemberCount(clan);
			if (count >= 40)
				continue;
			
			if (count < lowestCount)
			{
				lowestCount = count;
				selected = clan;
			}
		}
		
		return selected;
	}
	
	public void addPhantomMemberToClan(L2Clan clan, Player player)
	{
		if (clan == null || player == null)
			return;
		
		if (player.getClan() != null)
			return;
		
		final L2ClanMember member = new L2ClanMember(clan, player);
		member.setPlayerInstance(member.getPlayerInstance());
		
		member.getPlayerInstance().setClan(clan);
		member.getPlayerInstance().setPledgeClass(L2ClanMember.calculatePledgeClass(member.getPlayerInstance()));
		member.getPlayerInstance().setClanPrivileges(0);
		
		clan.addClanMember(member.getPlayerInstance());
		
		member.getPlayerInstance().sendPacket(new PledgeShowMemberListAll(clan, 0));
		member.getPlayerInstance().sendPacket(new UserInfo(member.getPlayerInstance()));
		member.getPlayerInstance().sendPacket(new CharInfo(member.getPlayerInstance()));
		
	}
	
	public void applyClanCrest(L2Clan clan, ClanAllyCrestHolder holder)
	{
		if (clan == null || holder == null)
			return;
		
		final byte[] data = loadCrestBytes(holder.getClanCrestPath());
		if (data == null || data.length == 0)
			return;
		
		if (!CrestCache.isValidCrestData(data))
		{
			_log.warning("Invalid clan crest data for " + holder.getName() + ": " + holder.getClanCrestPath());
			return;
		}
		
		final int crestId = IdFactory.getInstance().getNextId();
		if (CrestCache.getInstance().saveCrest(CrestType.PLEDGE, crestId, data))
			clan.changeClanCrest(crestId);
	}
	
	public void applyAllyCrest(L2Clan clan, ClanAllyCrestHolder holder)
	{
		if (clan == null || holder == null)
			return;
		
		if (holder.getAllyId() <= 0 || holder.getAllyName() == null || holder.getAllyName().isEmpty())
			return;
		
		final byte[] data = loadCrestBytes(holder.getAllyCrestPath());
		if (data == null || data.length == 0)
			return;
		
		if (!CrestCache.isValidCrestData(data))
		{
			_log.warning("Invalid ally crest data for " + holder.getName() + ": " + holder.getAllyCrestPath());
			return;
		}
		
		final int crestId = IdFactory.getInstance().getNextId();
		if (CrestCache.getInstance().saveCrest(CrestType.ALLY, crestId, data))
			clan.changeAllyCrest(crestId, false);
	}
	
	private void applyFakeWars()
	{
		for (ClanAllyCrestHolder holder : getClans())
		{
			final L2Clan clan = ClanTable.getInstance().getClanByName(holder.getName());
			if (clan == null)
				continue;
			
			for (int enemyId : holder.getWarClanIds())
			{
				final ClanAllyCrestHolder enemyHolder = getClan(enemyId);
				if (enemyHolder == null)
					continue;
				
				final L2Clan enemyClan = ClanTable.getInstance().getClanByName(enemyHolder.getName());
				if (enemyClan == null)
					continue;
				
				ClanTable.getInstance().storeClansWars(clan.getClanId(), enemyClan.getClanId());
				
				for (L2ClanMember clan1 : clan.getMembers())
				{
					clan1.getClan().broadcastClanStatus();
				}
				
				for (L2ClanMember clan2 : enemyClan.getMembers())
				{
					clan2.getClan().broadcastClanStatus();
				}
			}
		}
	}
	
	public byte[] loadCrestBytes(String path)
	{
		if (path == null || path.isEmpty())
			return null;
		
		try
		{
			final Path file = Paths.get(path);
			if (!Files.exists(file))
				return null;
			
			return Files.readAllBytes(file);
		}
		catch (Exception e)
		{
			_log.warning("Failed to read crest file: " + path + " -> " + e.getMessage());
			return null;
		}
	}
	
	public static ClanAllyCrestData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final ClanAllyCrestData INSTANCE = new ClanAllyCrestData();
	}
}