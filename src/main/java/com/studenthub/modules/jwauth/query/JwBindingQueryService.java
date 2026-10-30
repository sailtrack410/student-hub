package com.studenthub.modules.jwauth.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.jwauth.entity.JwBindingEntity;
import com.studenthub.modules.jwauth.mapper.JwBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JwBindingQueryService implements JwBindingQuery {
    private final JwBindingMapper mapper;

    @Override
    public String lastSemesterCode(Long userId) {
        if (userId == null) return null;
        JwBindingEntity binding = mapper.selectOne(new LambdaQueryWrapper<JwBindingEntity>()
                .select(JwBindingEntity::getLastSemesterCode)
                .eq(JwBindingEntity::getUserId, userId)
                .last("LIMIT 1"));
        return binding == null ? null : binding.getLastSemesterCode();
    }
}
