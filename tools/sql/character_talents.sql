CREATE TABLE IF NOT EXISTS character_talents (
  charId INT NOT NULL,
  treeId VARCHAR(32) NOT NULL,
  skillId INT NOT NULL,
  level INT NOT NULL,
  PRIMARY KEY (charId, treeId, skillId)
);