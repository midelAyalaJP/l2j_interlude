package net.sf.l2j.gameserver;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.Config;

public final class ThreadPool
{
	protected static final Logger LOG = Logger.getLogger(ThreadPool.class.getName());
	
	private static final int CPU = Runtime.getRuntime().availableProcessors();
	private static final long MAX_DELAY = TimeUnit.DAYS.toMillis(365);
	
	// Limites conservadores para não explodir memória.
	private static final int INSTANT_QUEUE_LIMIT = 5000;
	
	// Balanceador thread-safe.
	private static final AtomicLong POOL_BALANCER = new AtomicLong(0);
	
	private static ScheduledThreadPoolExecutor[] _scheduledPools = new ScheduledThreadPoolExecutor[0];
	private static ThreadPoolExecutor[] _instantPools = new ThreadPoolExecutor[0];
	
	private static volatile boolean SHUTTING_DOWN = false;
	private static volatile boolean INITIALIZED = false;
	
	// Métricas.
	private static final AtomicLong scheduledTasks = new AtomicLong(0);
	private static final AtomicLong instantTasks = new AtomicLong(0);
	private static final AtomicLong rejectedTasks = new AtomicLong(0);
	private static final AtomicLong taskErrors = new AtomicLong(0);
	
	// Controle de tasks agendadas.
	private static final Set<ScheduledFuture<?>> FUTURES = Collections.synchronizedSet(new HashSet<>());
	
	private ThreadPool()
	{
	}
	
	public static synchronized void init()
	{
		if (INITIALIZED)
		{
			LOG.warning("ThreadPool: init() chamado mais de uma vez. Ignorando.");
			return;
		}
		
		SHUTTING_DOWN = false;
		
		final int schedCount = Math.max(1, (Config.SCHEDULED_THREAD_POOL_COUNT == -1) ? CPU : Config.SCHEDULED_THREAD_POOL_COUNT);
		final int instCount = Math.max(1, (Config.INSTANT_THREAD_POOL_COUNT == -1) ? CPU : Config.INSTANT_THREAD_POOL_COUNT);
		
		_scheduledPools = new ScheduledThreadPoolExecutor[schedCount];
		for (int i = 0; i < schedCount; i++)
		{
			final ScheduledThreadPoolExecutor pool = new ScheduledThreadPoolExecutor(
				Config.THREADS_PER_SCHEDULED_THREAD_POOL,
				new NamedThreadFactory("ScheduledPool-" + i));
			
			// Importante para não segurar tasks canceladas desnecessariamente.
			pool.setRemoveOnCancelPolicy(true);
			pool.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
			pool.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
			pool.prestartAllCoreThreads();
			
			_scheduledPools[i] = pool;
		}
		
		final RejectedExecutionHandler rejectionHandler = (r, executor) ->
		{
			rejectedTasks.incrementAndGet();
			LOG.warning("ThreadPool: task descartada por sobrecarga. Active=" + executor.getActiveCount() + ", Queue=" + executor.getQueue().size());
		};
		
		_instantPools = new ThreadPoolExecutor[instCount];
		for (int i = 0; i < instCount; i++)
		{
			final ThreadPoolExecutor pool = new ThreadPoolExecutor(
				Config.THREADS_PER_INSTANT_THREAD_POOL,
				Config.THREADS_PER_INSTANT_THREAD_POOL,
				30L,
				TimeUnit.SECONDS,
				new LinkedBlockingQueue<>(INSTANT_QUEUE_LIMIT),
				new NamedThreadFactory("InstantPool-" + i),
				rejectionHandler);
			
			pool.prestartAllCoreThreads();
			_instantPools[i] = pool;
		}
		
		// Limpeza periódica de tasks canceladas/concluídas.
		scheduleAtFixedRate("ThreadPoolCleanup", () ->
		{
			for (ScheduledThreadPoolExecutor pool : _scheduledPools)
			{
				if (pool != null)
					pool.purge();
			}
			
			for (ThreadPoolExecutor pool : _instantPools)
			{
				if (pool != null)
					pool.purge();
			}
			
			synchronized (FUTURES)
			{
				FUTURES.removeIf(future -> (future == null) || future.isDone() || future.isCancelled());
			}
		}, 300000L, 300000L);
		
		// Monitor de carga.
		scheduleAtFixedRate("ThreadPoolMonitor", () ->
		{
			for (int i = 0; i < _scheduledPools.length; i++)
			{
				final ScheduledThreadPoolExecutor pool = _scheduledPools[i];
				if (pool == null)
					continue;
				
				final int active = pool.getActiveCount();
				final int queue = pool.getQueue().size();
				final int core = pool.getCorePoolSize();
				
				if ((queue > 500) || (active >= core))
					LOG.warning("ThreadPool Monitor: Scheduled #" + i + " sob carga. Active=" + active + ", Core=" + core + ", Queue=" + queue + ", Completed=" + pool.getCompletedTaskCount());
			}
			
			for (int i = 0; i < _instantPools.length; i++)
			{
				final ThreadPoolExecutor pool = _instantPools[i];
				if (pool == null)
					continue;
				
				final int active = pool.getActiveCount();
				final int queue = pool.getQueue().size();
				final int max = pool.getMaximumPoolSize();
				
				if (queue > 1000)
					LOG.warning("ThreadPool Monitor: Instant #" + i + " sob carga. Active=" + active + ", Max=" + max + ", Queue=" + queue + ", Completed=" + pool.getCompletedTaskCount());
			}
		}, 60000L, 60000L);
		
		INITIALIZED = true;
		LOG.info("ThreadPool: iniciado com " + schedCount + " scheduled pools e " + instCount + " instant pools.");
	}
	
