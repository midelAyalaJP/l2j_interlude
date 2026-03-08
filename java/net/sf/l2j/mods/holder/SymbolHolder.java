package net.sf.l2j.mods.holder;

import java.util.List;

public class SymbolHolder
{
	private final String _classIdName;
	private final List<Integer> _symbols;
	
	public SymbolHolder(String classIdName, List<Integer> symbols)
	{
		_classIdName = classIdName;
		_symbols = symbols;
	}
	
	public String getClassIdName()
	{
		return _classIdName;
	}
	
	public List<Integer> getSymbols()
	{
		return _symbols;
	}
}
