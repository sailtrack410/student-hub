package com.studenthub.modules.dict.controller.admin;

import com.studenthub.common.Result;
import com.studenthub.modules.dict.controller.dto.DictImportResult;
import com.studenthub.modules.dict.controller.dto.DictItemResponse;
import com.studenthub.modules.dict.controller.dto.DictItemUpsertRequest;
import com.studenthub.modules.dict.controller.dto.DictTypeResponse;
import com.studenthub.modules.dict.controller.dto.DictTypeUpsertRequest;
import com.studenthub.modules.dict.service.DictCsvService;
import com.studenthub.modules.dict.service.DictService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端-字典管理：字典类型与字典项的增删改查。
 * 编码（typeCode/itemCode）创建后不可修改，内置字典不可删除；停用后 App 侧不再下发。
 */
@RestController
@RequestMapping("/api/admin/dicts")
@Tag(name = "管理端-字典管理", description = "通用字典类型与字典项维护")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('platform:all') or hasAuthority('system:dict:manage')")
public class AdminDictController {

    private final DictService dictService;
    private final DictCsvService dictCsvService;


    @GetMapping("/export")
    @Operation(summary = "导出字典 CSV", description = "导出全部字典类型与字典项，UTF-8 带 BOM 兼容 Excel；可直接用于导入。",
            operationId = "adminExportDicts")
    public ResponseEntity<byte[]> export() {
        byte[] bytes = dictCsvService.exportCsv();
        byte[] withBom = new byte[bytes.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(bytes, 0, withBom, 3, bytes.length);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dicts.csv")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(withBom);
    }

    @PostMapping("/import")
    @Operation(summary = "导入字典 CSV", description = "逐行导入/更新字典项；overwrite=true 时更新已存在项，否则跳过。",
            operationId = "adminImportDicts")
    public Result<DictImportResult> importCsv(@RequestPart("file") MultipartFile file,
                                              @RequestParam(defaultValue = "false") boolean overwrite) {
        return Result.success(dictCsvService.importCsv(file, overwrite));
    }

    @PostMapping("/cache/refresh")
    @Operation(summary = "刷新字典缓存", description = "清空服务端 App 字典缓存，下一次读取从数据库重新加载。",
            operationId = "adminRefreshDictCache")
    public Result<Void> refreshCache() {
        dictService.refreshCache();
        return Result.success();
    }

    @GetMapping("/types")
    @Operation(summary = "字典类型列表", description = "按编码/名称关键字与状态过滤，编码升序；返回 itemCount。",
            operationId = "adminListDictTypes")
    public Result<List<DictTypeResponse>> listTypes(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        return Result.success(dictService.listTypes(keyword, status));
    }

    @PostMapping("/types")
    @Operation(summary = "创建字典类型", description = "typeCode 大写字母开头、2-64 位；重复返回 400。",
            operationId = "adminCreateDictType")
    public Result<DictTypeResponse> createType(@RequestBody @Valid DictTypeUpsertRequest request) {
        return Result.success(dictService.createType(request));
    }

    @PutMapping("/types/{id}")
    @Operation(summary = "更新字典类型", description = "仅可改名称/备注/状态；typeCode 不可修改。",
            operationId = "adminUpdateDictType")
    public Result<DictTypeResponse> updateType(@PathVariable Long id,
                                               @RequestBody @Valid DictTypeUpsertRequest request) {
        return Result.success(dictService.updateType(id, request));
    }

    @DeleteMapping("/types/{id}")
    @Operation(summary = "删除字典类型", description = "内置字典禁止删除（返回 400），删除会级联删除其字典项。",
            operationId = "adminDeleteDictType")
    public Result<Void> deleteType(@PathVariable Long id) {
        dictService.deleteType(id);
        return Result.success();
    }

    @GetMapping("/types/{typeCode}/items")
    @Operation(summary = "字典项列表", description = "默认只返回启用项；includeDisabled=true 返回全部。按 sortOrder 升序。",
            operationId = "adminListDictItems")
    public Result<List<DictItemResponse>> listItems(@PathVariable String typeCode,
                                                    @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return Result.success(dictService.listItems(typeCode, includeDisabled));
    }

    @PostMapping("/items")
    @Operation(summary = "创建字典项", description = "同一字典下 itemCode 唯一；extra 非空时必须是合法 JSON。",
            operationId = "adminCreateDictItem")
    public Result<DictItemResponse> createItem(@RequestBody @Valid DictItemUpsertRequest request) {
        return Result.success(dictService.createItem(request));
    }

    @PutMapping("/items/{id}")
    @Operation(summary = "更新字典项", description = "typeCode/itemCode 不可修改；itemValue 缺省取显示名称。",
            operationId = "adminUpdateDictItem")
    public Result<DictItemResponse> updateItem(@PathVariable Long id,
                                               @RequestBody @Valid DictItemUpsertRequest request) {
        return Result.success(dictService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    @Operation(summary = "删除字典项", description = "逻辑删除，App 侧立即不再下发。",
            operationId = "adminDeleteDictItem")
    public Result<Void> deleteItem(@PathVariable Long id) {
        dictService.deleteItem(id);
        return Result.success();
    }
}
