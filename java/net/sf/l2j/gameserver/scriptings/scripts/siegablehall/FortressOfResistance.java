package net.sf.l2j.gameserver.scriptings.scripts.siegablehall;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.datatables.ClanTable;
import net.sf.l2j.gameserver.datatables.MapRegionTable.TeleportWhereType;
import net.sf.l2j.gameserver.instancemanager.ClanHallManager;
import net.sf.l2j.gameserver.instancemanager.ZoneManager;
import net.sf.l2j.gameserver.model.L2Clan;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.entity.ClanHallSiege;
import net.sf.l2j.gameserver.model.zone.type.L2SiegeZone;
import net.sf.l2j.gameserver.network.serverpackets.NpcHtmlMessage;
import net.sf.l2j.gameserver.util.Broadcast;

public final class FortressOfResistance extends ClanHallSiege
{
	private static final int BRAKEL = 35382;
	private static final int CLAN_HALL_ID = 21;
	
	private static final int BLOODY_LORD_NURKA_1 = 35368;
	
	private static final int PARTISAN_HEALER = 35369;
	private static final int PARTISAN_COURT_GUARD_1 = 35370;
	private static final int PARTISAN_COURT_GUARD_2 = 35371;
	private static final int PARTISAN_SOLDIER = 35372;
	private static final int PARTISAN_SORCERER = 35373;
	private static final int PARTISAN_ARCHER = 35374;
	
	private static final int NURKA_HEAL_SKILL_ID = 4044;
	private static final long SIEGE_DURATION = 60 * 60 * 1000L; // 1 hora
	
	private static final String HTML_PATH = "data/html/siegablehall/FortressOfResistance/partisan_ordery_brakel001.htm";
	private static final SimpleDateFormat SIEGE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
	
	private final List<L2Npc> _spawnedNpcs = new ArrayList<>();
	private final Map<Integer, Long> _damageByClan = new ConcurrentHashMap<>();
	
	private final Map<Integer, Long> _damageByPlayer = new ConcurrentHashMap<>();
	private final Map<Integer, String> _playerNames = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> _playerClanIds = new ConcurrentHashMap<>();
	
	private final List<L2MonsterInstance> _healers = new ArrayList<>();
	private L2MonsterInstance _nurka = null;
	
	private L2SiegeZone _siegeZone;
	
	public FortressOfResistance()
	{
		super("FortressOfResistance", FORTRESS_OF_RESISTANCE, "siegablehall");
		
		addFirstTalkId(BRAKEL);
		addStartNpc(BRAKEL);
		addTalkId(BRAKEL);
		
		addAttackId(BLOODY_LORD_NURKA_1);
		addKillId(BLOODY_LORD_NURKA_1);
		
		initSiegeZone();

	}
	
	private void initSiegeZone()
	{
		for (L2SiegeZone zone : ZoneManager.getInstance().getAllZones(L2SiegeZone.class))
		{
			if (zone != null && zone.getSiegeObjectId() == CLAN_HALL_ID)
			{
				_siegeZone = zone;
				return;
			}
		}
		
	}
	
	private void startSiegeZone()
	{
		if (_siegeZone == null)
		{
			
			return;
		}
		
		for (Player player : _siegeZone.getKnownTypeInside(Player.class))
		{
			if (player != null && !player.isGM())
				player.teleToLocation(TeleportWhereType.TOWN);
		}
		
		_siegeZone.setIsActive(true);
		_siegeZone.updateZoneStatusForCharactersInside();
		
	}
	
	private void stopSiegeZone()
	{
		if (_siegeZone == null)
		{
			
			return;
		}
		
		_siegeZone.setIsActive(false);
		_siegeZone.updateZoneStatusForCharactersInside();
		
	}
	
	private void banishLosers(int winnerClanId)
	{
		if (_siegeZone == null)
		{
			
			return;
		}
		
		for (Player player : _siegeZone.getKnownTypeInside(Player.class))
		{
			if (player == null || player.isGM())
				continue;
			
			if (player.getClanId() != winnerClanId)
				player.teleToLocation(TeleportWhereType.TOWN);
		}
		
	}
	
	private void showMainHtml(L2Npc npc, Player player)
	{
		if (npc == null || player == null)
			return;
		
		final NpcHtmlMessage html = new NpcHtmlMessage(npc.getObjectId());
		html.setFile(HTML_PATH);
		
		String nextSiege = "unknown";
		if (_hall != null && _hall.getSiegeDate() != null)
			nextSiege = SIEGE_FORMAT.format(_hall.getSiegeDate().getTime());
		
		html.replace("%objectId%", String.valueOf(npc.getObjectId()));
		html.replace("%nextSiege%", nextSiege);
		player.sendPacket(html);
	}
	
