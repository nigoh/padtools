package padtools.core.formats.uml;

import padtools.core.formats.android.actions.MarkdownActionReport;
import padtools.core.formats.android.actions.UiActionEntry;
import padtools.core.refs.ReferenceIndex;
import padtools.core.refs.ReferenceKey;
import padtools.core.refs.ReferenceSite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 全クラスの関数（メソッド）一覧を、各関数の「利用側（呼び出し元）」と
 * 「実行条件（呼び出しを囲む分岐条件 / リスナーの UI トリガ）」付きで出力する。
 *
 * <p>出力形式は {@link Format} で選択する（Markdown テーブル または CSV）。
 * 署名整形は {@link PlantUmlClassDiagram#emitMethod} と同等のロジックをプレーン化したもの。
 * 呼び出し元は {@link ReferenceIndex}、実行条件はパース済み statement tree の分岐ラベルから求める。
 * ボタン押下時のリスナーは、コード内のラムダ/匿名本体に加え、{@code UiActionScanner} が検出した
 * XML / Compose / メニューのアクションを「## UI Actions」セクション（CSV では ui-action 行）に併記する。</p>
 */
public final class MethodUsageReport {

    /** 出力形式。 */
    public enum Format {
        /** Markdown テーブル（1メソッド=1行 + UI Actions 表）。 */
        TABLE,
        /** カンマ区切り（スプレッドシート取込向け）。 */
        CSV;

        /** 文字列 ("table" / "csv") から形式を解決する。不明・null は TABLE。 */
        public static Format fromString(String s) {
            return s != null && "csv".equalsIgnoreCase(s.trim()) ? CSV : TABLE;
        }
    }

    private MethodUsageReport() {
    }

    public static String render(List<JavaClassInfo> classes,
                                ReferenceIndex refIndex,
                                List<UiActionEntry> actions) {
        return render(classes, refIndex, actions, Format.TABLE);
    }

    public static String render(List<JavaClassInfo> classes,
                                ReferenceIndex refIndex,
                                List<UiActionEntry> actions,
                                Format format) {
        List<Row> rows = buildRows(classes, refIndex);
        return format == Format.CSV ? renderCsv(rows, actions) : renderTable(rows, actions);
    }

    /** 1メソッド（または1リスナー）分の出力行。形式非依存の中間表現。 */
    private static final class Row {
        final String classFqn;
        final String kind;
        final String signature;
        final boolean listener;
        final List<String> callers;
        final List<String> conditions;

        Row(String classFqn, String kind, String signature, boolean listener,
            List<String> callers, List<String> conditions) {
            this.classFqn = classFqn;
            this.kind = kind;
            this.signature = signature;
            this.listener = listener;
            this.callers = callers;
            this.conditions = conditions;
        }
    }

    private static List<Row> buildRows(List<JavaClassInfo> classes, ReferenceIndex refIndex) {
        Map<String, JavaClassInfo> byQn = new HashMap<>();
        List<JavaClassInfo> sorted = new ArrayList<>();
        for (JavaClassInfo c : classes) {
            if (c.getKind() == JavaClassInfo.Kind.MODULE) {
                continue;
            }
            sorted.add(c);
            if (c.getQualifiedName() != null) {
                byQn.put(c.getQualifiedName(), c);
            }
        }
        sorted.sort(Comparator.comparing(c -> nz(c.getQualifiedName())));
        List<Row> rows = new ArrayList<>();
        for (JavaClassInfo c : sorted) {
            String qn = nz(c.getQualifiedName());
            String kind = String.valueOf(c.getKind());
            for (JavaMethodInfo m : c.getMethods()) {
                rows.add(methodRow(qn, kind, m, refIndex, byQn));
            }
            for (JavaMethodInfo listener : collectListeners(c)) {
                List<String> trigger = new ArrayList<>();
                trigger.add(triggerOf(listener.getName()));
                rows.add(new Row(qn, kind, signature(listener), true,
                        new ArrayList<>(), trigger));
            }
        }
        return rows;
    }

    private static Row methodRow(String qn, String kind, JavaMethodInfo m,
                                 ReferenceIndex refIndex, Map<String, JavaClassInfo> byQn) {
        List<ReferenceSite> sites = refIndex == null
                ? new ArrayList<>()
                : refIndex.sites(ReferenceKey.ofMethod(qn, nz(m.getName())));
        List<String> callers = new ArrayList<>();
        Set<String> conditions = new LinkedHashSet<>();
        for (ReferenceSite s : sites) {
            callers.add(callerLabel(s));
            JavaClassInfo caller = byQn.get(s.getCallerFqn());
            if (caller != null && !nz(s.getCallerMethod()).isEmpty()) {
                for (JavaMethodInfo cm : caller.getMethods()) {
                    if (s.getCallerMethod().equals(cm.getName())) {
                        collectConditions(cm.getStatements(), new ArrayDeque<>(),
                                nz(m.getName()), conditions);
                    }
                }
            }
        }
        return new Row(qn, kind, signature(m), false, callers, new ArrayList<>(conditions));
    }

    private static String renderTable(List<Row> rows, List<UiActionEntry> actions) {
        StringBuilder out = new StringBuilder();
        out.append("# 関数使用マップ (Function usage map)\n\n");
        out.append("| クラス | 種別 | 関数 | 利用側 | 実行条件 |\n");
        out.append("|---|---|---|---|---|\n");
        for (Row r : rows) {
            String sig = r.listener ? "[listener] " + r.signature : r.signature;
            out.append("| `").append(mdInline(r.classFqn)).append("` | ")
                    .append(mdInline(r.kind)).append(" | `")
                    .append(mdInline(sig)).append("` | ")
                    .append(callersCell(r)).append(" | ")
                    .append(conditionsCell(r)).append(" |\n");
        }
        out.append('\n');
        if (actions != null && !actions.isEmpty()) {
            out.append("## UI Actions（ボタン押下時のリスナー: XML / Compose / メニュー含む）\n\n");
            out.append(MarkdownActionReport.render(actions));
        }
        return out.toString();
    }

    private static String callersCell(Row r) {
        if (r.listener) {
            return "—";
        }
        return r.callers.isEmpty() ? "(呼び出し元なし)" : joinMd(r.callers);
    }

    private static String conditionsCell(Row r) {
        if (r.listener) {
            return joinMd(r.conditions);
        }
        return r.conditions.isEmpty() ? "(直接呼び出し)" : joinMd(r.conditions);
    }

    private static String joinMd(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) {
                sb.append("<br>");
            }
            sb.append(mdInline(items.get(i)));
        }
        return sb.toString();
    }

    private static String renderCsv(List<Row> rows, List<UiActionEntry> actions) {
        StringBuilder out = new StringBuilder();
        out.append("category,class,kind,signature,callers,conditions\n");
        for (Row r : rows) {
            out.append(csv(r.listener ? "listener" : "method")).append(',')
                    .append(csv(r.classFqn)).append(',')
                    .append(csv(r.kind)).append(',')
                    .append(csv(r.signature)).append(',')
                    .append(csv(r.listener ? "" : String.join("; ", r.callers))).append(',')
                    .append(csv(String.join("; ", r.conditions))).append('\n');
        }
        if (actions != null) {
            for (UiActionEntry a : actions) {
                out.append(csv("ui-action")).append(',')
                        .append(csv(a.componentId)).append(',')
                        .append(csv(a.actionType.label)).append(',')
                        .append(csv(a.handler)).append(',')
                        .append(csv(actionSource(a))).append(',')
                        .append(csv("")).append('\n');
            }
        }
        return out.toString();
    }

    private static String actionSource(UiActionEntry a) {
        String name = a.shortFileName();
        if (a.line > 0) {
            return name.isEmpty() ? String.valueOf(a.line) : name + ":" + a.line;
        }
        return name;
    }

    /** クラス内の `setOnClickListener(...)` 等で渡されたラムダ/匿名本体を収集する。 */
    private static List<JavaMethodInfo> collectListeners(JavaClassInfo c) {
        List<JavaMethodInfo> out = new ArrayList<>();
        for (JavaMethodInfo m : c.getMethods()) {
            for (JavaMethodInfo.Call call : m.getCalls()) {
                out.addAll(call.getInlineMethods());
            }
        }
        for (JavaFieldInfo f : c.getFields()) {
            out.addAll(f.getInlineMethods());
        }
        return out;
    }

    /**
     * caller の statement tree を再帰走査し、{@code calleeName} の呼び出しを囲む分岐条件
     * （if/while/switch/try 等のラベル連鎖）を {@code acc} に集約する。
     * 直接（無条件）呼び出しは記録しない（呼び出し元側で「直接呼び出し」と表示）。
     */
    private static void collectConditions(List<JavaMethodInfo.Statement> stmts,
                                          Deque<String> ctx, String calleeName, Set<String> acc) {
        for (JavaMethodInfo.Statement s : stmts) {
            if (s instanceof JavaMethodInfo.Call) {
                if (calleeName.equals(((JavaMethodInfo.Call) s).getMethodName()) && !ctx.isEmpty()) {
                    acc.add(String.join(" → ", ctx));
                }
            } else if (s instanceof JavaMethodInfo.Block) {
                for (JavaMethodInfo.Branch br : ((JavaMethodInfo.Block) s).getBranches()) {
                    ctx.addLast(branchLabel(br));
                    collectConditions(br.getBody(), ctx, calleeName, acc);
                    ctx.removeLast();
                }
            }
        }
    }

    private static String branchLabel(JavaMethodInfo.Branch br) {
        String type = nz(br.getType());
        String label = nz(br.getLabel());
        return label.isEmpty() ? type : type + " (" + label + ")";
    }

    /** SAM メソッド名から UI トリガの説明を返す。 */
    private static String triggerOf(String samName) {
        if (samName == null || samName.isEmpty()) {
            return "リスナー";
        }
        switch (samName) {
            case "onClick":
                return "クリック (onClick)";
            case "onLongClick":
                return "長押し (onLongClick)";
            case "onCheckedChanged":
                return "チェック変更 (onCheckedChanged)";
            case "onTouch":
                return "タッチ (onTouch)";
            default:
                return "リスナー (" + samName + ")";
        }
    }

    /** メソッド署名を `<可視性> [static] [abstract] name(name: type, ...): returnType` で整形。 */
    private static String signature(JavaMethodInfo m) {
        StringBuilder sb = new StringBuilder();
        if (m.getVisibility() != null) {
            sb.append(m.getVisibility().mark()).append(' ');
        }
        if (m.isStatic()) {
            sb.append("static ");
        }
        if (m.isAbstract()) {
            sb.append("abstract ");
        }
        sb.append(nz(m.getName())).append('(');
        List<String> types = m.getParameterTypes();
        List<String> names = m.getParameterNames();
        for (int i = 0; i < types.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String name = i < names.size() ? names.get(i) : "";
            if (name != null && !name.isEmpty()) {
                sb.append(name).append(": ");
            }
            sb.append(types.get(i) == null ? "?" : types.get(i));
        }
        sb.append(')');
        if (!m.isConstructor() && m.getReturnType() != null && !m.getReturnType().isEmpty()) {
            sb.append(": ").append(m.getReturnType());
        }
        return sb.toString();
    }

    private static String callerLabel(ReferenceSite s) {
        StringBuilder sb = new StringBuilder(nz(s.getCallerFqn()));
        if (!nz(s.getCallerMethod()).isEmpty()) {
            sb.append('.').append(s.getCallerMethod());
        }
        String file = nz(s.getFile());
        if (!file.isEmpty()) {
            int sep = Math.max(file.lastIndexOf('/'), file.lastIndexOf('\\'));
            String fileName = sep >= 0 ? file.substring(sep + 1) : file;
            sb.append(" (").append(fileName);
            if (s.getLineHint() > 0) {
                sb.append(':').append(s.getLineHint());
            }
            sb.append(')');
        }
        return sb.toString();
    }

    private static String mdInline(String s) {
        return nz(s).replace("|", "\\|").replace("\r", " ").replace("\n", " ");
    }

    private static String csv(String s) {
        String v = nz(s);
        if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
