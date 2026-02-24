package net.sf.l2j.gameserver.handler.custom;

import java.util.HashMap;
import java.util.Map;

import net.sf.l2j.gameserver.handler.ICustomByPassHandler;
import net.sf.l2j.gameserver.model.actor.Player;

public class CustomBypassHandler
{
private static CustomBypassHandler _instance = null;
	private final Map<String, ICustomByPassHandler> _handlers;
	
	private CustomBypassHandler()
	{
		_handlers = new HashMap<>();
		
	}
	
	/**
	 * Receives the non-static instance of the RebirthManager.
	 * @return
	 */
	public static CustomBypassHandler getInstance()
	{
		if (_instance == null)
		{
			_instance = new CustomBypassHandler();
		}
		
		return _instance;
	}
	
	/**
	 * @param handler as ICustomByPassHandler
	 */
	public void registerCustomBypassHandler(final ICustomByPassHandler handler)
	{
		for (final String s : handler.getByPassCommands())
		{
			_handlers.put(s, handler);
		}
	}
	
	/**
	 * Handles player's Bypass request to the Custom Content.
	 * @param player
	 * @param command
	 */
	public void handleBypass(final Player player, final String command)
	{
		// Rebirth Manager and Engine Caller
		
		String cmd = "";
		String params = "";
		final int iPos = command.indexOf(" ");
		if (iPos != -1)
		{
			cmd = command.substring(7, iPos);
			params = command.substring(iPos + 1);
		}
		else
		{
			cmd = command.substring(7);
		}
		final ICustomByPassHandler ch = _handlers.get(cmd);
		if (ch != null)
		{
			ch.handleCommand(cmd, player, params);
		}
		else
		{
			//if (command.startsWith("custom_rebirth"))
			//{
				// Check to see if Rebirth is enabled to avoid hacks
			//	if (!Config.REBIRTH_ENABLE)
			//	{
				//	LOG.warn("[WARNING] Player " + player.getName() + " is trying to use rebirth system when it's disabled.");
			//		return;
			//	}
				
			//	L2Rebirth.getInstance().handleCommand(player, command);
			//}
		}
	}
}