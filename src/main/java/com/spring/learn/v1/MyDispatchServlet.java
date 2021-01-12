package com.spring.learn.v1;

import com.spring.annotation.AnAutoWired;
import com.spring.annotation.AnController;
import com.spring.annotation.AnRequestMapping;
import com.spring.annotation.AnRequestParam;
import com.spring.annotation.AnService;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Author yanGe
 * @Date 2021/1/11 17:36
 **/
public class MyDispatchServlet extends HttpServlet {

    //自定义配置文件格式
    private Properties properties = new Properties();

    private List<String> classNames = new ArrayList<>();

    private HashMap<String, Object> ioc = new HashMap<>();

    private HashMap<String,Method> handleMapping = new HashMap<>();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        this.doPost(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        //6.委派 根据url 找到一个method 并通过response返回
        try {
            doDispatcher(req, resp);
        } catch (Exception e) {
            resp.getWriter().write("500"+ Arrays.toString(e.getStackTrace()));
        }

    }

    private void doDispatcher(HttpServletRequest req, HttpServletResponse resp)
        throws IOException, InvocationTargetException, IllegalAccessException {

        String url = req.getRequestURI();
        String contextPath = req.getContextPath();
        url = url.replaceAll(contextPath,"").replaceAll("/+","/");
        if(!this.handleMapping.containsKey(url)){
            resp.getWriter().write("404 Not Found!!");
            return;
        }
        Map<String,String[]> params =  req.getParameterMap();
        Method method = handleMapping.get(url);
        //获取形参列表
        Class<?> [] parameterTypes = method.getParameterTypes();
        Object [] paramValues = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            Class paramterType = parameterTypes[i];
            if(paramterType == HttpServletRequest.class){
                paramValues[i] = req;
            }else if(paramterType == HttpServletResponse.class){
                paramValues[i] = resp;
            }else if(paramterType == String.class){
                //通过运行时的状态去拿到你
                Annotation[] [] pa = method.getParameterAnnotations();
                for (int j = 0; j < pa.length ; j ++) {
                    for(Annotation a : pa[j]){
                        if(a instanceof AnRequestParam){
                            String paramName = ((AnRequestParam) a).value();
                            if(!"".equals(paramName.trim())){
                                String value = Arrays.toString(params.get(paramName))
                                    .replaceAll("\\[|\\]","")
                                    .replaceAll("\\s+",",");
                                paramValues[i] = value;
                            }
                        }
                    }
                }
            }
        }
        //获取调用方法
        String beanName = method.getDeclaringClass().getSimpleName();
        method.invoke(ioc.get(toLowerFirstCase(beanName)),paramValues);
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        System.out.println("myFrameWork is init start");
        // 1、加载配置文件
        doLoadConfig(config.getInitParameter("contextConfigLocation"));

        //2、扫描相关类 通过配合文件拿到扫描包路径
        doScanner(properties.getProperty("scanPackage"));

        //3.初始化IOC容器，将扫描的类注实例化,保存到IOC容器  新生成代理对象
        doInstance();
        //-------------------加入AOP---------------
        //4。完成依赖注入
        doAutoWired();
        //5.初始化HandleMapping
        doInitHandleMapping();
        // 6.init over
        System.out.println("myFrameWork is init over");

    }

    private void doInitHandleMapping() {
        if (ioc.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            Class<?> clazz = entry.getValue().getClass();
            if (!clazz.isAnnotationPresent(AnController.class)) {
                continue;
            }
            String baseUrl = "";
            if (clazz.isAnnotationPresent(AnRequestMapping.class)) {
                AnRequestMapping requestMapping = clazz.getAnnotation(AnRequestMapping.class);
                baseUrl = requestMapping.value();
            }
            //只取public方法
            for (Method method : entry.getValue().getClass().getMethods()) {
                if (!method.isAnnotationPresent(AnRequestMapping.class)) {
                    continue;
                }
                AnRequestMapping requestMapping = method.getAnnotation(AnRequestMapping.class);
                //多个斜杠 替换为一个
                String url =("/"+ baseUrl +"/"+ requestMapping.value()).replaceAll("/+","/");
                handleMapping.put(url,method);
                System.out.println("Mapped : " + url + "," + method);
            }
        }
    }

    private void doAutoWired() {
        if (ioc.isEmpty()) {
            return;
        }
        //Field是一个类,位于java.lang.reflect包下。在Java反射中Field类描述的是类的属性信息，功能包括：
        //获取当前对象的成员变量的类型
        //对成员变量重新设值

        for (Map.Entry<String, Object> entry : ioc.entrySet()) {
            for (Field field : entry.getValue().getClass().getDeclaredFields()) {
                if (!field.isAnnotationPresent(AnAutoWired.class)) {
                    continue;
                }
                //如果没用自定义的beanName ，默认根据类型注入
                String beanName = field.getAnnotation(AnAutoWired.class).value().trim();
                if ("".equals(beanName)) {
                    //获取字段的类型
                    beanName = field.getType().getName();
                }
                //暴力访问 private 注入
                field.setAccessible(true);

                try {
                    //覆盖属性注入  通过接口的全名拿到接口实现类的实例
                    field.set(entry.getValue(), ioc.get(beanName));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void doInstance() {
        if (classNames.isEmpty()) {
            return;
        }
        try {
            for (String className : classNames) {
                //得到class类
                Class<?> clazz = Class.forName(className);
                //加了注解控制权才反转
                if (clazz.isAnnotationPresent(AnController.class)) {
                    // 类名 不包含路径
                    String beanName = toLowerFirstCase(clazz.getSimpleName());
                    ioc.put(beanName, clazz.newInstance());
                } else if (clazz.isAnnotationPresent(AnService.class)) {

                    //1、多个包下出现相同类名、只能自己去取名 全局唯一，自定义命名 抛异常
                    //读取自定义命名
                    String beanName = clazz.getAnnotation(AnService.class).value();
                    if (beanName.trim().equals("")) {
                        beanName = toLowerFirstCase(clazz.getSimpleName());
                    }

                    //2、默认的类名首字母小写
                    ioc.put(beanName, clazz.newInstance());

                    //3.如果接口有多个实现类 ，则抛异常，如果只有一个则默认选择一个

                    for (Class<?> i : clazz.getInterfaces()) {
                        if (ioc.containsKey(i.getName())) {
                            throw new Exception("接口存在多个实现类");
                        }
                        //接口全类名
                        ioc.put(i.getName(), clazz.newInstance());
                    }

                } else {
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void doScanner(String scanPackage) {
        URL url = this.getClass().getClassLoader().getResource(scanPackage.replaceAll("\\.", "/"));
        File classPath = new File(url.getFile());
        //扫描路径(文件夹)下所有文件
        for (File file : classPath.listFiles()) {
            if (file.isDirectory()) {
                doScanner(scanPackage + "." + file.getName());
            } else {
                if (!file.getName().endsWith(".class")) {
                    continue;
                }
                String className = scanPackage + "." + file.getName().replace(".class", "");
                //得到className 通过反射 得到实例对象
                classNames.add(className);
            }
        }
    }

    private void doLoadConfig(String contextConfigLocation) {
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(contextConfigLocation);
        try {
            properties.load(is);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (null != is) {
                try {
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private String toLowerFirstCase(String simpleName) {
        char[] chars = simpleName.toCharArray();
        chars[0] += 32;
        return String.valueOf(chars);
    }
}
