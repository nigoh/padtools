package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * クラス情報 ({@link JavaClassInfo}) のリストから PlantUML 形式のクラス図テキストを生成する。
 *
 * <p>{@link Options} で出力する要素 (継承線、利用関係、可視性記号、AAOS マーカー)
 * を切り替えられる。</p>
 */
public final class PlantUmlClassDiagram {

    /** コメント表示スタイル。 */
    public enum CommentStyle {
        /** クラス本体内に {@code .. text ..} セパレータでコメントを埋め込む (コンパクト)。 */
        INLINE,
        /** クラス・メンバーに対して {@code note ...} ブロックを発行する (詳細)。 */
        NOTE
    }

    /** 出力オプション。 */
    public static class Options {
        public boolean showVisibility = true;
        public boolean showInheritance = true;
        /**
         * {@code implements} によるインタフェース実装線を出力する。既定 true。
         * 関連線種別フィルタで extends と implements を別々に制御したい場合に使う。
         */
        public boolean showImplementations = true;
        public boolean showUsageRelations = true;
        public boolean showFields = true;
        public boolean showMethods = true;
        public boolean groupByPackage = true;
        public boolean markAaosCategories = true;
        /** 凡例ブロックをダイアグラム右に追加する。 */
        public boolean includeLegend = true;
        /** 利用関係を出すフィールド型の最大要素数 (1 クラスあたり)。多すぎる場合に抑制。 */
        public int maxUsagePerClass = 30;
        /** タイトル文字列 (null で省略)。 */
        public String title;
        /** JavaDoc / 直前コメントを出力する。 */
        public boolean showComments = true;
        /** コメント表示スタイル。 */
        public CommentStyle commentStyle = CommentStyle.INLINE;
        /** インライン表示時のコメント 1 件あたり最大文字数。超過分は ... で省略。 */
        public int commentMaxLength = 80;
        /**
         * コメント文字列の色 (PlantUML の {@code <color:#RRGGBB>} に渡す値)。
         * INLINE 表示時はテキストを {@code <color:...>...</color>} で囲み、
         * NOTE 表示時は note の枠線/文字色 skinparam に適用する。
         * null または空文字を指定すると色付けを行わない。
         */
        public String commentColor = "#008800";
        /** フィールド/メソッドのアノテーションを出力する。 */
        public boolean showAnnotations = true;
        /** {@link #showAnnotations} が true でも表示しないアノテーション名 (ノイズ抑制)。 */
        public Set<String> hiddenAnnotations = new HashSet<>(
                Arrays.asList("Override", "SuppressWarnings"));
        /** enum 定数を表示する。 */
        public boolean showEnumConstants = true;
        /** {@code final} フィールドに {@code &#123;final&#125;} マーカーを付ける。 */
        public boolean showFinal = true;
        /** 図全体に出すクラスの最大数 (0 以下で無制限)。超過時は先頭から切り詰める。 */
        public int maxClasses = 0;
        /** 図末尾に出す警告メッセージ (PlantUML の {@code footer} 行)。null/空で出力しない。 */
        public String footerWarning;
        /** Jetpack (Fragment / ViewModel / Hilt 等) ステレオタイプ・装飾の設定。既定で無効。 */
        public JetpackOptions jetpack = new JetpackOptions();
        /**
         * 各クラス宣言に {@code [[padtools://class/<FQN>]]} を付与する。
         * GUI プレビューで右クリック→メソッド一覧のヒットテスト用に SVG へ
         * {@code <a xlink:href>} を埋め込みたい場合のみ true にする。
         * CLI 出力や保存図には影響させないため既定で false。
         */
        public boolean interactiveLinks = false;
        /**
         * {@link #interactiveLinks} の URL スキーム接頭辞。
         * SVG 上の {@code href} から FQN を取り出す側 (GUI) と揃える。
         */
        public String interactiveLinkPrefix = "padtools://class/";
        /**
         * 外部ライブラリ (java.*, android.*, kotlin.* など) を完全に除外する。
         * 既定 false (除外せず {@code <<external>>} / {@code <<missing>>} ステレオタイプで区別表示する
         * 既存挙動を維持)。
         *
         * <p>判定は 2 段で行う:
         * <ol>
         *   <li>{@link JavaClassInfo#getOrigin()} が {@code EXTERNAL_JAR} / {@code MISSING_JAR}</li>
         *   <li>パッケージ名が {@link #externalPackagePrefixes} のいずれかに前方一致</li>
         * </ol>
         * いずれかに該当すれば除外される。</p>
         */
        public boolean excludeExternalLibraries = false;
        /**
         * 外部ライブラリ判定のパッケージ prefix セット。
         * null/空のときは {@link ExternalPackageMatcher#DEFAULT_PREFIXES} が使われる。
         */
        public Set<String> externalPackagePrefixes =
                new LinkedHashSet<>(ExternalPackageMatcher.DEFAULT_PREFIXES);
        /**
         * {@code public} 可視性のクラス / フィールド / メソッドのみを表示する。
         * 既定 false (全可視性を表示)。
         */
        public boolean publicOnly = false;
    }

