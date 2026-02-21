package net.sf.l2j.AutoFarm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.sf.l2j.Config;
import net.sf.l2j.event.bossevent.KTBEvent;
import net.sf.l2j.event.ctf.CTFEvent;
import net.sf.l2j.event.fortress.FOSEvent;
import net.sf.l2j.event.lastman.LMEvent;
import net.sf.l2j.event.tvt.TvTEvent;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.ai.CtrlEvent;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.ai.NextAction;
import net.sf.l2j.gameserver.datatables.GmListTable;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.handler.IItemHandler;
import net.sf.l2j.gameserver.handler.ItemHandler;
import net.sf.l2j.gameserver.handler.tutorialhandlers.Autofarm;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2ShortCut;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.L2WorldRegion;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.L2Summon;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.L2ChestInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2GrandBossInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2MonsterInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2PetInstance;
import net.sf.l2j.gameserver.model.actor.instance.L2RaidBossInstance;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.model.holder.IntIntHolder;
import net.sf.l2j.gameserver.model.item.instance.ItemInstance;
import net.sf.l2j.gameserver.model.item.kind.Item;
import net.sf.l2j.gameserver.model.item.type.ActionType;
import net.sf.l2j.gameserver.model.item.type.EtcItemType;
import net.sf.l2j.gameserver.model.item.type.WeaponType;
import net.sf.l2j.gameserver.model.itemcontainer.Inventory;
import net.sf.l2j.gameserver.model.zone.ZoneId;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.clientpackets.Say2;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.CreatureSay;
import net.sf.l2j.gameserver.network.serverpackets.ItemList;
import net.sf.l2j.gameserver.network.serverpackets.PetItemList;
import net.sf.l2j.gameserver.network.serverpackets.SystemMessage;
import net.sf.l2j.gameserver.scriptings.Quest;
import net.sf.l2j.gameserver.scriptings.QuestState;
import net.sf.l2j.gameserver.templates.skills.L2SkillType;
import net.sf.l2j.gameserver.util.Util;

public class AutofarmPlayerRoutine
{
	private final Player player;
	private Creature committedTarget = null;
	private ScheduledFuture<?> sweepTask = null; // Task para aguardar sweep
	private boolean waitingForSweep = false; // Flag indicando se está aguardando sweep
	
	// Constantes para Spoil e Sweep
	private static final int SPOIL_SKILL_ID = 254; // Skill de Spoiler (Scavenger)
	private static final int SWEEP_SKILL_ID = 42; // Skill de Sweeper
	private static final int SPOIL_PERCENTAGE_THRESHOLD = 100; // % de HP do mob para usar spoil
	
	public AutofarmPlayerRoutine(Player player)
	{
		this.player = player;
	}
	
