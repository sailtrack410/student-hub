package com.studenthub.modules.identity.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.studenthub.common.ErrorCode;
import com.studenthub.exception.BusinessException;
import com.studenthub.modules.identity.campus.AdminCampusContextService;
import com.studenthub.modules.identity.controller.dto.AdminUserImportResult;
import com.studenthub.modules.identity.controller.dto.AdminUserImportRow;
import com.studenthub.modules.identity.entity.OrganizationEntity;
import com.studenthub.modules.identity.entity.RoleEntity;
import com.studenthub.modules.identity.entity.UserEntity;
import com.studenthub.modules.identity.entity.UserRoleEntity;
import com.studenthub.modules.identity.mapper.OrganizationMapper;
import com.studenthub.modules.identity.mapper.RoleMapper;
import com.studenthub.modules.identity.mapper.UserMapper;
import com.studenthub.modules.identity.mapper.UserRoleMapper;
import com.studenthub.modules.identity.service.AdminUserImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户批量导入（U07）：CSV 模板 / 预览 / 同步导入。
 *
 * <p>同步实现说明：解析、校验、落库都在一次请求内完成，不创建异步任务、不写任务表、不做失败重试队列。
 * 逐行结果直接返回；上限 500 行、2MB，超限拒绝而不是静默截断。CSV 必须为 UTF-8
 * （可以带 BOM，Excel「CSV UTF-8」另存即可）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserImportServiceImpl implements AdminUserImportService {

    private static final int MAX_ROWS = 500;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final Set<String> IDENTITY_TYPES = Set.of("STUDENT", "TEACHER", "LOGISTICS", "ADMIN");
    private static final List<String> HEADERS = List.of(
            "username", "displayname", "phone", "email", "studentno", "college", "identitytype",
            "organizationcode", "rolecodes");
    private static final String TEMPLATE = "\uFEFF"
            + "username,displayName,phone,email,studentNo,college,identityType,organizationCode,roleCodes\n"
            + "2024010001,张同学,13800000001,zhang@example.com,2024010001,计算机学院,STUDENT,COLLEGE_CS,REQUESTER\n"
            + "t1001,王老师,13800000002,,T1001,教务处,TEACHER,OFFICE_ACADEMIC,REQUESTER\n";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OrganizationMapper organizationMapper;
    private final UserRoleMapper userRoleMapper;
    private final AdminCampusContextService campusContextService;

    @Override
    public byte[] templateCsv() {
        return TEMPLATE.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public AdminUserImportResult preview(MultipartFile file, String strategy) {
        return importInternal(null, file, strategy, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AdminUserImportResult execute(Long operatorId, MultipartFile file, String strategy) {
        return importInternal(operatorId, file, strategy, false);
    }

    private AdminUserImportResult importInternal(Long operatorId, MultipartFile file, String strategy, boolean dryRun) {
        String normalizedStrategy = normalizeStrategy(strategy);
        List<PreparedRow> preparedRows = parseAndPrepare(file, normalizedStrategy);

        int created = 0;
        int updated = 0;
        int skipped = 0;
        int failed = 0;
        List<AdminUserImportRow> results = new ArrayList<>(preparedRows.size());
        for (PreparedRow row : preparedRows) {
            if (row.error() != null) {
                failed++;
                results.add(new AdminUserImportRow(row.lineNo(), row.username(), row.displayName(),
                        "ERROR", row.error(), null));
                continue;
            }
            if ("SKIP".equals(row.action())) {
                skipped++;
                results.add(new AdminUserImportRow(row.lineNo(), row.username(), row.displayName(),
                        "SKIP", row.message(), row.existingUser() == null ? null : row.existingUser().getId()));
                continue;
            }
            if (dryRun) {
                if ("CREATE".equals(row.action())) {
                    created++;
                } else {
                    updated++;
                }
                results.add(new AdminUserImportRow(row.lineNo(), row.username(), row.displayName(),
                        row.action(), row.message(), null));
                continue;
            }
            try {
                Long userId = writeRow(operatorId, row, normalizedStrategy);
                if ("CREATE".equals(row.action())) {
                    created++;
                } else {
                    updated++;
                }
                results.add(new AdminUserImportRow(row.lineNo(), row.username(), row.displayName(),
                        row.action(), row.message(), userId));
            } catch (RuntimeException exception) {
                failed++;
                log.warn("用户导入单行失败, lineNo={}, username={}", row.lineNo(), row.username());
                results.add(new AdminUserImportRow(row.lineNo(), row.username(), row.displayName(),
                        "ERROR", "写入失败：" + rootMessage(exception), null));
            }
        }
        AdminUserImportResult result = new AdminUserImportResult(normalizedStrategy, dryRun,
                preparedRows.size(), created, updated, skipped, failed, results);
        log.info("用户导入完成, dryRun={}, strategy={}, total={}, created={}, updated={}, skipped={}, failed={}, operatorId={}",
                dryRun, normalizedStrategy, result.total(), created, updated, skipped, failed, operatorId);
        return result;
    }

    private Long writeRow(Long operatorId, PreparedRow row, String strategy) {
        UserEntity user = row.existingUser();
        Long userId;
        if (user == null) {
            UserEntity entity = new UserEntity();
            entity.setCampusId(campusContextService.currentCampusId());
            entity.setUsername(row.username());
            entity.setDisplayName(row.displayName());
            entity.setPhone(row.rawPhone());
            entity.setEmail(row.rawEmail());
            entity.setStudentNo(row.rawStudentNo());
            entity.setCollege(row.rawCollege());
            entity.setIdentityType(row.identityType());
            entity.setStatus("ENABLED");
            entity.setAuthorizationStatus("BASIC");
            entity.setAccountType("NORMAL");
            entity.setOfficial(false);
            entity.setVerificationStatus("NONE");
            entity.setDefaultOrganizationId(row.organization() == null ? null : row.organization().getId());
            userMapper.insert(entity);
            userId = entity.getId();
        } else {
            userId = user.getId();
            UserEntity update = new UserEntity();
            update.setId(user.getId());
            update.setDisplayName(row.displayName());
            update.setPhone(row.rawPhone());
            update.setEmail(row.rawEmail());
            update.setStudentNo(row.rawStudentNo());
            update.setCollege(row.rawCollege());
            update.setIdentityType(row.identityType());
            if (row.organization() != null) {
                update.setDefaultOrganizationId(row.organization().getId());
            }
            update.setVersion(user.getVersion());
            userMapper.updateById(update);
        }
        if (!row.roles().isEmpty()) {
            Set<Long> existingRoleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                            .eq(UserRoleEntity::getUserId, userId))
                    .stream().map(UserRoleEntity::getRoleId).collect(Collectors.toSet());
            for (RoleEntity role : row.roles()) {
                if (existingRoleIds.contains(role.getId())) {
                    continue;
                }
                UserRoleEntity binding = new UserRoleEntity();
                binding.setUserId(userId);
                binding.setRoleId(role.getId());
                binding.setOrganizationId(row.organization() == null
                        ? (user == null ? null : user.getDefaultOrganizationId())
                        : row.organization().getId());
                binding.setDataScope("SELF");
                binding.setCreatedBy(operatorId == null ? 0L : operatorId);
                userRoleMapper.insert(binding);
            }
        }
        return userId;
    }

    private List<PreparedRow> parseAndPrepare(MultipartFile file, String strategy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "请上传 CSV 文件");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文件过大，请控制在 2MB 以内");
        }
        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "文件读取失败，请重试");
        }
        if (content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        String[] lines = content.split("\r\n|\n|\r", -1);
        int headerIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (StringUtils.hasText(lines[i])) {
                headerIndex = i;
                break;
            }
        }
        if (headerIndex < 0) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "CSV 内容为空");
        }
        List<String> header = parseCsvLine(lines[headerIndex]).stream()
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .toList();
        Map<String, Integer> headerIndexes = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            headerIndexes.putIfAbsent(header.get(i), i);
        }
        if (!headerIndexes.containsKey("username") || !headerIndexes.containsKey("displayname")) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                    "CSV 缺少必需列：username、displayName；请下载模板后填写");
        }

        Long campusId = campusContextService.currentCampusId();
        // 校区组织 + 校级/跨校区组织（campus_id 为空）都可作为用户默认组织。
        Map<String, OrganizationEntity> organizationsByCode = organizationMapper.selectList(
                        new LambdaQueryWrapper<OrganizationEntity>()
                                .eq(OrganizationEntity::getStatus, "ENABLED")
                                .eq(OrganizationEntity::getDeleted, 0)
                                .and(wrapper -> wrapper.eq(OrganizationEntity::getCampusId, campusId)
                                        .or().isNull(OrganizationEntity::getCampusId)))
                .stream().collect(Collectors.toMap(
                        organization -> organization.getOrganizationCode().toUpperCase(Locale.ROOT),
                        Function.identity(), (left, right) -> left));
        Map<String, RoleEntity> rolesByCode = roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getStatus, "ENABLED"))
                .stream().collect(Collectors.toMap(
                        role -> role.getRoleCode().toUpperCase(Locale.ROOT), Function.identity(),
                        (left, right) -> left));
        // 只按文件中出现的用户名查重（避免无条件扫全表）；username 全局唯一，跨校区命中需拦截
        Set<String> usernamesInFile = new java.util.HashSet<>();
        for (int i = headerIndex + 1; i < lines.length && usernamesInFile.size() <= MAX_ROWS; i++) {
            if (!StringUtils.hasText(lines[i])) {
                continue;
            }
            String candidate = cell(parseCsvLine(lines[i]), headerIndexes, "username").trim();
            if (StringUtils.hasText(candidate)) {
                usernamesInFile.add(candidate);
            }
        }
        Map<String, UserEntity> existingUsers = usernamesInFile.isEmpty() ? Map.of()
                : userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .in(UserEntity::getUsername, usernamesInFile))
                .stream().collect(Collectors.toMap(
                        user -> user.getUsername().toLowerCase(Locale.ROOT), Function.identity(),
                        (left, right) -> left));

        List<PreparedRow> rows = new ArrayList<>();
        Set<String> seenUsernames = new java.util.HashSet<>();
        for (int i = headerIndex + 1; i < lines.length; i++) {
            if (!StringUtils.hasText(lines[i])) {
                continue;
            }
            if (rows.size() >= MAX_ROWS) {
                throw new BusinessException(ErrorCode.INVALID_PARAMETER,
                        "单次最多导入 " + MAX_ROWS + " 行，请拆分文件后重试");
            }
            int lineNo = i + 1;
            List<String> cells = parseCsvLine(lines[i]);
            String username = cell(cells, headerIndexes, "username").trim();
            String displayName = cell(cells, headerIndexes, "displayname").trim();
            if (!StringUtils.hasText(username)) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "用户名不能为空"));
                continue;
            }
            String usernameKey = username.toLowerCase(Locale.ROOT);
            if (!seenUsernames.add(usernameKey)) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "文件内用户名重复：" + username));
                continue;
            }
            String error = validateUsername(username);
            if (error != null) {
                rows.add(PreparedRow.error(lineNo, username, displayName, error));
                continue;
            }
            if (!StringUtils.hasText(displayName)) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "昵称不能为空"));
                continue;
            }
            if (displayName.length() > 128) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "昵称超过 128 字"));
                continue;
            }
            String phone = trimToNull(cell(cells, headerIndexes, "phone"));
            if (phone != null && !phone.matches("\\d{5,20}")) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "手机号格式不合法：" + phone));
                continue;
            }
            String email = trimToNull(cell(cells, headerIndexes, "email"));
            if (email != null && (!email.contains("@") || email.length() > 128)) {
                rows.add(PreparedRow.error(lineNo, username, displayName, "邮箱格式不合法：" + email));
                continue;
            }
            String identityType = trimToNull(cell(cells, headerIndexes, "identitytype"));
            identityType = identityType == null ? "STUDENT" : identityType.toUpperCase(Locale.ROOT);
            if (!IDENTITY_TYPES.contains(identityType)) {
                rows.add(PreparedRow.error(lineNo, username, displayName,
                        "身份类型不合法：" + identityType + "（可选 STUDENT/TEACHER/LOGISTICS/ADMIN）"));
                continue;
            }
            String organizationCode = trimToNull(cell(cells, headerIndexes, "organizationcode"));
            OrganizationEntity organization = null;
            if (organizationCode != null) {
                organization = organizationsByCode.get(organizationCode.toUpperCase(Locale.ROOT));
                if (organization == null) {
                    rows.add(PreparedRow.error(lineNo, username, displayName,
                            "组织编码不存在或已停用：" + organizationCode));
                    continue;
                }
            }
            List<RoleEntity> roles = new ArrayList<>();
            String roleCodes = trimToNull(cell(cells, headerIndexes, "rolecodes"));
            if (roleCodes != null) {
                boolean roleError = false;
                for (String rawCode : roleCodes.split("[;|]")) {
                    String roleCode = rawCode.trim();
                    if (roleCode.isEmpty()) {
                        continue;
                    }
                    RoleEntity role = rolesByCode.get(roleCode.toUpperCase(Locale.ROOT));
                    if (role == null) {
                        rows.add(PreparedRow.error(lineNo, username, displayName,
                                "角色编码不存在或已停用：" + roleCode));
                        roleError = true;
                        break;
                    }
                    roles.add(role);
                }
                if (roleError) {
                    continue;
                }
            }
            UserEntity existing = existingUsers.get(usernameKey);
            if (existing != null && existing.getCampusId() != null && !existing.getCampusId().equals(campusId)) {
                rows.add(PreparedRow.error(lineNo, username, displayName,
                        "用户名已被其他校区账号占用：" + username));
                continue;
            }
            String action;
            String message;
            if (existing == null) {
                action = "CREATE";
                message = "将新建用户";
            } else if ("UPDATE".equals(strategy)) {
                action = "UPDATE";
                message = "用户已存在，将覆盖资料字段（角色仅追加不删除）";
            } else {
                action = "SKIP";
                message = "用户已存在，按 SKIP 策略跳过";
            }
            rows.add(new PreparedRow(lineNo, username, displayName, phone, email,
                    trimToNull(cell(cells, headerIndexes, "studentno")),
                    trimToNull(cell(cells, headerIndexes, "college")),
                    identityType, organization, roles, existing, action, message, null));
        }
        if (rows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "CSV 中没有可导入的数据行");
        }
        return rows;
    }

    private String normalizeStrategy(String strategy) {
        String value = strategy == null || strategy.isBlank() ? "SKIP" : strategy.trim().toUpperCase(Locale.ROOT);
        if (!"SKIP".equals(value) && !"UPDATE".equals(value)) {
            throw new BusinessException(ErrorCode.INVALID_PARAMETER, "冲突策略仅支持 SKIP/UPDATE");
        }
        return value;
    }

    private String validateUsername(String username) {
        if (username.length() < 3 || username.length() > 32) {
            return "用户名长度需为 3-32 位";
        }
        if (!username.matches("[A-Za-z0-9_.-]+")) {
            return "用户名仅允许字母、数字、下划线、点、短横线";
        }
        return null;
    }

    private String cell(List<String> cells, Map<String, Integer> headerIndexes, String key) {
        Integer index = headerIndexes.get(key);
        if (index == null || index >= cells.size()) {
            return "";
        }
        return cells.get(index) == null ? "" : cells.get(index);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    /**
     * 极简 CSV 行解析：支持双引号包裹、引号内逗号与双引号转义（""）。
     */
    static List<String> parseCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
                continue;
            }
            if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    /**
     * 校验通过、待写入（或仅预览）的一行。
     *
     * <p>error 非空表示校验失败；action 为 CREATE/UPDATE/SKIP。</p>
     */
    private record PreparedRow(
            int lineNo,
            String username,
            String displayName,
            String rawPhone,
            String rawEmail,
            String rawStudentNo,
            String rawCollege,
            String identityType,
            OrganizationEntity organization,
            List<RoleEntity> roles,
            UserEntity existingUser,
            String action,
            String message,
            String error) {

        static PreparedRow error(int lineNo, String username, String displayName, String error) {
            return new PreparedRow(lineNo, username, displayName, null, null, null, null,
                    null, null, List.of(), null, "ERROR", null, error);
        }
    }
}