    private static final Pattern PRIMITIVE_OR_BUILTIN = Pattern.compile(
            "^(void|boolean|byte|char|short|int|long|float|double"
                    + "|String|Object|CharSequence|Number"
                    + "|Integer|Long|Short|Byte|Float|Double|Boolean|Character"
                    + "|Class|Map|List|Set|Collection|Iterable|Iterator|Queue"
                    + "|HashMap|ArrayList|LinkedList|HashSet|LinkedHashMap)$");

    /** デフォルト Options で生成。 */
    public static String generate(List<JavaClassInfo> classes) {
        return generate(classes, null);
    }

    /** オプション付き生成。 */
    public static String generate(List<JavaClassInfo> classes, Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        Options o = opts != null ? opts : new Options();
        // 1. 外部ライブラリ除外: Origin が EXTERNAL_JAR / MISSING_JAR か、
        //    パッケージ名が externalPackagePrefixes に前方一致するクラスを除く。
        if (o.excludeExternalLibraries) {
            List<JavaClassInfo> next = new ArrayList<>(classes.size());
            for (JavaClassInfo c : classes) {
                if (isExternalClass(c, o)) {
                    continue;
                }
                next.add(c);
            }
            classes = next;
        }
        // 2. publicOnly: クラス自体の public 修飾子で絞る。
        if (o.publicOnly) {
            List<JavaClassInfo> next = new ArrayList<>(classes.size());
            for (JavaClassInfo c : classes) {
                if (isPublicLike(c)) {
                    next.add(c);
                }
            }
            classes = next;
        }
        // 3. maxClasses が指定されていれば先頭から切り詰める。
        int originalTotal = classes.size();
        if (o.maxClasses > 0 && classes.size() > o.maxClasses) {
            classes = classes.subList(0, o.maxClasses);
        }
        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        }
        out.append("skinparam classAttributeIconSize 0\n");
        // NOTE 表示時のコメント色 (枠線・文字) を skinparam で指定する。
        // INLINE 表示時は <color:...> タグで個別に色付けするためここでは出力しない。
        if (o.showComments
                && o.commentStyle == CommentStyle.NOTE
                && o.commentColor != null
                && !o.commentColor.isEmpty()) {
            out.append("skinparam noteBorderColor ").append(o.commentColor).append('\n');
            out.append("skinparam noteFontColor ").append(o.commentColor).append('\n');
        }
        // クラスごとに一意のエイリアスを発行する。PlantUML は "a.b.c" 形式の識別子を
        // ネスト/名前空間として解釈してしまうため、引用符付き名 + as エイリアスで切り離す。
        Set<String> knownNames = new HashSet<>();
        java.util.Map<String, String> aliasByQn = new java.util.LinkedHashMap<>();
        java.util.Map<String, String> qnBySimple = new java.util.HashMap<>();
        int aliasSeq = 0;
        for (JavaClassInfo c : classes) {
            String qn = c.getQualifiedName();
            knownNames.add(qn);
            aliasByQn.put(qn, "C" + (aliasSeq++));
            qnBySimple.putIfAbsent(c.getSimpleName(), qn);
            if (c.getEnclosingClass() != null && !c.getEnclosingClass().isEmpty()) {
                qnBySimple.putIfAbsent(
                        c.getEnclosingClass() + "." + c.getSimpleName(), qn);
            }
        }

