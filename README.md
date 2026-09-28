# Mini Spring MVC

mini-springmvc一个基于 Servlet 的轻量 MVC 实现。项目使用 Spring 的 `spring-web` 和容器能力，但不依赖 `spring-webmvc`，基本复刻了SpringMVC的全部功能。
如果本项目能帮助到你，请给个STAR，谢谢！！！
## 已实现

- `MiniDispatcherServlet` 中央调度器，统一接收所有HTTP请求，分发到对应组件处理，并渲染响应。
- `HandlerMapping` 处理器映射器，根据请求URL映射到对应的Controller或处理方法。
- `HandlerAdapter` 处理器适配器，调用处理器的方法，完成请求的核心业务处理。
- `Handler（Controller）` 处理器，接受请求，执行具体的业务逻辑。
- `HandlerInterceptor` 拦截器，在请求处理前后执行预处理或后处理。
- `HandlerExceptionResolver` 处理器异常解析器，处理Controller抛出的异常。

## 其他说明
由于JSP已经过时，没有实现ViewResolver

## 模块

| 模块 | 内容 |
| --- | --- |
| `mvc` | MVC 框架实现 |
| `little` | 可部署到 Tomcat 的示例应用 |

## 构建和运行

需要 Java 8+、Maven 和 Tomcat。在项目根目录执行：

## 示例
```http request
GET localhost:8080/little/api/hello
```
