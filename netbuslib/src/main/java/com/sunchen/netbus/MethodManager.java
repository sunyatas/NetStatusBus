package com.sunchen.netbus;

import com.sunchen.netbus.type.Mode;

import java.lang.reflect.Method;

/**
 * Holds one validated {@code @NetSubscribe} method: its parameter type, {@link Mode}, and
 * reflective {@link Method}.
 * <p>
 * Internal to the library; not part of the supported public API.
 * <p>
 * 保存一份已校验的 {@code @NetSubscribe} 方法（参数类型、订阅模式、反射 Method）。库内部使用，非公开 API。
 */
final class MethodManager {

    /** Parameter type of the subscribe method; {@code null} when the method takes no args. */
    private final Class<?> parameterClazz;

    private final Mode mode;

    private final Method method;

    MethodManager(Class<?> clazz, Mode mode, Method method) {
        this.parameterClazz = clazz;
        this.mode = mode;
        this.method = method;
    }

    Class<?> getParameterClazz() {
        return parameterClazz;
    }

    Mode getMode() {
        return mode;
    }

    Method getMethod() {
        return method;
    }
}
