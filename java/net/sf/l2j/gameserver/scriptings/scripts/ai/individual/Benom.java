package net.sf.l2j.gameserver.scriptings.scripts.ai.individual;

import java.util.ArrayList;
import java.util.List;

import net.sf.l2j.commons.random.Rnd;
import net.sf.l2j.gameserver.cache.HtmCache;
import net.sf.l2j.gameserver.datatables.MapRegionTable.TeleportWhereType;
import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.SpawnLocation;
import net.sf.l2j.gameserver.model.actor.Attackable;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.entity.Siege;
import net.sf.l2j.gameserver.model.entity.Siege.SiegeStatus;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.clientpackets.Say2;
import net.sf.l2j.gameserver.network.serverpackets.NpcSay;
import net.sf.l2j.gameserver.scriptings.EventType;
import net.sf.l2j.gameserver.scriptings.scripts.ai.L2AttackableAIScript;

/**
 * Benom (Interlude / L2OFF-like) - Spawns 24h before Rune siege for owner clan access. - If not killed, can appear during siege after tower condition. - Combat: controlled casting (cooldowns + throttle), avoids skill spam.
 */
public class Benom extends L2AttackableAIScript
{
	private static final int BENOM = 29054;
	private static final int TELEPORT_CUBE = 29055;
	private static final int DUNGEON_KEEPER = 35506;
	
	private static final long DAY = 86_400_000L;
	
	// ===== Skills (Interlude Benom behavior) =====
	private static final int SKILL_SINGLE_TELEPORT = 4995; // teleport target + drop hate
	private static final int SKILL_MASS_TELEPORT = 4996; // teleport nearby targets + drop hate
	private static final int SKILL_RANGE_PULL_1 = 4993; // long range skill (client-known ids)
	private static final int SKILL_RANGE_PULL_2 = 4994;
	
	// ===== Cast control (L2OFF-like throttles) =====
	private static final long GLOBAL_CAST_GCD_MS = 2500; // mínimo entre casts (evita spam)
	private static final long CD_SINGLE_TELE_MS = 12000; // 12s
	private static final long CD_MASS_TELE_MS = 30000; // 30s (mais raro)
	private static final long CD_RANGE_MS = 15000; // 15s
	
	// chance “por janela de tempo”, não por hit
	private static final long CHECK_WINDOW_MS = 1500; // a cada 1.5s decide se tenta skill
	private static final int BASE_CAST_CHANCE = 18; // % por janela
	private static final int EMERGENCY_CHANCE = 30; // % quando hp baixo
	
	// condições
	private static final int MASS_TELE_HP_PCT = 35; // abaixo disso pode usar mass teleport
	private static final int FAR_DISTANCE = 300; // se alvo longe, tenta ranged skills
	
	private int _initialCtCount = -1;
	
	// ===== Teleports =====
	private static final SpawnLocation[] TARGET_TELEPORTS =
	{
		new SpawnLocation(12860, -49158, -976, 650),
		new SpawnLocation(14878, -51339, 1024, 100),
		new SpawnLocation(15674, -49970, 864, 100),
		new SpawnLocation(15696, -48326, 864, 100),
		new SpawnLocation(14873, -46956, 1024, 100),
		new SpawnLocation(12157, -49135, -1088, 650),
		new SpawnLocation(12875, -46392, -288, 200),
		new SpawnLocation(14087, -46706, -288, 200),
		new SpawnLocation(14086, -51593, -288, 200),
		new SpawnLocation(12864, -51898, -288, 200),
		new SpawnLocation(15538, -49153, -1056, 200),
		new SpawnLocation(17001, -49149, -1064, 650)
	};
	
	private static final SpawnLocation THRONE_LOC = new SpawnLocation(11025, -49152, -537, 0);
	private static final SpawnLocation PRISON_LOC = new SpawnLocation(11882, -49216, -3008, 0);
	
	private Siege _siege;
	private L2Npc _benom;
	private boolean _isPrisonOpened;
	
	// alvo para mass teleport (evita scan em todo hit)
	private final List<Player> _targets = new ArrayList<>();
	
	// ===== Timers internos (cooldown) =====
	private volatile long _nextAiCheckMs = 0;
	private volatile long _nextGlobalCastMs = 0;
	
	private volatile long _nextSingleTeleMs = 0;
	private volatile long _nextMassTeleMs = 0;
	private volatile long _nextRangeMs = 0;
	
	public Benom()
	{
		super("ai/individual");
		
		_siege = addSiegeNotify(8);
		
		addStartNpc(DUNGEON_KEEPER, TELEPORT_CUBE);
		addTalkId(DUNGEON_KEEPER, TELEPORT_CUBE);
	}
	
	@Override
	protected void registerNpcs()
	{
		addEventIds(BENOM, EventType.ON_AGGRO, EventType.ON_SPELL_FINISHED, EventType.ON_ATTACK, EventType.ON_KILL);
	}
	
