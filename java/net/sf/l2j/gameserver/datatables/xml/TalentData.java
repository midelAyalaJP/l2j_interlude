package net.sf.l2j.gameserver.datatables.xml;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import net.sf.l2j.commons.data.xml.IXmlReader;
import net.sf.l2j.gameserver.model.holder.TalentSkillHolder;

import org.w3c.dom.Document;

public class TalentData implements IXmlReader
{
	private static final Logger LOGGER = Logger.getLogger(TalentData.class.getName());
	private static final String FILE = "./data/xml/custom/talentTrees.xml";
	
	private final Map<String, TalentTreeHolder> _trees = new HashMap<>();
	
	public TalentData()
	{
		load();
	}
	
	public void reload()
	{
		_trees.clear();
		load();
	}
	@Override
	public void load()
	{
		_trees.clear();
		parseFile(FILE);
		LOGGER.info("TalentData: Loaded " + _trees.size() + " talent trees.");
	}
	
	@Override
	public void parseDocument(Document doc, Path path)
	{
		forEach(doc, "list", listNode -> {
			forEach(listNode, "tree", treeNode -> {
				String id = parseString(treeNode.getAttributes(), "id", null);
				int maxPoints = parseInteger(treeNode.getAttributes(), "maxPoints", 30);
				
				TalentTreeHolder tree = new TalentTreeHolder(id, maxPoints);
				
				forEach(treeNode, "tier", tierNode -> {
					int tierLevel = parseInteger(tierNode.getAttributes(), "level", 1);
					int required = parseInteger(tierNode.getAttributes(), "requiredPoints", 0);
					
					tree.addTier(tierLevel, required);
					
					forEach(tierNode, "skill", skillNode -> {
						int skillId = parseInteger(skillNode.getAttributes(), "id", 0);
						int maxLevel = parseInteger(skillNode.getAttributes(), "maxLevel", 1);
						
						tree.addSkill(tierLevel, new TalentSkillHolder(skillId, maxLevel, tierLevel));
					});
				});
				
				_trees.put(id, tree);
			});
		});
	}
	
	public TalentTreeHolder getTree(String id)
	{
		return _trees.get(id);
	}
	
	public static TalentData getInstance()
	{
		return SingletonHolder.INSTANCE;
	}
	
	private static class SingletonHolder
	{
		protected static final TalentData INSTANCE = new TalentData();
	}

}