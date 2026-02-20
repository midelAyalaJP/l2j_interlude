package net.sf.l2j.gameserver.ai.model;

import java.util.concurrent.Future;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.ai.Ctrl;
import net.sf.l2j.gameserver.ai.CtrlEvent;
import net.sf.l2j.gameserver.ai.CtrlIntention;
import net.sf.l2j.gameserver.ai.NextAction;
import net.sf.l2j.gameserver.geoengine.GeoEngine;
import net.sf.l2j.gameserver.model.L2Object;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.Location;
import net.sf.l2j.gameserver.model.SpawnLocation;
import net.sf.l2j.gameserver.model.actor.Creature;
import net.sf.l2j.gameserver.model.actor.L2Npc;
import net.sf.l2j.gameserver.model.actor.L2Summon;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.actor.instance.L2AgathionInstance;
import net.sf.l2j.gameserver.network.serverpackets.ActionFailed;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStart;
import net.sf.l2j.gameserver.network.serverpackets.AutoAttackStop;
import net.sf.l2j.gameserver.network.serverpackets.Die;
import net.sf.l2j.gameserver.network.serverpackets.MoveToLocation;
import net.sf.l2j.gameserver.network.serverpackets.MoveToPawn;
import net.sf.l2j.gameserver.network.serverpackets.StopMove;
import net.sf.l2j.gameserver.network.serverpackets.StopRotation;
import net.sf.l2j.gameserver.taskmanager.AttackStanceTaskManager;
import net.sf.l2j.gameserver.taskmanager.GameTimeController;

abstract class AbstractAI implements Ctrl
{
	protected static final Logger _log = Logger.getLogger(AbstractAI.class.getName());
	
	/** The character that this AI manages */
	protected final Creature _actor;
	
	/** Current long-term intention */
	protected CtrlIntention _intention = CtrlIntention.IDLE;
	protected Object _intentionArg0 = null;
	protected Object _intentionArg1 = null;
	
	private NextAction _nextAction;
	
	/** Flags about client's state, in order to know which messages to send */
	protected volatile boolean _clientMoving;
	protected volatile boolean _clientAutoAttacking;
	
	/** Different targets this AI maintains */
	private L2Object _target;
	protected Creature _followTarget;
	
	/** The skill we are currently casting by INTENTION_CAST */
	protected L2Skill _skill;
	
	/** Different internal state flags */
	private long _moveToPawnTimeout;
	protected int _clientMovingToPawnOffset;
	
	protected Future<?> _followTask = null;
	private static final int FOLLOW_INTERVAL = 1000;
	private static final int ATTACK_FOLLOW_INTERVAL = 500;
	
	protected AbstractAI(Creature character)
	{
		_actor = character;
	}
	
	/**
	 * Return the L2Character managed by this Accessor AI.
	 */
	@Override
	public Creature getActor()
	{
		return _actor;
	}
	
	/**
	 * Return the current Intention.
	 */
	@Override
	public CtrlIntention getIntention()
	{
		return _intention;
	}
	
