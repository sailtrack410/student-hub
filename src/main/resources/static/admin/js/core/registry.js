/*
 * 管理端页面注册中心（原生 ES Module）。
 *
 * 页面脚本仍是传统 <script>：它们通过 window.AdminPages.XxxPage = {...} 注册组件。
 * 本模块用 Proxy 包一层 window.AdminPages，使「先加载的页面」与「后注册的扩展页面」
 * 都能被 app.js 统一取到；同时提供 registerPage/getPage 给 ES Module 页面使用。
 *
 * 注意：页面脚本在 index.html 中位于 core/* 之后（defer），因此注册表先于页面就绪。
 */

const existing = window.AdminPages || {};
const pages = existing;

const proxy = new Proxy(pages, {
    set: function (target, key, value) {
        target[key] = value;
        return true;
    },
    get: function (target, key) {
        return target[key];
    },
    has: function (target, key) {
        return key in target;
    }
});

/** 注册页面组件；重复注册时后者覆盖（便于热更新/扩展）。 */
export function registerPage(name, component) {
    if (!name || !component) {
        return component;
    }
    pages[name] = component;
    return component;
}

export function getPage(name) {
    return pages[name];
}

export function hasPage(name) {
    return !!pages[name];
}

export function pageNames() {
    return Object.keys(pages);
}

export function allPages() {
    const copy = {};
    pageNames().forEach(function (name) {
        copy[name] = pages[name];
    });
    return copy;
}

export const AdminRegistry = {
    registerPage: registerPage,
    getPage: getPage,
    hasPage: hasPage,
    pageNames: pageNames,
    allPages: allPages
};

/* 兼容入口：传统页面脚本继续写 window.AdminPages.*；ES Module 页面可直接 import registerPage。 */
window.AdminPages = proxy;
window.AdminRegisterPage = registerPage;
window.AdminRegistry = AdminRegistry;

export default AdminRegistry;
