package com.yrainy.little.dto;

/** 接收 {@code @RequestBody} JSON 的示例 DTO。 */
public class DemoRequest {

	private String name;
	private String message;

	public DemoRequest() {
	}

	public String getName() {
		return this.name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getMessage() {
		return this.message;
	}

	public void setMessage(String message) {
		this.message = message;
	}
}