	@Override
	public String onTalk(L2Npc npc, Player talker)
	{
		switch (npc.getNpcId())
		{
			case TELEPORT_CUBE:
				talker.teleToLocation(TeleportWhereType.TOWN);
				break;
			
			case DUNGEON_KEEPER:
				if (_isPrisonOpened)
					talker.teleToLocation(12589, -49044, -3008, 0);
				else
					return HtmCache.getInstance().getHtm("data/html/doormen/35506-2.htm");
				break;
		}
		return super.onTalk(npc, talker);
	}
	
	@Override
	public String onAdvEvent(String event, L2Npc npc, Player player)
	{
		switch (event)
		{
			case "benom_spawn":
			{
				_isPrisonOpened = true;
				
				if (_benom != null && !_benom.isDead())
					return event;
				
				_benom = addSpawn(BENOM, PRISON_LOC, false, 0, false);
				
				if (_benom != null)
				{
					_benom.broadcastNpcSay("Who dares to covet the throne of our castle! Leave immediately or you will pay the price of your audacity with your very own blood!");
					
					// se siege em andamento, começa checks agora
					if (_siege.getStatus() == SiegeStatus.IN_PROGRESS)
						startQuestTimer("tower_check", 30000, _benom, null, true);
				}
				
				// reseta controles de cast ao (re)spawn
				resetCastControl();
				break;
			}
			
			case "tower_check":
			{
				if (npc == null)
					return event;
				
				final int alive = _siege.getControlTowerCount();
				if (_initialCtCount > 0 && alive <= (_initialCtCount - 2))
				{
					npc.teleToLocation(THRONE_LOC, 0);
					_siege.getCastle().getCastleZone().broadcastPacket(new NpcSay(0, Say2.ALL, DUNGEON_KEEPER, "Oh no! The defenses have failed. It is too dangerous to remain inside the castle. Flee! Every man for himself!"));
					
					cancelQuestTimer("tower_check", npc, null);
					startQuestTimer("raid_check", 10000, npc, null, true);
				}
				
				break;
			}
			
			case "raid_check":
			{
				if (npc == null)
					return event;
				
				// se for puxado pra fora/bug, traz de volta
				if (!npc.isInsideZone(ZoneId.SIEGE) && !npc.isTeleporting())
					npc.teleToLocation(THRONE_LOC, 0);
				break;
			}
		}
		return event;
	}
	
	@Override
	public void onSiegeEvent()
	{
		// sem dono: não roda (L2OFF-like)
		if (_siege.getCastle().getOwnerId() <= 0)
			return;
		
		switch (_siege.getStatus())
		{
			case REGISTRATION_OPENED:
			{
				_isPrisonOpened = false;
				_initialCtCount = -1;
				
				if (_benom != null)
				{
					cancelQuestTimer("tower_check", _benom, null);
					cancelQuestTimer("raid_check", _benom, null);
					_benom.deleteMe();
					_benom = null;
				}
				
				long delay = _siege.getSiegeDate().getTimeInMillis() - DAY - System.currentTimeMillis();
				if (delay < 0)
					delay = 1000L;
				
				startQuestTimer("benom_spawn", delay, null, null, false);
				break;
			}
			
			case REGISTRATION_OVER:
				// L2OFF-like: não spawna aqui
				break;
			
			case IN_PROGRESS:
			{
				_isPrisonOpened = false;
				
				if (_initialCtCount < 0)
					_initialCtCount = _siege.getControlTowerCount(); // total vivo no início = total inicial
					
				if (_benom == null || _benom.isDead())
					startQuestTimer("benom_spawn", 1000L, null, null, false);
				else
					startQuestTimer("tower_check", 30000, _benom, null, true);
				
				break;
			}
			
			default:
				break;
		}
	}
	
	@Override
	public String onAggro(L2Npc npc, Player player, boolean isPet)
	{
		if (isPet)
			return super.onAggro(npc, player, isPet);
		
		// guarda alguns alvos para mass teleport (não precisa lotar)
		if (_targets.size() < 10 && Rnd.get(3) == 0)
			_targets.add(player);
		
		return super.onAggro(npc, player, isPet);
	}
	
