package padtools.core.formats.kotlin;

import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaFieldInfo;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.Visibility;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kotlin ソースを正規表現ベースで軽量パースし、既存の {@link JavaClassInfo} ツリーに
 * 変換するブリッジ。
 *
 * <p>厳密な Kotlin パーサではなく、Java 側の解析パイプライン
 * ({@link padtools.core.dataflow.RoomAnalyzer} 等) で Kotlin クラスも見えるようにする
 * ための最小実装。抽出するもの:</p>
 *
 * <ul>
 *   <li>{@code package com.x} (セミコロン任意)</li>
 *   <li>{@code import com.x.Y} / {@code import com.x.*}</li>
 *   <li>{@code class Foo} / {@code interface Foo} / {@code object Foo} /
 *       {@code data class Foo} / {@code enum class Foo} /
 *       {@code annotation class Foo} と直前の {@code @Annotation}</li>
 *   <li>クラスのプライマリコンストラクタパラメータの {@code val/var name: Type}
 *       (Room の {@code @PrimaryKey} 付きパラメータが取れる)</li>
 *   <li>クラス本体の {@code val/var name: Type} プロパティ</li>
 *   <li>クラス本体の {@code fun name(...): ReturnType { ... }} (アノテーションも保持)</li>
 * </ul>
 *
 * <p>取らないもの: 関数本体の解析、ジェネリクスの精密展開、Lambda、Compose
 * {@code @Composable} ツリー、拡張関数 (extension function)。</p>
 */
