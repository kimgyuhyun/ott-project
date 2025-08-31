-- Update all video URLs from cdn.example.com to Google test videos
-- This migration replaces all hardcoded dummy video URLs with actual test videos for development

-- 1. Update all episodes with cdn.example.com URLs to use Google test videos
UPDATE episodes 
SET video_url = 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4'
WHERE video_url LIKE '%cdn.example.com%';

-- 2. Update thumbnail URLs to use placeholder images (optional)
UPDATE episodes 
SET thumbnail_url = 'https://placehold.co/120x80/3b82f6/ffffff?text=Episode+' || episode_number
WHERE thumbnail_url LIKE '%cdn.example.com%';

-- 3. Log the number of updated episodes
-- This will be visible in the migration logs
SELECT 'Updated ' || COUNT(*) || ' episodes with test video URLs' AS migration_result
FROM episodes 
WHERE video_url = 'http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4';