	@Override
	public String onFirstTalk(L2Npc npc, Player player)
	{
		showMainHtml(npc, player);
		return null;
	}
	
	@Override
	public String onTalk(L2Npc npc, Player player)
	{
		showMainHtml(npc, player);
		return null;
	}
	
	@Override
	public String onAttack(L2Npc npc, Player player, int damage, boolean isPet, L2Skill skill)
	{
		if (npc == null || player == null)
			return null;
		
		if (npc.getNpcId() != BLOODY_LORD_NURKA_1)
			return null;
		
		if (player.getClan() == null)
			return null;
		
		_damageByClan.merge(player.getClanId(), (long) damage, Long::sum);
		_damageByPlayer.merge(player.getObjectId(), (long) damage, Long::sum);
		_playerNames.put(player.getObjectId(), player.getName());
		_playerClanIds.put(player.getObjectId(), player.getClanId());
		
		return null;
	}
	
	@Override
	public String onKill(L2Npc npc, Player killer, boolean isPet)
	{
		if (npc == null)
			return null;
		
		if (npc.getNpcId() != BLOODY_LORD_NURKA_1)
			return null;
		
		cancelQuestTimer("heal_nurka", null, null);
		cancelQuestTimer("end_siege", null, null);
		
		printDamageReport();
		
		if (_damageByClan.isEmpty())
		{
			if (_hall != null)
				_hall.updateNextSiege();
			
			unspawnNpcs();
			return null;
		}
		
		final int winnerClanId = Collections.max(_damageByClan.entrySet(), Map.Entry.comparingByValue()).getKey();
		final L2Clan winnerClan = ClanTable.getInstance().getClan(winnerClanId);
		
		if (winnerClan == null)
		{
			if (_hall != null)
				_hall.updateNextSiege();
			
			unspawnNpcs();
			return null;
		}
		
		final int currentOwnerId = (_hall != null) ? _hall.getOwnerId() : 0;
		
		if (winnerClanId != currentOwnerId)
		{
			ClanHallManager.getInstance().setOwner(CLAN_HALL_ID, winnerClan);
			
			Broadcast.announceToOnlinePlayers("The clan " + winnerClan.getName() + " has conquered Fortress of Resistance.");
		}
		else
		{
			
			Broadcast.announceToOnlinePlayers("The clan " + winnerClan.getName() + " has successfully defended Fortress of Resistance.");
		}
		
		banishLosers(winnerClanId);
		
		if (_hall != null)
			_hall.updateNextSiege();
		
		_damageByClan.clear();
		_damageByPlayer.clear();
		_playerNames.clear();
		_playerClanIds.clear();
		
		unspawnNpcs();
		return null;
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, Player player)
	{
		if (event.equalsIgnoreCase("end_siege"))
		{
			cancelQuestTimer("heal_nurka", null, null);
			
			if (_nurka != null && !_nurka.isDead())
			{
				printDamageReport();
				
				Broadcast.announceToOnlinePlayers("Fortress of Resistance siege has ended. The current owner keeps the clan hall.");
			}
			
			if (_hall != null)
				_hall.updateNextSiege();
			
			_damageByClan.clear();
			_damageByPlayer.clear();
			_playerNames.clear();
			_playerClanIds.clear();
			
			unspawnNpcs();
			return null;
		}
		
		if (!event.equalsIgnoreCase("heal_nurka"))
			return null;
		
		if (_nurka == null || _nurka.isDead())
			return null;
		
		final boolean nurkaNeedHeal = _nurka.getCurrentHp() < _nurka.getMaxHp();
		if (!nurkaNeedHeal)
			return null;
		
		for (L2MonsterInstance healer : _healers)
		{
			if (healer == null || healer.isDead() || healer.isCastingNow())
				continue;
			
			if (healer.getAI() == null)
				continue;
			
			final L2Skill healSkill = healer.getSkill(NURKA_HEAL_SKILL_ID);
			if (healSkill == null)
				continue;
			
			healer.setTarget(_nurka);
			healer.getAI().setIntention(CtrlIntention.CAST, healSkill, _nurka);
		}
		return null;
	}
	
