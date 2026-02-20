package net.sf.l2j.event.lastman;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Logger;

import net.sf.l2j.event.fortress.FOSConfig;
import net.sf.l2j.gameserver.ThreadPool;
import net.sf.l2j.gameserver.util.Broadcast;

public class LMManager
{
	protected static final Logger _log = Logger.getLogger(LMManager.class.getName());
	
	private LMStartTask _task;
	private Calendar NextEvent;
	private final SimpleDateFormat format = new SimpleDateFormat("HH:mm");
	
	private LMManager()
	{
		if (LMConfig.LM_EVENT_ENABLED)
		{
			LMEvent.init();
			
			this.scheduleEventStart();
			_log.info("Last Man Engine: is Started.");
		}
		else
			_log.info("Last Man Engine: Engine is disabled.");
	}
	
	public static LMManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	public void scheduleEventStart()
	{
		
		try
		{
			if (LMConfig.LM_EVENT_INTERVAL == null || LMConfig.LM_EVENT_INTERVAL.length == 0)
			{
				_log.warning("LMEventEngine: LM_EVENT_INTERVAL is null or empty in config.");
				return;
			}
			
			Calendar currentTime = Calendar.getInstance();
			Calendar testStartTime = null;
			long flush2 = 0L;
			long timeL = 0L;
			int count = 0;
			for (String timeOfDay : LMConfig.LM_EVENT_INTERVAL)
			{
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(11, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(12, Integer.parseInt(splitTimeOfDay[1]));
				testStartTime.set(13, 0);
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(5, 1);
				}
				timeL = testStartTime.getTimeInMillis() - currentTime.getTimeInMillis();
				if (count == 0)
				{
					flush2 = timeL;
					NextEvent = testStartTime;
				}
				if (timeL < flush2)
				{
					flush2 = timeL;
					NextEvent = testStartTime;
				}
				count++;
			}
			_log.info("[LMEventEngine Event]: Proximo Evento: " + NextEvent.getTime().toString());
			ThreadPool.schedule(new LMStartTask(flush2), flush2);
		}
		catch (Exception e)
		{
			System.out.println("[LMEventEngine Event]: Algum erro nas config foi encontrado!");
		}
		
	}
	
	public void startReg()
	{
		if (!LMEvent.startParticipation())
		{
			Broadcast.announceToOnlinePlayers("Last Man: Event was cancelled.");
			_log.warning("LMEventEngine: Error spawning event npc for participation.");
			
			scheduleEventStart();
		}
		else
		{
			Broadcast.gameAnnounceToOnlinePlayers("Last Man: Joinable in " + LMConfig.LM_NPC_LOC_NAME + "!");
			
			if (LMConfig.ALLOW_EVENT_COMMANDS)
				Broadcast.gameAnnounceToOnlinePlayers("Last Man: Command: .lmjoin / .lmleave / .lminfo");
				
			// if (FakePlayerConfig.ALLOW_FAKE_PLAYER_LM)
			// LastManAI.spawnPhantoms();
			
			// schedule registration end
			_task.setStartTime(System.currentTimeMillis() + LMConfig.LM_EVENT_PARTICIPATION_TIME);
			ThreadPool.execute(_task);
		}
	}
	
	public void startEvent()
	{
		if (!LMEvent.startFight())
		{
			Broadcast.gameAnnounceToOnlinePlayers("Last Man: Event cancelled due to lack of Participation.");
			_log.info("LMEventEngine: Lack of registration, abort event.");
			
			scheduleEventStart();
		}
		else
		{
			LMEvent.sysMsgToAllParticipants("Teleporting participants in " + LMConfig.LM_EVENT_START_LEAVE_TELEPORT_DELAY + " second(s).");
			_task.setStartTime(System.currentTimeMillis() + 60000L * LMConfig.LM_EVENT_RUNNING_TIME);
			ThreadPool.execute(_task);
		}
	}
	
	public void endEvent()
	{
		Broadcast.gameAnnounceToOnlinePlayers(LMEvent.calculateRewards());
		LMEvent.sysMsgToAllParticipants("Teleporting back to town in " + LMConfig.LM_EVENT_START_LEAVE_TELEPORT_DELAY + " second(s).");
		LMEvent.stopFight();
		
		// if (FakePlayerConfig.ALLOW_FAKE_PLAYER_LM)
		// ThreadPoolManager.getInstance().scheduleAi(() -> LastManAI.unspawnPhantoms(), 25 * 1000);
		
		scheduleEventStart();
	}
	
	public void skipDelay()
	{
		if (_task.nextRun.cancel(false))
		{
			_task.setStartTime(System.currentTimeMillis());
			ThreadPool.execute(_task);
		}
	}
	
