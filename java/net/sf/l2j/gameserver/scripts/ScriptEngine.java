package net.sf.l2j.gameserver.scripts;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScriptEngine
{
	//public static final Hashtable<String, ParserFactory> parserFactories = new Hashtable<String, ParserFactory>();
	public static final Map<String, ParserFactory> parserFactories = new ConcurrentHashMap<>();


	protected static Parser createParser(String name) throws ParserNotCreatedException
	{
		ParserFactory s = parserFactories.get(name);
		if(s == null) // shape not found
		{
			try
			{
				Class.forName("net.sf.l2j.gameserver.script." + name);
				// By now the static block with no function would
				// have been executed if the shape was found.
				// the shape is expected to have put its factory
				// in the hashtable.

				s = parserFactories.get(name);
				if(s == null)
					throw new ParserNotCreatedException();
			}
			catch(ClassNotFoundException e)
			{
					e.printStackTrace();
				
				// We'll throw an exception to indicate that
				// the shape could not be created
				throw new ParserNotCreatedException();
			}
		}
		return s.create();
	}
}