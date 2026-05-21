package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * クラス情報からメソッド呼び出しを抽出して PlantUML 形式のシーケンス図を生成する。
 *
 * <p>特定メソッド (entryClass + entryMethod) を起点に、その本体で行われる
 * 呼び出しを並べる。呼び出し先メソッドが入力に含まれているクラスのものなら、
 * 設定上限 ({@link Options#maxDepth}) まで再帰的に展開する。
 * 制御構造 ({@code if/while/for/do/switch/try/synchronized}) は PlantUML の
 * {@code alt/opt/loop/group/critical} に変換する。</p>
 */
public final class PlantUmlSequenceDiagram {

    /** Consumer/Predicate/Function 等の汎用 SAM 名。参加者名の決定で calling method 名を優先する。 */
    private static final Set<String> GENERIC_SAM_NAMES = new HashSet<>(Arrays.asList(
            "accept", "test", "apply", "get",
            "getAsInt", "getAsLong", "getAsDouble", "<inline>"));

    /**
     * Stream 中間/終端操作のうち、コールバックを持たない場合にシーケンス図から除外するメソッド名。
     * ラムダ引数がある場合は除外しない。
     */
    private static final Set<String> STREAM_OPS = new HashSet<>(Arrays.asList(
            "stream", "parallelStream",
            "filter", "map", "flatMap",
            "mapToInt", "mapToLong", "mapToDouble", "mapToObj",
            "flatMapToInt", "flatMapToLong", "flatMapToDouble",
            "sorted", "distinct", "limit", "skip", "peek",
            "boxed", "sequential", "parallel", "unordered",
            "collect", "toList", "toArray",
            "count", "findFirst", "findAny",
            "anyMatch", "allMatch", "noneMatch",
            "min", "max", "sum", "average", "reduce", "summaryStatistics"));

    /** 繰り返し意味論を持つ操作名。コールバック展開を {@code loop} ブロックで囲む。 */
    private static final Set<String> FOR_EACH_OPS = new HashSet<>(Arrays.asList(
            "forEach", "forEachOrdered"));

    /** コメントを表示する位置。 */
    public enum CommentPlacement {
        /** 各メソッド呼び出しの直下に note を出す (デフォルト)。 */
        AT_CALL_SITE,
        /** participant 宣言の直後に participant ごとに集約 note を出す (旧動作)。 */
        PARTICIPANT_TOP
    }

    /** 出力オプション。 */
    public static class Options {
        /** ダイアグラムのタイトル。 */
        public String title;
        /** 起点メソッドを呼び出す仮想の participant 名。 */
        public String callerName = "Caller";
        /** receiver が空 (this 呼び出し) の場合に使用する自クラス名のサフィックス。 */
        public boolean inlineSelfCalls = true;
        /** 凡例ブロックを末尾に追加する。 */
        public boolean includeLegend = true;
        /**
         * 再帰展開の最大深さ。1 = 起点メソッドのみ、2 以上で呼び出し先の本体も展開。
         * 0 以下なら制限なし (ただしサイクル検出で停止する)。デフォルト 5。
         */
        public int maxDepth = 5;
        /**
         * プロジェクト内で解析済みのクラス (= 入力 {@code classes} に含まれるクラス)
         * に該当する participant を色付けする。外部ライブラリやシステムクラス
         * との視認性を高めるために使用する。デフォルト true。
         */
        public boolean highlightProjectClasses = true;
        /**
         * プロジェクト内クラスの participant 背景色 (PlantUML 形式)。
         * 例: {@code "#LightSkyBlue"}, {@code "#FFE4B5"}, {@code "#ADD8E6"}。
         * {@link #highlightProjectClasses} が true のときのみ使用される。
         * null または空文字を指定すると色付けを行わない。
         */
        public String projectClassColor = "#LightSkyBlue";
        /**
         * クラス/メソッドの JavaDoc・直前コメントを {@code note} として出力する。
         * 位置は {@link #commentPlacement} で制御する。
         */
        public boolean showComments = true;
        /**
         * コメントの表示位置。
         * {@link CommentPlacement#AT_CALL_SITE}: 各呼び出しの直下に出す (デフォルト)。
         * {@link CommentPlacement#PARTICIPANT_TOP}: participant 宣言直後に集約。
         */
        public CommentPlacement commentPlacement = CommentPlacement.AT_CALL_SITE;
        /** コメント表示スタイル (クラス図と共通)。INLINE は 1 行 note、NOTE は複数行ブロック。 */
        public PlantUmlClassDiagram.CommentStyle commentStyle =
                PlantUmlClassDiagram.CommentStyle.INLINE;
        /** INLINE 時の 1 行最大文字数。NOTE 時の本体内コメント 1 行制限にも使用。0 以下で無制限。 */
        public int commentMaxLength = 80;
        /**
         * コメント文字列の色 (PlantUML の {@code <color:#RRGGBB>} 値)。
         * INLINE 表示時はテキストを {@code <color:...>...</color>} で囲み、
         * NOTE 表示時は {@code skinparam noteBorderColor / noteFontColor} に適用する。
         * null または空文字で色付け無効。
         */
        public String commentColor = "#008800";
        /** NOTE モードでメソッド本体内のコメントも note 内に列挙する。 */
        public boolean showMethodBodyComments = true;
        /**
         * 依存 JAR/AAR のクラス解決インデックス。{@code mService.foo()} のような
         * 外部クラス参照を participant として描画するために参照する。null 可。
         */
        public DependencyJarIndex dependencyIndex;
        /** 外部 JAR 由来 participant の装飾を有効にする (`<<external>>` ステレオタイプ)。 */
        public boolean showExternalParticipants = true;
        /** 解決できなかった依存先 participant の装飾を有効にする (`<<missing>>` + 警告色)。 */
        public boolean showMissingParticipants = true;
        /** 解決できなかった participant に使う背景色。 */
        public String missingParticipantColor = "#LightYellow";
        /** メソッド引数に渡されたコールバック/リスナー ({@code <<inline>>}) の背景色。null/空文字で色なし。 */
        public String inlineParticipantColor = "#FFE4E1";
        /**
         * メソッド呼び出しラベルにクラス名を付けるか。
         * true (デフォルト) なら {@code "A -> B: B.method()"} の形で出力する。
         * false なら旧動作の {@code "A -> B: method()"}。
         */
        public boolean qualifyMethodNames = true;
        /**
         * シーケンス図から除外する participant 名の集合。
         * 含まれる participant への呼び出しは {@code A -> B: ...} 行も note も
         * 出力されず、ボディも再帰展開されない。
         * null/空なら全 participant を表示する (デフォルト)。
         */
        public Set<String> hiddenParticipants;
    }

    /** クラス・メソッドを指定して 1 本のシーケンス図を生成する。 */
    public static String generate(List<JavaClassInfo> classes,
                                  String entryClass,
                                  String entryMethod,
                                  Options opts) {
        if (classes == null) {
            throw new IllegalArgumentException("classes is null");
        }
        if (entryClass == null || entryMethod == null) {
            throw new IllegalArgumentException("entryClass/entryMethod is null");
        }
        Options o = opts != null ? opts : new Options();
        JavaClassInfo cls = findClass(classes, entryClass);
        if (cls == null) {
            return emptyDiagram(o, "Class not found: " + entryClass);
        }
        JavaMethodInfo method = findMethod(cls, entryMethod);
        if (method == null) {
            return emptyDiagram(o, "Method not found: " + entryClass + "." + entryMethod);
        }

        StringBuilder out = new StringBuilder();
        out.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            out.append("title ").append(o.title).append('\n');
        } else {
            out.append("title ").append(cls.getSimpleName()).append('.')
                    .append(method.getName()).append('\n');
        }
        // NOTE モードでコメント色が指定されていれば、note の枠線・文字色を skinparam で設定
        if (o.showComments
                && o.commentStyle == PlantUmlClassDiagram.CommentStyle.NOTE
                && o.commentColor != null
                && !o.commentColor.isEmpty()) {
            out.append("skinparam noteBorderColor ").append(o.commentColor).append('\n');
            out.append("skinparam noteFontColor ").append(o.commentColor).append('\n');
        }
        Set<String> participants = new LinkedHashSet<>();
        Set<String> inlineParticipants = new LinkedHashSet<>();
        participants.add(o.callerName);
        // 起点クラスが除外指定されていれば、空のシーケンスとして扱う
        boolean entryHidden = isHidden(o, cls.getSimpleName());
        if (!entryHidden) {
            participants.add(cls.getSimpleName());
        }
        // participant ごとに「シーケンス内で登場したメソッド名」を順序付きで記録する。
        // 冒頭の note 集約 (emitCommentNotes) で JavaDoc を表示するメソッドを決めるのに使う。
        Map<String, LinkedHashSet<String>> participantMethods = new LinkedHashMap<>();
        if (!entryHidden) {
            participantMethods.computeIfAbsent(cls.getSimpleName(),
                    k -> new LinkedHashSet<>()).add(method.getName());
        }

        StringBuilder body = new StringBuilder();
        if (!entryHidden) {
            body.append(o.callerName).append(" -> ").append(cls.getSimpleName())
                    .append(": ").append(formatCallLabel(cls.getSimpleName(),
                            method.getName(), o)).append('\n');
            emitCallSiteComment(body, "", cls.getSimpleName(), method, o);
            body.append("activate ").append(cls.getSimpleName()).append('\n');

            Set<String> stack = new HashSet<>();
            stack.add(cls.getSimpleName() + "." + method.getName());
            walkStatements(method.getStatements(), cls, classes, participants,
                    inlineParticipants, participantMethods, body, stack, 1, o, "");
            stack.remove(cls.getSimpleName() + "." + method.getName());

            body.append("deactivate ").append(cls.getSimpleName()).append('\n');
        }

        // walk 完了後に、解析対象外の participant を依存 JAR で解決 (EXTERNAL_JAR)
        // または missing 判定する。これにより emitCall 系の再帰呼び出しに
        // origin マップを引き回す必要がなくなる。
        Map<String, JavaClassInfo.Origin> participantOrigins
                = resolveParticipantOrigins(participants, cls, classes, o);

        // participant 宣言を先に書く。プロジェクト内で解析済みのクラスは色付けする。
        boolean colorize = o.highlightProjectClasses
                && o.projectClassColor != null
                && !o.projectClassColor.isEmpty();
        for (String p : participants) {
            out.append("participant ").append(quote(p));
            JavaClassInfo.Origin origin = participantOrigins.get(p);
            if (origin == JavaClassInfo.Origin.EXTERNAL_JAR && o.showExternalParticipants) {
                out.append(" <<external>>");
            } else if (origin == JavaClassInfo.Origin.MISSING_JAR && o.showMissingParticipants) {
                out.append(" <<missing>>");
                if (o.missingParticipantColor != null && !o.missingParticipantColor.isEmpty()) {
                    out.append(' ').append(o.missingParticipantColor);
                }
            } else if (inlineParticipants.contains(p)) {
                out.append(" <<inline>>");
                if (o.inlineParticipantColor != null && !o.inlineParticipantColor.isEmpty()) {
                    out.append(' ').append(o.inlineParticipantColor);
                }
            } else if (colorize && findClass(classes, p) != null) {
                out.append(' ').append(o.projectClassColor);
            }
            // AIDL 生成 stub を継承する binder service 実装は、`<<binder>>`
            // ステレオタイプを付与して participant 上で IPC 境界を示す。
            // 既存色付け・external/missing と独立に追加する。
            JavaClassInfo pCls = findClass(classes, p);
            if (pCls != null && AaosPattern.isAidlBinderImpl(pCls)) {
                out.append(" <<binder>>");
            }
            out.append('\n');
        }
        // PARTICIPANT_TOP モードのときだけ、participant 宣言の直後に集約 note を発行する。
        // AT_CALL_SITE (デフォルト) では emitCall 側で各呼び出しの直下に note を出す。
        if (o.showComments && o.commentPlacement == CommentPlacement.PARTICIPANT_TOP) {
            PlantUmlSequenceComments.emit(out, o, participants, participantMethods, classes);
        }
        out.append(body);
        if (o.includeLegend) {
            emitLegend(out, participants.size(), colorize, o.projectClassColor,
                    !inlineParticipants.isEmpty());
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * 指定された entry を起点にした場合に登場し得る participant 名を列挙する。
     * フィルタダイアログ等で利用するため、隠す側 ({@link Options#hiddenParticipants})
     * の設定は無視して、本来出るはずだった全 participant を返す。
     *
     * @return 出現順 ({@code Caller}, 起点クラス, 以降は呼び出し順) を保持した集合。
     *         entryClass / entryMethod が見つからない場合は空。
     */
    public static Set<String> collectParticipants(List<JavaClassInfo> classes,
                                                   String entryClass,
                                                   String entryMethod,
                                                   Options opts) {
        Set<String> result = new LinkedHashSet<>();
        if (classes == null || entryClass == null || entryMethod == null) {
            return result;
        }
        JavaClassInfo cls = findClass(classes, entryClass);
        if (cls == null) {
            return result;
        }
        JavaMethodInfo method = findMethod(cls, entryMethod);
        if (method == null) {
            return result;
        }
        // フィルタを無視した「全体像」用の Options を組み立てる
        Options o = opts != null ? opts : new Options();
        Options scanOpts = new Options();
        scanOpts.callerName = o.callerName;
        scanOpts.inlineSelfCalls = o.inlineSelfCalls;
        scanOpts.maxDepth = o.maxDepth;
        scanOpts.qualifyMethodNames = o.qualifyMethodNames;
        scanOpts.showComments = false;
        scanOpts.includeLegend = false;
        scanOpts.hiddenParticipants = null;
        result.add(scanOpts.callerName);
        result.add(cls.getSimpleName());
        Map<String, LinkedHashSet<String>> participantMethods = new LinkedHashMap<>();
        participantMethods.computeIfAbsent(cls.getSimpleName(),
                k -> new LinkedHashSet<>()).add(method.getName());
        Set<String> stack = new HashSet<>();
        stack.add(cls.getSimpleName() + "." + method.getName());
        // body は捨てる。participants だけ取り出す。
        StringBuilder discard = new StringBuilder();
        walkStatements(method.getStatements(), cls, classes, result,
                new HashSet<>(), participantMethods, discard, stack, 1, scanOpts, "");
        return result;
    }

    /** すべてのメソッドに対してシーケンス図を順に生成 (簡易プロジェクトサマリ用)。 */
    public static String generateAll(List<JavaClassInfo> classes, Options opts) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getStatements().isEmpty()) {
                    continue;
                }
                if (!first) {
                    out.append("newpage\n");
                }
                first = false;
                String single = generate(classes, c.getSimpleName(), m.getName(), opts);
                // @startuml/@enduml を 1 ファイルに統合する
                single = single.replaceFirst("^@startuml\\s*", "");
                single = single.replaceFirst("@enduml\\s*$", "");
                if (out.length() == 0) {
                    out.append("@startuml\n");
                }
                out.append(single);
            }
        }
        if (out.length() == 0) {
            return emptyDiagram(opts != null ? opts : new Options(), "No method calls found");
        }
        out.append("@enduml\n");
        return out.toString();
    }

    /**
     * シーケンス図に出現しうる Class.method 候補を列挙する。
     * 呼び出しを 1 件以上含むメソッドを優先 (count 降順、クラス名昇順)。
     */
    public static List<Candidate> listCandidates(List<JavaClassInfo> classes) {
        List<Candidate> list = new ArrayList<>();
        if (classes == null) {
            return list;
        }
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.isAbstract()) {
                    continue;
                }
                list.add(new Candidate(c.getSimpleName(), m.getName(),
                        m.getCalls().size(), m.getVisibility()));
            }
        }
        list.sort((a, b) -> {
            if (a.callCount != b.callCount) {
                return Integer.compare(b.callCount, a.callCount);
            }
            int n = a.className.compareTo(b.className);
            if (n != 0) {
                return n;
            }
            return a.methodName.compareTo(b.methodName);
        });
        return list;
    }

    /** {@link #listCandidates(List)} の戻り値要素。 */
    public static class Candidate {
        public final String className;
        public final String methodName;
        public final int callCount;
        public final Visibility visibility;

        public Candidate(String className, String methodName, int callCount,
                          Visibility visibility) {
            this.className = className;
            this.methodName = methodName;
            this.callCount = callCount;
            this.visibility = visibility;
        }

        /** {@code "Class.method"} の形式。 */
        public String getEntry() {
            return className + "." + methodName;
        }

        @Override
        public String toString() {
            return getEntry();
        }
    }

    /** 文ツリーを順に走査して、呼び出しと制御ブロックを emit する。 */
    private static void walkStatements(List<JavaMethodInfo.Statement> stmts,
                                        JavaClassInfo currentClass,
                                        List<JavaClassInfo> classes,
                                        Set<String> participants,
                                        Set<String> inlineParticipants,
                                        Map<String, LinkedHashSet<String>> participantMethods,
                                        StringBuilder body,
                                        Set<String> stack,
                                        int depth,
                                        Options opts,
                                        String indent) {
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                emitCall((JavaMethodInfo.Call) s, currentClass, classes,
                        participants, inlineParticipants, participantMethods,
                        body, stack, depth, opts, indent);
            } else if (s instanceof JavaMethodInfo.Return) {
                emitReturnStatement((JavaMethodInfo.Return) s,
                        currentClass.getSimpleName(), body, indent);
            } else if (s instanceof JavaMethodInfo.Throw) {
                emitThrowStatement((JavaMethodInfo.Throw) s,
                        currentClass.getSimpleName(), body, indent);
            } else if (s instanceof JavaMethodInfo.Block) {
                emitBlock((JavaMethodInfo.Block) s, currentClass, classes,
                        participants, inlineParticipants, participantMethods,
                        body, stack, depth, opts, indent);
            }
        }
    }

    private static void emitReturnStatement(JavaMethodInfo.Return r,
                                             String participant,
                                             StringBuilder body,
                                             String indent) {
        String expr = r.getExpression();
        String text = (expr == null || expr.isEmpty()) ? "return" : "return " + expr;
        if (text.length() > 80) {
            text = text.substring(0, 77) + "...";
        }
        body.append(indent).append("note over ").append(quote(participant))
                .append(" : ").append(text).append('\n');
    }

    private static void emitThrowStatement(JavaMethodInfo.Throw t,
                                            String participant,
                                            StringBuilder body,
                                            String indent) {
        String expr = t.getExpression();
        String text = (expr == null || expr.isEmpty()) ? "throw" : "throw " + expr;
        if (text.length() > 80) {
            text = text.substring(0, 77) + "...";
        }
        body.append(indent).append("note over ").append(quote(participant))
                .append(" : ").append(text).append('\n');
    }

    private static void emitCall(JavaMethodInfo.Call call,
                                  JavaClassInfo currentClass,
                                  List<JavaClassInfo> classes,
                                  Set<String> participants,
                                  Set<String> inlineParticipants,
                                  Map<String, LinkedHashSet<String>> participantMethods,
                                  StringBuilder body,
                                  Set<String> stack,
                                  int depth,
                                  Options opts,
                                  String indent) {
        String target = resolveTarget(currentClass, call, opts);
        if (target.equals(currentClass.getSimpleName()) && !opts.inlineSelfCalls) {
            return;
        }
        // 除外指定された participant への呼び出しは矢印も note も出力しない
        if (isHidden(opts, target)) {
            return;
        }
        // Stream 中間/終端操作でコールバックがない場合はノイズを抑制
        if (STREAM_OPS.contains(call.getMethodName()) && call.getInlineMethods().isEmpty()) {
            return;
        }
        participants.add(target);
        participantMethods.computeIfAbsent(target, k -> new LinkedHashSet<>())
                .add(call.getMethodName());
        body.append(indent).append(currentClass.getSimpleName())
                .append(" -> ").append(target)
                .append(": ").append(formatCallLabel(target, call.getMethodName(),
                        call.getFirstArgLabel(), opts))
                .append('\n');

        // 解析済みクラスに該当する呼び出し先メソッドを引いておく (note と再帰展開で共有)
        JavaClassInfo nextCls = findClass(classes, target);
        JavaMethodInfo nextMethod = nextCls != null
                ? findMethod(nextCls, call.getMethodName()) : null;
        // AT_CALL_SITE モードのとき、呼び出しの直下に呼ばれるメソッドのコメント note を出す
        emitCallSiteComment(body, indent, target, nextMethod, opts);

        // 再帰展開: 呼び出し先メソッドが解析済みクラスにあれば深掘りする
        boolean canRecurse = opts.maxDepth <= 0 || depth < opts.maxDepth;
        if (!canRecurse) {
            return;
        }
        // Case 1/1b: コールバックがある場合は nextCls の有無・メソッド定義の有無に関わらず展開する。
        // nextCls != null && nextMethod == null のケースはチェーン呼び出しで receiver が
        // 自クラスに誤解決された場合 (list.stream().forEach(lambda) 等) を想定している。
        if (!call.getInlineMethods().isEmpty() && (nextCls == null || nextMethod == null)) {
            // forEach/forEachOrdered は繰り返し意味論を持つので loop ブロックで囲む
            boolean isLoop = FOR_EACH_OPS.contains(call.getMethodName());
            if (isLoop) {
                body.append(indent).append("loop ").append(call.getMethodName()).append('\n');
            }
            for (JavaMethodInfo inline : call.getInlineMethods()) {
                // 参加者名: 定義元クラス$コールバック識別子
                // SAM 名が汎用的 (accept/test/apply/get 等) なら calling method 名を使う
                String inlineLabel = GENERIC_SAM_NAMES.contains(inline.getName())
                        ? call.getMethodName() : inline.getName();
                String inlineName = currentClass.getSimpleName() + "$" + inlineLabel;
                inlineParticipants.add(inlineName);
                participants.add(inlineName);
                participantMethods.computeIfAbsent(inlineName, k -> new LinkedHashSet<>())
                        .add(inlineLabel);
                String inlineKey = inlineName + "." + inlineLabel;
                if (stack.contains(inlineKey)) {
                    body.append(indent).append("note over ").append(quote(inlineName))
                            .append(" : recursive call (").append(inlineLabel).append(")\n");
                    continue;
                }
                body.append(indent).append(target).append(" -> ").append(quote(inlineName))
                        .append(": ").append(inlineLabel).append("()\n");
                if (inline.getStatements().isEmpty()) {
                    continue;
                }
                body.append(indent).append("activate ").append(quote(inlineName)).append('\n');
                stack.add(inlineKey);
                // inline body 内の `this.foo()` を定義元クラスに解決させるため currentClass を保つ
                walkStatements(inline.getStatements(), currentClass, classes,
                        participants, inlineParticipants, participantMethods,
                        body, stack, depth + 1, opts, isLoop ? indent + "    " : indent + "  ");
                stack.remove(inlineKey);
                body.append(indent).append("deactivate ").append(quote(inlineName)).append('\n');
            }
            if (isLoop) {
                body.append(indent).append("end\n");
            }
            return;
        }
        if (nextCls == null) {
            // Case 2: フィールド初期化子 (匿名クラス / ラムダ) で定義された inline メソッド
            JavaMethodInfo inline = findInlineMethod(currentClass, call);
            if (inline == null || inline.getStatements().isEmpty()) {
                return;
            }
            String inlineKey = target + "." + inline.getName();
            if (stack.contains(inlineKey)) {
                body.append(indent).append("note over ").append(quote(target))
                        .append(" : recursive call (").append(inline.getName())
                        .append(")\n");
                return;
            }
            body.append(indent).append("activate ").append(target).append('\n');
            stack.add(inlineKey);
            // inline body 内の `this.foo()` を呼び出し元クラスに解決させるため currentClass を保つ
            walkStatements(inline.getStatements(), currentClass, classes,
                    participants, inlineParticipants, participantMethods,
                    body, stack, depth + 1, opts, indent);
            stack.remove(inlineKey);
            body.append(indent).append("deactivate ").append(target).append('\n');
            return;
        }
        if (nextMethod == null || nextMethod.getStatements().isEmpty()) {
            return;
        }
        String key = nextCls.getSimpleName() + "." + nextMethod.getName();
        if (stack.contains(key)) {
            // サイクル検出: 既にスタック上にいるので展開しない
            body.append(indent).append("note over ").append(quote(target))
                    .append(" : recursive call (").append(nextMethod.getName())
                    .append(")\n");
            return;
        }
        body.append(indent).append("activate ").append(target).append('\n');
        stack.add(key);
        walkStatements(nextMethod.getStatements(), nextCls, classes,
                participants, inlineParticipants, participantMethods,
                body, stack, depth + 1, opts, indent);
        stack.remove(key);
        body.append(indent).append("deactivate ").append(target).append('\n');
    }

    /**
     * 解析対象外の participant について、依存 JAR/AAR で解決できれば EXTERNAL_JAR、
     * 「依存に宣言されているのに見つからない」場合は MISSING_JAR としてマップに記録する。
     * 解析済みプロジェクトクラスや virtual な Caller は対象外。
     */
    private static Map<String, JavaClassInfo.Origin> resolveParticipantOrigins(
            Set<String> participants, JavaClassInfo entry, List<JavaClassInfo> classes,
            Options o) {
        Map<String, JavaClassInfo.Origin> out = new LinkedHashMap<>();
        if (o.dependencyIndex == null) {
            return out;
        }
        for (String p : participants) {
            if (o.callerName.equals(p)) {
                continue;
            }
            if (findClass(classes, p) != null) {
                continue;
            }
            Optional<JavaClassInfo> resolved = o.dependencyIndex.resolve(p);
            if (resolved.isPresent()) {
                out.put(p, JavaClassInfo.Origin.EXTERNAL_JAR);
            } else if (o.dependencyIndex.isDeclaredButMissing(p)) {
                out.put(p, JavaClassInfo.Origin.MISSING_JAR);
            }
        }
        return out;
    }

    /**
     * 呼び出し receiver が {@code currentClass} のフィールドであり、そのフィールド初期化子
     * (匿名クラス / ラムダ) で定義された inline メソッドの中から、呼び出されたメソッド名に
     * 合致するものを返す。見つからなければ null。SAM 名が解決できなかったため
     * {@code <inline>} で保持されているケースは receiver さえ一致すれば fall through する。
     */
    private static JavaMethodInfo findInlineMethod(JavaClassInfo currentClass,
                                                    JavaMethodInfo.Call call) {
        if (currentClass == null || call == null) {
            return null;
        }
        String receiver = call.getReceiver();
        if (receiver == null || receiver.isEmpty()) {
            return null;
        }
        String head = receiver;
        int dot = head.indexOf('.');
        if (dot >= 0) {
            head = head.substring(0, dot);
        }
        for (JavaFieldInfo f : currentClass.getFields()) {
            if (!head.equals(f.getName())) {
                continue;
            }
            for (JavaMethodInfo m : f.getInlineMethods()) {
                if (call.getMethodName().equals(m.getName())
                        || "<inline>".equals(m.getName())) {
                    return m;
                }
            }
        }
        return null;
    }

    /** call ラベルの整形。{@link Options#qualifyMethodNames} 次第で {@code Class.method()} 形に。 */
    private static String formatCallLabel(String target, String methodName, Options o) {
        return formatCallLabel(target, methodName, null, o);
    }

    /**
     * 引数ラベル付きの call ラベル整形。{@code firstArgLabel} に値があれば
     * {@code method(<arg>)} 形式で添える (例:
     * {@code getProperty(VehiclePropertyIds.HVAC_TEMPERATURE_SET)})。
     * null/空のときは引数なし表記 ({@code method()})。
     */
    private static String formatCallLabel(String target, String methodName,
                                            String firstArgLabel, Options o) {
        String parens = firstArgLabel != null && !firstArgLabel.isEmpty()
                ? "(" + firstArgLabel + ")"
                : "()";
        if (o.qualifyMethodNames && target != null && !target.isEmpty()) {
            return target + "." + methodName + parens;
        }
        return methodName + parens;
    }

    /** {@code o.hiddenParticipants} に含まれているかを安全に判定する。 */
    private static boolean isHidden(Options o, String participant) {
        return o != null && o.hiddenParticipants != null
                && participant != null
                && o.hiddenParticipants.contains(participant);
    }

    /**
     * AT_CALL_SITE モードのとき、呼び出し対象メソッドの JavaDoc コメントを
     * 呼び出し行の直下に note として出力する。method が解析対象外 (null) や
     * コメントが空のときは何も出さない。
     */
    private static void emitCallSiteComment(StringBuilder body, String indent,
                                             String target, JavaMethodInfo method,
                                             Options o) {
        if (o == null || !o.showComments
                || o.commentPlacement != CommentPlacement.AT_CALL_SITE) {
            return;
        }
        if (method == null || method.getComment() == null
                || method.getComment().isEmpty()) {
            return;
        }
        if (o.commentStyle == PlantUmlClassDiagram.CommentStyle.NOTE) {
            emitNoteBlockAtCall(body, indent, target, method, o);
        } else {
            emitInlineNoteAtCall(body, indent, target, method, o);
        }
    }

    /** INLINE: 呼び出しの直下に 1 行 note を出す。 */
    private static void emitInlineNoteAtCall(StringBuilder body, String indent,
                                              String target, JavaMethodInfo method,
                                              Options o) {
        String first = JavaCommentScanner.firstLine(method.getComment());
        if (first == null || first.isEmpty()) {
            return;
        }
        String line = PlantUmlClassDiagram.sanitizeInlineComment(first, o.commentMaxLength);
        if (line == null || line.isEmpty()) {
            return;
        }
        body.append(indent).append("note right of ").append(quote(target)).append(" : ");
        if (o.commentColor != null && !o.commentColor.isEmpty()) {
            body.append("<color:").append(o.commentColor).append('>')
                    .append(line).append("</color>");
        } else {
            body.append(line);
        }
        body.append('\n');
    }

    /** NOTE: 呼び出しの直下に複数行 note ブロックを出す。 */
    private static void emitNoteBlockAtCall(StringBuilder body, String indent,
                                             String target, JavaMethodInfo method,
                                             Options o) {
        boolean hasBody = o.showMethodBodyComments
                && method.getBodyComments() != null
                && !method.getBodyComments().isEmpty();
        body.append(indent).append("note right of ").append(quote(target)).append('\n');
        String[] lines = method.getComment().split("\n", -1);
        boolean any = false;
        for (String raw : lines) {
            String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
            if (t.isEmpty()) {
                continue;
            }
            body.append(indent).append("  ").append(truncate(t, o.commentMaxLength)).append('\n');
            any = true;
        }
        if (!any) {
            body.append(indent).append("  ").append(method.getName()).append("()\n");
        }
        if (hasBody) {
            for (String bc : method.getBodyComments()) {
                String[] bl = bc.split("\n", -1);
                for (String raw : bl) {
                    String t = raw.replace('\r', ' ').replace('\t', ' ').trim();
                    if (t.isEmpty()) {
                        continue;
                    }
                    body.append(indent).append("  // ")
                            .append(truncate(t, o.commentMaxLength)).append('\n');
                }
            }
        }
        body.append(indent).append("end note\n");
    }

    /** PlantUmlSequenceComments と独立に保持する短縮ユーティリティ。 */
    private static String truncate(String s, int maxLen) {
        if (maxLen <= 0 || s == null || s.length() <= maxLen) {
            return s;
        }
        return s.substring(0, Math.max(1, maxLen - 1)) + "…";
    }

    private static void emitBlock(JavaMethodInfo.Block block,
                                   JavaClassInfo currentClass,
                                   List<JavaClassInfo> classes,
                                   Set<String> participants,
                                   Set<String> inlineParticipants,
                                   Map<String, LinkedHashSet<String>> participantMethods,
                                   StringBuilder body,
                                   Set<String> stack,
                                   int depth,
                                   Options opts,
                                   String indent) {
        List<JavaMethodInfo.Branch> bs = block.getBranches();
        if (bs.isEmpty()) {
            return;
        }
        String inner = indent + "    ";
        switch (block.getKind()) {
            case IF:
                emitIf(bs, currentClass, classes, participants, inlineParticipants,
                        participantMethods, body, stack, depth, opts, indent, inner);
                break;
            case WHILE:
            case FOR:
            case DO_WHILE:
                emitLoop(block, bs.get(0), currentClass, classes, participants,
                        inlineParticipants, participantMethods, body, stack, depth, opts, indent, inner);
                break;
            case SWITCH:
                emitSwitch(bs, currentClass, classes, participants, inlineParticipants,
                        participantMethods, body, stack, depth, opts, indent, inner);
                break;
            case TRY:
                emitTry(bs, currentClass, classes, participants, inlineParticipants,
                        participantMethods, body, stack, depth, opts, indent, inner);
                break;
            case SYNCHRONIZED:
                emitSynchronized(bs.get(0), currentClass, classes, participants,
                        inlineParticipants, participantMethods, body, stack, depth, opts, indent, inner);
                break;
            default:
                break;
        }
    }

    private static void emitIf(List<JavaMethodInfo.Branch> bs,
                                JavaClassInfo currentClass,
                                List<JavaClassInfo> classes,
                                Set<String> participants,
                                Set<String> inlineParticipants,
                                Map<String, LinkedHashSet<String>> participantMethods,
                                StringBuilder body,
                                Set<String> stack,
                                int depth,
                                Options opts,
                                String indent,
                                String inner) {
        JavaMethodInfo.Branch first = bs.get(0);
        boolean hasElseChain = bs.size() > 1;
        if (!hasElseChain) {
            // 単一分岐 → opt
            body.append(indent).append("opt ").append(escapeLabel(first.getLabel())).append('\n');
            walkStatements(first.getBody(), currentClass, classes, participants,
                    inlineParticipants, participantMethods, body, stack, depth, opts, inner);
            body.append(indent).append("end\n");
            return;
        }
        // 複数分岐 → alt + else
        body.append(indent).append("alt ").append(escapeLabel(first.getLabel())).append('\n');
        walkStatements(first.getBody(), currentClass, classes, participants,
                inlineParticipants, participantMethods, body, stack, depth, opts, inner);
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            if ("else if".equals(b.getType())) {
                body.append(indent).append("else ").append(escapeLabel(b.getLabel())).append('\n');
            } else {
                body.append(indent).append("else\n");
            }
            walkStatements(b.getBody(), currentClass, classes, participants,
                    inlineParticipants, participantMethods, body, stack, depth, opts, inner);
        }
        body.append(indent).append("end\n");
    }

    private static void emitLoop(JavaMethodInfo.Block block,
                                  JavaMethodInfo.Branch br,
                                  JavaClassInfo currentClass,
                                  List<JavaClassInfo> classes,
                                  Set<String> participants,
                                  Set<String> inlineParticipants,
                                  Map<String, LinkedHashSet<String>> participantMethods,
                                  StringBuilder body,
                                  Set<String> stack,
                                  int depth,
                                  Options opts,
                                  String indent,
                                  String inner) {
        String label;
        switch (block.getKind()) {
            case WHILE:
                label = "while (" + br.getLabel() + ")";
                break;
            case FOR:
                label = "for (" + br.getLabel() + ")";
                break;
            case DO_WHILE:
                label = br.getLabel().isEmpty() ? "do-while" : "do-while (" + br.getLabel() + ")";
                break;
            default:
                label = br.getLabel();
                break;
        }
        body.append(indent).append("loop ").append(escapeLabel(label)).append('\n');
        walkStatements(br.getBody(), currentClass, classes, participants,
                inlineParticipants, participantMethods, body, stack, depth, opts, inner);
        body.append(indent).append("end\n");
    }

    private static void emitSwitch(List<JavaMethodInfo.Branch> bs,
                                    JavaClassInfo currentClass,
                                    List<JavaClassInfo> classes,
                                    Set<String> participants,
                                    Set<String> inlineParticipants,
                                    Map<String, LinkedHashSet<String>> participantMethods,
                                    StringBuilder body,
                                    Set<String> stack,
                                    int depth,
                                    Options opts,
                                    String indent,
                                    String inner) {
        // bs[0] は switch ヘッダ ("switch", cond)、残りが case/default
        JavaMethodInfo.Branch head = bs.get(0);
        if (bs.size() <= 1) {
            return;
        }
        String switchLabel = "switch (" + head.getLabel() + ")";
        // 最初の case を alt の条件部に、それ以降を else として連ねる
        boolean openedAlt = false;
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            String caseLabel;
            if ("default".equals(b.getType())) {
                caseLabel = "default";
            } else {
                caseLabel = "case " + b.getLabel();
            }
            if (!openedAlt) {
                body.append(indent).append("alt ").append(escapeLabel(switchLabel + " / " + caseLabel)).append('\n');
                openedAlt = true;
            } else {
                body.append(indent).append("else ").append(escapeLabel(caseLabel)).append('\n');
            }
            walkStatements(b.getBody(), currentClass, classes, participants,
                    inlineParticipants, participantMethods, body, stack, depth, opts, inner);
        }
        if (openedAlt) {
            body.append(indent).append("end\n");
        }
    }

    private static void emitTry(List<JavaMethodInfo.Branch> bs,
                                 JavaClassInfo currentClass,
                                 List<JavaClassInfo> classes,
                                 Set<String> participants,
                                 Set<String> inlineParticipants,
                                 Map<String, LinkedHashSet<String>> participantMethods,
                                 StringBuilder body,
                                 Set<String> stack,
                                 int depth,
                                 Options opts,
                                 String indent,
                                 String inner) {
        body.append(indent).append("group try\n");
        for (JavaMethodInfo.Branch b : bs) {
            if ("try".equals(b.getType())) {
                walkStatements(b.getBody(), currentClass, classes, participants,
                        inlineParticipants, participantMethods, body, stack, depth, opts, inner);
            } else if ("catch".equals(b.getType())) {
                body.append(indent).append("else catch ").append(escapeLabel(b.getLabel())).append('\n');
                walkStatements(b.getBody(), currentClass, classes, participants,
                        inlineParticipants, participantMethods, body, stack, depth, opts, inner);
            } else if ("finally".equals(b.getType())) {
                body.append(indent).append("else finally\n");
                walkStatements(b.getBody(), currentClass, classes, participants,
                        inlineParticipants, participantMethods, body, stack, depth, opts, inner);
            }
        }
        body.append(indent).append("end\n");
    }

    private static void emitSynchronized(JavaMethodInfo.Branch br,
                                          JavaClassInfo currentClass,
                                          List<JavaClassInfo> classes,
                                          Set<String> participants,
                                          Set<String> inlineParticipants,
                                          Map<String, LinkedHashSet<String>> participantMethods,
                                          StringBuilder body,
                                          Set<String> stack,
                                          int depth,
                                          Options opts,
                                          String indent,
                                          String inner) {
        body.append(indent).append("critical synchronized(")
                .append(escapeLabel(br.getLabel())).append(")\n");
        walkStatements(br.getBody(), currentClass, classes, participants,
                inlineParticipants, participantMethods, body, stack, depth, opts, inner);
        body.append(indent).append("end\n");
    }

    private static void emitLegend(StringBuilder out, int participantCount,
                                    boolean colorize, String projectClassColor,
                                    boolean hasInlineParticipants) {
        out.append("legend top left\n");
        out.append("== シーケンス図 ==\n");
        out.append("participant         関与クラス/オブジェクト\n");
        out.append("X →→ Y : Z.m()      X から Y への同期メッセージ (呼び出し先 Class.method)\n");
        out.append("note right of Y      呼び出し直下の呼び出し先メソッドコメント\n");
        out.append("activate Y          Y のアクティベーション開始\n");
        out.append("deactivate Y        Y のアクティベーション終了\n");
        out.append("alt/opt/loop        if-else / 単一分岐 / ループ (while/for/do)\n");
        out.append("group/critical      try-catch-finally / synchronized\n");
        if (colorize) {
            out.append("<back:").append(projectClassColor).append(">  </back>")
                    .append("              プロジェクト内で解析済みのクラス (独自クラス)\n");
        }
        out.append("<<external>>    依存 JAR/AAR で解決できた外部ライブラリのクラス\n");
        out.append("<<missing>>     依存宣言はあるが JAR/AAR が見つからないクラス (要確認)\n");
        if (hasInlineParticipants) {
            out.append("<<inline>>      引数に渡されたコールバック/リスナー (定義元クラス$メソッド名)\n");
        }
        if (participantCount > 0) {
            out.append("Caller              仮想の呼び出し元 (オプションで変更可)\n");
        }
        out.append("endlegend\n");
    }

    private static String resolveTarget(JavaClassInfo cls,
                                        JavaMethodInfo.Call call,
                                        Options o) {
        String receiver = call.getReceiver();
        if (receiver == null || receiver.isEmpty() || "this".equals(receiver)) {
            return cls.getSimpleName();
        }
        // フィールドへのアクセスなら、フィールド型を participant とする
        // 例: receiver = "mAudioService" → field "mAudioService: IAudioService" → "IAudioService"
        String head = receiver;
        int dot = head.indexOf('.');
        if (dot >= 0) {
            head = head.substring(0, dot);
        }
        for (JavaFieldInfo f : cls.getFields()) {
            if (head.equals(f.getName()) && f.getType() != null && !f.getType().isEmpty()) {
                String t = f.getType();
                int lt = t.indexOf('<');
                if (lt >= 0) {
                    t = t.substring(0, lt);
                }
                int last = t.lastIndexOf('.');
                if (last >= 0) {
                    t = t.substring(last + 1);
                }
                return t.replaceAll("\\[\\]", "Array").trim();
            }
        }
        // それ以外は receiver の先頭シンボルをそのまま使用
        return head;
    }

    private static JavaClassInfo findClass(List<JavaClassInfo> classes, String name) {
        for (JavaClassInfo c : classes) {
            if (c.getSimpleName().equals(name) || c.getQualifiedName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    private static JavaMethodInfo findMethod(JavaClassInfo cls, String name) {
        for (JavaMethodInfo m : cls.getMethods()) {
            if (name.equals(m.getName())) {
                return m;
            }
        }
        return null;
    }

    private static String quote(String s) {
        return "\"" + s.replace("\"", "\\\"") + "\"";
    }

    /** PlantUML の制御行で安全に書ける形に整形する (改行や 80 文字超を畳む)。 */
    private static String escapeLabel(String s) {
        if (s == null) {
            return "";
        }
        String trimmed = s.replaceAll("\\s+", " ").trim();
        // 長すぎるラベルは省略 (... を付ける)
        if (trimmed.length() > 80) {
            trimmed = trimmed.substring(0, 77) + "...";
        }
        return trimmed;
    }

    private static String emptyDiagram(Options o, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n");
        if (o.title != null && !o.title.isEmpty()) {
            sb.append("title ").append(o.title).append('\n');
        }
        sb.append("note over of \"info\" : ").append(reason).append('\n');
        sb.append("@enduml\n");
        return sb.toString();
    }

    private PlantUmlSequenceDiagram() {
    }
}
