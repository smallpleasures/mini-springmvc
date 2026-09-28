package com.yrainy.web.servlet.mvc.condition;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/** 使用 Spring Web 的路径匹配器匹配 URL 模式。 */
public final class PatternsRequestCondition extends AbstractRequestCondition<PatternsRequestCondition> {

	private final SortedSet<String> patterns;
	private final UrlPathHelper urlPathHelper;
	private final PathMatcher pathMatcher;
	private final boolean suffixPatternMatch;
	private final boolean trailingSlashMatch;
	private final List<String> fileExtensions;

	public PatternsRequestCondition(String... patterns) {
		this(patterns, null, null, false, true, Collections.emptyList());
	}

	public PatternsRequestCondition(String[] patterns, UrlPathHelper urlPathHelper,
			PathMatcher pathMatcher, boolean suffixPatternMatch, boolean trailingSlashMatch,
			List<String> fileExtensions) {
		TreeSet<String> values = new TreeSet<>();
		if (patterns.length == 0 || (patterns.length == 1 && !StringUtils.hasText(patterns[0]))) {
			values.add("");
		}
		else {
			for (String pattern : patterns) {
				String value = (StringUtils.hasText(pattern) ? pattern.trim() : "");
				values.add(value.isEmpty() || value.startsWith("/") ? value : "/" + value);
			}
		}
		this.patterns = Collections.unmodifiableSortedSet(values);
		this.urlPathHelper = (urlPathHelper != null ? urlPathHelper : new UrlPathHelper());
		this.pathMatcher = pathMatcher;
		this.suffixPatternMatch = suffixPatternMatch;
		this.trailingSlashMatch = trailingSlashMatch;
		this.fileExtensions = (fileExtensions != null ? fileExtensions : Collections.emptyList());
	}

	private PatternsRequestCondition(SortedSet<String> patterns, PatternsRequestCondition source) {
		this.patterns = Collections.unmodifiableSortedSet(patterns);
		this.urlPathHelper = source.urlPathHelper;
		this.pathMatcher = source.pathMatcher;
		this.suffixPatternMatch = source.suffixPatternMatch;
		this.trailingSlashMatch = source.trailingSlashMatch;
		this.fileExtensions = source.fileExtensions;
	}

	public Set<String> getPatterns() {
		return this.patterns;
	}

	public Set<String> getDirectPaths() {
		Set<String> paths = new LinkedHashSet<>();
		for (String pattern : this.patterns) {
			if (!this.pathMatcher.isPattern(pattern)) {
				paths.add(pattern);
			}
		}
		return paths;
	}

	public boolean isEmptyPathMapping() {
		return this.patterns.size() == 1 && this.patterns.contains("");
	}

	@Override
	protected Collection<String> getContent() {
		return this.patterns;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	@Override
	public PatternsRequestCondition combine(PatternsRequestCondition other) {
		if (isEmptyPathMapping()) {
			return other;
		}
		if (other.isEmptyPathMapping()) {
			return this;
		}
		SortedSet<String> combined = new TreeSet<>();
		for (String first : this.patterns) {
			for (String second : other.patterns) {
				combined.add(this.pathMatcher.combine(first, second));
			}
		}
		return new PatternsRequestCondition(combined, this);
	}

	@Override
	public PatternsRequestCondition getMatchingCondition(HttpServletRequest request) {
		String lookupPath = this.urlPathHelper.getLookupPathForRequest(request);
		List<String> matches = new ArrayList<>();
		for (String pattern : this.patterns) {
			if (matches(pattern, lookupPath)) {
				matches.add(pattern);
			}
		}
		if (matches.isEmpty()) {
			return null;
		}
		matches.sort(this.pathMatcher.getPatternComparator(lookupPath));
		return new PatternsRequestCondition(new TreeSet<>(matches), this);
	}

	private boolean matches(String pattern, String path) {
		if (this.pathMatcher.match(pattern, path)) {
			return true;
		}
		if (this.trailingSlashMatch && !pattern.isEmpty()) {
			if (pattern.endsWith("/") && this.pathMatcher.match(pattern.substring(0, pattern.length() - 1), path)) {
				return true;
			}
			if (!pattern.endsWith("/") && this.pathMatcher.match(pattern + "/", path)) {
				return true;
			}
		}
		if (this.suffixPatternMatch && !pattern.isEmpty()) {
			String suffixPattern = pattern + ".*";
			if (this.fileExtensions.isEmpty() || this.fileExtensions.stream().anyMatch(path::endsWith)) {
				return this.pathMatcher.match(suffixPattern, path);
			}
		}
		return pattern.isEmpty() && (path.isEmpty() || "/".equals(path));
	}

	@Override
	public int compareTo(PatternsRequestCondition other, HttpServletRequest request) {
		String path = this.urlPathHelper.getLookupPathForRequest(request);
		Comparator<String> comparator = this.pathMatcher.getPatternComparator(path);
		Iterator<String> first = this.patterns.iterator();
		Iterator<String> second = other.patterns.iterator();
		while (first.hasNext() && second.hasNext()) {
			int result = comparator.compare(first.next(), second.next());
			if (result != 0) {
				return result;
			}
		}
		return first.hasNext() ? -1 : (second.hasNext() ? 1 : 0);
	}
}