	public void executeRoutine()
	{
		if (player.isNoBuffProtected() && player.getAllEffects().length <= 8)
		{
			player.sendMessage("You don't have buffs to use autofarm.");
			AutofarmManager.INSTANCE.stopFarm(player);
			player.setAutoFarm(false);
			Autofarm.showAutoFarm(player);
			player.broadcastUserInfo();
			return;
		}
		if (Config.ENABLE_DUALBOX_AUTOFARM)
		{
			AutoFarmIP(player, Config.NUMBER_BOX_IP_AUTOFARM, true);
		}
		if (player.getPvpFlag() > 0 && player.isInsideZone(ZoneId.BOSS) || player.getPvpFlag() > 0 && player.isInsideZone(ZoneId.RAID_ZONE) || player.getPvpFlag() > 0 && player.isInsideZone(ZoneId.FLAG_AREA_BOSS) || CTFEvent.isStarted() && CTFEvent.isPlayerParticipant(player.getObjectId()) || TvTEvent.isStarted() && TvTEvent.isPlayerParticipant(player.getObjectId()) || FOSEvent.isStarted() && FOSEvent.isPlayerParticipant(player.getObjectId()) || LMEvent.isStarted() && LMEvent.isPlayerParticipant(player.getObjectId()) || KTBEvent.isPlayerParticipant(player.getObjectId()) && KTBEvent.isStarted() || player.isInsideZone(ZoneId.SPOIL_AREA) && Config.BLOCK_AUTOFARM_SPOILZONE || player.isArenaAttack() || player.isInsideZone(ZoneId.PVP_CUSTOM))
		{
			player.sendMessage("You don't events to use autofarm or Flag.");
			AutofarmManager.INSTANCE.stopFarm(player);
			player.setAutoFarm(false);
			Autofarm.showAutoFarm(player);
			player.broadcastUserInfo();
			return;
		}
		if (Config.NO_USE_FARM_IN_PEACE_ZONE)
		{
			if (player.isInsideZone(ZoneId.PEACE))
			{
				player.sendMessage("No Use Auto farm in Peace Zone.");
				AutofarmManager.INSTANCE.stopFarm(player);
				player.setAutoFarm(false);
				Autofarm.showAutoFarm(player);
				player.broadcastUserInfo();
				return;
			}
		}
		
		if (player.isDead())
		{
			player.setAutoFarm(false);
			Autofarm.showAutoFarm(player);
			AutofarmManager.INSTANCE.stopFarm(player);
			player.broadcastUserInfo();
			return;
		}
		
		L2MonsterInstance monster = player.getTarget() instanceof L2MonsterInstance ? (L2MonsterInstance) player.getTarget() : null;
		if (monster != null && !GeoEngine.getInstance().canSeeTarget(player, monster))
		{
			monster = null;
			player.setTarget(monster);
		}
		
		if (!player.isMoving())
		{
			assistParty();
			
			if (checkAndProcessSweep())
			{
				return;
			}
			
			checkSpoil();
			targetEligibleCreature();
			attack();
			useAppropriateSpell();
			checkManaPots();
			checkHealthPots();
		}
		
	}
	
	private boolean checkAndProcessSweep()
	{
		
		if (waitingForSweep)
		{
			return true;
		}
		
		L2MonsterInstance deadMobWithSpoil = findDeadMobWithSpoil();
		
		if (deadMobWithSpoil != null)
		{
			
			cancelSweepTask();
			
			startSweepProcess(deadMobWithSpoil);
			return true;
		}
		
		return false;
	}
	
	private L2MonsterInstance findDeadMobWithSpoil()
	{
		List<L2MonsterInstance> deadMobs = getKnownMonstersInRadius(player, player.getRadius(), creature -> creature.isDead() && creature.getSpoilerId() == player.getObjectId() && creature.isSpoiled() && GeoEngine.getInstance().canSeeTarget(player, creature));
		
		if (deadMobs.isEmpty())
			return null;
		
		return deadMobs.stream().min(Comparator.comparingDouble(m -> player.getDistanceSq(m))).orElse(null);
	}
	
	private void startSweepProcess(L2MonsterInstance deadMob)
	{
		waitingForSweep = true;
		
		// Para qualquer movimento/ataque atual
		player.getAI().setIntention(CtrlIntention.IDLE);
		
		// Define como alvo
		committedTarget = deadMob;
		player.setTarget(deadMob);
		
		player.sendMessage("Aguardando para usar Sweep no monstro...");
		
		// Move para perto do mob se necessário
		if (player.getDistanceSq(deadMob) > 100 * 100)
		{
			player.moveToLocation(deadMob.getX(), deadMob.getY(), deadMob.getZ(), 0);
		}
		
		sweepTask = ThreadPool.scheduleAtFixedRate(new Runnable()
		{
			int attempts = 0;
			
			@Override
			public void run()
			{
				
				if (deadMob.isDead() && deadMob.getSpoilerId() == player.getObjectId() && deadMob.isSpoiled())
				{
					L2Skill sweeper = player.getSkill(SWEEP_SKILL_ID);
					
					if (sweeper != null && !player.isCastingNow() && !player.isCastingSimultaneouslyNow())
					{
						
						if (player.getDistanceSq(deadMob) <= 100 * 100)
						{
							
							player.sendMessage("Usando Sweep no monstro...");
							useMagicSkill(sweeper, false);
							
							ThreadPool.schedule(() -> {
								waitingForSweep = false;
								cancelSweepTask();
								player.sendMessage("Sweep concluido!");
							}, 500);
							
							return;
						}
						
						player.moveToLocation(deadMob.getX(), deadMob.getY(), deadMob.getZ(), 0);
					}
					
					attempts++;
					if (attempts > 10)
					{
						waitingForSweep = false;
						cancelSweepTask();
						player.sendMessage("Nao foi possivel usar Sweep. Continuando farming...");
					}
				}
				else
				{
					
					waitingForSweep = false;
					cancelSweepTask();
					player.sendMessage("Monstro nao está mais disponível para sweep.");
				}
			}
		}, 100, 500);
	}
	
