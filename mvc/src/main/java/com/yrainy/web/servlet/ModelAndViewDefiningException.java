package com.yrainy.web.servlet;

import org.springframework.util.Assert;

import javax.servlet.ServletException;

public class ModelAndViewDefiningException extends ServletException {

	private final ModelAndView modelAndView;


	/** 创建携带指定视图模型的异常，通常用于转发到错误页面。 */
	public ModelAndViewDefiningException(ModelAndView modelAndView) {
		Assert.notNull(modelAndView, "ModelAndView must not be null in ModelAndViewDefiningException");
		this.modelAndView = modelAndView;
	}

	/** 返回此异常携带的视图模型。 */
	public ModelAndView getModelAndView() {
		return this.modelAndView;
	}

}
