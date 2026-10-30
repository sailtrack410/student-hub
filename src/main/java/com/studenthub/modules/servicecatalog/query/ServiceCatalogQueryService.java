package com.studenthub.modules.servicecatalog.query;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.modules.servicecatalog.entity.ServiceItemEntity;
import com.studenthub.modules.servicecatalog.mapper.ServiceItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ServiceCatalogQueryService implements ServiceCatalogQuery {
    private final ServiceItemMapper mapper;

    @Override
    public Item enabledItem(String itemCode, Long campusId) {
        if (campusId == null || itemCode == null || itemCode.isBlank()) return null;
        ServiceItemEntity item = mapper.selectOne(new LambdaQueryWrapper<ServiceItemEntity>()
                .select(ServiceItemEntity::getId, ServiceItemEntity::getItemName)
                .eq(ServiceItemEntity::getItemCode, itemCode)
                .eq(ServiceItemEntity::getCampusId, campusId)
                .eq(ServiceItemEntity::getStatus, "ENABLED")
                .last("LIMIT 1"));
        return item == null ? null : new Item(item.getId(), item.getItemName());
    }
}
