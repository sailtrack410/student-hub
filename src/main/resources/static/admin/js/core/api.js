/*
 * Campus OS 管理端统一 API 客户端（原生 ES Module，无构建链）。
 *
 * 由旧 js/api.js 迁移而来，新增：
 *  - 所有 /api/admin/** 请求自动携带 X-Campus-Id（当前校区）；
 *  - 401 统一清理令牌并通知订阅者；
 *  - 统一解包 Result<T>，失败抛 ApiError（禁止静默吞错）。
 *
 * 兼容：仍向 window.AdminApi 暴露同一实例，未改造的页面脚本可继续使用。
 */
const TOKEN_KEY = 'studenthub_admin_token';
const CAMPUS_KEY = 'admin_campus_id';

export class ApiError extends Error {
    constructor(message, code, status) {
        super(message || '请求失败');
        this.name = 'ApiError';
        this.code = code || '';
        this.status = status || 0;
    }
}

export function getToken() {
    return localStorage.getItem(TOKEN_KEY) || '';
}

export function setToken(token) {
    if (token) {
        localStorage.setItem(TOKEN_KEY, token);
    } else {
        localStorage.removeItem(TOKEN_KEY);
    }
}

/** 当前校区 ID（字符串或 null）；由 core/context.js 在登录/切校区后写入。 */
export function getCampusId() {
    const value = localStorage.getItem(CAMPUS_KEY);
    return value && value !== 'null' && value !== 'undefined' ? value : null;
}

export function setCampusId(campusId) {
    if (campusId === null || campusId === undefined || campusId === '') {
        localStorage.removeItem(CAMPUS_KEY);
    } else {
        localStorage.setItem(CAMPUS_KEY, String(campusId));
    }
}

export function clearCampusId() {
    localStorage.removeItem(CAMPUS_KEY);
}

const unauthorizedHandlers = [];

export function onUnauthorized(handler) {
    if (typeof handler === 'function') {
        unauthorizedHandlers.push(handler);
    }
}

function handleUnauthorized() {
    setToken('');
    unauthorizedHandlers.forEach(function (handler) {
        try {
            handler();
        } catch (error) {
            /* 单个订阅者异常不应阻断其它订阅者 */
        }
    });
}

function isAdminPath(path) {
    return typeof path === 'string' && path.indexOf('/api/admin/') === 0;
}

function buildHeaders(method, body, options) {
    const headers = {Accept: 'application/json'};
    const token = getToken();
    if (token) {
        headers.Authorization = 'Bearer ' + token;
    }
    if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
    }
    if (!options.skipCampusHeader && isAdminPath(options.path || '')) {
        const campusId = getCampusId();
        if (campusId) {
            headers['X-Campus-Id'] = campusId;
        }
    }
    return headers;
}

function unwrap(res, payload, options) {
    if (res.status === 401) {
        if (!options.skipUnauthorized) {
            handleUnauthorized();
        }
        throw new ApiError((payload && payload.message) || '登录已过期，请重新登录', 'AUTH_401', 401);
    }
    if (!res.ok || !payload || payload.success === false) {
        throw new ApiError(
            (payload && payload.message) || ('请求失败（HTTP ' + res.status + '）'),
            payload && payload.code,
            res.status);
    }
    return payload.data;
}

function reportError(error, method, path, options) {
    if (!options.silent && error.status !== 401) {
        window.dispatchEvent(new CustomEvent('admin-api-error', {detail: {
            message: error.message || '请求失败', code: error.code || '',
            status: error.status || 0, method: method, path: path
        }}));
    }
    throw error;
}

/**
 * 发起请求并解包 Result。
 * @param {string} method HTTP 方法
 * @param {string} path 请求路径
 * @param {any} [body] JSON 请求体
 * @param {{skipUnauthorized?:boolean, skipCampusHeader?:boolean}} [options]
 * @returns {Promise<any>} Result.data
 */
export function request(method, path, body, options) {
    options = Object.assign({path: path}, options || {});
    let response;
    return fetch(path, {
        method: method,
        headers: buildHeaders(method, body, options),
        body: body === undefined ? undefined : JSON.stringify(body)
    }).then(function (res) {
        response = res;
        return res.json().catch(function () { return null; }).then(function (payload) {
            return unwrap(response, payload, options);
        });
    }, function () {
        throw new ApiError('网络异常，请检查连接后重试', 'NETWORK', 0);
    }).catch(function (error) { return reportError(error, method, path, options); });
}

export function upload(path, file, options) {
    options = Object.assign({path: path}, options || {});
    const headers = buildHeaders('POST', undefined, options);
    delete headers['Content-Type'];
    const form = new FormData();
    form.append('file', file);
    return fetch(path, {method: 'POST', headers: headers, body: form}).then(function (res) {
        return res.json().catch(function () { return null; }).then(function (payload) {
            return unwrap(res, payload, options);
        });
    }, function () {
        throw new ApiError('网络异常，请检查连接后重试', 'NETWORK', 0);
    }).catch(function (error) { return reportError(error, 'POST', path, options); });
}

export function fmtDateTime(value) {
    if (!value) return '-';
    return String(value).replace('T', ' ').slice(0, 16);
}

export const AdminApi = {
    ApiError: ApiError,
    request: request,
    get: function (path, options) { return request('GET', path, undefined, options); },
    post: function (path, body, options) { return request('POST', path, body, options); },
    put: function (path, body, options) { return request('PUT', path, body, options); },
    delete: function (path, options) { return request('DELETE', path, undefined, options); },
    upload: upload,
    getToken: getToken,
    setToken: setToken,
    getCampusId: getCampusId,
    setCampusId: setCampusId,
    clearCampusId: clearCampusId,
    onUnauthorized: onUnauthorized,
    fmtDateTime: fmtDateTime
};

/* 兼容入口：未迁移的页面脚本仍通过 window.AdminApi 访问。 */
window.AdminApi = AdminApi;

export default AdminApi;
