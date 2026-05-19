package padtools.core.formats.uml;

import java.util.ArrayList;
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
         * 図の冒頭 (participant 宣言の直後) に participant ごとに集約表示する。
         */
        public boolean showComments = true;
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
        participants.add(o.callerName);
        participants.add(cls.getSimpleName());
        // participant ごとに「シーケンス内で登場したメソッド名」を順序付きで記録する。
        // 冒頭の note 集約 (emitCommentNotes) で JavaDoc を表示するメソッドを決めるのに使う。
        Map<String, LinkedHashSet<String>> participantMethods = new LinkedHashMap<>();
        participantMethods.computeIfAbsent(cls.getSimpleName(),
                k -> new LinkedHashSet<>()).add(method.getName());
        StringBuilder body = new StringBuilder();
        body.append(o.callerName).append(" -> ").append(cls.getSimpleName())
                .append(": ").append(method.getName()).append("()\n");
        body.append("activate ").append(cls.getSimpleName()).append('\n');

        Set<String> stack = new HashSet<>();
        stack.add(cls.getSimpleName() + "." + method.getName());
        walkStatements(method.getStatements(), cls, classes, participants,
                participantMethods, body, stack, 1, o, "");
        stack.remove(cls.getSimpleName() + "." + method.getName());

        body.append("deactivate ").append(cls.getSimpleName()).append('\n');

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
            } else if (colorize && findClass(classes, p) != null) {
                out.append(' ').append(o.projectClassColor);
            }
            out.append('\n');
        }
        // participant 宣言の直後に JavaDoc / コメントを集約 note として発行
        if (o.showComments) {
            PlantUmlSequenceComments.emit(out, o, participants, participantMethods, classes);
        }
        out.append(body);
        if (o.includeLegend) {
            emitLegend(out, participants.size(), colorize, o.projectClassColor);
        }
        out.append("@enduml\n");
        return out.toString();
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
                                        Map<String, LinkedHashSet<String>> participantMethods,
                                        StringBuilder body,
                                        Set<String> stack,
                                        int depth,
                                        Options opts,
                                        String indent) {
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                emitCall((JavaMethodInfo.Call) s, currentClass, classes,
                        participants, participantMethods, body, stack, depth, opts, indent);
            } else if (s instanceof JavaMethodInfo.Block) {
                emitBlock((JavaMethodInfo.Block) s, currentClass, classes,
                        participants, participantMethods, body, stack, depth, opts, indent);
            }
        }
    }

    private static void emitCall(JavaMethodInfo.Call call,
                                  JavaClassInfo currentClass,
                                  List<JavaClassInfo> classes,
                                  Set<String> participants,
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
        participants.add(target);
        participantMethods.computeIfAbsent(target, k -> new LinkedHashSet<>())
                .add(call.getMethodName());
        body.append(indent).append(currentClass.getSimpleName())
                .append(" -> ").append(target)
                .append(": ").append(call.getMethodName()).append("()\n");

        // 再帰展開: 呼び出し先メソッドが解析済みクラスにあれば深掘りする
        boolean canRecurse = opts.maxDepth <= 0 || depth < opts.maxDepth;
        if (!canRecurse) {
            return;
        }
        JavaClassInfo nextCls = findClass(classes, target);
        if (nextCls == null) {
            // 解析済みクラスではない受信者: フィールド初期化子 (匿名クラス / ラムダ) で
            // 定義された inline メソッドがあれば、その本体を展開する。
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
                    participants, participantMethods, body, stack, depth + 1, opts, indent);
            stack.remove(inlineKey);
            body.append(indent).append("deactivate ").append(target).append('\n');
            return;
        }
        JavaMethodInfo nextMethod = findMethod(nextCls, call.getMethodName());
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
                participants, participantMethods, body, stack, depth + 1, opts, indent);
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

    private static void emitBlock(JavaMethodInfo.Block block,
                                   JavaClassInfo currentClass,
                                   List<JavaClassInfo> classes,
                                   Set<String> participants,
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
                emitIf(bs, currentClass, classes, participants, participantMethods,
                        body, stack, depth, opts, indent, inner);
                break;
            case WHILE:
            case FOR:
            case DO_WHILE:
                emitLoop(block, bs.get(0), currentClass, classes, participants,
                        participantMethods, body, stack, depth, opts, indent, inner);
                break;
            case SWITCH:
                emitSwitch(bs, currentClass, classes, participants, participantMethods,
                        body, stack, depth, opts, indent, inner);
                break;
            case TRY:
                emitTry(bs, currentClass, classes, participants, participantMethods,
                        body, stack, depth, opts, indent, inner);
                break;
            case SYNCHRONIZED:
                emitSynchronized(bs.get(0), currentClass, classes, participants,
                        participantMethods, body, stack, depth, opts, indent, inner);
                break;
            default:
                break;
        }
    }

    private static void emitIf(List<JavaMethodInfo.Branch> bs,
                                JavaClassInfo currentClass,
                                List<JavaClassInfo> classes,
                                Set<String> participants,
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
                    participantMethods, body, stack, depth, opts, inner);
            body.append(indent).append("end\n");
            return;
        }
        // 複数分岐 → alt + else
        body.append(indent).append("alt ").append(escapeLabel(first.getLabel())).append('\n');
        walkStatements(first.getBody(), currentClass, classes, participants,
                participantMethods, body, stack, depth, opts, inner);
        for (int i = 1; i < bs.size(); i++) {
            JavaMethodInfo.Branch b = bs.get(i);
            if ("else if".equals(b.getType())) {
                body.append(indent).append("else ").append(escapeLabel(b.getLabel())).append('\n');
            } else {
                body.append(indent).append("else\n");
            }
            walkStatements(b.getBody(), currentClass, classes, participants,
                    participantMethods, body, stack, depth, opts, inner);
        }
        body.append(indent).append("end\n");
    }

    private static void emitLoop(JavaMethodInfo.Block block,
                                  JavaMethodInfo.Branch br,
                                  JavaClassInfo currentClass,
                                  List<JavaClassInfo> classes,
                                  Set<String> participants,
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
                participantMethods, body, stack, depth, opts, inner);
        body.append(indent).append("end\n");
    }

    private static void emitSwitch(List<JavaMethodInfo.Branch> bs,
                                    JavaClassInfo currentClass,
                                    List<JavaClassInfo> classes,
                                    Set<String> participants,
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
                    participantMethods, body, stack, depth, opts, inner);
        }
        if (openedAlt) {
            body.append(indent).append("end\n");
        }
    }

    private static void emitTry(List<JavaMethodInfo.Branch> bs,
                                 JavaClassInfo currentClass,
                                 List<JavaClassInfo> classes,
                                 Set<String> participants,
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
                        participantMethods, body, stack, depth, opts, inner);
            } else if ("catch".equals(b.getType())) {
                body.append(indent).append("else catch ").append(escapeLabel(b.getLabel())).append('\n');
                walkStatements(b.getBody(), currentClass, classes, participants,
                        participantMethods, body, stack, depth, opts, inner);
            } else if ("finally".equals(b.getType())) {
                body.append(indent).append("else finally\n");
                walkStatements(b.getBody(), currentClass, classes, participants,
                        participantMethods, body, stack, depth, opts, inner);
            }
        }
        body.append(indent).append("end\n");
    }

    private static void emitSynchronized(JavaMethodInfo.Branch br,
                                          JavaClassInfo currentClass,
                                          List<JavaClassInfo> classes,
                                          Set<String> participants,
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
                participantMethods, body, stack, depth, opts, inner);
        body.append(indent).append("end\n");
    }

    private static void emitLegend(StringBuilder out, int participantCount,
                                    boolean colorize, String projectClassColor) {
        out.append("legend right\n");
        out.append("== シーケンス図 ==\n");
        out.append("participant     関与クラス/オブジェクト\n");
        out.append("A -> B : msg    A から B への同期メッセージ呼び出し\n");
        out.append("activate B      B のアクティベーション開始\n");
        out.append("deactivate B    B のアクティベーション終了\n");
        out.append("alt/opt/loop    if-else / 単一分岐 / ループ (while/for/do)\n");
        out.append("group/critical  try-catch-finally / synchronized\n");
        if (colorize) {
            out.append("<back:").append(projectClassColor).append(">  </back>")
                    .append("          プロジェクト内で解析済みのクラス (独自クラス)\n");
        }
        out.append("<<external>>    依存 JAR/AAR で解決できた外部ライブラリのクラス\n");
        out.append("<<missing>>     依存宣言はあるが JAR/AAR が見つからないクラス (要確認)\n");
        if (participantCount > 0) {
            out.append("Caller          仮想の呼び出し元 (オプションで変更可)\n");
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
