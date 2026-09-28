
package com.yrainy.web.servlet.mvc.condition;

import org.springframework.http.server.PathContainer;
import org.springframework.util.StringUtils;
import org.springframework.web.util.ServletRequestPathUtils;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 将请求与一组 URL 路径模式进行逻辑或匹配的请求条件。
 *
 * <p>与 {@link PatternsRequestCondition} 不同，此条件使用解析后的 {@link PathPattern}，
 * 而不是 {@link org.springframework.util.AntPathMatcher AntPathMatcher} 字符串匹配。
 *
 * @author Rossen Stoyanchev
 * @since 5.3
 */
public final class PathPatternsRequestCondition extends AbstractRequestCondition<PathPatternsRequestCondition> {

	private static final SortedSet<PathPattern> EMPTY_PATH_PATTERN =
			new TreeSet<>(Collections.singleton(new PathPatternParser().parse("")));

	private static final Set<String> EMPTY_PATH = Collections.singleton("");


	private final SortedSet<PathPattern> patterns;


	/**
	 * 默认构造方法，创建映射到 {@code ""}（空路径）的条件。
	 */
	public PathPatternsRequestCondition() {
		this(EMPTY_PATH_PATTERN);
	}

	/**
	 * 使用给定路径模式创建请求条件。
	 */
	public PathPatternsRequestCondition(PathPatternParser parser, String... patterns) {
		this(parse(parser, patterns));
	}

	private static SortedSet<PathPattern> parse(PathPatternParser parser, String... patterns) {
		if (patterns.length == 0 || (patterns.length == 1 && !StringUtils.hasText(patterns[0]))) {
			return EMPTY_PATH_PATTERN;
		}
		SortedSet<PathPattern> result = new TreeSet<>();
		for (String path : patterns) {
			if (StringUtils.hasText(path) && !path.startsWith("/")) {
				path = "/" + path;
			}
			result.add(parser.parse(path));
		}
		return result;
	}

	private PathPatternsRequestCondition(SortedSet<PathPattern> patterns) {
		this.patterns = patterns;
	}

	/**
	 * 返回此条件中的路径模式。只需要最优模式时请使用 {@link #getFirstPattern()}。
	 */
	public Set<PathPattern> getPatterns() {
		return this.patterns;
	}

	@Override
	protected Collection<PathPattern> getContent() {
		return this.patterns;
	}

	@Override
	protected String getToStringInfix() {
		return " || ";
	}

	/**
	 * 返回排序后的首个路径模式。
	 */
	public PathPattern getFirstPattern() {
		return this.patterns.first();
	}

	/**
	 * 判断此条件是否映射到 {@code ""}（空路径）。
	 */
	public boolean isEmptyPathMapping() {
		return this.patterns == EMPTY_PATH_PATTERN;
	}

	/**
	 * 返回不含模式语法的映射路径。
	 */
	public Set<String> getDirectPaths() {
		if (isEmptyPathMapping()) {
			return EMPTY_PATH;
		}
		Set<String> result = Collections.emptySet();
		for (PathPattern pattern : this.patterns) {
			if (!pattern.hasPatternSyntax()) {
				result = (result.isEmpty() ? new HashSet<>(1) : result);
				result.add(pattern.getPatternString());
			}
		}
		return result;
	}

	/**
	 * 返回 {@link #getPatterns()} 中路径模式对应的字符串。
	 */
	public Set<String> getPatternValues() {
		return (isEmptyPathMapping() ? EMPTY_PATH :
				getPatterns().stream().map(PathPattern::getPatternString).collect(Collectors.toSet()));
	}

	/**
	 * 根据当前条件和另一个条件中的 URL 模式创建合并结果：
	 * <ul>
	 * <li>两边都有模式时，使用 {@link PathPattern#combine(PathPattern)} 逐一合并。</li>
	 * <li>只有一边有模式时，使用该边的模式。</li>
	 * <li>两边都没有模式时，使用空字符串 {@code ""}。</li>
	 * </ul>
	 */
	@Override
	public PathPatternsRequestCondition combine(PathPatternsRequestCondition other) {
		if (isEmptyPathMapping() && other.isEmptyPathMapping()) {
			return this;
		}
		else if (other.isEmptyPathMapping()) {
			return this;
		}
		else if (isEmptyPathMapping()) {
			return other;
		}
		else {
			SortedSet<PathPattern> combined = new TreeSet<>();
			for (PathPattern pattern1 : this.patterns) {
				for (PathPattern pattern2 : other.patterns) {
					combined.add(pattern1.combine(pattern2));
				}
			}
			return new PathPatternsRequestCondition(combined);
		}
	}

	/**
	 * 检查路径模式是否匹配当前请求，并返回仅包含匹配模式且已排序的条件。
	 * @param request 当前请求
	 * @return 条件为空时返回当前实例；有匹配项时返回仅含匹配模式的新实例；没有匹配项时返回 {@code null}
	 */
	@Override
	public PathPatternsRequestCondition getMatchingCondition(HttpServletRequest request) {
		PathContainer path = ServletRequestPathUtils.getParsedRequestPath(request).pathWithinApplication();
		SortedSet<PathPattern> matches = getMatchingPatterns(path);
		return (matches != null ? new PathPatternsRequestCondition(matches) : null);
	}
	private SortedSet<PathPattern> getMatchingPatterns(PathContainer path) {
		TreeSet<PathPattern> result = null;
		for (PathPattern pattern : this.patterns) {
			if (pattern.matches(path)) {
				result = (result != null ? result : new TreeSet<>());
				result.add(pattern);
			}
		}
		return result;
	}

	/**
	 * 按条件中的 URL 模式比较两个匹配结果，依次比较排序后的模式；若已比较模式相同，则模式更多的一方更优。
	 * <p>假定两个条件均由 {@link #getMatchingCondition(HttpServletRequest)} 生成，且只包含匹配当前请求的模式。
	 */
	@Override
	public int compareTo(PathPatternsRequestCondition other, HttpServletRequest request) {
		Iterator<PathPattern> iterator = this.patterns.iterator();
		Iterator<PathPattern> iteratorOther = other.getPatterns().iterator();
		while (iterator.hasNext() && iteratorOther.hasNext()) {
			int result = PathPattern.SPECIFICITY_COMPARATOR.compare(iterator.next(), iteratorOther.next());
			if (result != 0) {
				return result;
			}
		}
		if (iterator.hasNext()) {
			return -1;
		}
		else if (iteratorOther.hasNext()) {
			return 1;
		}
		else {
			return 0;
		}
	}

}
