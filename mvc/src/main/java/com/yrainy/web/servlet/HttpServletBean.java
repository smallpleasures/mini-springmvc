package com.yrainy.web.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.*;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceEditor;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.web.context.support.ServletContextResourceLoader;
import org.springframework.web.context.support.StandardServletEnvironment;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import java.util.Enumeration;

/** 将 Servlet 初始化参数绑定到属性，并提供 Spring 环境访问能力。 */
public abstract class HttpServletBean extends HttpServlet implements EnvironmentCapable, EnvironmentAware {

    protected final Log logger = LogFactory.getLog(getClass());

    private ConfigurableEnvironment environment;

    @Override
    public final void init() throws ServletException {
        // 解析 init-param 并封装成 pvs (xml)
        PropertyValues pvs = new ServletConfigPropertyValues(getServletConfig());
        if (!pvs.isEmpty()) {
            try {
                // 将当前的这个 Servlet 类转化为一个 BeanWrapper，从而能够以 Spring 的方法来对 init-param 的值进行注入
                BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(this);
                ResourceLoader resourceLoader = new ServletContextResourceLoader(getServletContext());
                bw.registerCustomEditor(Resource.class, new ResourceEditor(resourceLoader, getEnvironment()));
                // 属性注入
                bw.setPropertyValues(pvs, true);
            }
            catch (BeansException ex) {
                if (logger.isErrorEnabled()) {
                    logger.error("Failed to set bean properties on servlet '" + getServletName() + "'", ex);
                }
                throw ex;
            }
        }

        // 调用子类的 Servlet 初始化逻辑。
        // 初始化Servlet，创建Spring容器
        initServletBean();
    }

    protected abstract void initServletBean() throws ServletException;

    @Override
    public void setEnvironment(Environment environment) {
        Assert.isInstanceOf(ConfigurableEnvironment.class, environment, "ConfigurableEnvironment required");
        this.environment = (ConfigurableEnvironment) environment;
    }

    @Override
    public ConfigurableEnvironment getEnvironment() {
        if (this.environment == null) {
            this.environment = new StandardServletEnvironment();
        }
        return this.environment;
    }

    private static class ServletConfigPropertyValues extends MutablePropertyValues {

        public ServletConfigPropertyValues(ServletConfig config) {

            Enumeration<String> paramNames = config.getInitParameterNames();
            while (paramNames.hasMoreElements()) {
                String property = paramNames.nextElement();
                Object value = config.getInitParameter(property);
                addPropertyValue(new PropertyValue(property, value));
            }
        }
    }
}
