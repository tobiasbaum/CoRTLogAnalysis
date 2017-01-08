CREATE OR REPLACE VIEW propertyNames AS SELECT DISTINCT e.eventType, p.name FROM events AS e LEFT JOIN properties AS p ON e.time = p.time AND e.user = p.user;

CREATE OR REPLACE VIEW propertyInEvent AS SELECT e.*, p.name, p.value FROM events AS e INNER JOIN properties AS p ON p.time = e.time AND p.user = e.user;

CREATE OR REPLACE VIEW filterEffectiveness AS SELECT 
    e.time,
    e.user,
    e.session,
    de.value AS filterName,
    CAST(si.value AS SIGNED) AS size
    FROM events AS e
    INNER JOIN properties AS de ON e.time = de.time AND e.user = de.user
    INNER JOIN properties AS si ON e.time = si.time AND e.user = si.user
    WHERE de.name = 'description'
    AND si.name = 'size'
    AND e.eventType = 'relevanceFilterResult';

CREATE OR REPLACE VIEW sessionsExt AS SELECT s.*, 
    UNIX_TIMESTAMP(endTime) - UNIX_TIMESTAMP(startTime) AS duration,
    (SELECT COUNT(*) FROM events WHERE eventType = 'remarkCreated' AND user = s.user AND session = s.session) AS createdRemarkCount,
    (SELECT tool FROM events WHERE user = s.user AND session = s.session LIMIT 1) AS tool,
    (SELECT value FROM propertyInEvent WHERE eventType = 'reviewEnded' AND name = 'endTransition' AND user = s.user AND session = s.session ORDER BY time DESC LIMIT 1) AS endType,
    (SELECT COUNT(*) FROM events WHERE eventType = 'launch' AND user = s.user AND session = s.session) AS launchCount,
    (SELECT MAX(size) FROM filterEffectiveness WHERE filterName = 'ignore changes in imports' AND user = s.user AND session = s.session) AS importFilterSize,
    (SELECT MAX(size) FROM filterEffectiveness WHERE filterName = 'ignore whitespace only changes' AND user = s.user AND session = s.session) AS whitespaceFilterSize,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'reviewStarted' AND name = 'cntTours' AND user = s.user AND session = s.session) AS tourCount,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'originalTourStructure' AND name = 'cntTours' AND user = s.user AND session = s.session LIMIT 1) AS tourCountOriginal,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'reviewStarted' AND name = 'cntStopsRel' AND user = s.user AND session = s.session) AS stopCountRel,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'originalTourStructure' AND name = 'cntStops' AND user = s.user AND session = s.session LIMIT 1) AS stopCountOriginal,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'reviewStarted' AND name = 'cntAddedLinesRel' AND user = s.user AND session = s.session) AS addedLinesRel,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'originalTourStructure' AND name = 'cntAddedLines' AND user = s.user AND session = s.session LIMIT 1) AS addedLinesOriginal,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'reviewStarted' AND name = 'cntRemovedLinesRel' AND user = s.user AND session = s.session) AS removedLinesRel,
    (SELECT CAST(value AS SIGNED) FROM propertyInEvent WHERE eventType = 'originalTourStructure' AND name = 'cntRemovedLines' AND user = s.user AND session = s.session LIMIT 1) AS removedLinesOriginal
    FROM sessions AS s;
    
