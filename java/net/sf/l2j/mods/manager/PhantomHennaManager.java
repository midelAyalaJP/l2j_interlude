package net.sf.l2j.mods.manager;

import java.util.List;

import net.sf.l2j.gameserver.datatables.HennaTable;
import net.sf.l2j.gameserver.model.actor.Player;
import net.sf.l2j.gameserver.model.base.ClassId;
import net.sf.l2j.gameserver.model.item.Henna;
import net.sf.l2j.gameserver.network.SystemMessageId;
import net.sf.l2j.gameserver.network.serverpackets.HennaInfo;
import net.sf.l2j.gameserver.network.serverpackets.UserInfo;
import net.sf.l2j.mods.data.SymbolsData;
import net.sf.l2j.mods.holder.SymbolHolder;

public class PhantomHennaManager
{
	public static void applySymbols(Player fakePlayer)
	{
		ClassId classId = fakePlayer.getClassId();
		SymbolHolder symbolHolder = SymbolsData.getInstance().getSymbolSet(classId.name());
		
		if (symbolHolder == null || symbolHolder.getSymbols().isEmpty())
		{
			return;
		}
		
		List<Integer> symbolsToApply = symbolHolder.getSymbols();
		List<Henna> allowedHennas = HennaTable.getInstance().getAvailableHenna(fakePlayer.getClassId().getId());
		
		int slotIndex = 1;
		
		for (int symbolId : symbolsToApply)
		{
			if (slotIndex > 3)
				break;
			
			boolean isAllowed = allowedHennas.stream().anyMatch(h -> h.getSymbolId() == symbolId);
			if (!isAllowed)
				continue;
			
			Henna henna = HennaTable.getInstance().getTemplate(symbolId);
			if (henna == null)
			{
				return;
			}
			
			
			fakePlayer.addHenna(henna);

			fakePlayer.sendPacket(new HennaInfo(fakePlayer));
			fakePlayer.sendPacket(new UserInfo(fakePlayer));
			fakePlayer.sendPacket(SystemMessageId.SYMBOL_ADDED);
		}
	}
}
