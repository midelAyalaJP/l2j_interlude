package net.sf.l2j.gameserver.datatables.xml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sf.l2j.gameserver.model.holder.TalentSkillHolder;

public class TalentTreeHolder
{
    private final String _id;
    private final int _maxPoints;

    private final Map<Integer, Integer> _tierRequiredPoints = new HashMap<>();
    private final Map<Integer, List<TalentSkillHolder>> _skillsByTier = new HashMap<>();

    public TalentTreeHolder(String id, int maxPoints)
    {
        _id = id;
        _maxPoints = maxPoints;
    }

    public void addTier(int tierLevel, int requiredPoints)
    {
        _tierRequiredPoints.put(tierLevel, requiredPoints);
        _skillsByTier.putIfAbsent(tierLevel, new ArrayList<>());
    }

    public void addSkill(int tier, TalentSkillHolder skill)
    {
        _skillsByTier.computeIfAbsent(tier, k -> new ArrayList<>()).add(skill);
    }

    public String getId()
    {
        return _id;
    }

    public int getMaxPoints()
    {
        return _maxPoints;
    }

    public int getRequiredPointsForTier(int tier)
    {
        return _tierRequiredPoints.getOrDefault(tier, 0);
    }

    public Map<Integer, List<TalentSkillHolder>> getSkillsByTier()
    {
        return _skillsByTier;
    }
    
    public TalentSkillHolder findSkill(int skillId)
    {
        for (List<TalentSkillHolder> list : _skillsByTier.values())
        {
            for (TalentSkillHolder s : list)
            {
                if (s.getSkillId() == skillId)
                    return s;
            }
        }
        return null;
    }
}