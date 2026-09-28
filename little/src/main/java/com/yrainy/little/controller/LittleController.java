package com.yrainy.little.controller;

import com.yrainy.little.dto.DemoRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 演示 Mini Spring MVC 的路由、参数解析、DTO 请求体和 JSON 响应扩展点。 */
@RestController
@RequestMapping("/api")
public class LittleController {

	/** 演示查询参数绑定与 JSON 响应。 */
	@GetMapping("/hello")
	public Map<String, Object> hello(@RequestParam(value = "name", defaultValue = "world") String name) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("message", "hello, " + name);
		return result;
	}

	/** 演示路径变量绑定。 */
	@GetMapping("/users/{id}")
	public Map<String, Object> user(@PathVariable("id") String id) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("id", id);
		return result;
	}

	/** 演示 JSON DTO 反序列化、请求体缓存、请求体通知和自定义参数解析器。 */
	@PostMapping(value = "/echo", consumes = "application/json", produces = "application/json")
	public Map<String, Object> echo(@RequestBody DemoRequest body) {
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("request", body);
		return result;
	}


}
