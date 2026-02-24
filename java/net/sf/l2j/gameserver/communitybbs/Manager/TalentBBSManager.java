package net.sf.l2j.gameserver.communitybbs.Manager;

import java.util.Arrays;
import java.util.List;

import net.sf.l2j.gameserver.datatables.SkillTable;
import net.sf.l2j.gameserver.datatables.xml.TalentData;
import net.sf.l2j.gameserver.datatables.xml.TalentTreeHolder;
import net.sf.l2j.gameserver.model.L2Skill;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.holder.TalentSkillHolder;
import net.sf.l2j.gameserver.network.serverpackets.ShowBoard;

public class TalentBBSManager extends BaseBBSManager
{
	
	private static final int MAX_PER_TIER_ROW = 3;
	
	@Override
	public void parseCmd(String command, Player activeChar)
	{
		if (activeChar == null)
			return;
		
		if (!command.startsWith("_bbstalent"))
		{
			super.parseCmd(command, activeChar);
			return;
		}
		
		// aceita "_bbstalent show" e "_bbstalent;show"
		final String normalized = command.replace(';', ' ').trim();
		final String[] parts = normalized.split("\\s+");
		
		final String param = (parts.length > 1) ? parts[1] : null;
		if (param == null)
		{
			activeChar.sendMessage("Invalid command parameter.");
			return;
		}
		
		switch (param.toLowerCase())
		{
			case "show":
				separateAndSend(buildTreesHtml(activeChar, Arrays.asList("POWER", "MASTERY_A", "MASTERY_B")), activeChar);
				return;
			
			case "learn":
			{
				final String treeId = (parts.length > 2) ? parts[2] : null;
				
				final int skillId = (parts.length > 3) ? parseInt(parts[3]) : 0;
				final int level = (parts.length > 4) ? parseInt(parts[4]) : 0;
				
				if (treeId == null || treeId.isEmpty() || skillId <= 0 || level <= 0)
				{
					separateAndSend(buildTreesHtml(activeChar, Arrays.asList("POWER", "MASTERY_A", "MASTERY_B")), activeChar);
					
					return;
				}
				
				final boolean ok = activeChar.learnTalent(treeId, skillId, level);
				if (!ok)
					activeChar.sendMessage("You cannot learn this talent right now.");
				
				separateAndSend(buildTreesHtml(activeChar, Arrays.asList("POWER", "MASTERY_A", "MASTERY_B")), activeChar);
				return;
				
			}
			
			default:
				separateAndSend(buildIndexHtml(activeChar), activeChar);
				return;
		}
		
	}
	
	private static int parseInt(String s)
	{
		try
		{
			return Integer.parseInt(s);
		}
		catch (Exception e)
		{
			return 0;
		}
	}
	
	private static String buildIndexHtml(Player player)
	{
		final StringBuilder sb = new StringBuilder(2048);
		
		sb.append("<html><body><br><br><center>");
		sb.append("<table width=720>");
		sb.append("<tr><td align=center><font name=hs12 color=LEVEL>Talent Trees</font></td></tr>");
		sb.append("<tr><td height=8></td></tr>");
		sb.append("<tr><td align=center>");
		sb.append("<button value=\"Open\" action=\"bypass _bbstalent show\" width=120 height=26 back=\"L2UI_CT1.Button_DF_Down\" fore=\"L2UI_CT1.Button_DF\">");
		sb.append("</td></tr>");
		sb.append("</table>");
		sb.append("</center></body></html>");
		
		return sb.toString();
	}
	
	private static String buildTreesHtml(Player player, List<String> treeIds)
	{
		final StringBuilder sb = new StringBuilder(12000);
		
		sb.append("<html><body><br><br><center>");
		sb.append("<table width=520 cellpadding=0 cellspacing=0 border=0><tr>");
		
		for (String id : treeIds)
		{
			sb.append("<td width=200 valign=top>");
			final TalentTreeHolder tree = TalentData.getInstance().getTree(id);
			if (tree != null)
				sb.append(buildOneTreeHtml(player, tree));
			else
				sb.append("<br><center><font color=FF5555>Missing tree: ").append(id).append("</font></center><br>");
			sb.append("</td>");
		}
		
		sb.append("</tr></table>");
		sb.append("</center></body></html>");
		
		return sb.toString();
	}
	
	private static String buildOneTreeHtml(Player player, TalentTreeHolder tree)
	{
		final String treeId = tree.getId();
		final int spent = player.getTalentSpentPoints(treeId);
		final int available = player.getTalentAvailablePoints(treeId);
		final int max = tree.getMaxPoints();
		
		final StringBuilder sb = new StringBuilder(6000);

		
		sb.append("<table width=200 cellpadding=0 cellspacing=0 border=0>");
		sb.append("<tr><td align=center><font name=hs12 color=LEVEL>").append(escape(treeId)).append("</font></td></tr>");
		sb.append("<tr><td height=2></td></tr>");
		
		for (int tier = 1; tier <= 3; tier++)
		{
			final int req = tree.getRequiredPointsForTier(tier);
			final int tierSpent = getSpentInTier(player, tree, tier);
			final int tierMax = getMaxPointsInTier(tree, tier);
			
			sb.append("<tr><td align=center>");
			sb.append("<font color=AAAAAA>Tier ").append(tier).append(" (req ").append(req).append("): ").append(tierSpent).append("/").append(tierMax).append("</font>");
			sb.append("</td></tr>");
			
			sb.append("<tr><td height=4></td></tr>");
			sb.append(buildTierGrid(player, tree, tier));
			sb.append("<tr><td height=10></td></tr>");
		}
		
		sb.append("<tr><td align=center><font color=AAAAAA>Total: ").append(spent).append("/").append(max).append("</font></td></tr>");
		sb.append("<tr><td align=center><font color=FFFF66>Available: ").append(available).append("</font></td></tr>");
		sb.append("</table>");
		
	
		
		
		return sb.toString();
	}
	
