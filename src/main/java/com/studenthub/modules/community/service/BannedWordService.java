package com.studenthub.modules.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.community.entity.CommunityBannedWordEntity;
import com.studenthub.modules.community.mapper.CommunityBannedWordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 屏蔽词匹配：内容发布/评论时命中即自动隐藏（不人工审核）。
 * 词表按校区缓存在内存，管理端增删改后调用 {@link #invalidate(Long)} 刷新。
 *
 * <p>V71 增强：支持 LITERAL（字面量）与 REGEX（正则）两种匹配方式，命中后累计 hit_count/last_hit_at，
 * 用于管理端命中统计与误伤排查。正则仅允许安全子集：长度 ≤ 200、禁止嵌套量词与环视，
 * 避免 ReDoS 拖垮发布接口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BannedWordService {

    /** 正则最大长度：超出直接拒绝，降低灾难性回溯概率 */
    public static final int MAX_REGEX_LENGTH = 200;
    /** 单段文本最大匹配长度：超长正文截断后再匹配，避免大文本配合复杂正则长时间占用 CPU */
    private static final int MAX_MATCH_TEXT_LENGTH = 20_000;

    private final CommunityBannedWordMapper bannedWordMapper;
    private final Map<Long, List<Rule>> cache = new ConcurrentHashMap<>();

    /**
     * 返回命中的第一个屏蔽词（未命中返回 null）；匹配标题、正文与文章块文本。
     * 命中时累计命中次数（统计失败不影响内容发布）。
     */
    public String firstHit(Long campusId, String... texts) {
        List<Rule> rules = rules(campusId);
        if (rules.isEmpty()) {
            return null;
        }
        for (String raw : texts) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String text = raw.length() > MAX_MATCH_TEXT_LENGTH ? raw.substring(0, MAX_MATCH_TEXT_LENGTH) : raw;
            for (Rule rule : rules) {
                if (rule.matches(text)) {
                    recordHit(rule.id());
                    return rule.word();
                }
            }
        }
        return null;
    }

    public List<String> words(Long campusId) {
        return rules(campusId).stream().map(Rule::word).toList();
    }

    public void invalidate(Long campusId) {
        if (campusId == null) {
            cache.clear();
        } else {
            cache.remove(campusId);
        }
    }

    /**
     * 正则安全校验：可编译、长度受限、禁止嵌套量词 / 环视等高价构造。
     * 校验失败抛出 400，避免把「看起来能存但会打挂接口」的正则写进词表。
     */
    public static void validatePattern(String regex) {
        if (!StringUtils.hasText(regex)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正则表达式不能为空");
        }
        if (regex.length() > MAX_REGEX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "正则表达式不能超过 " + MAX_REGEX_LENGTH + " 个字符");
        }
        if (regex.contains("(?=") || regex.contains("(?!") || regex.contains("(?<")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正则不支持环视/命名分组等高级构造");
        }
        if (regex.matches(".*\\([^)]*[+*][^)]*\\)[+*{].*")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正则不允许嵌套量词（如 (a+)+）");
        }
        try {
            Pattern.compile(regex);
        } catch (PatternSyntaxException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "正则语法错误：" + exception.getDescription());
        }
    }

    private void recordHit(Long wordId) {
        if (wordId == null) {
            return;
        }
        try {
            bannedWordMapper.update(null, new LambdaUpdateWrapper<CommunityBannedWordEntity>()
                    .eq(CommunityBannedWordEntity::getId, wordId)
                    .setSql("hit_count = COALESCE(hit_count, 0) + 1")
                    .set(CommunityBannedWordEntity::getLastHitAt, LocalDateTime.now()));
        } catch (Exception exception) {
            // 统计失败不影响内容发布
            log.warn("屏蔽词命中统计失败, wordId={}, reason={}", wordId, exception.getMessage());
        }
    }

    private List<Rule> rules(Long campusId) {
        return cache.computeIfAbsent(campusId == null ? 0L : campusId, this::load);
    }

    private List<Rule> load(Long campusId) {
        List<CommunityBannedWordEntity> entities = bannedWordMapper.selectList(
                new LambdaQueryWrapper<CommunityBannedWordEntity>()
                        .eq(CommunityBannedWordEntity::getCampusId, campusId)
                        .eq(CommunityBannedWordEntity::getStatus, "ENABLED"));
        List<Rule> rules = new ArrayList<>();
        for (CommunityBannedWordEntity entity : entities) {
            if (!StringUtils.hasText(entity.getWord())) {
                continue;
            }
            String word = entity.getWord().trim();
            if ("REGEX".equalsIgnoreCase(entity.getMatchType())) {
                try {
                    rules.add(new Rule(entity.getId(), word, Pattern.compile(word)));
                } catch (PatternSyntaxException exception) {
                    log.warn("屏蔽词正则不可用已跳过, wordId={}, word={}", entity.getId(), word);
                }
            } else {
                rules.add(new Rule(entity.getId(), word, null));
            }
        }
        log.info("屏蔽词表已加载, campusId={}, size={}", campusId, rules.size());
        return rules;
    }

    /** 单条规则：pattern 为 null 表示字面量包含匹配 */
    private record Rule(Long id, String word, Pattern pattern) {
        boolean matches(String text) {
            if (pattern == null) {
                return text.contains(word);
            }
            try {
                return pattern.matcher(text).find();
            } catch (Exception exception) {
                // 运行期异常（如栈溢出）按未命中处理，不能让内容发布失败
                return false;
            }
        }
    }
}