	private void cancelSweepTask()
	{
		if (sweepTask != null && !sweepTask.isDone())
		{
			sweepTask.cancel(false);
			sweepTask = null;
		}
	}
	
	public boolean AutoFarmIP(Player activeChar, Integer numberBox, Boolean forcedTeleport)
	{
		if (CheckAutoFarmMetodo(activeChar, numberBox))
		{
			if (forcedTeleport)
			{
				activeChar.sendPacket(new CreatureSay(0, Say2.TELL, "SYS", "Allowed only " + numberBox + " Client in Auto Farm!"));
				for (Player allgms : GmListTable.getInstance().getAllGms(true))
				{
					if (!activeChar.isGM())
						allgms.sendPacket(new CreatureSay(0, Say2.TELL, "[Double HWID]", activeChar.getName() + " in Auto Farm!"));
				}
				player.setAutoFarm(false);
				Autofarm.showAutoFarm(player);
				AutofarmManager.INSTANCE.stopFarm(player);
				player.broadcastUserInfo();
				return false;
			}
			return true;
		}
		return false;
	}
	
	private static boolean CheckAutoFarmMetodo(Player activeChar, Integer numberBox)
	{
		Map<String, List<Player>> map = new HashMap<>();
		
		if (activeChar != null)
		{
			for (Player player : L2World.getInstance().getPlayers())
			{
				if (!player.isAutoFarm() || player.getHWID() == null)
					continue;
				
				String ip1 = activeChar.getHWID();
				String ip2 = player.getHWID();
				
				if (ip1.equals(ip2))
				{
					if (map.get(ip1) == null)
						map.put(ip1, new ArrayList<>());
					
					map.get(ip1).add(player);
					
					if (map.get(ip1).size() > numberBox)
						return true;
				}
			}
		}
		return false;
	}
	
	private void assistParty()
	{
		if (player.isInParty() && player.isAssistParty())
			if (!player.isPartyLeader())
				if (player.isInsideRadius(player.getParty().getLeader(), 1000, false, false))
				{
					if (player.getTarget() == null)
						player.moveToLocation(player.getParty().getLeader().getX(), player.getParty().getLeader().getY(), player.getParty().getLeader().getZ(), 0);
					
					player.setTarget(player.getParty().getLeader().getTarget());
				}
	}
	
	private void checkHealthPots()
	{
		if (getHpPercentage() <= AutofarmConstants.useHpPotsPercentageThreshold)
		{
			if (player.getFirstEffect(AutofarmConstants.hpPotSkillId) != null)
			{
				return;
			}
			
			ItemInstance hpPots = player.getInventory().getItemByItemId(AutofarmConstants.hpPotItemId);
			if (hpPots != null)
			{
				useItem(hpPots);
			}
		}
	}
	
	private void checkManaPots()
	{
		
		if (getMpPercentage() <= AutofarmConstants.useMpPotsPercentageThreshold)
		{
			ItemInstance mpPots = player.getInventory().getItemByItemId(AutofarmConstants.mpPotItemId);
			if (mpPots != null)
			{
				useItem(mpPots);
			}
		}
	}
	
	private void attack()
	{
		
		if (player.getTarget() instanceof L2MonsterInstance)
		{
			L2MonsterInstance target = (L2MonsterInstance) player.getTarget();
			
			if (target.isAutoAttackable(player))
			{
				physicalAttack();
			}
			
		}
	}
	
	private Double getMpPercentage()
	{
		return player.getCurrentMp() * 100.0f / player.getMaxMp();
	}
	
