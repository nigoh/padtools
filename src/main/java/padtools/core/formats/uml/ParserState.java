package padtools.core.formats.uml;

import padtools.core.formats.java.JavaToken;
import padtools.util.ErrorListener;
import java.util.*;

/**
 * トークン列を消化するパーサ群が共有する状態 (カーソル位置・結果・スタック等) と、
 * 状態に依存する低水準のカーソル/字句/先読みヘルパを保持する。文法生成規則は
 * {@link DeclarationParser} / {@link StatementParser} / {@link ExpressionParser} が担う。
 */
final class ParserState {

    final List<JavaToken> tokens;
    final String src;
    final List<JavaCommentScanner.Comment> comments;
    final ErrorListener listener;
    final List<JavaClassInfo> results = new ArrayList<>();
    final List<JavaClassInfo> classStack = new ArrayList<>();
    final List<PendingAssignment> pendingFieldAssignments = new ArrayList<>();
    String packageName = "";
    final List<String> imports = new ArrayList<>();
    int idx;
    int switchDepth;
    int nextBodyCommentIdx;

    static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "private", "protected", "static", "final", "abstract",
            "synchronized", "native", "strictfp", "default", "transient",
            "volatile", "sealed"));

    // 文法パーサ間の相互参照 (facade で配線する)。
    DeclarationParser decl;
    StatementParser stmt;
    ExpressionParser expr;

    static final class PendingAssignment {
        final JavaClassInfo cls;
        final String fieldName;
        final List<JavaMethodInfo> inlineMethods;

        PendingAssignment(JavaClassInfo cls, String fieldName,
                          List<JavaMethodInfo> inlineMethods) {
            this.cls = cls;
            this.fieldName = fieldName;
            this.inlineMethods = inlineMethods;
        }
    }

    ParserState(List<JavaToken> tokens, String src,
                List<JavaCommentScanner.Comment> comments, ErrorListener listener) {
        this.tokens = tokens;
        this.src = src;
        this.comments = comments;
        this.listener = listener;
    }


        String findCommentBefore(int pos) {
            return JavaCommentScanner.findCommentBefore(src, comments, pos);
        }


        void warn(int line, String msg) {
            listener.onError(null, line, msg);
        }


        JavaToken peek() {
            return tokens.get(idx);
        }


        JavaToken peek(int n) {
            int i = idx + n;
            if (i >= tokens.size()) {
                i = tokens.size() - 1;
            }
            return tokens.get(i);
        }


        JavaToken next() {
            JavaToken t = tokens.get(idx);
            if (t.type != JavaToken.Type.EOF) {
                idx++;
            }
            return t;
        }


        boolean atEnd() {
            return peek().type == JavaToken.Type.EOF;
        }


        /**
         * クラス本体パース中に出現したが宣言順の都合で同名フィールドにマッチできなかった
         * 代入 ({@link PendingAssignment}) を、最終的にフィールドへ紐づける。
         * 該当フィールドが見つからない場合は破棄する (解析対象外のローカル変数等)。
         */
        void resolvePendingFieldAssignments() {
            for (PendingAssignment p : pendingFieldAssignments) {
                JavaFieldInfo f = JavaParseSupport.findFieldByName(p.cls, p.fieldName);
                if (f != null) {
                    f.getInlineMethods().addAll(p.inlineMethods);
                }
            }
            pendingFieldAssignments.clear();
        }


        /** {@code A.b.C, D.e.F, ...} を読み取って文字列リストとして返す。 */
        List<String> readDottedNameList() {
            List<String> out = new ArrayList<>();
            String first = readDottedName();
            if (!first.isEmpty()) {
                out.add(first);
            }
            while (peek().is(",")) {
                next();
                String n = readDottedName();
                if (!n.isEmpty()) {
                    out.add(n);
                }
            }
            return out;
        }


        String buildEnclosingPath() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < classStack.size(); i++) {
                if (i > 0) {
                    sb.append('.');
                }
                sb.append(classStack.get(i).getSimpleName());
            }
            return sb.toString();
        }


        /**
         * 初期化式の途中から {@code ,} または {@code ;} までを括弧深度を考慮して読み飛ばす。
         * フィールドの複数宣言 ({@code int a = 1, b = 2;}) で次の変数に進むために使う。
         */
        void skipUntilCommaOrSemicolonRespectingBlocks() {
            int d = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (d == 0 && (t.is(";") || t.is(","))) {
                    return;
                }
                if (t.is("(") || t.is("[") || t.is("{")) {
                    d++;
                } else if (t.is(")") || t.is("]") || t.is("}")) {
                    if (d > 0) {
                        d--;
                    } else {
                        return;
                    }
                }
                next();
            }
        }


        /**
         * 指定位置 {@code from} のトークンが {@code open} なら対応する {@code close} の
         * 次の位置を返す。バランスしなければ tokens.size() を返す。
         * {@link #skipBalanced(String, String)} と異なり {@code idx} を変更しない peek 専用。
         */
        int skipBalancedAt(int from, String open, String close) {
            if (from >= tokens.size() || !tokens.get(from).is(open)) {
                return from;
            }
            int d = 1;
            int i = from + 1;
            boolean angle = "<".equals(open);
            while (i < tokens.size() && d > 0) {
                JavaToken t = tokens.get(i);
                if (t.is(open)) {
                    d++;
                } else if (t.is(close)) {
                    d--;
                    if (d == 0) {
                        return i + 1;
                    }
                } else if (angle && (t.is(">>") || t.is(">>>"))) {
                    d -= t.text.length();
                    if (d <= 0) {
                        return i + 1;
                    }
                }
                i++;
            }
            return i;
        }


        /** {@code [bodyStart, bodyEnd)} の範囲にあるコメントを {@link JavaMethodInfo#getBodyComments()} に追加する。 */
        void collectBodyComments(JavaMethodInfo m, int bodyStart, int bodyEnd) {
            if (comments == null || comments.isEmpty() || bodyEnd <= bodyStart) {
                return;
            }
            for (JavaCommentScanner.Comment c : comments) {
                if (c.start >= bodyEnd) {
                    break;
                }
                if (c.start < bodyStart) {
                    continue;
                }
                String t = JavaCommentScanner.cleanText(c);
                if (!t.isEmpty()) {
                    m.getBodyComments().add(t);
                }
            }
        }


        /**
         * 現在のトークン位置 ({@code peek().start}) より前にある未出力コメントを
         * {@link JavaMethodInfo.InlineComment} として {@code out} に追加し、
         * {@link #nextBodyCommentIdx} を進める。
         *
         * <p>JavaDoc コメントは宣言に紐づくものとして除外する。</p>
         */
        void emitPrecedingComments(List<JavaMethodInfo.Statement> out) {
            if (comments == null || atEnd()) {
                return;
            }
            int currentPos = peek().start;
            while (nextBodyCommentIdx < comments.size()) {
                JavaCommentScanner.Comment c = comments.get(nextBodyCommentIdx);
                if (c.start >= currentPos) {
                    break;
                }
                nextBodyCommentIdx++;
                if (c.kind == JavaCommentScanner.Kind.JAVADOC) {
                    continue;
                }
                String text = JavaCommentScanner.cleanText(c);
                if (!text.isEmpty()) {
                    out.add(new JavaMethodInfo.InlineComment(text));
                }
            }
        }


        /**
         * 現在位置の {@code yield} が文として始まっているかを判定する。
         * 直後に {@code (} ({@code yield(...)} 呼び出し)、{@code =} (代入)、
         * {@code .} (フィールド/メソッドアクセス) が続く場合は通常の式と
         * みなして false を返す。
         */
        boolean looksLikeYieldStatement() {
            JavaToken nxt = peek(1);
            if (nxt.is("(") || nxt.is(".") || nxt.is("=") || nxt.is(";")
                    || nxt.is("::")) {
                return false;
            }
            return true;
        }


        /**
         * 現在位置がローカル変数宣言の開始に見えるかを lookahead で判定する。
         * idx は変更しない (probe のみ使用)。
         *
         * <p>検出対象: {@code [final] Type [<...>] [[][]] VarName [[][]] (= | ; | ,)}<br>
         * 非検出対象: {@code IDENT(} (メソッド呼び出し)、{@code IDENT.IDENT=}
         * (フィールド代入)、{@code IDENT++} (インクリメント) 等。</p>
         */
        boolean looksLikeLocalVarDecl() {
            int probe = idx;
            // optional "final"
            while (probe < tokens.size() && tokens.get(probe).isKw("final")) {
                probe++;
            }
            // 型トークン列を probe で読み飛ばす
            probe = probeTypeTokens(probe);
            if (probe < 0) {
                return false;
            }
            // probe が IDENT (変数名) を指しているか
            if (probe >= tokens.size()
                    || tokens.get(probe).type != JavaToken.Type.IDENT) {
                return false;
            }
            int afterVar = probe + 1;
            if (afterVar >= tokens.size()) {
                return false;
            }
            JavaToken after = tokens.get(afterVar);
            return after.is("=") || after.is(";") || after.is(",");
        }


        /**
         * probe 位置から型トークン列 (IDENT・ドット連鎖・ジェネリクス・配列) を
         * 読み飛ばし、型の終端インデックス (次に変数名 IDENT が来る位置) を返す。
         * 型として解釈できなければ -1。idx は変更しない。
         */
        int probeTypeTokens(int probe) {
            if (probe >= tokens.size()) {
                return -1;
            }
            JavaToken first = tokens.get(probe);
            // 型の先頭は IDENT でなければならない
            if (first.type != JavaToken.Type.IDENT) {
                return -1;
            }
            // 直後が '(' ならメソッド呼び出し → 除外
            if (probe + 1 < tokens.size() && tokens.get(probe + 1).is("(")) {
                return -1;
            }
            probe++;
            // ドット連鎖 (例: Map.Entry, java.util.List)
            while (probe + 1 < tokens.size()
                    && tokens.get(probe).is(".")
                    && tokens.get(probe + 1).type == JavaToken.Type.IDENT) {
                // IDENT.IDENT(...) はメソッドアクセスなので除外
                if (probe + 2 < tokens.size() && tokens.get(probe + 2).is("(")) {
                    return -1;
                }
                probe += 2;
            }
            // ジェネリクス <...>
            if (probe < tokens.size() && tokens.get(probe).is("<")) {
                probe = skipBalancedAt(probe, "<", ">");
                if (probe >= tokens.size()) {
                    return -1;
                }
            }
            // 配列ブラケット [] (空のみ: 要素アクセス [expr] は除外)
            while (probe + 1 < tokens.size()
                    && tokens.get(probe).is("[")
                    && tokens.get(probe + 1).is("]")) {
                probe += 2;
            }
            return probe;
        }


        /**
         * 現在位置がローカルクラス宣言の開始に見える場合、{@code class}/{@code record}/
         * {@code interface}/{@code enum} キーワードのトークン位置を返す。違えば -1。
         * 修飾子 ({@code final}, {@code abstract}, {@code static}) とアノテーション
         * ({@code @Foo} / {@code @Foo(...)}) を間に許容する。
         */
        int peekLocalClassDeclKeyword() {
            int p = idx;
            while (p < tokens.size()) {
                JavaToken t = tokens.get(p);
                if (t.isKw("class") || t.isKw("interface")
                        || t.isKw("enum") || t.isKw("record")) {
                    return p;
                }
                if (t.is("@") && p + 1 < tokens.size()
                        && tokens.get(p + 1).type == JavaToken.Type.IDENT) {
                    p += 2;
                    while (p < tokens.size()
                            && (tokens.get(p).type == JavaToken.Type.IDENT
                                || tokens.get(p).is("."))) {
                        p++;
                    }
                    if (p < tokens.size() && tokens.get(p).is("(")) {
                        p = skipBalancedAt(p, "(", ")");
                    }
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT && MODIFIERS.contains(t.text)) {
                    p++;
                    continue;
                }
                return -1;
            }
            return -1;
        }


        /**
         * {@code ::} の直前を起点に receiver を組み立てる。
         * {@code Foo::bar} は {@code Foo}、{@code a.b.c::m} は {@code a.b.c}。
         * メソッド呼び出しのレシーバ検出 ({@link #findReceiver()}) は直前が
         * {@code .} の場合だけ拾うが、メソッド参照は識別子自身もレシーバなので
         * 起点が異なる。
         */
        String findReceiverBeforeColonColon() {
            int i = idx - 1;
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j >= 0) {
                JavaToken t = tokens.get(j);
                if (t.is(".")) {
                    sb.insert(0, '.');
                    j--;
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT) {
                    sb.insert(0, t.text);
                    if (j - 1 >= 0 && tokens.get(j - 1).is(".")) {
                        j--;
                        continue;
                    }
                    break;
                }
                break;
            }
            return sb.toString();
        }


        /** 直前のトークンを見て {@code receiver.method(...)} の receiver を組み立てる。 */
        String findReceiver() {
            // 直前が "." なら、ドット前の識別子(連鎖)を集める
            // 例: a.b.c.method(...) では receiver = "a.b.c"
            int i = idx - 1;
            if (i < 0 || !tokens.get(i).is(".")) {
                return "";
            }
            // ドット連鎖を逆方向に集める
            StringBuilder sb = new StringBuilder();
            int j = i;
            while (j >= 0) {
                JavaToken t = tokens.get(j);
                if (t.is(".")) {
                    j--;
                    continue;
                }
                if (t.type == JavaToken.Type.IDENT) {
                    sb.insert(0, t.text);
                    if (j - 1 >= 0 && tokens.get(j - 1).is(".")) {
                        sb.insert(0, '.');
                        j -= 2;
                    } else {
                        break;
                    }
                } else {
                    break;
                }
            }
            return sb.toString();
        }


        // --- ヘルパ ---

        List<String> readAnnotations() {
            List<String> result = new ArrayList<>();
            while (!atEnd() && peek().is("@")) {
                if (peek(1).isKw("interface")) {
                    return result;
                }
                next();
                StringBuilder name = new StringBuilder();
                while (peek().type == JavaToken.Type.IDENT) {
                    name.append(next().text);
                    if (peek().is(".")) {
                        next();
                        name.append('.');
                    } else {
                        break;
                    }
                }
                String args = "";
                if (peek().is("(")) {
                    int s = peek().start;
                    skipBalanced("(", ")");
                    int e = idx > 0 ? tokens.get(idx - 1).end : s;
                    args = src.substring(s, e);
                }
                result.add(name.toString() + args);
            }
            return result;
        }


        List<String> readModifiers() {
            List<String> result = new ArrayList<>();
            while (!atEnd()
                    && peek().type == JavaToken.Type.IDENT
                    && MODIFIERS.contains(peek().text)) {
                result.add(next().text);
            }
            return result;
        }


        String readDottedName() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                sb.append(next().text);
                if (peek().is(".")) {
                    next();
                    sb.append('.');
                } else {
                    break;
                }
            }
            return sb.toString();
        }


        /**
         * import 宣言の本体 ({@code foo.bar.Baz} もしくは {@code foo.bar.*}) を読み取る。
         * 末尾の {@code ;} は呼び出し側で消費する。
         */
        String readImportName() {
            StringBuilder sb = new StringBuilder();
            while (!atEnd() && peek().type == JavaToken.Type.IDENT) {
                sb.append(next().text);
                if (peek().is(".")) {
                    next();
                    sb.append('.');
                    if (peek().is("*")) {
                        next();
                        sb.append('*');
                        break;
                    }
                } else {
                    break;
                }
            }
            return sb.toString();
        }


        String readTypeName() {
            int s = peek().start;
            int e = s;
            int depth = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (depth == 0
                        && (t.is(",") || t.is("{") || t.is(";")
                            || t.isKw("implements") || t.isKw("extends")
                            || t.isKw("permits"))) {
                    break;
                }
                if (t.is("<")) {
                    depth++;
                } else if (t.is(">")) {
                    depth--;
                } else if (t.is(">>") || t.is(">>>")) {
                    depth -= t.text.length();
                }
                e = t.end;
                next();
            }
            return JavaParseSupport.normalizeType(JavaParseSupport.stripAnnotations(src.substring(s, e)));
        }


        List<String> readTypeList() {
            List<String> result = new ArrayList<>();
            while (!atEnd()) {
                if (peek().is("{") || peek().is(";")) {
                    break;
                }
                String n = readTypeName();
                if (!n.isEmpty()) {
                    result.add(n);
                }
                if (peek().is(",")) {
                    next();
                } else {
                    break;
                }
            }
            return result;
        }


        void consume(String s) {
            if (peek().is(s)) {
                next();
            }
        }


        void skipUntilSemicolon() {
            while (!atEnd() && !peek().is(";")) {
                next();
            }
            if (peek().is(";")) {
                next();
            }
        }


        void skipUntilSemicolonRespectingBlocks() {
            int d = 0;
            while (!atEnd()) {
                JavaToken t = peek();
                if (d == 0 && t.is(";")) {
                    next();
                    return;
                }
                if (t.is("(") || t.is("[") || t.is("{")) {
                    d++;
                } else if (t.is(")") || t.is("]") || t.is("}")) {
                    if (d > 0) {
                        d--;
                    } else {
                        return;
                    }
                }
                next();
            }
        }


        void skipBalanced(String open, String close) {
            if (!peek().is(open)) {
                return;
            }
            int startLine = peek().line;
            next();
            int depth = 1;
            boolean angle = "<".equals(open);
            while (!atEnd() && depth > 0) {
                JavaToken t = peek();
                if (t.is(open)) {
                    depth++;
                } else if (t.is(close)) {
                    depth--;
                    if (depth == 0) {
                        next();
                        return;
                    }
                } else if (angle && (t.is(">>") || t.is(">>>"))) {
                    depth -= t.text.length();
                    if (depth <= 0) {
                        next();
                        return;
                    }
                }
                next();
            }
            warn(startLine, "EOF reached before matching '" + close + "'");
        }
}
