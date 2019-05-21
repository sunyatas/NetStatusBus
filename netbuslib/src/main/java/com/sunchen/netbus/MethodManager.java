package com.sunchen.netbus;

/**
 * Created by 「孙晨」 on 2019/4/2   22:28.
 * <p>
 * God bless me only
 * <p>
 * MethodManager
 */
import com.sunchen.netbus.type.Mode;
import java.lang.reflect.Method;

/**
 * 保存符合要求的网络监听注解方法
 */
public class MethodManager {

    //参数类型
    private Class<?> parameterClazz;

    //订阅类型
    private Mode mode;

    //需要执行的方法
    private Method method;

    public MethodManager(Class<?> clazz, Mode mode, Method method) {
        this.parameterClazz = clazz;
        this.mode = mode;
        this.method = method;
    }

    public Class<?> getParameterClazz() {
        return parameterClazz;
    }

    public void setParameterClazz(Class<?> parameterClazz) {
        this.parameterClazz = parameterClazz;
    }

    public Mode getMode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }
}