	private void useAppropriateSpell()
	{
		
		// Não usa skills se estiver aguardando sweep
		if (waitingForSweep)
			return;
		
		L2MonsterInstance target = getMonsterTarget();
		if (target == null)
			return;
		
		// PRIORIDADE 1: Se o monstro está morto e tem spoil, usa SWEEP (coletar)
		if (target.isDead() && target.getSpoilerId() == player.getObjectId() && target.isSpoiled())
		{
			L2Skill sweeper = player.getSkill(SWEEP_SKILL_ID);
			if (sweeper != null && !player.isCastingNow() && !player.isCastingSimultaneouslyNow())
			{
				useMagicSkill(sweeper, false);
				return;
			}
		}
		
		// PRIORIDADE 2: Se o monstro está vivo e sem spoil, usa SPOIL
		if (!target.isDead() && !monsterIsAlreadySpoiled() && canUseSpoil(target))
		{
			double targetHpPercent = target.getCurrentHp() * 100.0 / target.getMaxHp();
			if (targetHpPercent <= SPOIL_PERCENTAGE_THRESHOLD)
			{
				L2Skill spoilSkill = player.getSkill(SPOIL_SKILL_ID);
				if (spoilSkill != null && !player.isCastingNow() && !player.isCastingSimultaneouslyNow())
				{
					player.setTarget(target);
					useMagicSkill(spoilSkill, false);
					return;
				}
			}
		}
		
		L2Skill chanceSkill = nextAvailableSkill(getChanceSpells(), AutofarmSpellType.Chance);
		
		if (chanceSkill != null && player.getTarget() instanceof L2MonsterInstance && !(chanceSkill.getSkillType() == L2SkillType.SWEEP))
		{
			useMagicSkill(chanceSkill, false);
			return;
		}
		
		L2Skill lowLifeSkill = nextAvailableSkill(getLowLifeSpells(), AutofarmSpellType.LowLife);
		
		if (lowLifeSkill != null && !(lowLifeSkill.getSkillType() == L2SkillType.SWEEP))
		{
			useMagicSkill(lowLifeSkill, true);
			return;
		}
		
		L2Skill attackSkill = nextAvailableSkill(getAttackSpells(), AutofarmSpellType.Attack);
		
		if (attackSkill != null && player.getTarget() instanceof L2MonsterInstance && !(attackSkill.getSkillType() == L2SkillType.SWEEP))
		{
			useMagicSkill(attackSkill, false);
			return;
		}
	}
	
	public L2Skill nextAvailableSkill(List<Integer> skillIds, AutofarmSpellType spellType)
	{
		
		// Verificar se o alvo está morto
		if (getMonsterTarget() == null || getMonsterTarget().isDead())
		{
			
			return null;
		}
		
		List<Integer> availableSkills = new ArrayList<>(skillIds);
		
		while (!availableSkills.isEmpty())
		{
			// Seleciona uma habilidade aleatória da lista disponível
			int randomIndex = (int) (Math.random() * availableSkills.size());
			Integer selectedSkillId = availableSkills.get(randomIndex);
			
			L2Skill skill = player.getSkill(selectedSkillId);
			
			if (skill == null)
			{
				availableSkills.remove(randomIndex); // Remove se a habilidade não for encontrada
				continue;
			}
			
			// Ignorar habilidades do tipo SIGNET ou SIGNET_CASTTIME
			if (skill.getSkillType() == L2SkillType.SIGNET || skill.getSkillType() == L2SkillType.SIGNET_CASTTIME)
			{
				availableSkills.remove(randomIndex); // Remove essas habilidades
				continue;
			}
			
			// Verificar se as condições de cast estão ok
			if (!player.checkDoCastConditions(skill))
			{
				availableSkills.remove(randomIndex); // Remove habilidades que não podem ser lançadas
				continue;
			}
			
			if (isSpoil(selectedSkillId))
			{
				if (monsterIsAlreadySpoiled())
				{
					availableSkills.remove(randomIndex);
					continue;
				}
				return skill;
			}
			
			if (spellType == AutofarmSpellType.Chance && getMonsterTarget() != null)
			{
				if (getMonsterTarget().getFirstEffect(selectedSkillId) == null)
				{
					return skill;
				}
				availableSkills.remove(randomIndex);
				continue;
			}
			
			if (spellType == AutofarmSpellType.LowLife && getHpPercentage() > player.getHealPercent())
				break;
			
			availableSkills.remove(randomIndex);
			return skill;
		}
		
		return null;
	}
	
