package net.sf.l2j.gameserver.model.holder;

public class TalentSkillHolder
{
    private final int _skillId;
    private final int _maxLevel;
    private final int _tier;

    public TalentSkillHolder(int skillId, int maxLevel, int tier)
    {
        _skillId = skillId;
        _maxLevel = maxLevel;
        _tier = tier;
    }

    public int getSkillId()
    {
        return _skillId;
    }

    public int getMaxLevel()
    {
        return _maxLevel;
    }

    public int getTier()
    {
        return _tier;
    }
}