	/**
	 * Set the Intention of this AbstractAI.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : This method is USED by AI classes</B></FONT><BR>
	 * <BR>
	 * <B><U> Overridden in </U> : </B><BR>
	 * <B>L2AttackableAI</B> : Create an AI Task executed every 1s (if necessary)<BR>
	 * <B>L2PlayerAI</B> : Stores the current AI intention parameters to later restore it if necessary<BR>
	 * <BR>
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention
	 * @param arg1 The second parameter of the Intention
	 */
	synchronized void changeIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		_intention = intention;
		_intentionArg0 = arg0;
		_intentionArg1 = arg1;
	}
	
	/**
	 * Launch the L2CharacterAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * @param intention The new Intention to set to the AI
	 */
	@Override
	public final void setIntention(CtrlIntention intention)
	{
		setIntention(intention, null, null);
	}
	
	/**
	 * Launch the L2CharacterAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention (optional target)
	 */
	@Override
	public final void setIntention(CtrlIntention intention, Object arg0)
	{
		setIntention(intention, arg0, null);
	}
	
	/**
	 * Launch the L2CharacterAI onIntention method corresponding to the new Intention.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Stop the FOLLOW mode if necessary</B></FONT><BR>
	 * <BR>
	 * @param intention The new Intention to set to the AI
	 * @param arg0 The first parameter of the Intention (optional target)
	 * @param arg1 The second parameter of the Intention (optional target)
	 */
	@Override
	public final void setIntention(CtrlIntention intention, Object arg0, Object arg1)
	{
		// Stop the follow mode if necessary
		if (intention != CtrlIntention.FOLLOW && intention != CtrlIntention.ATTACK)
			stopFollow();
		
		// Launch the onIntention method of the L2CharacterAI corresponding to the new Intention
		switch (intention)
		{
			case IDLE:
				onIntentionIdle();
				break;
			case ACTIVE:
				onIntentionActive();
				break;
			case REST:
				onIntentionRest();
				break;
			case ATTACK:
				onIntentionAttack((Creature) arg0);
				break;
			case CAST:
				onIntentionCast((L2Skill) arg0, (L2Object) arg1);
				break;
			case MOVE_TO:
				onIntentionMoveTo((Location) arg0);
				break;
			case FOLLOW:
				onIntentionFollow((Creature) arg0);
				break;
			case PICK_UP:
				onIntentionPickUp((L2Object) arg0);
				break;
			case INTERACT:
				onIntentionInteract((L2Object) arg0);
				break;
		}
		
		// If do move or follow intention drop next action.
		if (_nextAction != null && _nextAction.getIntention() == intention)
			_nextAction = null;
	}
	
	/**
	 * Launch the L2CharacterAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character attack and is stunned, he will attack again after the stunned periode)</B></FONT><BR>
	 * <BR>
	 * @param evt The event whose the AI must be notified
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt)
	{
		notifyEvent(evt, null, null);
	}
	
	/**
	 * Launch the L2CharacterAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character attack and is stunned, he will attack again after the stunned periode)</B></FONT><BR>
	 * <BR>
	 * @param evt The event whose the AI must be notified
	 * @param arg0 The first parameter of the Event (optional target)
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt, Object arg0)
	{
		notifyEvent(evt, arg0, null);
	}
	
	/**
	 * Launch the L2CharacterAI onEvt method corresponding to the Event.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : The current general intention won't be change (ex : If the character attack and is stunned, he will attack again after the stunned periode)</B></FONT><BR>
	 * <BR>
	 * @param evt The event whose the AI must be notified
	 * @param arg0 The first parameter of the Event (optional target)
	 * @param arg1 The second parameter of the Event (optional target)
	 */
	@Override
	public final void notifyEvent(CtrlEvent evt, Object arg0, Object arg1)
	{
		if ((!_actor.isVisible() && !_actor.isTeleporting()) || !_actor.hasAI())
			return;
		
		switch (evt)
		{
			case EVT_THINK:
				onEvtThink();
				break;
			case EVT_ATTACKED:
				onEvtAttacked((Creature) arg0);
				break;
			case EVT_AGGRESSION:
				onEvtAggression((Creature) arg0, ((Number) arg1).intValue());
				break;
			case EVT_STUNNED:
				onEvtStunned((Creature) arg0);
				break;
			case EVT_PARALYZED:
				onEvtParalyzed((Creature) arg0);
				break;
			case EVT_SLEEPING:
				onEvtSleeping((Creature) arg0);
				break;
			case EVT_ROOTED:
				onEvtRooted((Creature) arg0);
				break;
			case EVT_CONFUSED:
				onEvtConfused((Creature) arg0);
				break;
			case EVT_MUTED:
				onEvtMuted((Creature) arg0);
				break;
			case EVT_EVADED:
				onEvtEvaded((Creature) arg0);
				break;
			case EVT_READY_TO_ACT:
				if (!_actor.isCastingNow() && !_actor.isCastingSimultaneouslyNow())
					onEvtReadyToAct();
				break;
			case EVT_USER_CMD:
				onEvtUserCmd(arg0, arg1);
				break;
			case EVT_ARRIVED:
				if (!_actor.isCastingNow() && !_actor.isCastingSimultaneouslyNow())
					onEvtArrived();
				break;
			case EVT_ARRIVED_BLOCKED:
				onEvtArrivedBlocked((SpawnLocation) arg0);
				break;
			case EVT_FORGET_OBJECT:
				onEvtForgetObject((L2Object) arg0);
				break;
			case EVT_CANCEL:
				onEvtCancel();
				break;
			case EVT_DEAD:
				onEvtDead();
				break;
			case EVT_FAKE_DEATH:
				onEvtFakeDeath();
				break;
			case EVT_FINISH_CASTING:
				onEvtFinishCasting();
				break;
		}
		
		// Do next action.
		if (_nextAction != null && _nextAction.getEvent() == evt)
		{
			_nextAction.run();
			_nextAction = null;
		}
	}
	
	protected abstract void onIntentionIdle();
	
	protected abstract void onIntentionActive();
	
	protected abstract void onIntentionRest();
	
	protected abstract void onIntentionAttack(Creature target);
	
	protected abstract void onIntentionCast(L2Skill skill, L2Object target);
	
	protected abstract void onIntentionMoveTo(Location loc);
	
	protected abstract void onIntentionFollow(Creature target);
	
	protected abstract void onIntentionPickUp(L2Object item);
	
	protected abstract void onIntentionInteract(L2Object object);
	
	protected abstract void onEvtThink();
	
	protected abstract void onEvtAttacked(Creature attacker);
	
	protected abstract void onEvtAggression(Creature target, int aggro);
	
	protected abstract void onEvtStunned(Creature attacker);
	
	protected abstract void onEvtParalyzed(Creature attacker);
	
	protected abstract void onEvtSleeping(Creature attacker);
	
	protected abstract void onEvtRooted(Creature attacker);
	
	protected abstract void onEvtConfused(Creature attacker);
	
	protected abstract void onEvtMuted(Creature attacker);
	
	protected abstract void onEvtEvaded(Creature attacker);
	
	protected abstract void onEvtReadyToAct();
	
	protected abstract void onEvtUserCmd(Object arg0, Object arg1);
	
	protected abstract void onEvtArrived();
	
	protected abstract void onEvtArrivedBlocked(SpawnLocation loc);
	
	protected abstract void onEvtForgetObject(L2Object object);
	
	protected abstract void onEvtCancel();
	
	protected abstract void onEvtDead();
	
	protected abstract void onEvtFakeDeath();
	
	protected abstract void onEvtFinishCasting();
	
	/**
	 * Cancel action client side by sending Server->Client packet ActionFailed to the L2PcInstance actor.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void clientActionFailed()
	{
	}
	
	
	protected void moveToPawn(L2Object pawn, int offset)
	{
		if (_actor.isMovementDisabled())
		{
			clientActionFailed();
			return;
		}
		
		if (offset < 10)
			offset = 10;
		
		if (pawn instanceof L2Npc && ((L2Npc) pawn).getNpcId() == 29025)
			offset = 150; // Ajuste para Baium
			
		boolean sendPacket = true;
		
		final int currentTick = GameTimeController.getInstance().getGameTicks();
		final int timeoutTicks = 250 / GameTimeController.MILLIS_IN_TICK; // 250ms (mais responsivo)
		final int geoTicks = 500 / GameTimeController.MILLIS_IN_TICK;     // 500ms (era 2s)
		boolean onGeo = _actor.isOnGeodataPath();

		if (_clientMoving && _target == pawn)
		{
		    if (_clientMovingToPawnOffset == offset)
		    {
		        // Em geodata path: não segure demais, o cliente precisa de correções frequentes
		        final int limit = onGeo ? geoTicks : timeoutTicks;

		        if (currentTick < _moveToPawnTimeout + limit)
		            return;

		        // Se não for geodata, podemos economizar o pacote às vezes
		        sendPacket = onGeo; // em geo manda sempre
		    }
		}

		_clientMoving = true;
		_clientMovingToPawnOffset = offset;
		_target = pawn;
		_moveToPawnTimeout = currentTick + timeoutTicks;

		_actor.moveToLocation(pawn.getX(), pawn.getY(), pawn.getZ(), offset);

		if (!_actor.isMoving())
		{
		    clientActionFailed();
		    return;
		}

		if (pawn instanceof Creature)
		{
		    if (onGeo)
		    {
		        // Em geodata: sempre MoveToLocation (mais “natural” e estável)
		        _actor.broadcastPacket(new MoveToLocation(_actor));
		        _clientMovingToPawnOffset = 0;
		    }
		    else if (sendPacket)
		    {
		        _actor.broadcastPacket(new MoveToPawn(_actor, pawn, offset));
		    }
		}
		else
		{
		    _actor.broadcastPacket(new MoveToLocation(_actor));
		}
	}
	private int _moveToTimeout;
	private int _moveToX;
	private int _moveToY;
	private int _moveToZ;
	protected void moveTo(int x, int y, int z)
	{
	    if (_actor == null)
	        return;

	    if (_actor.isMovementDisabled())
	    {
	        clientActionFailed();
	        return;
	    }

	    final int currentTick = GameTimeController.getInstance().getGameTicks();

	    // Ajustes parecidos com moveToPawn()
	    final int timeoutTicks = 250 / GameTimeController.MILLIS_IN_TICK; // 250ms
	    final int geoTicks     = 500 / GameTimeController.MILLIS_IN_TICK; // 500ms
	    final boolean onGeo    = _actor.isOnGeodataPath();

	    // Evita spam: se já está movendo e o destino é "quase igual", ignore
	    // (reduz slide por micro-cliques)
	    if (_clientMoving)
	    {
	        final int dx = x - _moveToX;
	        final int dy = y - _moveToY;
	        final int dz = z - _moveToZ;

	        // tolerância em unidades do jogo
	        final int samePos2D = 30;  // 30~60 é um bom range
	        final int samePosZ  = 80;

	        if ((Math.abs(dx) <= samePos2D) && (Math.abs(dy) <= samePos2D) && (Math.abs(dz) <= samePosZ))
	        {
	            // destino quase igual: respeita timeout
	            final int limit = onGeo ? geoTicks : timeoutTicks;
	            if (currentTick < _moveToTimeout + limit)
	                return;
	        }
	        else
	        {
	            // destino mudou bastante: ainda assim evita flood extremo
	            if (currentTick < _moveToTimeout + timeoutTicks)
	                return;
	        }
	    }

	    _clientMoving = true;
	    _clientMovingToPawnOffset = 0;

	    _moveToX = x;
	    _moveToY = y;
	    _moveToZ = z;
	    _moveToTimeout = currentTick;

	    _actor.moveToLocation(x, y, z, 0);

	    if (!_actor.isMoving())
	    {
	        _actor.sendPacket(ActionFailed.STATIC_PACKET);
	        return;
	    }

	    // Em geo, MoveToLocation é o mais consistente
	    _actor.broadcastPacket(new MoveToLocation(_actor));
	}
	
	/**
	 * Stop the actor movement server side AND client side by sending Server->Client packet StopMove/StopRotation <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 * @param loc
	 */
	protected void clientStopMoving(SpawnLocation loc)
	{
		// Stop movement of the L2Character
		// if (_actor.isMoving() || _actor.isAttackingNow())
		if (_actor.isMoving() || _actor.isAttackingNow() || _actor.isCastingNow())
			_actor.stopMove(loc);
		
		_clientMovingToPawnOffset = 0;
		
		if (_clientMoving || loc != null)
		{
			_clientMoving = false;
			
			_actor.broadcastPacket(new StopMove(_actor));
			
			if (loc != null)
				_actor.broadcastPacket(new StopRotation(_actor.getObjectId(), loc.getHeading(), 0));
		}
	}
	
	// Client has already arrived to target, no need to force StopMove packet
	protected void clientStoppedMoving()
	{
		if (_clientMovingToPawnOffset > 0) // movetoPawn needs to be stopped
		{
			_clientMovingToPawnOffset = 0;
			_actor.broadcastPacket(new StopMove(_actor));
		}
		_clientMoving = false;
	}
	
	public boolean isAutoAttacking()
	{
		return _clientAutoAttacking;
	}
	
	public void setAutoAttacking(boolean isAutoAttacking)
	{
		if (_actor instanceof L2Summon)
		{
			L2Summon summon = (L2Summon) _actor;
			if (summon.getOwner() != null)
				summon.getOwner().getAI().setAutoAttacking(isAutoAttacking);
			return;
		}
		_clientAutoAttacking = isAutoAttacking;
	}
	
	/**
	 * Start the actor Auto Attack client side by sending Server->Client packet AutoAttackStart <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	public void clientStartAutoAttack()
	{
		if (_actor instanceof L2Summon)
		{
			_actor.getActingPlayer().getAI().clientStartAutoAttack();
			return;
		}
		
		if (!isAutoAttacking())
		{
			if (_actor instanceof Player && ((Player) _actor).getPet() != null)
				((Player) _actor).getPet().broadcastPacket(new AutoAttackStart(((Player) _actor).getPet().getObjectId()));
			
			_actor.broadcastPacket(new AutoAttackStart(_actor.getObjectId()));
			setAutoAttacking(true);
		}
		AttackStanceTaskManager.getInstance().add(_actor);
	}
	
	/**
	 * Stop the actor auto-attack client side by sending Server->Client packet AutoAttackStop <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT>
	 */
	public void clientStopAutoAttack()
	{
		if (_actor instanceof L2Summon)
		{
			_actor.getActingPlayer().getAI().clientStopAutoAttack();
			return;
		}
		
		if (_actor instanceof Player)
		{
			if (!AttackStanceTaskManager.getInstance().isInAttackStance(_actor) && isAutoAttacking())
				AttackStanceTaskManager.getInstance().add(_actor);
		}
		else if (isAutoAttacking())
		{
			_actor.broadcastPacket(new AutoAttackStop(_actor.getObjectId()));
			setAutoAttacking(false);
		}
	}
	
	/**
	 * Kill the actor client side by sending Server->Client packet AutoAttackStop, StopMove/StopRotation, Die <I>(broadcast)</I>.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 */
	protected void clientNotifyDead()
	{
		// Send a Server->Client packet Die to the actor and all L2PcInstance in its _knownPlayers
		_actor.broadcastPacket(new Die(_actor));
		
		// Init AI
		_intention = CtrlIntention.IDLE;
		_target = null;
		
		// Cancel the follow task if necessary
		stopFollow();
	}
	
	/**
	 * Update the state of this actor client side by sending Server->Client packet MoveToPawn/MoveToLocation and AutoAttackStart to the L2PcInstance player.<BR>
	 * <BR>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Low level function, used by AI subclasses</B></FONT><BR>
	 * <BR>
	 * @param player The L2PcIstance to notify with state of this L2Character
	 */
	public void describeStateToPlayer(Player player)
	{
		if (_clientMoving)
		{
			// Send a Server->Client packet MoveToPawn to the actor and all L2PcInstance in its _knownPlayers
			if (_clientMovingToPawnOffset != 0 && _followTarget != null)
				player.sendPacket(new MoveToPawn(_actor, _followTarget, _clientMovingToPawnOffset));
			// Send a Server->Client packet CharMoveToLocation to the actor and all L2PcInstance in its _knownPlayers
			else
				player.sendPacket(new MoveToLocation(_actor));
		}
	}
	
	/**
	 * Create and Launch an AI Follow Task to execute every 1s.<BR>
	 * <BR>
	 * @param target The L2Character to follow
	 */
	public synchronized void startFollow(Creature target)
	{
		if (_followTask != null)
		{
			_followTask.cancel(false);
			_followTask = null;
		}
		
		// Create and Launch an AI Follow Task to execute every 1s
		_followTarget = target;
		_followTask = ThreadPool.scheduleAtFixedRate(new FollowTask(), 5, FOLLOW_INTERVAL);
	}
	
	/**
	 * Create and Launch an AI Follow Task to execute every 0.5s, following at specified range.
	 * @param target The L2Character to follow
	 * @param range
	 */
	public synchronized void startFollow(Creature target, int range)
	{
		if (_followTask != null)
		{
			_followTask.cancel(false);
			_followTask = null;
		}
		
		_followTarget = target;
		_followTask = ThreadPool.scheduleAtFixedRate(new FollowTask(range), 5, ATTACK_FOLLOW_INTERVAL);
	}
	
	/**
	 * Stop an AI Follow Task.
	 */
	public synchronized void stopFollow()
	{
		if (_followTask != null)
		{
			// Stop the Follow Task
			_followTask.cancel(false);
			_followTask = null;
		}
		_followTarget = null;
	}
	
	protected Creature getFollowTarget()
	{
		return _followTarget;
	}
	
	public L2Object getTarget()
	{
		return _target;
	}
	
	protected void setTarget(L2Object target)
	{
		_target = target;
	}
	
	/**
	 * Stop all Ai tasks and futures.
	 */
	public void stopAITask()
	{
		stopFollow();
	}
	
	/**
	 * @param nextAction the _nextAction to set
	 */
	public void setNextAction(NextAction nextAction)
	{
		_nextAction = nextAction;
	}
	
	@Override
	public String toString()
	{
		return "Actor: " + _actor;
	}
	
	// retail
	private class FollowTask implements Runnable
	{
		protected int _range = 70;
		private final int spawnX;
		private final int spawnY;
		private final int spawnZ;
		
		public FollowTask()
		{
			// Definir valores padrões de spawn se não houver valores específicos
			spawnX = _actor.getX();
			spawnY = _actor.getY();
			spawnZ = _actor.getZ();
		}
		
		public FollowTask(int range)
		{
			this(); // Chama o construtor padrão para obter os valores de spawn
			_range = range;
		}
		
		@Override
		public void run()
		{
			if (_followTask == null)
				return;
			
			Creature followTarget = _followTarget;
			if (followTarget == null)
			{
				if (_actor instanceof L2Summon)
					((L2Summon) _actor).setFollowStatus(false);
				
				if (_actor instanceof L2AgathionInstance)
					((L2AgathionInstance) _actor).setFollowStatus(false);
				
				setIntention(CtrlIntention.IDLE);
				return;
			}
			
			// Caso Agathion trave e não saia de algum lugar
			if (!_actor.isInsideRadius(followTarget, 1200, true, false))
			{
				if (_actor instanceof L2AgathionInstance)
				{
					((L2AgathionInstance) _actor).setFollowStatus(false);
					_actor.teleToLocation(followTarget.getX(), followTarget.getY(), followTarget.getZ(), 0);
					_actor.setRunning();
					((L2AgathionInstance) _actor).setFollowStatus(true);
				}
			}
			
			// Se o ator for Agathion, a distância de seguimento será 20
			if (_actor instanceof L2AgathionInstance)
			{
				_range = 30;
			}
			
			// Agora, o Agathion irá se mover sempre para a direção do seu ponto de spawn, não para a frente do personagem
			if (!_actor.isInsideRadius(followTarget, _range, true, false))
			{
				// Usando Geodata para verificar se pode se mover para o ponto de spawn
				if (GeoEngine.getInstance().canMoveToTarget(_actor.getX(), _actor.getY(), _actor.getZ(), spawnX, spawnY, spawnZ))
				{
					moveToPawn(followTarget, _range);
				}
				else
				{
					moveToPawn(followTarget, _range);
				}
			}
		}
	}
	
}