        if (o.groupByPackage) {
            Map<String, List<JavaClassInfo>> byPkg = new LinkedHashMap<>();
            for (JavaClassInfo c : classes) {
                byPkg.computeIfAbsent(
                        c.getPackageName() == null ? "" : c.getPackageName(),
                        k -> new ArrayList<>()).add(c);
            }
            for (Map.Entry<String, List<JavaClassInfo>> e : byPkg.entrySet()) {
                String pkg = e.getKey();
                if (pkg.isEmpty()) {
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "", aliasByQn);
                    }
                } else {
                    out.append("package \"").append(pkg).append("\" {\n");
                    for (JavaClassInfo c : e.getValue()) {
                        emitClass(out, c, o, "  ", aliasByQn);
                    }
                    out.append("}\n");
                }
            }
        } else {
            for (JavaClassInfo c : classes) {
                emitClass(out, c, o, "", aliasByQn);
            }
        }

        // 関係線
        if (o.showInheritance || o.showImplementations) {
            for (JavaClassInfo c : classes) {
                emitInheritance(out, c, o, aliasByQn, qnBySimple);
            }
        }
        if (o.showUsageRelations) {
            for (JavaClassInfo c : classes) {
                emitUsage(out, c, knownNames, aliasByQn, qnBySimple, o);
            }
        }
        if (o.includeLegend) {
            PlantUmlClassLegend.emit(out, classes, o);
        }
        // フッタ警告: maxClasses で切り詰めた場合の自動メッセージを優先
        String footer = o.footerWarning;
        if ((footer == null || footer.isEmpty())
                && o.maxClasses > 0 && originalTotal > classes.size()) {
            footer = "showing " + classes.size() + " of " + originalTotal + " classes";
        }
        if (footer != null && !footer.isEmpty()) {
            out.append("footer ").append(footer).append('\n');
        }
        out.append("@enduml\n");
        return out.toString();
    }

    static boolean hasVisibleAnnotation(List<String> annotations, Options o) {
        if (annotations == null || annotations.isEmpty()) {
            return false;
        }
        for (String a : annotations) {
            if (a == null || a.isEmpty()) {
                continue;
            }
            String name = annotationName(a);
            if (o.hiddenAnnotations != null && o.hiddenAnnotations.contains(name)) {
                continue;
            }
            return true;
        }
        return false;
    }

    static String stereoDesc(String stereo) {
        switch (stereo) {
            case "CarManager": return "AAOS の Car*Manager クラス";
            case "CarService": return "AAOS の Car*Service クラス";
            case "ICarInterface": return "ICar* 命名規約の AIDL 派生インタフェース";
            case "AIDL": return "AIDL ファイル由来のインタフェース";
            case "AaosApi": return "@AddedIn 等の AAOS API アノテーション付きクラス";
            case "aidl": return "AIDL 由来 (補助)";
            default: return stereo;
        }
    }

    static String androidStereoDesc(String stereo) {
        switch (stereo) {
            case "Activity": return "AndroidManifest.xml の <activity>";
            case "Service": return "AndroidManifest.xml の <service>";
            case "BroadcastReceiver": return "AndroidManifest.xml の <receiver>";
            case "ContentProvider": return "AndroidManifest.xml の <provider>";
            default: return stereo;
        }
    }

    static String jetpackStereoDesc(String stereo) {
        switch (stereo) {
            case "Fragment": return "androidx.fragment.app.Fragment 派生";
            case "DialogFragment": return "DialogFragment 派生";
            case "BottomSheetDialogFragment": return "Material BottomSheetDialogFragment 派生";
            case "NavHostFragment": return "Navigation Component の NavHostFragment 派生";
            case "ViewModel": return "androidx.lifecycle.ViewModel 派生";
            case "AndroidViewModel": return "androidx.lifecycle.AndroidViewModel 派生";
            case "AndroidEntryPoint": return "@AndroidEntryPoint 注入対象 (Hilt)";
            case "HiltViewModel": return "@HiltViewModel (Hilt 注入の ViewModel)";
            case "HiltAndroidApp": return "@HiltAndroidApp (Hilt のアプリ起点)";
            case "HiltModule": return "@Module + @InstallIn (Hilt モジュール)";
            case "DaggerModule": return "@Module (Dagger モジュール)";
            case "Injectable": return "@Inject コンストラクタを持つクラス";
            default: return stereo;
        }
    }

    private static void emitClass(StringBuilder out, JavaClassInfo c,
                                  Options o, String indent,
                                  java.util.Map<String, String> aliasByQn) {
        String kw = classKeyword(c);
        String stereo = stereotype(c, o);
        out.append(indent).append(kw).append(' ');
        out.append(quoteId(displayId(c)));
        String alias = aliasByQn.get(c.getQualifiedName());
        if (alias != null) {
            out.append(" as ").append(alias);
        }
        if (!stereo.isEmpty()) {
            out.append(' ').append(stereo);
        }
        if (o.interactiveLinks) {
            String prefix = o.interactiveLinkPrefix != null
                    ? o.interactiveLinkPrefix : "padtools://class/";
            out.append(" [[").append(prefix).append(c.getQualifiedName()).append("]]");
        }
        out.append(" {\n");
        // INLINE 表示時はクラスコメントを本体の先頭に置く
        if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
            emitInlineComment(out, c.getComment(), o, indent + "  ");
        }
        if (o.showEnumConstants
                && c.getKind() == JavaClassInfo.Kind.ENUM
                && !c.getEnumConstants().isEmpty()) {
            for (String name : c.getEnumConstants()) {
                out.append(indent).append("  ").append(name).append('\n');
            }
            // 定数と他メンバーの区切り (PlantUML の区切り線)
            boolean hasOtherMembers = (o.showFields && !c.getFields().isEmpty())
                    || (o.showMethods && !c.getMethods().isEmpty());
            if (hasOtherMembers) {
                out.append(indent).append("  --\n");
            }
        }
        if (o.showFields) {
            for (JavaFieldInfo f : c.getFields()) {
                if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
                    emitInlineComment(out, f.getComment(), o, indent + "  ");
                }
                emitField(out, f, o, indent + "  ");
            }
        }
        if (o.showMethods) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (o.showComments && o.commentStyle == CommentStyle.INLINE) {
                    emitInlineComment(out, m.getComment(), o, indent + "  ");
                }
                emitMethod(out, m, o, indent + "  ");
            }
        }
        out.append(indent).append("}\n");
        // NOTE 表示時はクラスの外に note ブロックを発行
        if (o.showComments && o.commentStyle == CommentStyle.NOTE && alias != null) {
            emitNoteBlocks(out, c, alias, o, indent);
        }
    }

    /** INLINE モード用のコメント行 {@code .. text ..} を 1 行発行する。空コメントは何もしない。 */
    private static void emitInlineComment(StringBuilder out, String comment,
                                           Options o, String indent) {
        if (comment == null || comment.isEmpty()) {
            return;
        }
        String line = JavaCommentScanner.firstLine(comment);
        if (line.isEmpty()) {
            return;
        }
        line = sanitizeInlineComment(line, o.commentMaxLength);
        out.append(indent).append(".. ");
        if (o.commentColor != null && !o.commentColor.isEmpty()) {
            out.append("<color:").append(o.commentColor).append('>')
                    .append(line)
                    .append("</color>");
        } else {
            out.append(line);
        }
        out.append(" ..\n");
    }

    /**
     * PlantUML の {@code ..} セパレータと干渉する文字を抑止し、長さも制限する。
     * シーケンス図側からも再利用するため package-private。
     */
    static String sanitizeInlineComment(String s, int maxLen) {
        // PlantUML の class body 内でレイアウトを乱す制御文字を除去
        String t = s.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        // 末尾の '..' は区切りと干渉するためスペースに置換
        t = t.replaceAll("\\.\\.+$", ".");
        if (maxLen > 0 && t.length() > maxLen) {
            t = t.substring(0, Math.max(1, maxLen - 1)) + "…";
        }
        return t;
    }

    /** NOTE モード: クラス・各メンバーの JavaDoc を {@code note ...} で出力。 */
    private static void emitNoteBlocks(StringBuilder out, JavaClassInfo c,
                                        String alias, Options o, String indent) {
        if (c.getComment() != null && !c.getComment().isEmpty()) {
            out.append(indent).append("note top of ").append(alias).append('\n');
            appendNoteBody(out, c.getComment(), indent);
            out.append(indent).append("end note\n");
        }
        if (o.showFields) {
            for (JavaFieldInfo f : c.getFields()) {
                if (f.getComment() == null || f.getComment().isEmpty()) {
                    continue;
                }
                if (f.getName() == null || f.getName().isEmpty()) {
                    continue;
                }
                out.append(indent).append("note right of ").append(alias).append("::")
                        .append(f.getName()).append('\n');
                appendNoteBody(out, f.getComment(), indent);
                out.append(indent).append("end note\n");
            }
        }
        if (o.showMethods) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getComment() == null || m.getComment().isEmpty()) {
                    continue;
                }
                if (m.getName() == null || m.getName().isEmpty()) {
                    continue;
                }
                out.append(indent).append("note right of ").append(alias).append("::")
                        .append(m.getName()).append('\n');
                appendNoteBody(out, m.getComment(), indent);
                out.append(indent).append("end note\n");
            }
        }
    }

    /** note ブロックの本文を 1 行ずつ書き出す。シーケンス図側からも再利用するため package-private。 */
    static void appendNoteBody(StringBuilder out, String comment, String indent) {
        String[] lines = comment.split("\n", -1);
        for (String line : lines) {
            String t = line.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            out.append(indent).append("  ").append(t).append('\n');
        }
    }

    private static String classKeyword(JavaClassInfo c) {
        switch (c.getKind()) {
            case INTERFACE: return "interface";
            case ENUM: return "enum";
            case ANNOTATION: return "annotation";
            case AIDL_INTERFACE: return "interface";
            case CLASS:
            default:
                return c.isAbstract() ? "abstract class" : "class";
        }
    }

    private static String stereotype(JavaClassInfo c, Options o) {
        List<String> parts = new ArrayList<>();
        // 外部 JAR 由来 / 解決失敗のステレオタイプを先頭に出す (視認性最優先)
        switch (c.getOrigin()) {
            case EXTERNAL_JAR:
                parts.add("external");
                break;
            case MISSING_JAR:
                parts.add("missing");
                break;
            case SOURCE:
            default:
                break;
        }
        if (o.markAaosCategories) {
            String cat = c.getAaosCategory();
            if (cat == null) {
                cat = AaosPattern.categorize(c);
            }
            if (cat != null) {
                parts.add(cat);
            }
        }
        if (c.getAndroidComponentType() != null && !c.getAndroidComponentType().isEmpty()) {
            parts.add(c.getAndroidComponentType());
        }
        if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            parts.add("aidl");
        }
        if (o.jetpack != null && o.jetpack.enabled) {
            for (String j : c.getJetpackStereotypes()) {
                if (!parts.contains(j)) {
                    parts.add(j);
                }
            }
        }
        if (parts.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            sb.append("<<").append(p).append(">>");
        }
        // MISSING_JAR には警告色を suffix で付与
        if (c.getOrigin() == JavaClassInfo.Origin.MISSING_JAR) {
            sb.append(" #LightYellow");
        }
        return sb.toString();
    }

    private static String displayId(JavaClassInfo c) {
        return c.getQualifiedName();
    }

    private static String quoteId(String id) {
        return "\"" + id.replace("\"", "\\\"") + "\"";
    }

    private static void emitField(StringBuilder out, JavaFieldInfo f,
                                   Options o, String indent) {
        if (o.publicOnly && f.getVisibility() != Visibility.PUBLIC) {
            return;
        }
        out.append(indent);
        if (o.showVisibility) {
            out.append(f.getVisibility().mark());
        }
        if (f.isStatic()) {
            out.append("{static} ");
        }
        if (o.showFinal && f.isFinal()) {
            out.append("{final} ");
        }
        appendAnnotations(out, f.getAnnotations(), o);
        if (f.getName() != null && !f.getName().isEmpty()) {
            out.append(f.getName());
        }
        if (f.getType() != null && !f.getType().isEmpty()) {
            out.append(": ").append(f.getType());
        }
        out.append('\n');
    }

    /** PlantUML 行に表示するアノテーションを {@code @Foo @Bar } 形式で追記する。 */
    private static void appendAnnotations(StringBuilder out, List<String> annotations,
                                           Options o) {
        if (!o.showAnnotations || annotations == null || annotations.isEmpty()) {
            return;
        }
        for (String raw : annotations) {
            if (raw == null || raw.isEmpty()) {
                continue;
            }
            String name = annotationName(raw);
            if (o.hiddenAnnotations != null && o.hiddenAnnotations.contains(name)) {
                continue;
            }
            out.append('@').append(name).append(' ');
        }
    }

    /** {@code "Nullable"} や {@code "Retention(RetentionPolicy.RUNTIME)"} から名前部分を取り出す。 */
    private static String annotationName(String raw) {
        String s = raw.trim();
        int paren = s.indexOf('(');
        if (paren >= 0) {
            s = s.substring(0, paren);
        }
        int dot = s.lastIndexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        return s;
    }

    private static void emitMethod(StringBuilder out, JavaMethodInfo m,
                                    Options o, String indent) {
        if (o.publicOnly && m.getVisibility() != Visibility.PUBLIC) {
            return;
        }
        out.append(indent);
        if (o.showVisibility) {
            out.append(m.getVisibility().mark());
        }
        if (m.isStatic()) {
            out.append("{static} ");
        }
        if (m.isAbstract()) {
            out.append("{abstract} ");
        }
        appendAnnotations(out, m.getAnnotations(), o);
        out.append(m.getName() == null ? "" : m.getName()).append('(');
        for (int i = 0; i < m.getParameterTypes().size(); i++) {
            if (i > 0) {
                out.append(", ");
            }
            String type = m.getParameterTypes().get(i);
            String name = i < m.getParameterNames().size()
                    ? m.getParameterNames().get(i) : "";
            if (name != null && !name.isEmpty()) {
                out.append(name).append(": ");
            }
            out.append(type == null ? "?" : type);
        }
        out.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            out.append(": ").append(m.getReturnType());
        }
        out.append('\n');
    }

    private static void emitInheritance(StringBuilder out, JavaClassInfo c,
                                         Options o,
                                         java.util.Map<String, String> aliasByQn,
                                         java.util.Map<String, String> qnBySimple) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        if (o.showInheritance
                && c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
            String parent = relationId(simplifyTypeRef(c.getSuperClass()), aliasByQn, qnBySimple);
            out.append(parent).append(" <|-- ").append(me).append('\n');
        }
        if (o.showImplementations) {
            for (String iface : c.getInterfaces()) {
                String parent = relationId(simplifyTypeRef(iface), aliasByQn, qnBySimple);
                out.append(parent).append(" <|.. ").append(me).append('\n');
            }
        }
    }

    private static void emitUsage(StringBuilder out, JavaClassInfo c,
                                   Set<String> known,
                                   java.util.Map<String, String> aliasByQn,
                                   java.util.Map<String, String> qnBySimple,
                                   Options o) {
        String me = aliasByQn.get(c.getQualifiedName());
        if (me == null) {
            return;
        }
        Set<String> emitted = new LinkedHashSet<>();
        int count = 0;
        for (JavaFieldInfo f : c.getFields()) {
            if (count >= o.maxUsagePerClass) {
                break;
            }
            String target = pickUsageTarget(f.getType(), known);
            if (target == null || target.equals(c.getQualifiedName())
                    || target.equals(c.getSimpleName())) {
                continue;
            }
            String tid = relationId(target, aliasByQn, qnBySimple);
            // 自己参照スキップ
            if (tid.equals(me)) {
                continue;
            }
            if (emitted.add(tid)) {
                out.append(me).append(" --> ").append(tid).append('\n');
                count++;
            }
        }
    }

    /**
     * 関係性の片端の識別子を返す。
     * - 完全修飾名で既知ならそのエイリアス
     * - 単純名で既知なら対応する完全修飾名のエイリアス
     * - 既知ではないなら引用符付き名 (PlantUML が暗黙生成)
     */
    private static String relationId(String typeRef,
                                      java.util.Map<String, String> aliasByQn,
                                      java.util.Map<String, String> qnBySimple) {
        if (typeRef == null || typeRef.isEmpty()) {
            return "\"?\"";
        }
        String alias = aliasByQn.get(typeRef);
        if (alias != null) {
            return alias;
        }
        String qn = qnBySimple.get(typeRef);
        if (qn != null) {
            String a = aliasByQn.get(qn);
            if (a != null) {
                return a;
            }
        }
        // 未定義: PlantUML に暗黙作成させる。"a.b.C" 形式は namespace 扱いされうるため
        // 末尾の単純名のみを使う。
        String simple = typeRef;
        int lastDot = simple.lastIndexOf('.');
        if (lastDot >= 0) {
            simple = simple.substring(lastDot + 1);
        }
        return quoteId(simple);
    }

    /** 型参照 (たとえば {@code Map<String, Foo>}) から、利用対象となるユーザ定義型を推定する。 */
    static String pickUsageTarget(String type, Set<String> known) {
        if (type == null || type.isEmpty()) {
            return null;
        }
        String t = type.replaceAll("\\[\\]", "").trim();
        // 一番外側のジェネリックがあれば、その引数を再帰的に検索
        int lt = t.indexOf('<');
        if (lt >= 0) {
            int gt = t.lastIndexOf('>');
            String inner = (gt > lt) ? t.substring(lt + 1, gt) : "";
            String outer = t.substring(0, lt).trim();
            String tgt = matchKnown(outer, known);
            if (tgt != null) {
                return tgt;
            }
            for (String part : splitTopLevelCsv(inner)) {
                String r = pickUsageTarget(part.trim(), known);
                if (r != null) {
                    return r;
                }
            }
            return null;
        }
        return matchKnown(t, known);
    }

    private static String matchKnown(String name, Set<String> known) {
        if (PRIMITIVE_OR_BUILTIN.matcher(name).matches()) {
            return null;
        }
        if (known.contains(name)) {
            return name;
        }
        for (String k : known) {
            if (k.endsWith("." + name)) {
                return k;
            }
        }
        return null;
    }

    private static String simplifyTypeRef(String t) {
        if (t == null) {
            return "";
        }
        // ジェネリクスを除く
        int lt = t.indexOf('<');
        return (lt >= 0 ? t.substring(0, lt) : t).trim();
    }

    private static List<String> splitTopLevelCsv(String s) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '<' || c == '(') {
                depth++;
            } else if (c == '>' || c == ')') {
                depth--;
            }
            if (c == ',' && depth == 0) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(c);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    /**
     * 外部ライブラリ由来クラス判定。
     * Origin が EXTERNAL_JAR / MISSING_JAR、またはパッケージ名が
     * {@link Options#externalPackagePrefixes} に前方一致する場合に true。
     */
    private static boolean isExternalClass(JavaClassInfo c, Options o) {
        JavaClassInfo.Origin origin = c.getOrigin();
        if (origin == JavaClassInfo.Origin.EXTERNAL_JAR
                || origin == JavaClassInfo.Origin.MISSING_JAR) {
            return true;
        }
        return ExternalPackageMatcher.isExternal(c.getPackageName(), o.externalPackagePrefixes);
    }

    /** クラスが {@code public} 修飾子を持つか (publicOnly フィルタ用)。 */
    private static boolean isPublicLike(JavaClassInfo c) {
        List<String> mods = c.getModifiers();
        return mods != null && mods.contains("public");
    }

    private PlantUmlClassDiagram() {
    }
}
