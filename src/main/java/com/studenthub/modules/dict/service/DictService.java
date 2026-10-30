package com.studenthub.modules.dict.service;

import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictItemUpsertRequest;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeUpsertRequest;

import java.util.List;

/**
 * 通用字典：管理端维护字典类型/字典项，App 侧按 typeCode 读取启用项。
 */
public interface DictService {

    List<DictTypeResponse> listTypes(String keyword, String status);

    DictTypeResponse createType(DictTypeUpsertRequest request);

    DictTypeResponse updateType(Long id, DictTypeUpsertRequest request);

    void deleteType(Long id);

    List<DictItemResponse> listItems(String typeCode, boolean includeDisabled);

    DictItemResponse createItem(DictItemUpsertRequest request);

    DictItemResponse updateItem(Long id, DictItemUpsertRequest request);

    void deleteItem(Long id);

    /** App 侧读取：仅启用字典的启用项，按 sortOrder 升序 */
    List<DictItemResponse> enabledItems(String typeCode);

    /** 清空 App 侧字典缓存，下一次读取重新加载。 */
    void refreshCache();
}
