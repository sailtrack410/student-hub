package com.studenthub.modules.dict.provider;

import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;

import java.util.List;

/**
 * 系统内置字典提供者：数据已经在业务表中维护（如勋章目录），字典管理页只做统一只读展示，
 * 避免把同一份数据复制到 sys_dict_type 后出现两处维护、互相覆盖。
 *
 * <p>只读字典由 {@link DictTypeResponse#readonly()} 标记，管理端不允许新增/编辑/删除字典项；
 * 实际维护入口仍在对应业务页面（例如“成就管理”）。</p>
 */
public interface SystemDictProvider {

    /** 该提供者是否负责此 typeCode。 */
    boolean supports(String typeCode);

    /** 该提供者负责的全部字典类型（itemCount 实时统计）。 */
    List<DictTypeResponse> listTypes();

    /** 按 typeCode 返回字典项；{@code includeDisabled=false} 时只返回启用项。 */
    List<DictItemResponse> listItems(String typeCode, boolean includeDisabled);
}
