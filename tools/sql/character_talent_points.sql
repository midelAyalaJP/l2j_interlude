CREATE TABLE IF NOT EXISTS character_talent_points (
  charId INT NOT NULL,
  treeId VARCHAR(32) NOT NULL,
  points INT NOT NULL,
  PRIMARY KEY (charId, treeId)
);