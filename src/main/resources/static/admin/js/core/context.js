/*
 * 管理端校区上下文（原生 ES Module）。
 *
 * 数据源：GET /api/admin/context
 *   {userId,displayName,identityType,roles,platformAdmin,campusId,campusName,
 *    campuses:[{campusId,campusCode,campusName,status}]}
 *
 * 职责：
 *  - 登录后 / 旧 token 刷新页面时加载上下文；
 *  - 维护当前校区，并把 campusId 写入 localStorage（key: admin_campus_id），
 *    由 core/api.js 在每个 /api/admin/** 请求上带 X-Campus-Id；
 *  - 切换校区（仅平台管理员），失败时保留既有用户信息并给出 message，不白屏。
 *
 * 与 Vue 解耦：订阅者通过 subscribe() 接收快照。
 */
import { AdminApi } from './api.js';

const CAMPUS_KEY = 'admin_campus_id';

function emptyState() {
    return {
        loaded: false,
        loading: false,
        error: null,
        userId: null,
        displayName: '',
        identityType: '',
        roles: [],
        platformAdmin: false,
        campusId: null,
        campusName: '',
        campuses: []
    };
}

const state = emptyState();
const listeners = [];

function clone(value) {
    return JSON.parse(JSON.stringify(value === undefined ? null : value));
}

export function snapshot() {
    return {
        loaded: state.loaded,
        loading: state.loading,
        error: state.error,
        userId: state.userId,
        displayName: state.displayName,
        identityType: state.identityType,
        roles: state.roles.slice(),
        platformAdmin: state.platformAdmin,
        campusId: state.campusId,
        campusName: state.campusName,
        campuses: clone(state.campuses) || []
    };
}

function emit() {
    const current = snapshot();
    listeners.slice().forEach(function (listener) {
        try {
            listener(current);
        } catch (error) {
            /* 订阅者异常不影响上下文本身 */
        }
    });
}

export function subscribe(listener) {
    if (typeof listener !== 'function') {
        return function () {};
    }
    listeners.push(listener);
    return function unsubscribe() {
        const index = listeners.indexOf(listener);
        if (index >= 0) {
            listeners.splice(index, 1);
        }
    };
}

/** 当前校区 ID；优先内存态，其次 localStorage。 */
export function getCampusId() {
    if (state.campusId !== null && state.campusId !== undefined) {
        return state.campusId;
    }
    const stored = AdminApi.getCampusId();
    return stored === null ? null : stored;
}

/** 当前校区展示名；未加载完成时给出安全占位。 */
export function getCampusLabel() {
    return state.campusName || '校区上下文';
}

export function getCampuses() {
    return clone(state.campuses) || [];
}

export function isPlatformAdmin() {
    return state.platformAdmin === true;
}

export function getError() {
    return state.error;
}

function applyContext(data) {
    state.loaded = true;
    state.loading = false;
    state.error = null;
    state.userId = data && data.userId !== undefined ? data.userId : null;
    state.displayName = (data && data.displayName) || '';
    state.identityType = (data && data.identityType) || '';
    state.roles = (data && data.roles) || [];
    state.platformAdmin = !!(data && data.platformAdmin);
    state.campusId = data && data.campusId !== undefined ? data.campusId : null;
    state.campusName = (data && data.campusName) || '';
    state.campuses = (data && data.campuses) || [];
    if (state.campusId !== null && state.campusId !== undefined) {
        AdminApi.setCampusId(state.campusId);
    }
    emit();
}

/**
 * 加载上下文。
 * 若本地缓存的校区已失效/越权（后端 403），自动清除缓存并用默认校区重试一次，
 * 避免普通管理员因历史缓存被锁死在错误校区。
 * @returns {Promise<object>} 上下文快照
 */
export function load() {
    if (state.loading && load.$inflight) {
        return load.$inflight;
    }
    state.loading = true;
    state.error = null;
    emit();

    function fetchContext() {
        return AdminApi.get('/api/admin/context');
    }

    load.$inflight = fetchContext()
        .catch(function (error) {
            /* 缓存校区可能已删除/停用（400）或越权（403）：清缓存后用账号默认校区重试一次。 */
            const recoverable = error && (
                error.status === 400 || error.status === 403 || error.status === 404
                || error.code === 'AUTH_403' || error.code === 'FORBIDDEN'
                || error.code === 'COMMON_400');
            if (!recoverable) {
                throw error;
            }
            AdminApi.clearCampusId();
            return fetchContext();
        })
        .then(function (data) {
            applyContext(data || {});
            return snapshot();
        })
        .catch(function (error) {
            state.loading = false;
            state.error = (error && error.message) || '校区上下文加载失败';
            emit();
            throw error;
        })
        .finally(function () {
            load.$inflight = null;
        });
    return load.$inflight;
}

/**
 * 切换校区（仅保存选择与本地状态，是否刷新页面由调用方决定）。
 * 非平台管理员只允许停留在自己所属校区。
 * @returns {boolean} 是否切换成功
 */
export function setCampus(campusId) {
    if (campusId === null || campusId === undefined) {
        return false;
    }
    const target = state.campuses.filter(function (item) {
        return String(item.campusId) === String(campusId);
    })[0];
    if (!target) {
        return false;
    }
    if (!state.platformAdmin && String(target.campusId) !== String(state.campusId)) {
        return false;
    }
    state.campusId = target.campusId;
    state.campusName = target.campusName || '';
    AdminApi.setCampusId(target.campusId);
    emit();
    return true;
}

export function reset() {
    const next = emptyState();
    Object.keys(next).forEach(function (key) {
        state[key] = next[key];
    });
    AdminApi.clearCampusId();
    emit();
}

/** 切换校区后设置一次性提示，刷新回来仍能看到成功反馈。 */
export function flashCampusSwitch(campusName) {
    try {
        sessionStorage.setItem('admin_campus_flash', campusName || '');
    } catch (error) {
        /* 隐私模式忽略 */
    }
}

export function consumeCampusFlash() {
    try {
        const value = sessionStorage.getItem('admin_campus_flash');
        if (value) {
            sessionStorage.removeItem('admin_campus_flash');
            return value;
        }
    } catch (error) {
        /* 隐私模式忽略 */
    }
    return '';
}

export const AdminContext = {
    CAMPUS_KEY: CAMPUS_KEY,
    /* 只读属性形式，便于调用方直接读取 context.campusLabel / campuses / platformAdmin。 */
    load: load,
    reset: reset,
    snapshot: snapshot,
    subscribe: subscribe,
    getCampusId: getCampusId,
    getCampusLabel: getCampusLabel,
    getCampuses: getCampuses,
    isPlatformAdmin: isPlatformAdmin,
    getError: getError,
    setCampus: setCampus,
    flashCampusSwitch: flashCampusSwitch,
    consumeCampusFlash: consumeCampusFlash
};

Object.defineProperties(AdminContext, {
    campusLabel: {enumerable: true, get: getCampusLabel},
    campusId: {enumerable: true, get: getCampusId},
    campuses: {enumerable: true, get: getCampuses},
    platformAdmin: {enumerable: true, get: isPlatformAdmin},
    loaded: {enumerable: true, get: function () { return state.loaded === true; }},
    error: {enumerable: true, get: getError}
});

window.AdminContext = AdminContext;

export default AdminContext;
