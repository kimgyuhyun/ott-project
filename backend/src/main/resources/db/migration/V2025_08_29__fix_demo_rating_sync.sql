-- Sync demo aggregates on anime from actual ratings data

-- 1) Set aggregates to 0 for titles without any ratings
UPDATE anime a
SET rating = 0.0,
    rating_count = 0
WHERE NOT EXISTS (
    SELECT 1 FROM ratings r WHERE r.ani_id = a.id
);

-- 2) For titles with ratings, recompute average (rounded to 1 decimal) and count
WITH agg AS (
    SELECT ani_id,
           ROUND(AVG(score)::numeric, 1)::float8 AS avg_score,
           COUNT(*)::int                         AS cnt
    FROM ratings
    GROUP BY ani_id
)
UPDATE anime a
SET rating = COALESCE(agg.avg_score, 0.0),
    rating_count = COALESCE(agg.cnt, 0)
FROM agg
WHERE a.id = agg.ani_id;