	private static boolean canSchedule()
	{
		return INITIALIZED && !SHUTTING_DOWN && (_scheduledPools.length > 0) && (_instantPools.length > 0);
	}
	
	/* ========================= EXECUÇÃO ========================= */
	
	// Compatibilidade antiga.
	public static ScheduledFuture<?> schedule(Runnable r, long delay)
	{
		return schedule("UnnamedScheduledTask", r, delay);
	}
	
	public static ScheduledFuture<?> schedule(String name, Runnable r, long delay)
	{
		if ((r == null) || !canSchedule())
			return null;
		
		try
		{
			final ScheduledFuture<?> future = getScheduledPool().schedule(new SafeTask(name, r), validate(delay), TimeUnit.MILLISECONDS);
			FUTURES.add(future);
			scheduledTasks.incrementAndGet();
			return future;
		}
		catch (Throwable t)
		{
			LOG.log(Level.WARNING, "ThreadPool: falha ao agendar task [" + safeName(name) + "]", t);
			return null;
		}
	}
	
	// Compatibilidade antiga.
	public static ScheduledFuture<?> scheduleAtFixedRate(Runnable r, long delay, long period)
	{
		return scheduleAtFixedRate("UnnamedPeriodicTask", r, delay, period);
	}
	
	public static ScheduledFuture<?> scheduleAtFixedRate(String name, Runnable r, long delay, long period)
	{
		if ((r == null) || !canSchedule())
			return null;
		
		try
		{
			final ScheduledFuture<?> future = getScheduledPool().scheduleAtFixedRate(new SafeTask(name, r), validate(delay), validatePeriod(period), TimeUnit.MILLISECONDS);
			FUTURES.add(future);
			scheduledTasks.incrementAndGet();
			return future;
		}
		catch (Throwable t)
		{
			LOG.log(Level.WARNING, "ThreadPool: falha ao agendar task periódica [" + safeName(name) + "]", t);
			return null;
		}
	}
	
	// Compatibilidade antiga.
	public static void execute(Runnable r)
	{
		execute("UnnamedInstantTask", r);
	}
	
	public static void execute(String name, Runnable r)
	{
		if ((r == null) || !canSchedule())
			return;
		
		try
		{
			getInstantPool().execute(new SafeTask(name, r));
			instantTasks.incrementAndGet();
		}
		catch (Throwable t)
		{
			LOG.log(Level.WARNING, "ThreadPool: falha ao executar task [" + safeName(name) + "]", t);
		}
	}
	
	/* ========================= POOL SELECTION ========================= */
	
	private static ScheduledThreadPoolExecutor getScheduledPool()
	{
		final long index = POOL_BALANCER.getAndIncrement() & Long.MAX_VALUE;
		return _scheduledPools[(int) (index % _scheduledPools.length)];
	}
	
	private static ThreadPoolExecutor getInstantPool()
	{
		final long index = POOL_BALANCER.getAndIncrement() & Long.MAX_VALUE;
		return _instantPools[(int) (index % _instantPools.length)];
	}
	
	private static long validate(long delay)
	{
		return Math.max(0L, Math.min(MAX_DELAY, delay));
	}
	
	private static long validatePeriod(long period)
	{
		return Math.max(1L, Math.min(MAX_DELAY, period));
	}
	