	private void checkSpoil()
	{
		L2MonsterInstance target = getMonsterTarget();
		
		// Se o alvo é nulo ou não é um monstro, retorna
		if (target == null)
			return;
		
		// PRIORIDADE 1: Se o monstro está morto, tenta usar SWEEP (coletar)
		if (target.isDead())
		{
			// Verifica se o monstro tem spoil disponível e se fui eu que spoilei
			if (target.getSpoilerId() == player.getObjectId() && target.isSpoiled())
			{
				L2Skill sweeper = player.getSkill(SWEEP_SKILL_ID);
				if (sweeper != null && !player.isCastingNow() && !player.isCastingSimultaneouslyNow())
				{
					// Usa sweep no monstro morto
					useMagicSkill(sweeper, false);
					player.sendMessage("Coletando spoil do monstro morto...");
				}
			}
			return;
		}
		
		// PRIORIDADE 2: Se o monstro está vivo, verifica se pode usar SPOIL
		if (!target.isDead() && !monsterIsAlreadySpoiled() && canUseSpoil(target))
		{
			L2Skill spoilSkill = player.getSkill(SPOIL_SKILL_ID);
			
			if (spoilSkill != null && !player.isCastingNow() && !player.isCastingSimultaneouslyNow())
			{
				// Verifica se o monstro está com % de HP ideal para spoil
				double targetHpPercent = target.getCurrentHp() * 100.0 / target.getMaxHp();
				
				if (targetHpPercent <= SPOIL_PERCENTAGE_THRESHOLD)
				{
					// Usa spoil no monstro
					player.setTarget(target);
					useMagicSkill(spoilSkill, false);
					player.sendMessage("Usando spoil no monstro...");
				}
			}
		}
	}
	
	private boolean monsterIsAlreadySpoiled()
	{
		return getMonsterTarget() != null && getMonsterTarget().getSpoilerId() != 0;
	}
	
	private static boolean isSpoil(Integer skillId)
	{
		return skillId == 254 || skillId == 302;
	}
	
	private Double getHpPercentage()
	{
		return player.getCurrentHp() * 100.0f / player.getMaxHp();
	}
	
	private List<Integer> getAttackSpells()
	{
		return getSpellsInSlots(AutofarmConstants.attackSlots);
	}
	
	private List<Integer> getSpellsInSlots(List<Integer> attackSlots)
	{
		return Arrays.stream(player.getAllShortCuts()).filter(shortcut -> shortcut.getPage() == player.getPage() && shortcut.getType() == L2ShortCut.TYPE_SKILL && attackSlots.contains(shortcut.getSlot())).map(L2ShortCut::getId).collect(Collectors.toList());
	}
	
	private List<Integer> getChanceSpells()
	{
		return getSpellsInSlots(AutofarmConstants.chanceSlots);
	}
	
	private List<Integer> getLowLifeSpells()
	{
		return getSpellsInSlots(AutofarmConstants.lowLifeSlots);
	}
	
	private void castSpellWithAppropriateTarget(L2Skill skill, Boolean forceOnSelf)
	{
		// Verifica se o alvo está morto antes de lançar a habilidade
		if (player.getTarget() != null)
		{
			// Adiciona a verificação se committedTarget não é null
			if (committedTarget != null && committedTarget.isDead())
			{
				// Se o alvo está morto, não faz nada e retorna
				return;
			}
		}
		
		// Se a habilidade deve ser forçada no próprio jogador
		if (forceOnSelf)
		{
			L2Object oldTarget = player.getTarget();
			player.setTarget(player); // Define o próprio jogador como alvo
			player.useMagic(skill, false, false); // Usa a magia
			player.setTarget(oldTarget); // Restaura o alvo anterior
			return;
		}
		
		// Caso contrário, usa a magia com o alvo atual
		player.useMagic(skill, false, false);
	}
	
	private boolean canUseSpoil(L2MonsterInstance target)
	{
		// Verifica se o jogador tem a skill de spoil
		if (player.getSkill(SPOIL_SKILL_ID) == null)
		{
			return false;
		}
		
		// Verifica se é uma classe que pode usar spoil
		if (!isSpoilerClass(player))
		{
			return false;
		}
		
		// Verifica se o monstro pode ser spoileado (não é raid boss, etc)
		if (target instanceof L2RaidBossInstance || target instanceof L2GrandBossInstance || target instanceof L2ChestInstance)
		{
			return false;
		}
		
		return true;
	}
	
