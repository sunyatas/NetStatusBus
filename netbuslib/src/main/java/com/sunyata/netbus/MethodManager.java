package com.sunyata.netbus;

/**
 * Created by 「孙晨」 on 2019/4/2   22:28.
 * <p>
 * God bless me only
 * <p>
 * MethodManager
 */

import com.sunyata.netbus.type.NetType;

import java.lang.reflect.Method;

/**
 * 保存符合要求的网络监听注解方法
 */
public class MethodManager {

    //参数类型
    private Class<?> parameterClazz;

//    网络类型
    private NetType annotationNetType;

//    需要这执行的方法
    private Method method;

    public MethodManager(Class<?> clazz, NetType netType, Method method) {
        this.parameterClazz = clazz;
        this.annotationNetType = netType;
        this.method = method;
    }



    public Class<?> getParameterClazz() {
        return parameterClazz;
    }

    public void setParameterClazz(Class<?> parameterClazz) {
        this.parameterClazz = parameterClazz;
    }

    public NetType getAnnotationNetType() {
        return annotationNetType;
    }

    public void setAnnotationNetType(NetType annotationNetType) {
        this.annotationNetType = annotationNetType;
    }

    public Method getMethod() {
        return method;
    }

    public void setMethod(Method method) {
        this.method = method;
    }
}
