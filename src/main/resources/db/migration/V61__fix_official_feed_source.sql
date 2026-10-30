-- 教学管理栏目为部门导航页（无 /info/ 文章列表），改为收费公示栏目；
-- 收费公示同样由学校官网发布，适合作为官方通知来源。
UPDATE official_feed_source
SET source_name = '收费公示',
    list_url = 'https://www.mycc.edu.cn/xxgong_ka/sfgs.htm',
    author_id = 990000000000000001
WHERE id = 8003;
