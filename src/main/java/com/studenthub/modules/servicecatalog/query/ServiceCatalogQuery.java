package com.studenthub.modules.servicecatalog.query;

public interface ServiceCatalogQuery {
    /** 仅返回指定校区已启用的服务项。 */
    Item enabledItem(String itemCode, Long campusId);

    record Item(Long id, String itemName) {}
}
