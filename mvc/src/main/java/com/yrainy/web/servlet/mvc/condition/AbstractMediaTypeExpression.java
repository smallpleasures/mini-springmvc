package com.yrainy.web.servlet.mvc.condition;

import org.springframework.http.MediaType;

/** 保存媒体类型及其取反标记，并提供通用的特异度比较。 */
abstract class AbstractMediaTypeExpression implements MediaTypeExpression,
		Comparable<AbstractMediaTypeExpression> {

	private final MediaType mediaType;
	private final boolean negated;

	AbstractMediaTypeExpression(String expression) {
		String value = expression.trim();
		this.negated = value.startsWith("!");
		this.mediaType = MediaType.parseMediaType(this.negated ? value.substring(1).trim() : value);
	}

	AbstractMediaTypeExpression(MediaType mediaType, boolean negated) {
		this.mediaType = mediaType;
		this.negated = negated;
	}

	@Override
	public MediaType getMediaType() {
		return this.mediaType;
	}

	@Override
	public boolean isNegated() {
		return this.negated;
	}

	@Override
	public int compareTo(AbstractMediaTypeExpression other) {
		int result = this.mediaType.compareTo(other.mediaType);
		return (result != 0 ? result : Boolean.compare(this.negated, other.negated));
	}

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (other == null || getClass() != other.getClass()) {
			return false;
		}
		AbstractMediaTypeExpression that = (AbstractMediaTypeExpression) other;
		return this.negated == that.negated && this.mediaType.equals(that.mediaType);
	}

	@Override
	public int hashCode() {
		return 31 * this.mediaType.hashCode() + (this.negated ? 1 : 0);
	}

	@Override
	public String toString() {
		return (this.negated ? "!" : "") + this.mediaType;
	}
}
