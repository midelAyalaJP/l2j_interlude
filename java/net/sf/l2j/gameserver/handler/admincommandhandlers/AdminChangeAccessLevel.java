package net.sf.l2j.gameserver.handler.admincommandhandlers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.l2j.gameserver.ConnectionPool;
import net.sf.l2j.gameserver.handler.IAdminCommandHandler;
import net.sf.l2j.gameserver.model.GmAccessService;
import net.sf.l2j.gameserver.model.L2World;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.network.SystemMessageId;

public class AdminChangeAccessLevel implements IAdminCommandHandler
{
	private static final Logger LOGGER = Logger.getLogger(AdminChangeAccessLevel.class.getName());
	
	private static final String[] ADMIN_COMMANDS =
	{
		"admin_changelvl",
		"admin_setaccess",
		"admin_banlvl",
		"admin_changelv" // se você tiver variações antigas
	};
	
	// Ajuste se quiser impor limites
	private static final int MIN_LEVEL = -1; // <= 0 costuma ser ban/sem acesso
	private static final int MAX_LEVEL = 127; // exemplo; ajuste ao seu projeto
	
	@Override
	public boolean useAdminCommand(String command, Player activeChar)
	{
		if (activeChar == null)
			return false;
		
		final String[] parts = command.trim().split("\\s+");
		
		// Aceita: //changelvl <lvl> (no target) OR //changelvl <player> <lvl>
		if (parts.length == 2)
		{
			final Integer lvl = parseInt(parts[1]);
			if (lvl == null)
				return showUsage(activeChar);
			
			if (!(activeChar.getTarget() instanceof Player))
			{
				activeChar.sendPacket(SystemMessageId.INCORRECT_TARGET);
				return true;
			}
			
			final Player target = (Player) activeChar.getTarget();
			return applyAccessLevel(activeChar, target, target.getName(), lvl);
		}
		else if (parts.length == 3)
		{
			final String name = parts[1];
			final Integer lvl = parseInt(parts[2]);
			
			if (name == null || name.isBlank() || lvl == null)
				return showUsage(activeChar);
			
			// tenta online primeiro
			final Player target = L2World.getInstance().getPlayer(name);
			
			GmAccessService.reconcilePlayerByNameOrObj(target.getName(), target.getObjectId());
			return applyAccessLevel(activeChar, target, name, lvl);
		}
		
		return showUsage(activeChar);
	}
	
	@Override
	public String[] getAdminCommandList()
	{
		return ADMIN_COMMANDS;
	}
	
	private static boolean applyAccessLevel(Player activeChar, Player targetOnline, String name, int lvl)
	{
		// valida range
		if (lvl < MIN_LEVEL || lvl > MAX_LEVEL)
		{
			activeChar.sendMessage("AccessLevel inválido. Range permitido: " + MIN_LEVEL + " .. " + MAX_LEVEL);
			return true;
		}
		
		// opcional: impedir mexer em si mesmo
		if (targetOnline != null && targetOnline == activeChar)
		{
			activeChar.sendMessage("Você não pode alterar seu próprio access level por este comando.");
			return true;
		}
		
		// Atualiza DB (fonte de verdade)
		final int rows = updateAccessLevelInDb(name, lvl);
		if (rows == 0)
		{
			// se não achou no DB e não está online com esse nome, informa.
			if (targetOnline == null)
			{
				activeChar.sendMessage("Character \"" + name + "\" não encontrado (online/offline).");
				return true;
			}
			// Se está online e não atualizou DB, ainda tentamos prosseguir com sync online,
			// mas avisamos. (Pode ocorrer se coluna/nome diferir.)
			activeChar.sendMessage("Aviso: não foi possível atualizar no DB para \"" + name + "\" (0 rows).");
		}
		
		// Se o player está online, sincroniza o objeto em memória
		if (targetOnline != null)
		{
			targetOnline.setAccessLevel(lvl);
			
			if (lvl <= 0)
			{
				targetOnline.sendMessage("Seu personagem foi banido (access level: " + lvl + ").");
				targetOnline.logout();
			}
			else
			{
				targetOnline.sendMessage("Seu access level foi alterado para " + lvl + ".");
				// Após mudar access level (DB/online):
				GmAccessService.reconcilePlayerByNameOrObj(name, targetOnline.getObjectId());
			}
		}
		
		activeChar.sendMessage("Access level de \"" + name + "\" agora é " + lvl + "." + (targetOnline == null ? " (offline)" : " (online)") + " DB=" + rows + " row(s).");
		
		// dica útil: muitas permissões são lidas no login
		if (lvl > 0)
			activeChar.sendMessage("Obs: algumas permissões/efeitos podem exigir relog do jogador.");
		
		return true;
	}
	
	private static int updateAccessLevelInDb(String name, int lvl)
	{
		// aCis costuma usar characters.char_name
		final String sql = "UPDATE characters SET accesslevel=? WHERE char_name=?";
		try (Connection con = ConnectionPool.getConnection(); PreparedStatement ps = con.prepareStatement(sql))
		{
			ps.setInt(1, lvl);
			ps.setString(2, name);
			return ps.executeUpdate();
		}
		catch (SQLException e)
		{
			LOGGER.log(Level.WARNING, "Failed to update accesslevel for " + name + " -> " + lvl, e);
			return 0;
		}
	}
	
	private static Integer parseInt(String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (Exception e)
		{
			return null;
		}
	}
	
	private static boolean showUsage(Player activeChar)
	{
		activeChar.sendMessage("Usage:");
		activeChar.sendMessage("  //changelvl <new_level>                 (alvo deve ser um Player)");
		activeChar.sendMessage("  //changelvl <player_name> <new_level>   (online ou offline)");
		activeChar.sendMessage("Exemplos:");
		activeChar.sendMessage("  //changelvl 7");
		activeChar.sendMessage("  //changelvl Julio 0   (ban)");
		return true;
	}
}