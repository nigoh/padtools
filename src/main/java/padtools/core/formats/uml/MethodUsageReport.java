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
 * 「実行条件（呼び出しを囲む分岐条件 / リスナーの UI トリガ）」付きで Markdown 出力する。
 *
 * <p>署名整形は {@link PlantUmlClassDiagram#emitMethod} と同等のロジックをプレーン化したもの。
 * 呼び出し元は {@link ReferenceIndex}、実行条件はパース済み statement tree の分岐ラベルから求める。
 * ボタン押下時のリスナーは、コード内のラムダ/匿名本体に加え、{@code UiActionScanner} が検出した
 * XML / Compose / メニューのアクションを「## UI Actions」セクションに併記する。</p>
 */
public final class MethodUsageReport {

    private MethodUsageReport() {
    }

    public static String render(List<JavaClassInfo> classes,
                                ReferenceIndex refIndex,
                                List<UiActionEntry> actions) {
        StringBuilder out = new StringBuilder();
        out.append("# 関数使用マップ (Function usage map)\n\n");
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
        for (JavaClassInfo c : sorted) {
            renderClass(out, c, refIndex, byQn);
        }
        if (actions != null && !actions.isEmpty()) {
            out.append("## UI Actions（ボタン押下時のリスナー: XML / Compose / メニュー含む）\n\n");
            out.append(MarkdownActionReport.render(actions));
        }
        return out.toString();
    }

    private static void renderClass(StringBuilder out, JavaClassInfo c,
                                    ReferenceIndex refIndex, Map<String, JavaClassInfo> byQn) {
        out.append("## ").append(c.getKind()).append(' ').append(nz(c.getQualifiedName()))
                .append("\n\n");
        boolean any = false;
        for (JavaMethodInfo m : c.getMethods()) {
            renderMethod(out, c, m, refIndex, byQn);
            any = true;
        }
        for (JavaMethodInfo listener : collectListeners(c)) {
            renderListener(out, listener);
            any = true;
        }
        if (!any) {
            out.append("_(no methods)_\n");
        }
        out.append('\n');
    }

    private static void renderMethod(StringBuilder out, JavaClassInfo owner, JavaMethodInfo m,
                                     ReferenceIndex refIndex, Map<String, JavaClassInfo> byQn) {
        out.append("- `").append(signature(m)).append("`\n");
        List<ReferenceSite> sites = refIndex == null
                ? new ArrayList<>()
                : refIndex.sites(ReferenceKey.ofMethod(nz(owner.getQualifiedName()), nz(m.getName())));
        if (sites.isEmpty()) {
            out.append("    - 利用側: (呼び出し元なし)\n");
            out.append("    - 実行条件: (直接呼び出し)\n");
            return;
        }
        out.append("    - 利用側:\n");
        Set<String> conditions = new LinkedHashSet<>();
        for (ReferenceSite s : sites) {
            out.append("        - ").append(callerLabel(s)).append('\n');
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
        out.append("    - 実行条件: ")
                .append(conditions.isEmpty() ? "(直接呼び出し)" : String.join(" / ", conditions))
                .append('\n');
    }

    private static void renderListener(StringBuilder out, JavaMethodInfo listener) {
        out.append("- `[listener] ").append(signature(listener)).append("`\n");
        out.append("    - 実行条件: ").append(triggerOf(listener.getName())).append('\n');
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

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