	private static boolean isSpoilerClass(Player player)
	{
		ClassId classId = player.getClassId();
		return classId == ClassId.SCAVENGER || classId == ClassId.BOUNTY_HUNTER || classId == ClassId.FORTUNE_SEEKER;
	}
	
	private void physicalAttack()
	{
		if (!(player.getTarget() instanceof L2MonsterInstance))
			return;
		
		L2MonsterInstance target = (L2MonsterInstance) player.getTarget();
		
		if (!player.isMageClass())
		{
			if (target.isAutoAttackable(player) && GeoEngine.getInstance().canSeeTarget(player, target))
			{
				double distanceSq = player.getDistanceSq(target);
				double attackRange = player.getPhysicalAttackRange();
				
				if (distanceSq <= attackRange * attackRange)
				{
					if (!player.isCastingNow())
						player.doAttack(target);
				}
				else
				{
					// AI cuida do movimento e ataque no alcance correto
					if (player.getAI().getIntention() != CtrlIntention.ATTACK || player.getAI().getTarget() != target)
						player.getAI().setIntention(CtrlIntention.ATTACK, target);
				}
			}
		}
	}
	
	public void targetEligibleCreature()
	{
		// Verifica se o committedTarget não é nulo
		if (committedTarget != null && GeoEngine.getInstance().canSeeTarget(player, committedTarget) && !(committedTarget != null))
		{
			// Se o alvo está morto e pode ser visto, trata esse caso
			if (committedTarget.isDead() && GeoEngine.getInstance().canSeeTarget(player, committedTarget))
			{
				if (isPlayerClassSpecial(player))
				{
					// Se for uma das classes especiais, limpa o alvo após 1 segundo
					clearTargetAfterDelay();
					return;
				}
				
				// Caso contrário, apenas resetamos o alvo
				resetTarget();
				attack();
				return;
			}
			
			// Se o alvo não estiver morto e pode ser visto, continua atacando
			if (!committedTarget.isDead() && GeoEngine.getInstance().canSeeTarget(player, committedTarget))
			{
				attack();
				return;
			}
			
			// Se o alvo não é válido, tenta seguir o alvo ou reseta
			// player.getAI().setIntention(CtrlIntention.FOLLOW, committedTarget);
			resetTarget();
			return;
		}
		
		// Verifica se o committedTarget é uma invocação, nesse caso não faz nada
		if (committedTarget instanceof L2Summon)
			return;
		
		// Busca novos alvos válidos
		List<L2MonsterInstance> targets = getValidTargets();
		
		// Se não houver alvos válidos, não faz nada
		if (targets.isEmpty())
			return;
		
		L2MonsterInstance closestTarget = targets.stream().min(Comparator.comparingDouble(target -> Math.sqrt(player.getDistanceSq(target)))).orElse(null);
		
		if (closestTarget != null)
		{
			// Se já tem committedTarget válido e diferente do closestTarget, NÃO troca
			if (committedTarget != null && !committedTarget.isDead() && GeoEngine.getInstance().canSeeTarget(player, committedTarget))
			{
				// mantém o alvo atual, não troca
				return;
			}
			
			// Caso contrário, troca o alvo para o mais próximo
			committedTarget = closestTarget;
			player.setTarget(closestTarget);
		}
		
	}
	
	private static boolean isPlayerClassSpecial(Player player)
	{
		return player.getClassId() == ClassId.SCAVENGER || player.getClassId() == ClassId.BOUNTY_HUNTER || player.getClassId() == ClassId.FORTUNE_SEEKER;
	}
	
	private void clearTargetAfterDelay()
	{
		// Agenda a limpeza do alvo e do target do jogador
		ThreadPool.schedule(() -> {
			committedTarget = null;
			player.setTarget(null);
			attack(); // Inicia ataque
		}, 1000);
	}
	
	private void resetTarget()
	{
		// Reseta o alvo do jogador
		committedTarget = null;
		player.setTarget(null);
	}
	
