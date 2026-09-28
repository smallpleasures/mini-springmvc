package com.yrainy.web.servlet;

import org.springframework.http.HttpStatus;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;

import java.util.Map;

public class ModelAndView {

	/** 视图实例或视图名称。 */
	private Object view;

	/** 视图模型数据。 */
	private ModelMap model;

	/** 可选的 HTTP 响应状态。 */
	private HttpStatus status;

	/** 标记对象是否已通过 {@link #clear()} 清空。 */
	private boolean cleared = false;


	/** 默认构造方法，供属性注入方式创建对象。 */
	public ModelAndView() {
	}

	/** 创建仅包含视图名称的视图模型，可随后调用 {@code addObject} 添加数据。 */
	public ModelAndView(String viewName) {
		this.view = viewName;
	}

	/** 创建仅包含视图实例的视图模型，可随后调用 {@code addObject} 添加数据。 */
	public ModelAndView(View view) {
		this.view = view;
	}

	/** 使用视图名称和模型数据创建视图模型。 */
	public ModelAndView(String viewName, Map<String, ?> model) {
		this.view = viewName;
		if (model != null) {
			getModelMap().addAllAttributes(model);
		}
	}

	/** 使用视图实例和模型数据创建视图模型；模型数据会复制到内部存储。 */
	public ModelAndView(View view, Map<String, ?> model) {
		this.view = view;
		if (model != null) {
			getModelMap().addAllAttributes(model);
		}
	}

	/** 使用视图名称和 HTTP 状态创建视图模型。 */
	public ModelAndView(String viewName, HttpStatus status) {
		this.view = viewName;
		this.status = status;
	}

	/** 使用视图名称、模型数据和 HTTP 状态创建视图模型。 */
	public ModelAndView(String viewName, Map<String, ?> model, HttpStatus status) {
		this.view = viewName;
		if (model != null) {
			getModelMap().addAllAttributes(model);
		}
		this.status = status;
	}

	/** 使用视图名称和单个模型属性创建视图模型。 */
	public ModelAndView(String viewName, String modelName, Object modelObject) {
		this.view = viewName;
		addObject(modelName, modelObject);
	}

	/** 使用视图实例和单个模型属性创建视图模型。 */
	public ModelAndView(View view, String modelName, Object modelObject) {
		this.view = view;
		addObject(modelName, modelObject);
	}


	/** 设置视图名称，并覆盖已有的视图引用。 */
	public void setViewName(String viewName) {
		this.view = viewName;
	}

	/** 返回视图名称；当前使用视图实例时返回 {@code null}。 */
	public String getViewName() {
		return (this.view instanceof String ? (String) this.view : null);
	}

	/** 设置视图实例，并覆盖已有的视图引用。 */
	public void setView(View view) {
		this.view = view;
	}

	/** 返回视图实例；当前使用视图名称时返回 {@code null}。 */
	public View getView() {
		return (this.view instanceof View ? (View) this.view : null);
	}

	/** 判断是否已设置视图名称或视图实例。 */
	public boolean hasView() {
		return (this.view != null);
	}

	/** 判断当前视图是否通过名称引用。 */
	public boolean isReference() {
		return (this.view instanceof String);
	}

	/** 返回内部模型映射，供分发器读取模型数据。 */
	protected Map<String, Object> getModelInternal() {
		return this.model;
	}

	/** 返回内部模型映射；尚未创建时会创建一个空映射。 */
	public ModelMap getModelMap() {
		if (this.model == null) {
			this.model = new ModelMap();
		}
		return this.model;
	}

	/** 返回模型映射，供应用代码读取或修改模型数据。 */
	public Map<String, Object> getModel() {
		return getModelMap();
	}

	/** 设置待返回的 HTTP 状态。 */
	public void setStatus(HttpStatus status) {
		this.status = status;
	}

	/** 返回已配置的 HTTP 状态。 */
	public HttpStatus getStatus() {
		return this.status;
	}


	/** 向模型添加指定名称和值的属性。 */
	public ModelAndView addObject(String attributeName, Object attributeValue) {
		getModelMap().addAttribute(attributeName, attributeValue);
		return this;
	}

	/** 向模型添加属性，并根据对象类型生成属性名称。 */
	public ModelAndView addObject(Object attributeValue) {
		getModelMap().addAttribute(attributeValue);
		return this;
	}

	/** 将给定映射中的所有属性添加到模型。 */
	public ModelAndView addAllObjects(Map<String, ?> modelMap) {
		getModelMap().addAllAttributes(modelMap);
		return this;
	}


	/** 清空视图和模型数据，可在拦截器中用来取消本次视图渲染。 */
	public void clear() {
		this.view = null;
		this.model = null;
		this.cleared = true;
	}

	/** 判断对象是否既没有视图也没有模型数据。 */
	public boolean isEmpty() {
		return (this.view == null && CollectionUtils.isEmpty(this.model));
	}

	/** 判断对象是否已清空且之后未重新添加视图或模型数据。 */
	public boolean wasCleared() {
		return (this.cleared && isEmpty());
	}


	/** 返回视图模型的诊断信息。 */
	@Override
	public String toString() {
		return "ModelAndView [view=" + formatView() + "; model=" + this.model + "]";
	}

	private String formatView() {
		return isReference() ? "\"" + this.view + "\"" : "[" + this.view + "]";
	}

}