	@Override
	public String onAttack(L2Npc npc, Player attacker, int damage, boolean isPet, L2Skill skill)
	{
		if (npc == null || attacker == null)
			return super.onAttack(npc, attacker, damage, isPet, skill);
		
		// só controla skill do próprio Benom
		if (npc.getNpcId() != BENOM)
			return super.onAttack(npc, attacker, damage, isPet, skill);
		
		// throttle: decisão de AI por janela de tempo (evita spam por hit)
		final long now = System.currentTimeMillis();
		if (now < _nextAiCheckMs)
			return super.onAttack(npc, attacker, damage, isPet, skill);
		_nextAiCheckMs = now + CHECK_WINDOW_MS;
		
		// se já está castando, não tenta enfileirar
		if (npc.isCastingNow())
			return super.onAttack(npc, attacker, damage, isPet, skill);
		
		// global gcd de skills
		if (now < _nextGlobalCastMs)
			return super.onAttack(npc, attacker, damage, isPet, skill);
		
		// chance base por janela (não por hit)
		final int hpPct = (int) ((npc.getCurrentHp() * 100) / npc.getMaxHp());
		final int chance = (hpPct <= MASS_TELE_HP_PCT) ? EMERGENCY_CHANCE : BASE_CAST_CHANCE;
		
		if (Rnd.get(100) >= chance)
			return super.onAttack(npc, attacker, damage, isPet, skill);
		
		// escolhe skill com regras + cooldowns
		if (tryCastControlled(npc, attacker, now, hpPct))
			_nextGlobalCastMs = now + GLOBAL_CAST_GCD_MS;
		
		return super.onAttack(npc, attacker, damage, isPet, skill);
	}
	
	private boolean tryCastControlled(L2Npc npc, Player attacker, long now, int hpPct)
	{
		// 1) mass teleport (raríssimo, hp baixo)
		if (hpPct <= MASS_TELE_HP_PCT && now >= _nextMassTeleMs && !npc.isCastingNow())
		{
			final L2Skill s = SkillTable.getInstance().getInfo(SKILL_MASS_TELEPORT, 1);
			if (s != null)
			{
				npc.setTarget(attacker);
				npc.doCast(s);
				_nextMassTeleMs = now + CD_MASS_TELE_MS;
				return true;
			}
		}
		
		// 2) se alvo longe, tenta ranged skills (com cooldown)
		if (!npc.isInsideRadius(attacker, FAR_DISTANCE, true, false) && now >= _nextRangeMs && !npc.isCastingNow())
		{
			final int pick = Rnd.get(2);
			final int id = (pick == 0) ? SKILL_RANGE_PULL_1 : SKILL_RANGE_PULL_2;
			final L2Skill s = SkillTable.getInstance().getInfo(id, 1);
			if (s != null)
			{
				npc.setTarget(attacker);
				npc.doCast(s);
				_nextRangeMs = now + CD_RANGE_MS;
				return true;
			}
		}
		
		// 3) single teleport (controle forte)
		if (now >= _nextSingleTeleMs && !npc.isCastingNow())
		{
			final L2Skill s = SkillTable.getInstance().getInfo(SKILL_SINGLE_TELEPORT, 1);
			if (s != null)
			{
				npc.setTarget(attacker);
				npc.doCast(s);
				_nextSingleTeleMs = now + CD_SINGLE_TELE_MS;
				return true;
			}
		}
		
		return false;
	}
	
	@Override
	public String onSpellFinished(L2Npc npc, Player player, L2Skill skill)
	{
		if (npc == null || skill == null)
			return null;
		
		switch (skill.getId())
		{
			case SKILL_SINGLE_TELEPORT:
			{
				teleportTarget(player);
				((Attackable) npc).stopHating(player);
				break;
			}
			
			case SKILL_MASS_TELEPORT:
			{
				teleportTarget(player);
				((Attackable) npc).stopHating(player);
				
				if (!_targets.isEmpty())
				{
					for (Player target : _targets)
					{
						if (target == null)
							continue;
						
						final long x = player.getX() - target.getX();
						final long y = player.getY() - target.getY();
						final long z = player.getZ() - target.getZ();
						final long range = 250;
						if (((x * x) + (y * y) + (z * z)) <= (range * range))
						{
							teleportTarget(target);
							((Attackable) npc).stopHating(target);
						}
					}
					_targets.clear();
				}
				break;
			}
		}
		return null;
	}
	
	@Override
	public String onKill(L2Npc npc, Player killer, boolean isPet)
	{
		npc.broadcastNpcSay("It's not over yet... It won't be... over... like this... Never...");
		cancelQuestTimer("raid_check", npc, null);
		
		addSpawn(TELEPORT_CUBE, 12589, -49044, -3008, 0, false, 120000, false);
		return null;
	}
	
	private void resetCastControl()
	{
		final long now = System.currentTimeMillis();
		_nextAiCheckMs = now + 1500;
		_nextGlobalCastMs = now + 2500;
		
		_nextSingleTeleMs = now + 5000;
		_nextMassTeleMs = now + 15000;
		_nextRangeMs = now + 8000;
		
		_targets.clear();
	}
	
	private static void teleportTarget(Player player)
	{
		if (player != null)
		{
			final SpawnLocation loc = Rnd.get(TARGET_TELEPORTS);
			player.teleToLocation(loc, loc.getHeading());
		}
	}
}
