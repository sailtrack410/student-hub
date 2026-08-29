/* 管理台统一 API 客户端：所有请求走 Result 包装，失败必须抛错并提示，禁止静默吞掉。 */
(function () {
    'use strict';

    var TOKEN_KEY = 'studenthub_admin_token';

    function ApiError(message, code, status) {
        this.name = 'ApiError';
        this.message = message;
        this.code = code || '';
        this.status = status || 0;
    }
    ApiError.prototype = Object.create(Error.prototype);

    function getToken() {
        return localStorage.getItem(TOKEN_KEY) || '';
    }

    function setToken(token) {
        if (token) {
            localStorage.setItem(TOKEN_KEY, token);
        } else {
            localStorage.removeItem(TOKEN_KEY);
        }
    }

    var unauthorizedHandlers = [];

    function onUnauthorized(handler) {
        unauthorizedHandlers.push(handler);
    }

    function handleUnauthorized() {
        setToken('');
        unauthorizedHandlers.forEach(function (handler) { handler(); });
    }

    /**
     * 发起请求并解包 Result；401 时清除本地令牌并通知跳转登录页。
     * @returns {Promise<any>} Result.data
     */
    function request(method, path, body, options) {
        options = options || {};
        var headers = {};
        var token = getToken();
        if (token) {
            headers['Authorization'] = 'Bearer ' + token;
        }
        if (body !== undefined) {
            headers['Content-Type'] = 'application/json';
        }
        return fetch(path, {
            method: method,
            headers: headers,
            body: body === undefined ? undefined : JSON.stringify(body)
        }).then(function (res) {
            return res.json().catch(function () { return null; }).then(function (payload) {
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
            });
        }, function () {
            throw new ApiError('网络异常，请检查连接后重试', 'NETWORK', 0);
        });
    }

    function fmtDateTime(value) {
        if (!value) return '-';
        return String(value).replace('T', ' ').slice(0, 16);
    }

    window.AdminApi = {
        ApiError: ApiError,
        request: request,
        get: function (path, options) { return request('GET', path, undefined, options); },
        post: function (path, body, options) { return request('POST', path, body, options); },
        put: function (path, body, options) { return request('PUT', path, body, options); },
        delete: function (path, options) { return request('DELETE', path, undefined, options); },
        getToken: getToken,
        setToken: setToken,
        onUnauthorized: onUnauthorized,
        fmtDateTime: fmtDateTime
    };
})();
