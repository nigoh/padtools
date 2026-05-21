package padtools.core.formats.uml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link JavaStructureExtractor} 系のパーサが共有する、状態を持たない純粋ヘルパ群。
 *
 * <p>トークンカーソルやパース状態に依存しない文字列処理・命名推定・判定ロジックのみを集約する。
 * これらをパーサ本体から切り離すことで、字句走査のロジックと純粋関数を分離して見通しを良くする。</p>
 */
final class JavaParseSupport {

    private JavaParseSupport() {
    }

    private static final String[] SAM_NAME_SUFFIXES = {
            "Listener", "Handler", "Callback", "Observer", "Action"
    };

    private static final Map<String, String> SAM_FALLBACK;
    static {
        Map<String, String> m = new HashMap<>();
        m.put("Runnable", "run");
        m.put("OnClickListener", "onClick");
        m.put("OnLongClickListener", "onLongClick");
        m.put("OnFocusChangeListener", "onFocusChange");
        m.put("OnCheckedChangeListener", "onCheckedChanged");
        m.put("OnItemSelectedListener", "onItemSelected");
        m.put("OnItemClickListener", "onItemClick");
        m.put("OnTouchListener", "onTouch");
        m.put("OnSeekBarChangeListener", "onProgressChanged");
        m.put("OnEditorActionListener", "onEditorAction");
        m.put("OnKeyListener", "onKey");
        m.put("OnScrollListener", "onScrollStateChanged");
        m.put("OnRefreshListener", "onRefresh");
        m.put("Callback", "callback");
        m.put("Observer", "onChanged");
        m.put("Consumer", "accept");
        m.put("Supplier", "get");
        m.put("Function", "apply");
        m.put("Predicate", "test");
        m.put("BiConsumer", "accept");
        m.put("BiFunction", "apply");
        m.put("ActionListener", "actionPerformed");
        m.put("ChangeListener", "stateChanged");
        m.put("PropertyChangeListener", "propertyChange");
        SAM_FALLBACK = Collections.unmodifiableMap(m);
    }

    /**
     * フィールド宣言型から、ラムダ/メソッド参照に対応する SAM メソッド名を推定する。
     * よく使う型は組み込みマップで解決し、未知でもサフィックス(Listener/Handler/Callback/
     * Observer/Action) を検出できれば命名規約から推定する。最終的に解決できなければ
     * {@code "<inline>"} を返す。
     */
    static String resolveSamMethodName(String type) {
        return resolveSamMethodName(type, null);
    }

    static String resolveSamMethodName(String type, String nameHint) {
        if (type == null || type.isEmpty()) {
            // nameHint が set+型名 パターン (例: setOnCheckedChangeListener → OnCheckedChangeListener)
            // なら型名を抽出して SAM_FALLBACK / 命名規約で解決を再試行する
            if (nameHint != null && nameHint.length() > 3
                    && nameHint.startsWith("set")
                    && Character.isUpperCase(nameHint.charAt(3))) {
                return resolveSamMethodName(nameHint.substring(3), null);
            }
            // onXxx 形式の nameHint ならそのまま SAM メソッド名として採用
            if (nameHint != null && nameHint.length() > 2
                    && nameHint.startsWith("on")
                    && Character.isUpperCase(nameHint.charAt(2))) {
                return nameHint;
            }
            return "<inline>";
        }
        // ジェネリックを取り除く
        String t = type;
        int lt = t.indexOf('<');
        if (lt >= 0) {
            t = t.substring(0, lt);
        }
        // 配列を取り除く
        t = t.replace("[]", "").trim();
        // 末尾のシンプル名にする
        int dot = t.lastIndexOf('.');
        if (dot >= 0) {
            t = t.substring(dot + 1);
        }
        String mapped = SAM_FALLBACK.get(t);
        if (mapped != null) {
            return mapped;
        }
        // 命名規約による推定: <Stem><Suffix> → <stem>
        // 例: PrintHandler → print / OnFooListener → onFoo / MyCallback → my
        for (String suf : SAM_NAME_SUFFIXES) {
            if (t.endsWith(suf) && t.length() > suf.length()) {
                String stem = t.substring(0, t.length() - suf.length());
                if (!stem.isEmpty()) {
                    return Character.toLowerCase(stem.charAt(0)) + stem.substring(1);
                }
            }
        }
        // 命名規約に当たらないが、フィールド/受け取り変数名が onXxx 形式ならそれを採用
        if (nameHint != null && nameHint.length() > 2
                && nameHint.startsWith("on")
                && Character.isUpperCase(nameHint.charAt(2))) {
            return nameHint;
        }
        return "<inline>";
    }

    static String stripAnnotations(String s) {
        // @Foo / @Foo.Bar / @Foo(args) を取り除く。引数部分はネストした括弧や
        // 文字列リテラル (@Foo(bar = @Baz("x"))) を考慮して手動で対応する括弧
        // までスキップする。正規表現の [^)]* だと最初の ) で止まってしまうため。
        if (s == null || s.indexOf('@') < 0) {
            return s == null ? null : s.trim();
        }
        StringBuilder out = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '@' && i + 1 < n
                    && (Character.isJavaIdentifierStart(s.charAt(i + 1)))) {
                // @ + 識別子 (a.b.c)
                i++;
                while (i < n) {
                    char ic = s.charAt(i);
                    if (Character.isJavaIdentifierPart(ic) || ic == '.') {
                        i++;
                    } else {
                        break;
                    }
                }
                // 引数 (...) はネスト・文字列対応でスキップ
                if (i < n && s.charAt(i) == '(') {
                    i = skipBalancedParens(s, i, n);
                }
                out.append(' ');
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString().trim();
    }

    /**
     * {@code s[from]} の {@code (} に対応する {@code )} の次のインデックスを返す。
     * 文字列リテラルとネストを考慮する。
     */
    private static int skipBalancedParens(String s, int from, int n) {
        int depth = 0;
        int i = from;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                char quote = c;
                i++;
                while (i < n && s.charAt(i) != quote) {
                    if (s.charAt(i) == '\\' && i + 1 < n) {
                        i += 2;
                    } else {
                        i++;
                    }
                }
                if (i < n) {
                    i++;
                }
                continue;
            }
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i + 1;
                }
            }
            i++;
        }
        return i;
    }

    static String normalizeType(String s) {
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Java の定数命名規約 {@code UPPER_CASE_WITH_UNDERSCORES} に従う識別子か。
     * 大文字始まりで、英大文字・数字・アンダースコアのみで構成され、長さ 2 以上。
     * ({@code F} のような 1 文字は誤検出を避けて除外。{@code PI} は採用。)
     */
    static boolean looksLikeConstantSymbol(String name) {
        if (name == null || name.length() < 2) {
            return false;
        }
        if (!Character.isUpperCase(name.charAt(0))) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isUpperCase(c) && !Character.isDigit(c) && c != '_') {
                return false;
            }
        }
        return true;
    }

    static JavaFieldInfo findFieldByName(JavaClassInfo cls, String name) {
        if (cls == null || name == null) {
            return null;
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (name.equals(f.getName())) {
                return f;
            }
        }
        return null;
    }

    static boolean isControlKeyword(String s) {
        return "if".equals(s) || "while".equals(s) || "for".equals(s)
                || "switch".equals(s) || "synchronized".equals(s)
                || "catch".equals(s) || "return".equals(s)
                || "throw".equals(s) || "new".equals(s)
                || "do".equals(s) || "else".equals(s) || "try".equals(s)
                || "finally".equals(s);
    }
}