	private List<L2MonsterInstance> getValidTargets()
	{
		return getKnownMonstersInRadius(player, player.getRadius(), creature -> GeoEngine.getInstance().canMoveToTarget(player.getX(), player.getY(), player.getZ(), creature.getX(), creature.getY(), creature.getZ()) && GeoEngine.getInstance().canSeeTarget(player, creature) && // opcional extra
			!creature.isDead() && !(creature instanceof L2ChestInstance) && !(creature instanceof L2RaidBossInstance) && !(creature instanceof L2GrandBossInstance) && !creature.isAgathion() && !creature.isRaidMinion() && !(player.isAntiKsProtected() && creature.getTarget() != null && creature.getTarget() != player && creature.getTarget() != player.getPet()));
	}
	
	public final static List<L2MonsterInstance> getKnownMonstersInRadius(Player player, int radius, Function<L2MonsterInstance, Boolean> condition)
	{
		// Garantir que a condição nunca seja nula
		if (condition == null)
		{
			condition = monster -> true; // Condição que sempre retorna true
		}
		
		final L2WorldRegion region = player.getRegion();
		if (region == null)
			return Collections.emptyList();
		
		final List<L2MonsterInstance> result = new ArrayList<>();
		
		for (L2WorldRegion reg : region.getSurroundingRegions())
		{
			for (L2Object obj : reg.getVisibleObjects().values())
			{
				if (obj instanceof L2MonsterInstance)
				{
					L2MonsterInstance monster = (L2MonsterInstance) obj;
					
					if (Util.checkIfInRange(radius, player, obj, true) && condition.apply(monster))
					{
						result.add(monster);
					}
				}
			}
		}
		
		return result;
	}
	
	public L2MonsterInstance getMonsterTarget()
	{
		
		if (player.getTarget() instanceof L2MonsterInstance)
		{
			return (L2MonsterInstance) player.getTarget();
		}
		
		return null;
	}
	
	public void useItem(ItemInstance item)
	{
		if (player.isInStoreMode())
		{
			player.sendPacket(SystemMessageId.ITEMS_UNAVAILABLE_FOR_STORE_MANUFACTURE);
			return;
		}
		if (player.isInOlympiadMode())
		{
			return;
		}
		
		if (player.getActiveTradeList() != null)
		{
			player.sendPacket(SystemMessageId.CANNOT_PICKUP_OR_USE_ITEM_WHILE_TRADING);
			return;
		}
		
		if (item == null)
			return;
		
		if (item.getItem().getType2() == Item.TYPE2_QUEST)
		{
			player.sendPacket(SystemMessageId.CANNOT_USE_QUEST_ITEMS);
			return;
		}
		
		if (player.isAlikeDead() || player.isStunned() || player.isSleeping() || player.isParalyzed() || player.isAfraid())
			return;
		
		if (!Config.KARMA_PLAYER_CAN_TELEPORT && player.getKarma() > 0)
		{
			final IntIntHolder[] sHolders = item.getItem().getSkills();
			if (sHolders != null)
			{
				for (IntIntHolder sHolder : sHolders)
				{
					final L2Skill skill = sHolder.getSkill();
					if (skill != null && (skill.getSkillType() == L2SkillType.TELEPORT || skill.getSkillType() == L2SkillType.RECALL))
						return;
				}
			}
		}
		
		if (player.isFishing() && item.getItem().getDefaultAction() != ActionType.fishingshot)
		{
			player.sendPacket(SystemMessageId.CANNOT_DO_WHILE_FISHING_3);
			return;
		}
		
		if (item.isPetItem())
		{
			if (!player.hasPet())
			{
				player.sendPacket(SystemMessageId.CANNOT_EQUIP_PET_ITEM);
				return;
			}
			
			final L2PetInstance pet = ((L2PetInstance) player.getPet());
			
			if (!pet.canWear(item.getItem()))
			{
				player.sendPacket(SystemMessageId.PET_CANNOT_USE_ITEM);
				return;
			}
			
			if (pet.isDead())
			{
				player.sendPacket(SystemMessageId.CANNOT_GIVE_ITEMS_TO_DEAD_PET);
				return;
			}
			
			if (!pet.getInventory().validateCapacity(item))
			{
				player.sendPacket(SystemMessageId.YOUR_PET_CANNOT_CARRY_ANY_MORE_ITEMS);
				return;
			}
			
			if (!pet.getInventory().validateWeight(item, 1))
			{
				player.sendPacket(SystemMessageId.UNABLE_TO_PLACE_ITEM_YOUR_PET_IS_TOO_ENCUMBERED);
				return;
			}
			
			player.transferItem("Transfer", item.getObjectId(), 1, pet.getInventory(), pet);
			
			if (item.isEquipped())
			{
				pet.getInventory().unEquipItemInSlot(item.getLocationSlot());
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PET_TOOK_OFF_S1).addItemName(item));
			}
			else
			{
				pet.getInventory().equipPetItem(item);
				player.sendPacket(SystemMessage.getSystemMessage(SystemMessageId.PET_PUT_ON_S1).addItemName(item));
			}
			