	private void printDamageReport()
	{
		long totalDamage = 0L;
		
		for (long dmg : _damageByClan.values())
			totalDamage += dmg;
		
		
		final List<Map.Entry<Integer, Long>> clans = new ArrayList<>(_damageByClan.entrySet());
		Collections.sort(clans, (a, b) -> Long.compare(b.getValue(), a.getValue()));
		
		for (Map.Entry<Integer, Long> clanEntry : clans)
		{
			final int clanId = clanEntry.getKey();
			final long clanDamage = clanEntry.getValue();
			final double clanPercent = (clanDamage * 100.0) / totalDamage;
			
			final L2Clan clan = ClanTable.getInstance().getClan(clanId);
			final String clanName = (clan != null) ? clan.getName() : ("UnknownClan(" + clanId + ")");
			
			System.out.println(String.format("Clan %s -> %d damage (%.2f%%)", clanName, clanDamage, clanPercent));
			
			final List<Map.Entry<Integer, Long>> members = new ArrayList<>();
			
			for (Map.Entry<Integer, Long> playerEntry : _damageByPlayer.entrySet())
			{
				final int objectId = playerEntry.getKey();
				final Integer playerClanId = _playerClanIds.get(objectId);
				
				if (playerClanId != null && playerClanId == clanId)
					members.add(playerEntry);
			}
			
			Collections.sort(members, (a, b) -> Long.compare(b.getValue(), a.getValue()));
			
		}
		
		
	}
	
	@Override
	public L2Clan getWinner()
	{
		return null;
	}
	
	@Override
	public void spawnNpcs()
	{
		unspawnNpcs();
		
		_damageByClan.clear();
		_damageByPlayer.clear();
		_playerNames.clear();
		_playerClanIds.clear();
		
		_healers.clear();
		_nurka = null;
		
		startSiegeZone();
		
		final L2Npc nurkaNpc = spawnNpcReturn(BLOODY_LORD_NURKA_1, 44584, 108776, -2032, 16000);
		if (nurkaNpc instanceof L2MonsterInstance)
			_nurka = (L2MonsterInstance) nurkaNpc;
		
		addHealer(spawnNpcReturn(PARTISAN_HEALER, 44620, 108776, -2032, 16000));
		addHealer(spawnNpcReturn(PARTISAN_HEALER, 44550, 108776, -2032, 16000));
		
		spawnNpc(PARTISAN_COURT_GUARD_1, 44610, 108820, -2032, 16000);
		spawnNpc(PARTISAN_COURT_GUARD_1, 44560, 108820, -2032, 16000);
		spawnNpc(PARTISAN_COURT_GUARD_2, 44610, 108730, -2032, 16000);
		spawnNpc(PARTISAN_COURT_GUARD_2, 44560, 108730, -2032, 16000);
		
		spawnNpc(PARTISAN_SOLDIER, 44640, 108776, -2032, 16000);
		spawnNpc(PARTISAN_SOLDIER, 44530, 108776, -2032, 16000);
		spawnNpc(PARTISAN_SOLDIER, 44584, 108840, -2032, 16000);
		spawnNpc(PARTISAN_SOLDIER, 44584, 108710, -2032, 16000);
		
		spawnNpc(PARTISAN_SORCERER, 44630, 108820, -2032, 16000);
		spawnNpc(PARTISAN_SORCERER, 44540, 108730, -2032, 16000);
		
		spawnNpc(PARTISAN_ARCHER, 44660, 108800, -2032, 16000);
		spawnNpc(PARTISAN_ARCHER, 44510, 108750, -2032, 16000);
		
		startQuestTimer("heal_nurka", 1000, null, null, true);
		startQuestTimer("end_siege", SIEGE_DURATION, null, null, false);
		
		System.out.println("FortressOfResistance: spawned " + _spawnedNpcs.size() + " siege NPCs.");
	}
	
	@Override
	public void unspawnNpcs()
	{
		cancelQuestTimer("heal_nurka", null, null);
		cancelQuestTimer("end_siege", null, null);
		
		stopSiegeZone();
		
		for (L2Npc npc : _spawnedNpcs)
		{
			if (npc != null)
				npc.deleteMe();
		}
		
		_spawnedNpcs.clear();
		_healers.clear();
		_nurka = null;
		
		System.out.println("FortressOfResistance: unspawned siege NPCs.");
	}
	
	private void addHealer(L2Npc npc)
	{
		if (npc == null)
			return;
		
		if (npc instanceof L2MonsterInstance)
		{
			final L2MonsterInstance healer = (L2MonsterInstance) npc;
			_healers.add(healer);
		}
	}
	
	private void spawnNpc(int npcId, int x, int y, int z, int heading)
	{
		spawnNpcReturn(npcId, x, y, z, heading);
	}
	
	private L2Npc spawnNpcReturn(int npcId, int x, int y, int z, int heading)
	{
		try
		{
			final L2Npc npc = addSpawn(npcId, x, y, z, heading, false, 0, false);
			if (npc != null)
				_spawnedNpcs.add(npc);
			
			return npc;
		}
		catch (Exception e)
		{
			System.out.println("FortressOfResistance: failed to spawn npcId=" + npcId + " at " + x + "," + y + "," + z);
			e.printStackTrace();
		}
		return null;
	}
}