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
    (SELECT COUNT(*) FROM events WHERE eventType = 'remarkCreated' AND user = s.user AND session = s.session) AS createdRemarkCount,
    (SELECT tool FROM events WHERE user = s.user AND session = s.session LIMIT 1) AS tool,
    (SELECT value FROM propertyInEvent WHERE eventType = 'reviewEnded' AND name = 'endTransition' AND user = s.user AND session = s.session) AS endType,
    (SELECT COUNT(*) FROM events WHERE eventType = 'launch' AND user = s.user AND session = s.session) AS launchCount,
    (SELECT MAX(size) FROM filterEffectiveness WHERE filterName = 'ignore changes in imports' AND user = s.user AND session = s.session) AS importFilterSize,
    (SELECT MAX(size) FROM filterEffectiveness WHERE filterName = 'ignore whitespace only changes' AND user = s.user AND session = s.session) AS whitespaceFilterSize
    FROM sessions AS s;
	
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

CREATE OR REPLACE VIEW recentCortUsage AS (SELECT realReviewer, COUNT(*) AS anz, MAX(startTime) AS zeit FROM `reviewsJiraAndCort` WHERE realStartTime > DATE_SUB(NOW(), INTERVAL 21 day) AND cortKey IS NOT NULL GROUP BY realReviewer);

CREATE OR REPLACE VIEW recentCortNonUsage AS (SELECT realReviewer, COUNT(*) AS anz, MAX(startTime) AS zeit FROM `reviewsJiraAndCort` WHERE realStartTime > DATE_SUB(NOW(), INTERVAL 21 day) AND cortKey IS NULL GROUP BY realReviewer);
	
CREATE OR REPLACE VIEW toolNutzung AS
	SELECT r.realReviewer AS rev, mit.anz AS anzMit, mit.zeit AS zeitMit, ohne.anz AS anzOhne, ohne.zeit AS zeitOhne, (mit.zeit > ohne.zeit OR ohne.zeit IS NULL) AS mitIstNeuer
	FROM reviewers AS r
	LEFT JOIN recentCortUsage AS mit ON r.realReviewer = mit.realReviewer
	LEFT JOIN recentCortNonUsage AS ohne ON r.realReviewer = ohne.realReviewer
	WHERE mit.anz IS NOT NULL OR ohne.anz IS NOT NULL
	ORDER BY anzMit, anzMit + anzOhne;
