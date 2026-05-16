package padtools.core.formats.android;

import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gradle ビルドスクリプト (Groovy DSL: {@code build.gradle}、Kotlin DSL: {@code build.gradle.kts})
 * から代表的な構成情報を抽出するパーサ。
 *
 * <p>厳密な Groovy/Kotlin パーサは実装せず、コメント除去 + 文字列リテラルマスク + ブロック追跡
 * + 各ステートメントへの正規表現マッチで best-effort 抽出する。動的構文や条件分岐は best-effort
 * 扱いとし、抽出できなかった箇所は ErrorListener に通知することもある。</p>
 *
 * <p>抽出対象:</p>
 * <ul>
 *   <li>{@code plugins { id "..." }} および {@code apply plugin: "..."}</li>
 *   <li>{@code android { namespace, compileSdk(Version), defaultConfig { applicationId,
 *       minSdk, targetSdk, versionCode, versionName }, buildTypes { ... },
 *       productFlavors { ... }, signingConfigs { ... }, flavorDimensions ... }}</li>
 *   <li>{@code dependencies { implementation/api/testImplementation/... "group:name:version" }}</li>
 *   <li>{@code settings.gradle} の {@code include ':app'}, {@code include(":lib")}</li>
 * </ul>
 */
public final class GradleScriptParser {

    /** デフォルト (silent) リスナーでパース。 */
    public static GradleProjectInfo parse(String script, String fileName) {
        return parse(script, fileName, null);
    }