	private static String buildTierGrid(Player player, TalentTreeHolder tree, int tier)
	{
		final List<TalentSkillHolder> skills = tree.getSkillsByTier().get(tier);
		if (skills == null || skills.isEmpty())
			return "<tr><td align=center><font color=777777>(empty)</font></td></tr>";
		
		final StringBuilder sb = new StringBuilder(4096);
		
		sb.append("<tr><td align=center><table cellpadding=2 cellspacing=0 border=0>");
		sb.append("<tr>");
		
		int col = 0;
		
		for (TalentSkillHolder h : skills)
		{
			if (col == MAX_PER_TIER_ROW)
			{
				sb.append("</tr><tr>");
				col = 0;
			}
			
			sb.append("<td width=62 align=center>");
			sb.append(buildSkillIconCell(player, tree, h));
			sb.append("</td>");
			
			col++;
		}
		
		while (col > 0 && col < MAX_PER_TIER_ROW)
		{
			sb.append("<td width=62></td>");
			col++;
		}
		
		sb.append("</tr></table></td></tr>");
		return sb.toString();
	}
	
	private static String buildSkillIconCell(Player player, TalentTreeHolder tree, TalentSkillHolder h)
	{
		final String treeId = tree.getId();
		final int skillId = h.getSkillId();
		
		final int current = player.getTalentLevel(treeId, skillId);
		final int nextLevel = current + 1;
		final int iconLvl = Math.max(1, current);
		final String icon = getSkillIcon(skillId, iconLvl);
		
		final StringBuilder sb = new StringBuilder(256);
		
		sb.append("<button value=\"\" action=\"bypass _bbstalent learn ").append(treeId).append(" ").append(skillId).append(" ").append(nextLevel).append("\" width=\"32\" height=\"32\" back=\"").append(icon).append("\" fore=\"").append(icon).append("\">");
		sb.append("<br1><font color=FFFFFF>").append(current).append("/</font><font color=LEVEL>").append(h.getMaxLevel()).append("</font><br1>");
		
		return sb.toString();
	}
	
	private static int getSpentInTier(Player player, TalentTreeHolder tree, int tier)
	{
		int sum = 0;
		final List<TalentSkillHolder> skills = tree.getSkillsByTier().get(tier);
		if (skills == null)
			return 0;
		
		final String treeId = tree.getId();
		for (TalentSkillHolder h : skills)
			sum += player.getTalentLevel(treeId, h.getSkillId());
		
		return sum;
	}
	
	private static String getSkillIcon(int skillId, int level)
	{
		final L2Skill sk = SkillTable.getInstance().getInfo(skillId, level);
		if (sk == null)
			return "Icon.skill0000";
		
		final String ico = sk.getIcon();
		if (ico == null || ico.isEmpty())
			return "Icon.skill0000";
		
		if (ico.startsWith("icon."))
			return "Icon." + ico.substring(5);
		
		if (ico.startsWith("Icon."))
			return ico;
		
		return "Icon." + ico;
	}
	
	private static int getMaxPointsInTier(TalentTreeHolder tree, int tier)
	{
		int sum = 0;
		final List<TalentSkillHolder> skills = tree.getSkillsByTier().get(tier);
		if (skills == null)
			return 0;
		
		for (TalentSkillHolder h : skills)
			sum += Math.max(0, h.getMaxLevel());
		
		return sum;
	}
	
	private static String escape(String s)
	{
		if (s == null)
			return "";
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}
	
	public static void separateAndSend(String html, Player player)
	{
		if (html == null || player == null)
			return;
		
		if (!html.startsWith("<html>"))
			html = "<html><body>" + html + "</body></html>";
		
		if (html.length() < 4090)
		{
			player.sendPacket(new ShowBoard(html, "101"));
			player.sendPacket(ShowBoard.STATIC_SHOWBOARD_102);
			player.sendPacket(ShowBoard.STATIC_SHOWBOARD_103);
		}
		else if (html.length() < 8180)
		{
			player.sendPacket(new ShowBoard(html.substring(0, 4090), "101"));
			player.sendPacket(new ShowBoard(html.substring(4090), "102"));
			player.sendPacket(ShowBoard.STATIC_SHOWBOARD_103);
		}
		else
		{
			final int end = Math.min(html.length(), 12270);
			player.sendPacket(new ShowBoard(html.substring(0, 4090), "101"));
			player.sendPacket(new ShowBoard(html.substring(4090, 8180), "102"));
			player.sendPacket(new ShowBoard(html.substring(8180, end), "103"));
		}
	}
	
	public static TalentBBSManager getInstance()
	{
		return SingletonHolder._instance;
	}
	
	private static class SingletonHolder
	{
		protected static final TalentBBSManager _instance = new TalentBBSManager();
	}
}