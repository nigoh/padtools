package padtools.core.formats.uml;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * クラス情報からメソッド呼び出しを抽出して PlantUML 形式のシーケンス図を生成する。
 *
 * <p>典型的には特定メソッド (entryClass + entryMethod) を起点とし、その本体で
 * 行われる呼び出しを順に並べる。受信側 receiver は識別子文字列なので、フィールド
 * との対応関係から「フィールド型 → クラス名」に置換できる場合は置換する。</p>
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
        Set<String> participants = new LinkedHashSet<>();
        participants.add(o.callerName);
        participants.add(cls.getSimpleName());

        StringBuilder body = new StringBuilder();
        body.append(o.callerName).append(" -> ").append(cls.getSimpleName())
                .append(": ").append(method.getName()).append("()\n");
        body.append("activate ").append(cls.getSimpleName()).append('\n');
        for (JavaMethodInfo.Call call : method.getCalls()) {
            String target = resolveTarget(cls, call, o);
            if (target.equals(cls.getSimpleName()) && !o.inlineSelfCalls) {
                continue;
            }
            participants.add(target);
            body.append(cls.getSimpleName()).append(" -> ").append(target)
                    .append(": ").append(call.getMethodName()).append("()\n");
        }
        body.append("deactivate ").append(cls.getSimpleName()).append('\n');

        // participant 宣言を先に書く
        for (String p : participants) {
            out.append("participant ").append(quote(p)).append('\n');
        }
        out.append(body);
        if (o.includeLegend) {
            emitLegend(out, participants.size());
        }
        out.append("@enduml\n");
        return out.toString();
    }

    private static void emitLegend(StringBuilder out, int participantCount) {
        out.append("legend right\n");
        out.append("== シーケンス図 ==\n");
        out.append("participant     関与クラス/オブジェクト\n");
        out.append("A -> B : msg    A から B への同期メッセージ呼び出し\n");
        out.append("activate B      B のアクティベーション開始\n");
        out.append("deactivate B    B のアクティベーション終了\n");
        if (participantCount > 0) {
            out.append("Caller          仮想の呼び出し元 (オプションで変更可)\n");
        }
        out.append("endlegend\n");
    }

    /** すべてのメソッドに対してシーケンス図を順に生成 (簡易プロジェクトサマリ用)。 */
    public static String generateAll(List<JavaClassInfo> classes, Options opts) {
        StringBuilder out = new StringBuilder();
        boolean first = true;
        for (JavaClassInfo c : classes) {
            for (JavaMethodInfo m : c.getMethods()) {
                if (m.getCalls().isEmpty()) {
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