    /** リスナー付きパース。 */
    public static GradleProjectInfo parse(String script, String fileName,
                                           ErrorListener listener) {
        if (script == null) {
            throw new IllegalArgumentException("script is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        GradleProjectInfo info = new GradleProjectInfo();
        if (fileName != null) {
            info.setModuleType(fileName.endsWith(".kts") ? "kotlin" : "groovy");
        }
        String cleaned = stripCommentsAndMaskStrings(script);
        Impl impl = new Impl(cleaned, info, l);
        impl.parse();
        return info;
    }

    private GradleScriptParser() {
    }

    /**
     * 文字列リテラルを保持しつつコメントを除去する。
     * 文字列リテラル内のクォート/改行はそのまま残し、解析時にキーワードが文字列内かを
     * 判定しなくて済むよう、文字列内容を一律で _STR_ に置換することはしない (notation
     * 抽出のため値を保つ)。
     */
    private static String stripCommentsAndMaskStrings(String src) {
        StringBuilder out = new StringBuilder(src.length());
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '/') {
                while (i < n && src.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(src.charAt(i) == '*' && src.charAt(i + 1) == '/')) {
                    out.append(src.charAt(i) == '\n' ? '\n' : ' ');
                    i++;
                }
                i = Math.min(i + 2, n);
                continue;
            }
            if (c == '"' || c == '\'') {
                int s = i;
                char q = c;
                // triple-quoted string ("""..."""): Groovy/Kotlin の長文字列
                if (i + 2 < n && src.charAt(i + 1) == q && src.charAt(i + 2) == q) {
                    out.append(src, s, i + 3);
                    i += 3;
                    while (i + 2 < n && !(src.charAt(i) == q
                            && src.charAt(i + 1) == q && src.charAt(i + 2) == q)) {
                        out.append(src.charAt(i));
                        i++;
                    }
                    if (i + 2 < n) {
                        out.append(src, i, i + 3);
                        i += 3;
                    }
                    continue;
                }
                out.append(c);
                i++;
                while (i < n) {
                    char x = src.charAt(i);
                    out.append(x);
                    if (x == '\\' && i + 1 < n) {
                        out.append(src.charAt(i + 1));
                        i += 2;
                        continue;
                    }
                    i++;
                    if (x == q) {
                        break;
                    }
                    if (x == '\n') {
                        break;
                    }
                }
                continue;
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }

    /** パース本体。トップレベルのブロック単位で走査する。 */
    private static final class Impl {

        private static final Pattern PLUGIN_ID = Pattern.compile(
                "\\bid\\s*[(]?\\s*([\"'])([^\"']+)\\1");
        private static final Pattern PLUGIN_APPLY = Pattern.compile(
                "\\bapply\\s+plugin\\s*:\\s*[\"']([^\"']+)[\"']");
        private static final Pattern KW_VALUE_STR = Pattern.compile(
                "\\b(applicationId|namespace|versionName)\\s*[=]?\\s*[\"']([^\"']+)[\"']");
        private static final Pattern KW_VALUE_INT = Pattern.compile(
                "\\b(compileSdk(?:Version)?|minSdk(?:Version)?|targetSdk(?:Version)?|versionCode)"
                        + "\\s*[=]?\\s*(\\d+)");
        private static final Pattern DEP_NOTATION = Pattern.compile(
                "\\b(implementation|api|compileOnly|runtimeOnly|testImplementation"
                        + "|androidTestImplementation|annotationProcessor|kapt"
                        + "|debugImplementation|releaseImplementation)\\b"
                        + "\\s*[(]?\\s*[\"']([^\"']+)[\"']");
        private static final Pattern DEP_PROJECT = Pattern.compile(
                "\\b(implementation|api|compileOnly|runtimeOnly|testImplementation"
                        + "|androidTestImplementation|annotationProcessor|kapt"
                        + "|debugImplementation|releaseImplementation)\\b"
                        + "\\s*[(]?\\s*project\\s*[(]\\s*[\"']([^\"']+)[\"']\\s*[)]");
        private static final Pattern INCLUDE_PROJECT = Pattern.compile(
                "\\binclude\\s*[(]?\\s*[\"']([^\"']+)[\"']");
        private static final Pattern FLAVOR_DIMENSIONS = Pattern.compile(
                "\\bflavorDimensions\\s*[(]?\\s*((?:[\"'][^\"']+[\"'](?:\\s*,\\s*)?)+)");
        private static final Pattern STRING_LIST_ITEM = Pattern.compile(
                "[\"']([^\"']+)[\"']");

        private final String src;
        private final GradleProjectInfo info;
        private final ErrorListener listener;

        Impl(String src, GradleProjectInfo info, ErrorListener listener) {
            this.src = src;
            this.info = info;
            this.listener = listener;
        }

        void parse() {
            // settings.gradle: include 行を見つけて行内の全文字列リテラルを拾う
            // 例: include ':app', ':lib:core', ':lib:net'
            Pattern includeLine = Pattern.compile(
                    "(?m)^\\s*include\\b([^\\n]*)$");
            Matcher lm = includeLine.matcher(src);
            while (lm.find()) {
                String tail = lm.group(1);
                Matcher items = Pattern.compile("[\"']([^\"']+)[\"']").matcher(tail);
                while (items.find()) {
                    String mod = items.group(1).trim();
                    if (mod.startsWith(":")) {
                        mod = mod.substring(1);
                    }
                    if (!mod.isEmpty() && !info.getSubprojects().contains(mod)) {
                        info.getSubprojects().add(mod);
                    }
                }
            }
            // plugins / apply plugin (どこにあっても拾う)
            extractPlugins();
            // android { ... } ブロックを取り出して中をパース
            String android = extractBlock("android");
            if (android != null) {
                parseAndroidBlock(android);
            }
            // dependencies { ... } ブロック
            String deps = extractBlock("dependencies");
            if (deps != null) {
                parseDependenciesBlock(deps);
            }
        }

        private void extractPlugins() {
            // plugins { id 'x' ; id("y") } 形式
            String pluginsBlock = extractBlock("plugins");
            String src1 = pluginsBlock != null ? pluginsBlock : src;
            Matcher m = PLUGIN_ID.matcher(src1);
            while (m.find()) {
                String id = m.group(2);
                if (!info.getPlugins().contains(id)) {
                    info.getPlugins().add(id);
                }
            }
            // apply plugin: 'x' 形式
            Matcher m2 = PLUGIN_APPLY.matcher(src);
            while (m2.find()) {
                String id = m2.group(1);
                if (!info.getPlugins().contains(id)) {
                    info.getPlugins().add(id);
                }
            }
        }

        private void parseAndroidBlock(String body) {
            // 文字列値 (applicationId / namespace / versionName)
            Matcher s = KW_VALUE_STR.matcher(body);
            while (s.find()) {
                String key = s.group(1);
                String val = s.group(2);
                switch (key) {
                    case "applicationId":
                        if (info.getApplicationId() == null) {
                            info.setApplicationId(val);
                        }
                        break;
                    case "namespace":
                        if (info.getNamespace() == null) {
                            info.setNamespace(val);
                        }
                        break;
                    case "versionName":
                        if (info.getVersionName() == null) {
                            info.setVersionName(val);
                        }
                        break;
                    default: break;
                }
            }
            // 整数値 (compileSdk / minSdk / targetSdk / versionCode)
            Matcher i = KW_VALUE_INT.matcher(body);
            while (i.find()) {
                String key = i.group(1);
                int val = Integer.parseInt(i.group(2));
                if (key.startsWith("compileSdk") && info.getCompileSdk() == null) {
                    info.setCompileSdk(val);
                } else if (key.startsWith("minSdk") && info.getMinSdk() == null) {
                    info.setMinSdk(val);
                } else if (key.startsWith("targetSdk") && info.getTargetSdk() == null) {
                    info.setTargetSdk(val);
                } else if ("versionCode".equals(key) && info.getVersionCode() == null) {
                    info.setVersionCode(val);
                }
            }
            // flavorDimensions
            Matcher fd = FLAVOR_DIMENSIONS.matcher(body);
            if (fd.find()) {
                Matcher items = STRING_LIST_ITEM.matcher(fd.group(1));
                while (items.find()) {
                    info.getFlavorDimensions().add(items.group(1));
                }
            }
            // buildTypes
            String bt = extractBlockFrom(body, "buildTypes");
            if (bt != null) {
                parseBuildTypes(bt);
            }
            // productFlavors
            String pf = extractBlockFrom(body, "productFlavors");
            if (pf != null) {
                parseProductFlavors(pf);
            }
            // signingConfigs
            String sc = extractBlockFrom(body, "signingConfigs");
            if (sc != null) {
                parseSigningConfigs(sc);
            }
        }

        private void parseBuildTypes(String body) {
            for (NamedBlock nb : namedBlocks(body)) {
                GradleBuildType bt = new GradleBuildType(nb.name);
                Matcher mb = Pattern.compile(
                        "\\b(minifyEnabled|debuggable)\\s*[=]?\\s*(true|false)").matcher(nb.body);
                while (mb.find()) {
                    boolean v = "true".equals(mb.group(2));
                    if ("minifyEnabled".equals(mb.group(1))) {
                        bt.setMinifyEnabled(v);
                    } else {
                        bt.setDebuggable(v);
                    }
                }
                Matcher ms = Pattern.compile(
                        "\\b(applicationIdSuffix|versionNameSuffix|signingConfig)\\s*[=]?\\s*"
                                + "[\"']?([\\w.\\-]+)[\"']?").matcher(nb.body);
                while (ms.find()) {
                    String k = ms.group(1);
                    String v = ms.group(2);
                    if ("applicationIdSuffix".equals(k)) {
                        bt.setApplicationIdSuffix(v);
                    } else if ("versionNameSuffix".equals(k)) {
                        bt.setVersionNameSuffix(v);
                    } else if ("signingConfig".equals(k)) {
                        bt.setSigningConfig(v);
                    }
                }
                info.getBuildTypes().put(nb.name, bt);
            }
        }

        private void parseProductFlavors(String body) {
            for (NamedBlock nb : namedBlocks(body)) {
                GradleProductFlavor pf = new GradleProductFlavor(nb.name);
                Matcher m = Pattern.compile(
                        "\\b(dimension|applicationIdSuffix|versionNameSuffix)\\s*[=]?\\s*"
                                + "[\"']([^\"']*)[\"']").matcher(nb.body);
                while (m.find()) {
                    String k = m.group(1);
                    String v = m.group(2);
                    if ("dimension".equals(k)) {
                        pf.setDimension(v);
                    } else if ("applicationIdSuffix".equals(k)) {
                        pf.setApplicationIdSuffix(v);
                    } else if ("versionNameSuffix".equals(k)) {
                        pf.setVersionNameSuffix(v);
                    }
                }
                info.getProductFlavors().put(nb.name, pf);
            }
        }

        private void parseSigningConfigs(String body) {
            for (NamedBlock nb : namedBlocks(body)) {
                GradleSigningConfig sc = new GradleSigningConfig(nb.name);
                Matcher m = Pattern.compile(
                        "\\b(keyAlias|storeFile)\\b\\s*[=]?\\s*(?:\\w+\\s*[(])?\\s*"
                                + "[\"']([^\"']*)[\"']").matcher(nb.body);
                while (m.find()) {
                    if ("keyAlias".equals(m.group(1))) {
                        sc.setKeyAlias(m.group(2));
                    } else {
                        sc.setStoreFile(m.group(2));
                    }
                }
                info.getSigningConfigs().put(nb.name, sc);
            }
        }

        private void parseDependenciesBlock(String body) {
            Matcher mp = DEP_PROJECT.matcher(body);
            while (mp.find()) {
                String scope = mp.group(1);
                String ref = mp.group(2);
                info.getDependencies().add(new GradleDependency(scope,
                        "project('" + ref + "')"));
            }
            // 既に project(...) として拾った領域は除去せず、通常パターンと別マッチで通常依存も
            // 拾う。重複は notation 文字列で判定。
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (GradleDependency d : info.getDependencies()) {
                seen.add(d.getScope() + " " + d.getNotation());
            }
            Matcher mn = DEP_NOTATION.matcher(body);
            while (mn.find()) {
                String scope = mn.group(1);
                String notation = mn.group(2);
                // project() の中身が誤マッチした場合は notation に ':' が含まれない
                if (!notation.contains(":")) {
                    continue;
                }
                String key = scope + " " + notation;
                if (seen.add(key)) {
                    info.getDependencies().add(new GradleDependency(scope, notation));
                }
            }
        }

        /** トップレベルのブロック {@code name { ... }} を取り出す。最初の 1 件のみ。 */
        private String extractBlock(String blockName) {
            return extractBlockFrom(src, blockName);
        }

        private static String extractBlockFrom(String text, String blockName) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(blockName) + "\\s*\\{");
            Matcher m = p.matcher(text);
            if (!m.find()) {
                return null;
            }
            int start = m.end();
            int depth = 1;
            int i = start;
            while (i < text.length() && depth > 0) {
                char c = text.charAt(i);
                if (c == '"' || c == '\'') {
                    i = skipString(text, i, c);
                    continue;
                }
                if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        return text.substring(start, i);
                    }
                }
                i++;
            }
            return text.substring(start, Math.min(i, text.length()));
        }

        private static int skipString(String text, int i, char q) {
            int j = i + 1;
            while (j < text.length()) {
                char c = text.charAt(j);
                if (c == '\\' && j + 1 < text.length()) {
                    j += 2;
                    continue;
                }
                if (c == q || c == '\n') {
                    return j + 1;
                }
                j++;
            }
            return j;
        }

        /** {@code name { ... }} 形式の名前付きサブブロックを順に列挙する。 */
        private static List<NamedBlock> namedBlocks(String text) {
            List<NamedBlock> result = new ArrayList<>();
            Pattern p = Pattern.compile("\\b(\\w+)\\s*(?:\\([^)]*\\)\\s*)?\\{");
            Matcher m = p.matcher(text);
            while (m.find()) {
                String name = m.group(1);
                if (isReservedKeyword(name)) {
                    continue;
                }
                int start = m.end();
                int depth = 1;
                int i = start;
                while (i < text.length() && depth > 0) {
                    char c = text.charAt(i);
                    if (c == '"' || c == '\'') {
                        i = skipString(text, i, c);
                        continue;
                    }
                    if (c == '{') {
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            break;
                        }
                    }
                    i++;
                }
                String body = text.substring(start, Math.min(i, text.length()));
                result.add(new NamedBlock(name, body));
                m.region(Math.min(i + 1, text.length()), text.length());
            }
            return result;
        }

        private static boolean isReservedKeyword(String name) {
            return "if".equals(name) || "while".equals(name) || "for".equals(name)
                    || "do".equals(name) || "else".equals(name) || "try".equals(name)
                    || "catch".equals(name) || "finally".equals(name)
                    || "return".equals(name);
        }

        private static final class NamedBlock {
            final String name;
            final String body;

            NamedBlock(String name, String body) {
                this.name = name;
                this.body = body;
            }
        }
    }
}