			player.sendPacket(new PetItemList(pet));
			pet.updateAndBroadcastStatus(1);
			return;
		}
		
		if (!item.isEquipped())
		{
			if (!item.getItem().checkCondition(player, player, true))
				return;
		}
		
		if (item.isEquipable())
		{
			if (player.isCastingNow() || player.isCastingSimultaneouslyNow())
			{
				player.sendPacket(SystemMessageId.CANNOT_USE_ITEM_WHILE_USING_MAGIC);
				return;
			}
			
			switch (item.getItem().getBodyPart())
			{
				case Item.SLOT_LR_HAND:
				case Item.SLOT_L_HAND:
				case Item.SLOT_R_HAND:
				{
					if (player.isMounted())
					{
						player.sendPacket(SystemMessageId.CANNOT_EQUIP_ITEM_DUE_TO_BAD_CONDITION);
						return;
					}
					
					if (player.isCursedWeaponEquipped())
						return;
					
					break;
				}
			}
			
			if (player.isCursedWeaponEquipped() && item.getItemId() == 6408)
				return;
			
			if (player.isAttackingNow())
				ThreadPool.schedule(() -> {
					final ItemInstance itemToTest = player.getInventory().getItemByObjectId(item.getObjectId());
					if (itemToTest == null)
						return;
					
					player.useEquippableItem(itemToTest, false);
				}, player.getAttackEndTime() - System.currentTimeMillis());
			else
				player.useEquippableItem(item, true);
		}
		else
		{
			if (player.isCastingNow() && !(item.isPotion() || item.isElixir()))
				return;
			
			if (player.getAttackType() == WeaponType.FISHINGROD && item.getItem().getItemType() == EtcItemType.LURE)
			{
				player.getInventory().setPaperdollItem(Inventory.PAPERDOLL_LHAND, item);
				player.broadcastUserInfo();
				
				player.sendPacket(new ItemList(player, false));
				return;
			}
			
			final IItemHandler handler = ItemHandler.getInstance().getItemHandler(item.getEtcItem());
			if (handler != null)
				handler.useItem(player, item, false);
			
			for (Quest quest : item.getQuestEvents())
			{
				QuestState state = player.getQuestState(quest.getName());
				if (state == null || !state.isStarted())
					continue;
				
				quest.notifyItemUse(item, player, player.getTarget());
			}
		}
	}
	
	private void useMagicSkill(L2Skill skill, Boolean forceOnSelf)
	{
		
		if (skill.getSkillType() == L2SkillType.RECALL && !Config.KARMA_PLAYER_CAN_TELEPORT && player.getKarma() > 0)
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (skill.isToggle() && player.isMounted())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isOutOfControl())
		{
			player.sendPacket(ActionFailed.STATIC_PACKET);
			return;
		}
		
		if (player.isAttackingNow())
		{
			player.getAI().setNextAction(new NextAction(CtrlEvent.EVT_READY_TO_ACT, CtrlIntention.CAST, () -> {
				
				if (player.getTarget() != null && committedTarget != null && committedTarget.isDead())
				{
					player.sendPacket(ActionFailed.STATIC_PACKET);
					
					return;
				}
				castSpellWithAppropriateTarget(skill, forceOnSelf);
			}));
		}
		else
		{
			
			if (player.getTarget() != null && committedTarget != null && committedTarget.isDead())
			{
				player.sendPacket(ActionFailed.STATIC_PACKET);
				
				return;
			}
			castSpellWithAppropriateTarget(skill, forceOnSelf);
		}
	}
	
}