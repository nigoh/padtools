package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * メソッド宣言情報。
 *
 * <p>シーケンス図生成のため、メソッド本体内の文を構造化して
 * {@link #getStatements()} に保持する。各文は {@link Call}（メソッド呼び出し）
 * もしくは {@link Block}（{@code if/while/for/try/...} 等の制御ブロック）。
 * {@link #getCalls()} は後方互換のため、ネストを平坦化した呼び出し列を返す。</p>
 */
public class JavaMethodInfo {

    /** メソッド本体内の文の共通インタフェース。 */
    public interface Statement {
    }

    /** {@code receiver.method(...)} 形式の呼び出し情報。 */
    public static class Call implements Statement {
        private final String receiver;
        private final String methodName;

        public Call(String receiver, String methodName) {
            this.receiver = receiver;
            this.methodName = methodName;
        }

        public String getReceiver() {
            return receiver;
        }

        public String getMethodName() {
            return methodName;
        }
    }

    /**
     * 制御ブロック ({@code if}/{@code while}/{@code for}/{@code do-while}/
     * {@code switch}/{@code try}/{@code synchronized})。
     *
     * <p>{@code if/else if/else} や {@code try/catch/finally} のように
     * 複数の分岐を持つ構文は {@link Branch} の列で表現する。
     * 単一の本体しかもたない {@code while}/{@code for}/{@code synchronized} は
     * 分岐 1 つだけ持つ。</p>
     */
    public static class Block implements Statement {

        /** ブロック種別。 */
        public enum Kind { IF, WHILE, FOR, DO_WHILE, SWITCH, TRY, SYNCHRONIZED }

        private final Kind kind;
        private final List<Branch> branches = new ArrayList<>();

        public Block(Kind kind) {
            this.kind = kind;
        }

        public Kind getKind() {
            return kind;
        }

        public List<Branch> getBranches() {
            return branches;
        }
    }

    /** {@link Block} 内の 1 つの分岐 (if 節、case 節、catch 節など)。 */
    public static class Branch {
        /** "if" / "else if" / "else" / "case" / "default" / "try" / "catch" / "finally" / "while" / "for" / "do" / "synchronized" 等。 */
        private final String type;
        /** 条件式や case ラベル等の元ソース文字列。なければ空文字。 */
        private String label;
        private final List<Statement> body = new ArrayList<>();

        public Branch(String type, String label) {
            this.type = type;
            this.label = label == null ? "" : label;
        }

        public String getType() {
            return type;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label == null ? "" : label;
        }

        public List<Statement> getBody() {
            return body;
        }
    }

    private String name;
    private String returnType;
    private final List<String> parameterTypes = new ArrayList<>();
    private final List<String> parameterNames = new ArrayList<>();
    private Visibility visibility = Visibility.PACKAGE;
    private boolean isStatic;
    private boolean isAbstract;
    private boolean isConstructor;
    private final List<String> annotations = new ArrayList<>();
    private final List<Statement> statements = new ArrayList<>();
    private String comment;
    private final List<String> bodyComments = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public List<String> getParameterNames() {
        return parameterNames;
    }

    public Visibility getVisibility() {
        return visibility;
    }

    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public void setStatic(boolean aStatic) {
        isStatic = aStatic;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public void setAbstract(boolean anAbstract) {
        isAbstract = anAbstract;
    }

    public boolean isConstructor() {
        return isConstructor;
    }

    public void setConstructor(boolean constructor) {
        isConstructor = constructor;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    /** メソッド本体の構造化された文ツリー。 */
    public List<Statement> getStatements() {
        return statements;
    }

    /**
     * 後方互換: メソッド本体内の呼び出しを平坦化して返す。
     * 制御ブロック内の呼び出しもすべて含む。返値は新規リストなので
     * 変更してもツリー側には影響しない。
     */
    public List<Call> getCalls() {
        List<Call> out = new ArrayList<>();
        collectCalls(statements, out);
        return out;
    }

    private static void collectCalls(List<Statement> in, List<Call> out) {
        for (Statement s : in) {
            if (s instanceof Call) {
                out.add((Call) s);
            } else if (s instanceof Block) {
                for (Branch b : ((Block) s).getBranches()) {
                    collectCalls(b.getBody(), out);
                }
            }
        }
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * メソッド本体内で出現したコメント (行/ブロック/JavaDoc) を整形済み文字列で順に保持する。
     * シーケンス図の note にメソッド意図を併載する際に利用する。
     */
    public List<String> getBodyComments() {
        return bodyComments;
    }
}