CREATE OR REPLACE VIEW reviewsCort AS
   SELECT
   `key`,
   `round`,
   `user`,
   MIN(startTime) AS startTime,
   MAX(endTime) AS endTime,
   SUM(duration) AS duration,
   SUM(createdRemarkCount) AS createdRemarkCount,
   SUM(launchCount) AS launchCount,
   MAX(tourCountOriginal) AS tourCountOriginal,
   MAX(stopCountOriginal) AS stopCountOriginal,
   MAX(addedLinesOriginal) AS addedLinesOriginal,
   MAX(removedLinesOriginal) AS removedLinesOriginal,
   MAX(tourCount) AS tourCount,
   MAX(stopCountRel) AS stopCountRel,
   MAX(addedLinesRel) AS addedLinesRel,
   MAX(removedLinesRel) AS removedLinesRel,
   MAX(addedLinesRel) / SUM(duration) * 3600 AS reviewRate,
   FLOOR(MAX(addedLinesRel) / SUM(duration) * 36) AS reviewRateRange,
   SUM(createdRemarkCount) / SUM(duration) * 3600 AS reviewEfficiency,
   COUNT(*) AS sessionCount
   FROM sessionsExt
   WHERE `type` = 'R'
   GROUP BY `key`,`round`,`user`;
	
CREATE OR REPLACE VIEW reviewsJiraAndCort AS 
	SELECT j.`key` AS realKey, j.reviewer AS realReviewer, j.startTime AS realStartTime, j.*, c.key AS cortKey, COUNT(*) AS sessionCount 
	FROM reviewsJira AS j
	LEFT JOIN sessions AS c
	ON j.`key` = c.`key` AND j.round = c.round AND c.`type` = 'R'
	GROUP BY j.`key`, c.`key`
	UNION ALL
	SELECT c.`key` AS realKey, c.`user` AS realReviewer, j.*, c.startTime AS realStartTime, c.key AS cortKey, COUNT(*) AS sessionCount 
	FROM reviewsJira AS j
	RIGHT JOIN sessions AS c
	ON j.`key` = c.`key` AND j.round = c.round AND c.`type` = 'R'
	WHERE j.`key` IS NULL
	GROUP BY j.`key`, c.`key`;
	
CREATE OR REPLACE VIEW reviewers AS (SELECT DISTINCT realReviewer FROM `reviewsJiraAndCort`);

CREATE OR REPLACE VIEW recentCortUsage AS (SELECT realReviewer, COUNT(*) AS anz, MAX(startTime) AS zeit FROM `reviewsJiraAndCort` WHERE realStartTime > DATE_SUB(NOW(), INTERVAL 40 day) AND cortKey IS NOT NULL GROUP BY realReviewer);

CREATE OR REPLACE VIEW recentCortNonUsage AS (SELECT realReviewer, COUNT(*) AS anz, MAX(startTime) AS zeit FROM `reviewsJiraAndCort` WHERE realStartTime > DATE_SUB(NOW(), INTERVAL 40 day) AND cortKey IS NULL GROUP BY realReviewer);
	
CREATE OR REPLACE VIEW toolNutzung AS
	SELECT r.realReviewer AS rev, mit.anz AS anzMit, mit.zeit AS zeitMit, ohne.anz AS anzOhne, ohne.zeit AS zeitOhne, (mit.zeit > ohne.zeit OR ohne.zeit IS NULL) AS mitIstNeuer
	FROM reviewers AS r
	LEFT JOIN recentCortUsage AS mit ON r.realReviewer = mit.realReviewer
	LEFT JOIN recentCortNonUsage AS ohne ON r.realReviewer = ohne.realReviewer
	WHERE mit.anz IS NOT NULL OR ohne.anz IS NOT NULL
	ORDER BY anzMit, anzMit + anzOhne;

CREATE OR REPLACE VIEW navigationTracesUnordered AS
	select e.*, (select p.value from properties as p where e.time = p.time and e.user = p.user and p.name = 'files') as files, '' as jumpType, '' as line from `events` as e where e.eventType = 'activeFilesChanged'
	union all
	select e.*, (select p.value from properties as p where e.time = p.time and e.user = p.user and p.name = 'resource') as res, (select p.value from properties as p where e.time = p.time and e.user = p.user and p.name = 'type') as jumpType, (select p.value from properties as p where e.time = p.time and e.user = p.user and p.name = 'line') as line from `events` as e where e.eventType = 'jumpedTo';
	
CREATE OR REPLACE VIEW navigationTraces AS
	select * from navigationTracesUnordered order by `user`, `session`, `time`
