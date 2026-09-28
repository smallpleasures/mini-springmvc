/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yrainy.web.servlet.mvc.condition;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 表示 {@code "name!=value"} 格式表达式的接口，用于在 {@code @RequestMapping} 中声明请求参数和请求头条件。
 *
 * @author Rossen Stoyanchev
 * @since 3.1
 * @param <T> 表达式值的类型
 * @see RequestMapping#params()
 * @see RequestMapping#headers()
 */
public interface NameValueExpression<T> {

	String getName();
	T getValue();

	boolean isNegated();

}