public final class KotlinLightScanner {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile(
            "(?m)^\\s*package\\s+([\\w.]+)\\s*;?\\s*$");
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*import\\s+([\\w.*]+)\\s*;?\\s*$");
    /**
     * クラスヘッダパターン。グループ 1 = annotations + modifiers (空白区切り),
     * グループ 2 = 種別キーワード, グループ 3 = クラス名。
     */
    private static final Pattern CLASS_HEADER = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*|"
                    + "public\\s+|protected\\s+|private\\s+|internal\\s+|"
                    + "open\\s+|abstract\\s+|final\\s+|sealed\\s+|data\\s+|"
                    + "inner\\s+|companion\\s+|enum\\s+|annotation\\s+)*)"
                    + "(class|interface|object)\\s+([A-Za-z_$][A-Za-z0-9_$]*)");
    /** プライマリコンストラクタ引数の {@code val/var name: Type}。 */
    private static final Pattern PRIMARY_CTOR_PARAM = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "(?:private\\s+|protected\\s+|public\\s+|internal\\s+)?"
                    + "(val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                    + "([A-Za-z_$][\\w.<>?\\[\\]\\s,]*)");
    /** クラス本体内の {@code val/var name: Type}。 */
    private static final Pattern PROPERTY = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "(?:private\\s+|protected\\s+|public\\s+|internal\\s+"
                    + "|lateinit\\s+|const\\s+|override\\s+)*"
                    + "(val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*"
                    + "([A-Za-z_$][\\w.<>?\\[\\]\\s,]*?)(?=\\s*[=\\n{;])");
    /** {@code fun name(params): ReturnType}。 */
    private static final Pattern FUN_DECL = Pattern.compile(
            "((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                    + "(?:public\\s+|private\\s+|protected\\s+|internal\\s+"
                    + "|open\\s+|abstract\\s+|final\\s+|override\\s+|suspend\\s+|inline\\s+)*"
                    + "fun\\s+(?:<[^>]+>\\s+)?"
                    + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(([^)]*)\\)"
                    + "(?:\\s*:\\s*([A-Za-z_$][\\w.<>?\\[\\]\\s,]*?))?(?=\\s*[={\\n])");

    /** Kotlin ソースから {@link JavaClassInfo} のリストを抽出する。 */
    public static List<JavaClassInfo> scan(String source, ErrorListener listener) {
        List<JavaClassInfo> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        String pkg = "";
        Matcher pm = PACKAGE_PATTERN.matcher(source);
        if (pm.find()) {
            pkg = pm.group(1);
        }
        List<String> imports = new ArrayList<>();
        Matcher im = IMPORT_PATTERN.matcher(source);
        while (im.find()) {
            imports.add(im.group(1));
        }

        // クラスヘッダごとに本体を切り出す
        Matcher cm = CLASS_HEADER.matcher(source);
        while (cm.find()) {
            String annsAndMods = cm.group(1);
            String kindKw = cm.group(2);
            String name = cm.group(3);
            int headerEnd = cm.end();

            JavaClassInfo info = new JavaClassInfo();
            info.setPackageName(pkg);
            info.setSimpleName(name);
            info.setKind(mapKind(kindKw, annsAndMods));
            info.getImports().addAll(imports);
            extractAnnotations(annsAndMods, info.getAnnotations());

            // プライマリコンストラクタ引数 (class Foo(val x: Int, ...))
            int primaryCtorParen = findNextChar(source, headerEnd, '(');
            int bodyBraceOpen = findNextChar(source, headerEnd, '{');
            if (primaryCtorParen >= 0
                    && (bodyBraceOpen < 0 || primaryCtorParen < bodyBraceOpen)) {
                int primaryCtorClose = matchParen(source, primaryCtorParen);
                if (primaryCtorClose > primaryCtorParen) {
                    String paramsText = source.substring(primaryCtorParen + 1,
                            primaryCtorClose);
                    extractPrimaryCtorFields(paramsText, info);
                }
            }

            // クラス本体
            if (bodyBraceOpen >= 0) {
                int bodyEnd = matchBrace(source, bodyBraceOpen);
                if (bodyEnd > bodyBraceOpen) {
                    String body = source.substring(bodyBraceOpen + 1, bodyEnd);
                    extractProperties(body, info);
                    extractFunctions(body, info);
                }
            }

            out.add(info);
        }
        return out;
    }

    private static JavaClassInfo.Kind mapKind(String kindKw, String modifiers) {
        if ("interface".equals(kindKw)) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        if ("object".equals(kindKw)) {
            // Kotlin object は事実上シングルトン → CLASS として扱う
            return JavaClassInfo.Kind.CLASS;
        }
        // class: enum / annotation / data class を分類
        if (modifiers != null) {
            if (modifiers.contains("enum")) return JavaClassInfo.Kind.ENUM;
            if (modifiers.contains("annotation")) return JavaClassInfo.Kind.ANNOTATION;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    private static void extractAnnotations(String annsAndMods, List<String> into) {
        if (annsAndMods == null) return;
        Pattern annPattern = Pattern.compile(
                "@([A-Za-z_][\\w.]*)(\\([^)]*\\))?");
        Matcher m = annPattern.matcher(annsAndMods);
        while (m.find()) {
            String full = "@" + m.group(1) + (m.group(2) == null ? "" : m.group(2));
            into.add(full);
        }
    }

    /**
     * プライマリコンストラクタ引数を解析してフィールドとして追加。
     * カンマで分割した後、各パラメータごとに {@code val/var name: Type} を取り出す。
     * 通常のメソッド引数 (val/var なしの単純 {@code name: Type}) はフィールド化しない。
     */
    private static void extractPrimaryCtorFields(String paramsText, JavaClassInfo info) {
        if (paramsText == null) return;
        Pattern perParam = Pattern.compile(
                "^\\s*((?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*)"
                        + "(?:private\\s+|protected\\s+|public\\s+|internal\\s+)?"
                        + "(?:val|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*(.+?)"
                        + "(?:\\s*=.*)?\\s*$");
        for (String p : splitTopLevelCommas(paramsText)) {
            Matcher m = perParam.matcher(p);
            if (m.matches()) {
                String anns = m.group(1);
                String name = m.group(2);
                String type = m.group(3).trim();
                JavaFieldInfo f = new JavaFieldInfo();
                f.setName(name);
                f.setType(type);
                f.setVisibility(Visibility.PUBLIC);
                extractAnnotations(anns, f.getAnnotations());
                info.getFields().add(f);
            }
        }
    }

    /** クラス本体のプロパティを解析してフィールドとして追加。 */
    private static void extractProperties(String body, JavaClassInfo info) {
        Matcher m = PROPERTY.matcher(body);
        while (m.find()) {
            String anns = m.group(1);
            String name = m.group(3);
            String type = m.group(4).trim();
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(name);
            f.setType(type);
            f.setVisibility(Visibility.PUBLIC);
            extractAnnotations(anns, f.getAnnotations());
            info.getFields().add(f);
        }
    }

    /** クラス本体の {@code fun ...} を解析してメソッドとして追加。 */
    private static void extractFunctions(String body, JavaClassInfo info) {
        Matcher m = FUN_DECL.matcher(body);
        while (m.find()) {
            String anns = m.group(1);
            String name = m.group(2);
            String paramsText = m.group(3);
            String returnType = m.group(4);
            JavaMethodInfo mth = new JavaMethodInfo();
            mth.setName(name);
            mth.setReturnType(returnType == null ? "Unit" : returnType.trim());
            mth.setVisibility(Visibility.PUBLIC);
            extractAnnotations(anns, mth.getAnnotations());
            parseParameters(paramsText, mth);
            info.getMethods().add(mth);
        }
    }

    /** {@code name: Type, name2: Type2 = default} を解析してパラメータに追加。 */
    private static void parseParameters(String text, JavaMethodInfo mth) {
        if (text == null || text.trim().isEmpty()) return;
        // ジェネリクスを尊重した split
        List<String> parts = splitTopLevelCommas(text);
        for (String p : parts) {
            String trimmed = p.trim();
            if (trimmed.isEmpty()) continue;
            // "@A name: Type = default" / "name: Type"
            // アノテーションと修飾子を取り除き、name: Type を取る
            Pattern simple = Pattern.compile(
                    "(?:@[A-Za-z_][\\w.]*(?:\\([^)]*\\))?\\s*)*"
                            + "(?:vararg\\s+|crossinline\\s+|noinline\\s+)?"
                            + "([A-Za-z_$][A-Za-z0-9_$]*)\\s*:\\s*([^=]+?)\\s*(?:=.*)?$");
            Matcher s = simple.matcher(trimmed);
            if (s.matches()) {
                mth.getParameterNames().add(s.group(1));
                mth.getParameterTypes().add(s.group(2).trim());
            }
        }
    }

    private static List<String> splitTopLevelCommas(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(' || c == '[' || c == '{') depth++;
            else if (c == '>' || c == ')' || c == ']' || c == '}') {
                if (depth > 0) depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static int findNextChar(String src, int from, char target) {
        for (int i = from; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == target) return i;
            // クラスヘッダ末尾と本体開始の間に出てくる文字: '<' (generics),
            // ':' (継承), 'where' などを想定して途中で他の不正な文字に遭遇しても続行
        }
        return -1;
    }

    private static int matchParen(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '(') return open;
        return matchBalance(src, open, '(', ')');
    }

    private static int matchBrace(String src, int open) {
        if (open < 0 || open >= src.length() || src.charAt(open) != '{') return open;
        return matchBalance(src, open, '{', '}');
    }

    private static int matchBalance(String src, int open, char openCh, char closeCh) {
        int depth = 1;
        boolean inString = false;
        for (int i = open + 1; i < src.length(); i++) {
            char c = src.charAt(i);
            if (inString) {
                if (c == '\\' && i + 1 < src.length()) { i++; continue; }
                if (c == '"') inString = false;
                continue;
            }
            if (c == '"') inString = true;
            else if (c == openCh) depth++;
            else if (c == closeCh) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return src.length();
    }

    private KotlinLightScanner() {
    }
}