	private static String safeName(String name)
	{
		return ((name == null) || name.isEmpty()) ? "UnnamedTask" : name;
	}
	
	/* ========================= SHUTDOWN ========================= */
	
	public static synchronized void shutdown()
	{
		if (!INITIALIZED)
			return;
		
		SHUTTING_DOWN = true;
		LOG.info("ThreadPool: iniciando desligamento seguro...");
		
		try
		{
			synchronized (FUTURES)
			{
				for (ScheduledFuture<?> future : FUTURES)
				{
					if (future != null)
						future.cancel(false);
				}
				FUTURES.clear();
			}
			
			for (ScheduledThreadPoolExecutor pool : _scheduledPools)
			{
				if (pool != null)
					pool.shutdown();
			}
			
			for (ThreadPoolExecutor pool : _instantPools)
			{
				if (pool != null)
					pool.shutdown();
			}
			
			for (ScheduledThreadPoolExecutor pool : _scheduledPools)
			{
				if ((pool != null) && !pool.awaitTermination(5, TimeUnit.SECONDS))
					pool.shutdownNow();
			}
			
			for (ThreadPoolExecutor pool : _instantPools)
			{
				if ((pool != null) && !pool.awaitTermination(5, TimeUnit.SECONDS))
					pool.shutdownNow();
			}
			
			LOG.info("ThreadPool: desligado com sucesso.");
		}
		catch (Throwable t)
		{
			LOG.log(Level.WARNING, "ThreadPool: erro ao desligar.", t);
		}
		finally
		{
			INITIALIZED = false;
		}
	}
	
	public static boolean isShuttingDown()
	{
		return SHUTTING_DOWN;
	}
	
	/* ========================= MÉTRICAS ========================= */
	
	public static void printStats()
	{
		LOG.info("========== ThreadPool Metrics ==========");
		LOG.info("Scheduled tasks: " + scheduledTasks.get());
		LOG.info("Instant tasks:   " + instantTasks.get());
		LOG.info("Rejected tasks:  " + rejectedTasks.get());
		LOG.info("Task errors:     " + taskErrors.get());
		
		for (int i = 0; i < _scheduledPools.length; i++)
		{
			final ScheduledThreadPoolExecutor pool = _scheduledPools[i];
			if (pool != null)
				LOG.info("Scheduled #" + i + ": Active=" + pool.getActiveCount() + ", Queue=" + pool.getQueue().size() + ", Completed=" + pool.getCompletedTaskCount());
		}
		
		for (int i = 0; i < _instantPools.length; i++)
		{
			final ThreadPoolExecutor pool = _instantPools[i];
			if (pool != null)
				LOG.info("Instant #" + i + ": Active=" + pool.getActiveCount() + ", Queue=" + pool.getQueue().size() + ", Completed=" + pool.getCompletedTaskCount());
		}
		
		synchronized (FUTURES)
		{
			LOG.info("Tracked futures: " + FUTURES.size());
		}
	}
	
	public static long getRejectedTasks()
	{
		return rejectedTasks.get();
	}
	
	public static long getTaskErrors()
	{
		return taskErrors.get();
	}
	
	/* ========================= TASK WRAPPER ========================= */
	
	private static final class SafeTask implements Runnable
	{
		private final String name;
		private final Runnable task;
		
		SafeTask(String name, Runnable task)
		{
			this.name = safeName(name);
			this.task = task;
		}
		
		@Override
		public void run()
		{
			if (SHUTTING_DOWN)
				return;
			
			try
			{
				task.run();
			}
			catch (Throwable t)
			{
				taskErrors.incrementAndGet();
				LOG.log(Level.WARNING, "ThreadPool Task Error [" + name + "]", t);
			}
		}
		
		@Override
		public String toString()
		{
			return name;
		}
	}
	
	/* ========================= THREAD FACTORY ========================= */
	
	private static final class NamedThreadFactory implements ThreadFactory
	{
		private final String prefix;
		private final AtomicLong counter = new AtomicLong(1);
		
		NamedThreadFactory(String prefix)
		{
			this.prefix = prefix;
		}
		
		@Override
		public Thread newThread(Runnable r)
		{
			final Thread t = new Thread(r);
			t.setName(prefix + "-" + counter.getAndIncrement());
			t.setDaemon(false);
			t.setPriority(Thread.NORM_PRIORITY);
			t.setUncaughtExceptionHandler((thread, throwable) ->
				LOG.log(Level.WARNING, "Uncaught exception in thread " + thread.getName(), throwable));
			return t;
		}
	}
}