	class LMStartTask implements Runnable
	{
		private long _startTime;
		public ScheduledFuture<?> nextRun;
		
		public LMStartTask(long startTime)
		{
			_startTime = startTime;
		}
		
		public void setStartTime(long startTime)
		{
			_startTime = startTime;
		}
		
		@Override
		public void run()
		{
			int delay = (int) Math.round((_startTime - System.currentTimeMillis()) / 1000.0);
			
			if (delay > 0)
			{
				this.announce(delay);
			}
			
			int nextMsg = 0;
			if (delay > 3600)
			{
				nextMsg = delay - 3600;
			}
			else if (delay > 1800)
			{
				nextMsg = delay - 1800;
			}
			else if (delay > 900)
			{
				nextMsg = delay - 900;
			}
			else if (delay > 600)
			{
				nextMsg = delay - 600;
			}
			else if (delay > 300)
			{
				nextMsg = delay - 300;
			}
			else if (delay > 60)
			{
				nextMsg = delay - 60;
			}
			else if (delay > 5)
			{
				nextMsg = delay - 5;
			}
			else if (delay > 0)
			{
				nextMsg = delay;
			}
			else
			{
				// start
				if (LMEvent.isInactive())
				{
					LMManager.this.startReg();
				}
				else if (LMEvent.isParticipating())
				{
					LMManager.this.startEvent();
				}
				else
				{
					LMManager.this.endEvent();
				}
			}
			
			if (delay > 0)
			{
				nextRun = ThreadPool.schedule(this, nextMsg * 1000);
			}
		}
		
		private void announce(long time)
		{
			if (time >= 3600 && time % 3600 == 0)
			{
				if (LMEvent.isParticipating())
				{
					Broadcast.gameAnnounceToOnlinePlayers("Last Man: " + (time / 60 / 60) + " hour(s) until registration is closed!");
				}
				else if (LMEvent.isStarted())
				{
					LMEvent.sysMsgToAllParticipants("" + (time / 60 / 60) + " hour(s) until event is finished!");
				}
			}
			else if (time >= 60)
			{
				if (LMEvent.isParticipating())
				{
					Broadcast.gameAnnounceToOnlinePlayers("Last Man: " + (time / 60) + " minute(s) until registration is closed!");
				}
				else if (LMEvent.isStarted())
				{
					LMEvent.sysMsgToAllParticipants("" + (time / 60) + " minute(s) until the event is finished!");
				}
			}
			else
			{
				if (LMEvent.isParticipating())
				{
					Broadcast.gameAnnounceToOnlinePlayers("Last Man: " + time + " second(s) until registration is closed!");
				}
				else if (LMEvent.isStarted())
				{
					LMEvent.sysMsgToAllParticipants("" + time + " second(s) until the event is finished!");
				}
			}
		}
	}
	
	public String getNextTime()
	{
		if (getNextEventTime().getTime() != null)
			return format.format(getNextEventTime().getTime());
		return "Erro";
	}
	
	public Calendar getNextEventTime()
	{
		try
		{
			Calendar currentTime = Calendar.getInstance();
			Calendar testStartTime = null;
			long flush2 = 0, timeL = 0;
			int count = 0;
			Calendar nextEvent = null;
			for (String timeOfDay : FOSConfig.FOS_EVENT_INTERVAL)
			{
				testStartTime = Calendar.getInstance();
				testStartTime.setLenient(true);
				String[] splitTimeOfDay = timeOfDay.split(":");
				testStartTime.set(Calendar.HOUR_OF_DAY, Integer.parseInt(splitTimeOfDay[0]));
				testStartTime.set(Calendar.MINUTE, Integer.parseInt(splitTimeOfDay[1]));
				testStartTime.set(Calendar.SECOND, 00);
				if (testStartTime.getTimeInMillis() < currentTime.getTimeInMillis())
				{
					testStartTime.add(Calendar.DAY_OF_MONTH, 1);
				}
				
				timeL = testStartTime.getTimeInMillis() - currentTime.getTimeInMillis();
				
				if (count == 0)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				
				if (timeL < flush2)
				{
					flush2 = timeL;
					nextEvent = testStartTime;
				}
				count++;
			}
			// _log.info("Tournament: Next Event " + nextEvent.getTime().toString());
			return nextEvent;
		}
		catch (Exception e)
		{
			e.printStackTrace();
			System.out.println("LMEventEngine Invade]: " + e);
			return null;
		}
		
	}
	
	private static class SingletonHolder
	{
		protected static final LMManager _instance = new LMManager();
	}
}