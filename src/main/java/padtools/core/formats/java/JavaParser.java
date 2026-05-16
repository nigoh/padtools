package padtools.core.formats.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Java トークン列を SPD 形式テキストに変換する構文解析器。
 *
 * <p>クラス・メソッドを発見し、メソッド本体内の制御構造を再帰的に降下解析して
 * SPD コマンド (:terminal, :if, :else, :while, :dowhile, :switch, :case など)
 * を出力する。</p>
 */
final class JavaParser {

    private static final Set<String> MODIFIERS = new HashSet<>(Arrays.asList(
            "public", "private", "protected", "static", "final", "abstract",
            "synchronized", "native", "strictfp", "default", "transient",
            "volatile"));

    private final List<JavaToken> tokens;
    private final String src;
    private final JavaSourceConverter.Options opts;
    private final StringBuilder out = new StringBuilder();
    private final List<String> classStack = new ArrayList<>();
    private int idx;
    private int methodCount;

    JavaParser(List<JavaToken> tokens, String src, JavaSourceConverter.Options opts) {
        this.tokens = tokens;
        this.src = src;
        this.opts = opts;
        this.idx = 0;
        this.methodCount = 0;
    }

    String result() {
        return out.toString();
    }

    // --- トークン操作 ---

    private JavaToken peek() {
        return tokens.get(idx);
    }

    private JavaToken peek(int n) {
        int i = idx + n;
        if (i >= tokens.size()) {
            i = tokens.size() - 1;
        }
        return tokens.get(i);
    }

    private JavaToken next() {
        JavaToken t = tokens.get(idx);
        if (t.type != JavaToken.Type.EOF) {
            idx++;
        }
        return t;
    }

    private boolean atEnd() {
        return peek().type == JavaToken.Type.EOF;
    }

    private void skipBalanced(String open, String close) {
        if (!peek().is(open)) {
            return;
        }
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
    }

    private String readBalancedRaw(String open, String close) {
        if (!peek().is(open)) {
            return "";
        }
        next();
        int startPos = peek().start;
        int endPos = startPos;
        int depth = 1;
        while (!atEnd() && depth > 0) {
            JavaToken t = peek();
            if (t.is(open)) {
                depth++;
            } else if (t.is(close)) {
                depth--;
                if (depth == 0) {
                    next();
                    return cleanSrcSlice(src, startPos, endPos);
                }
            }
            endPos = t.end;
            next();
        }
        return cleanSrcSlice(src, startPos, endPos);
    }

    /** ソースから [start, end) をスライスし、コメントと改行を正規化した文字列を返す。 */
    private static String cleanSrcSlice(String src, int start, int end) {
        if (start >= end || start < 0 || end > src.length()) {
            return "";
        }
        return sanitize(stripComments(src.substring(start, end)));
    }

    private static String stripComments(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
                continue;
            }
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    i++;
                }
                i = Math.min(i + 2, n);
                continue;
            }
            if (c == '"' || c == '\'') {
                int j = i + 1;
                while (j < n) {
                    char x = s.charAt(j);
                    if (x == '\\' && j + 1 < n) {
                        j += 2;
                        continue;
                    }
                    if (x == c) {
                        j++;
                        break;
                    }
                    j++;
                }
                sb.append(s, i, j);
                i = j;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    // --- アノテーション/修飾子のスキップ ---

    private void skipAnnotations() {
        while (!atEnd() && peek().is("@")) {
            // @interface はアノテーション型宣言なので消費しない
            if (peek(1).isKw("interface")) {
                return;
            }
            next();
            while (peek().type == JavaToken.Type.IDENT) {
                next();
                if (peek().is(".")) {
                    next();
                } else {
                    break;
                }
            }
            if (peek().is("(")) {
                skipBalanced("(", ")");
            }
        }
    }

    private void skipModifiers() {
        while (!atEnd()
                && peek().type == JavaToken.Type.IDENT
                && MODIFIERS.contains(peek().text)) {
            next();
        }
    }

    // --- ファイル/クラスレベル ---

    void parseFile() {
        while (!atEnd()) {
            if (peek().isKw("package") || peek().isKw("import")) {
                skipUntilSemicolon();
                continue;
            }
            skipAnnotations();
            skipModifiers();
            if (atEnd()) {
                break;
            }
            if (peek().isKw("class") || peek().isKw("interface")
                    || peek().isKw("enum")) {
                parseClassDecl();
            } else if (peek().is("@")) {
                next();
                if (peek().isKw("interface")) {
                    parseClassDecl();
                }
            } else {
                next();
            }
        }
    }

    private void parseClassDecl() {
        next(); // class/interface/enum
        String name = "Anonymous";
        if (peek().type == JavaToken.Type.IDENT) {
            name = next().text;
        }
        // <T>, extends, implements 等は { まで読み飛ばし
        while (!atEnd() && !peek().is("{")) {
            if (peek().is("<")) {
                skipBalanced("<", ">");
            } else {
                next();
            }
        }
        if (!peek().is("{")) {
            return;
        }
        next();
        classStack.add(name);
        try {
            parseClassBody();
        } finally {
            classStack.remove(classStack.size() - 1);
        }
    }

    private void parseClassBody() {
        while (!atEnd()) {
            if (peek().is("}")) {
                next();
                return;
            }
            if (peek().is(";")) {
                next();
                continue;
            }
            skipAnnotations();
            skipModifiers();

            if (peek().isKw("class") || peek().isKw("interface")
                    || peek().isKw("enum")) {
                parseClassDecl();
                continue;
            }
            if (peek().is("{")) {
                // インスタンス/static イニシャライザブロック
                parseInitBlock("(initializer)");
                continue;
            }
            if (atEnd() || peek().is("}")) {
                continue;
            }
            int before = idx;
            if (!tryParseMember()) {
                if (idx == before) {
                    next();
                }
            }
        }
    }

    private boolean tryParseMember() {
        // 先読みでメソッドかフィールドかを判定する
        int save = idx;
        // ジェネリックメソッド宣言 <T> をスキップ
        if (peek().is("<")) {
            skipBalanced("<", ">");
        }
        int depth = 0;
        while (!atEnd()) {
            JavaToken t = peek();
            if (depth == 0) {
                if (t.is(";")) {
                    next();
                    return true;
                }
                if (t.is("=")) {
                    // フィールド (初期化子付き)
                    skipUntilSemicolonRespectingBlocks();
                    return true;
                }
                if (t.is("(")) {
                    idx = save;
                    parseMethodDecl();
                    return true;
                }
                if (t.is("{") || t.is("}")) {
                    idx = save;
                    return false;
                }
            }
            if (t.is("(") || t.is("[") || t.is("<")) {
                depth++;
            } else if (t.is(")") || t.is("]")) {
                if (depth > 0) {
                    depth--;
                }
            } else if (t.is(">") || t.is(">>") || t.is(">>>")) {
                depth = Math.max(0, depth - t.text.length());
            }
            next();
        }
        idx = save;
        return false;
    }

    private void parseMethodDecl() {
        if (peek().is("<")) {
            skipBalanced("<", ">");
        }
        // メソッド名と戻り型を収集 (最後の IDENT がメソッド名)
        List<JavaToken> headerToks = new ArrayList<>();
        while (!atEnd() && !peek().is("(")) {
            headerToks.add(next());
        }
        if (atEnd()) {
            return;
        }
        String methodName = "(unknown)";
        for (int i = headerToks.size() - 1; i >= 0; i--) {
            JavaToken t = headerToks.get(i);
            if (t.type == JavaToken.Type.IDENT) {
                methodName = t.text;
                break;
            }
        }
        String params = readBalancedRaw("(", ")");
        // throws 節をスキップ
        while (!atEnd() && !peek().is("{") && !peek().is(";")) {
            next();
        }
        if (peek().is(";")) {
            next();
            return; // 抽象メソッド/インタフェース宣言
        }
        if (!peek().is("{")) {
            return;
        }
        next();

        if (opts.methodFilter != null
                && !opts.methodFilter.isEmpty()
                && !opts.methodFilter.contains(methodName)) {
            int d = 1;
            while (!atEnd() && d > 0) {
                if (peek().is("{")) {
                    d++;
                } else if (peek().is("}")) {
                    d--;
                }
                next();
            }
            return;
        }

        emitMethodHeader(methodName, params);
        // :terminal は子を持てない (PAD 仕様) ため、本体はトップレベルと同じ深さ 0 で出力する
        parseBlockBody(0);
        emitMethodFooter();
    }

    private void parseInitBlock(String label) {
        if (!peek().is("{")) {
            return;
        }
        next();
        if (methodCount > 0) {
            out.append('\n');
        }
        methodCount++;
        if (opts.includeTerminals) {
            emitCmd(0, "terminal", qualifiedName(label));
        }
        parseBlockBody(0);
        if (opts.includeTerminals) {
            emitCmd(0, "terminal", "END");
        }
    }

    private void emitMethodHeader(String name, String params) {
        if (methodCount > 0) {
            out.append('\n');
        }
        methodCount++;
        String sig = qualifiedName(name + "(" + sanitize(params) + ")");
        if (opts.includeTerminals) {
            emitCmd(0, "terminal", sig);
        } else {
            emitCmd(0, "comment", sig);
        }
    }

    private void emitMethodFooter() {
        if (opts.includeTerminals) {
            emitCmd(0, "terminal", "END");
        }
    }

    private String qualifiedName(String name) {
        if (classStack.isEmpty()) {
            return name;
        }
        StringBuilder sb = new StringBuilder();
        for (String c : classStack) {
            sb.append(c).append(opts.classSeparator);
        }
        sb.append(name);
        return sb.toString();
    }

    // --- メソッド本体・文 ---

    private void parseBlockBody(int padDepth) {
        while (!atEnd() && !peek().is("}")) {
            parseStatement(padDepth);
        }
        if (peek().is("}")) {
            next();
        }
    }

    private void parseStatement(int padDepth) {
        if (peek().is(";")) {
            next();
            return;
        }
        if (peek().is("{")) {
            next();
            parseBlockBody(padDepth);
            return;
        }
        // ラベル
        if (peek().type == JavaToken.Type.IDENT
                && peek(1).is(":")
                && !peek().isKw("case")
                && !peek().isKw("default")) {
            next();
            next();
            parseStatement(padDepth);
            return;
        }
        if (peek().isKw("if")) {
            parseIf(padDepth);
            return;
        }
        if (peek().isKw("while")) {
            parseWhile(padDepth);
            return;
        }
        if (peek().isKw("do")) {
            parseDoWhile(padDepth);
            return;
        }
        if (peek().isKw("for")) {
            parseFor(padDepth);
            return;
        }
        if (peek().isKw("switch")) {
            parseSwitch(padDepth);
            return;
        }
        if (peek().isKw("try")) {
            parseTry(padDepth);
            return;
        }
        if (peek().isKw("synchronized")) {
            parseSynchronized(padDepth);
            return;
        }
        if (peek().isKw("return")) {
            parseReturn(padDepth);
            return;
        }
        if (peek().isKw("throw")) {
            parseThrow(padDepth);
            return;
        }
        if (peek().isKw("break") || peek().isKw("continue")) {
            parseBreakContinue(padDepth);
            return;
        }
        // 通常文
        String stmt = readExpressionUntilSemicolon();
        if (!stmt.isEmpty()) {
            emitProcess(padDepth, stmt);
        }
    }

    private void parseStmtOrBlock(int padDepth) {
        if (peek().is("{")) {
            next();
            parseBlockBody(padDepth);
        } else {
            parseStatement(padDepth);
        }
    }

    // --- 制御構造 ---

    private void parseIf(int padDepth) {
        next(); // if
        String cond = readBalancedRaw("(", ")");
        emitCmd(padDepth, "if", cond);
        parseStmtOrBlock(padDepth + 1);
        if (peek().isKw("else")) {
            next();
            if (peek().isKw("if")) {
                // else if: SPD では :else の下に :if をネストする
                emitCmd(padDepth, "else", null);
                parseIf(padDepth + 1);
            } else {
                emitCmd(padDepth, "else", null);
                parseStmtOrBlock(padDepth + 1);
            }
        }
    }

    private void parseWhile(int padDepth) {
        next();
        String cond = readBalancedRaw("(", ")");
        emitCmd(padDepth, "while", cond);
        parseStmtOrBlock(padDepth + 1);
    }

    private void parseDoWhile(int padDepth) {
        next(); // do
        // ボディを一旦バッファに書き出し、後で先頭に :dowhile cond を入れる
        int bodyStart = out.length();
        parseStmtOrBlock(padDepth + 1);
        String body = out.substring(bodyStart);
        out.setLength(bodyStart);

        String cond = "";
        if (peek().isKw("while")) {
            next();
            cond = readBalancedRaw("(", ")");
            if (peek().is(";")) {
                next();
            }
        }
        emitCmd(padDepth, "dowhile", cond);
        out.append(body);
    }

    private void parseFor(int padDepth) {
        next();
        String control = readBalancedRaw("(", ")");
        int colonIdx = findEnhancedForColon(control);
        if (colonIdx >= 0) {
            String varDecl = control.substring(0, colonIdx).trim();
            String iter = control.substring(colonIdx + 1).trim();
            emitCmd(padDepth, "while", iter + " に要素がある間");
            if (!varDecl.isEmpty()) {
                emitProcess(padDepth + 1, varDecl + " = next");
            }
            parseStmtOrBlock(padDepth + 1);
            return;
        }
        String[] parts = splitTopLevel(control, ';');
        String init = parts.length > 0 ? parts[0].trim() : "";
        String cond = parts.length > 1 ? parts[1].trim() : "";
        String upd = parts.length > 2 ? parts[2].trim() : "";
        if (opts.unrollFor) {
            if (!init.isEmpty()) {
                emitProcess(padDepth, init);
            }
            emitCmd(padDepth, "while", cond.isEmpty() ? "true" : cond);
            parseStmtOrBlock(padDepth + 1);
            if (!upd.isEmpty()) {
                emitProcess(padDepth + 1, upd);
            }
        } else {
            emitCmd(padDepth, "while", "for(" + control + ")");
            parseStmtOrBlock(padDepth + 1);
        }
    }

    private void parseSwitch(int padDepth) {
        next();
        String expr = readBalancedRaw("(", ")");
        emitCmd(padDepth, "switch", expr);
        if (!peek().is("{")) {
            return;
        }
        next();
        while (!atEnd() && !peek().is("}")) {
            if (peek().isKw("case") || peek().isKw("default")) {
                parseCase(padDepth);
            } else if (peek().is(";")) {
                next();
            } else {
                next();
            }
        }
        if (peek().is("}")) {
            next();
        }
    }

    private void parseCase(int padDepth) {
        StringBuilder label = new StringBuilder();
        while (peek().isKw("case") || peek().isKw("default")) {
            String l;
            if (peek().isKw("default")) {
                next();
                l = "default";
            } else {
                next();
                l = readCaseLabel();
            }
            if (label.length() > 0) {
                label.append(" / ");
            }
            label.append(l);
            if (peek().is(":") || peek().is("->")) {
                next();
            }
            if (!(peek().isKw("case") || peek().isKw("default"))) {
                break;
            }
        }
        emitCmd(padDepth, "case", label.toString());
        while (!atEnd() && !peek().is("}")
                && !peek().isKw("case") && !peek().isKw("default")) {
            parseStatement(padDepth + 1);
        }
    }

    private String readCaseLabel() {
        int startPos = peek().start;
        int endPos = startPos;
        int d = 0;
        while (!atEnd()) {
            JavaToken t = peek();
            if (d == 0 && (t.is(":") || t.is("->"))) {
                break;
            }
            if (t.is("(") || t.is("[")) {
                d++;
            } else if (t.is(")") || t.is("]")) {
                d--;
            }
            endPos = t.end;
            next();
        }
        return cleanSrcSlice(src, startPos, endPos);
    }

    private void parseTry(int padDepth) {
        next();
        if (peek().is("(")) {
            String resources = readBalancedRaw("(", ")");
            if (!resources.isEmpty()) {
                emitProcess(padDepth, "try-with-resources: " + resources);
            }
        }
        if (!peek().is("{")) {
            return;
        }
        emitCmd(padDepth, "switch", "try-catch");
        emitCmd(padDepth, "case", "正常");
        next();
        parseBlockBody(padDepth + 1);
        while (peek().isKw("catch")) {
            next();
            String spec = readBalancedRaw("(", ")");
            emitCmd(padDepth, "case", "catch " + spec);
            if (peek().is("{")) {
                next();
                parseBlockBody(padDepth + 1);
            }
        }
        if (peek().isKw("finally")) {
            next();
            if (peek().is("{")) {
                next();
                emitCmd(padDepth, "case", "finally");
                parseBlockBody(padDepth + 1);
            }
        }
    }

    private void parseSynchronized(int padDepth) {
        next();
        String lock = "";
        if (peek().is("(")) {
            lock = readBalancedRaw("(", ")");
        }
        emitProcess(padDepth, "synchronized(" + lock + ")");
        parseStmtOrBlock(padDepth + 1);
    }

    private void parseReturn(int padDepth) {
        next();
        String expr = readExpressionUntilSemicolon();
        String text = expr.isEmpty() ? "return" : ("return " + expr);
        if (opts.returnAsTerminal && opts.includeTerminals) {
            emitCmd(padDepth, "terminal", text);
        } else {
            emitProcess(padDepth, text);
        }
    }

    private void parseThrow(int padDepth) {
        next();
        String expr = readExpressionUntilSemicolon();
        String text = expr.isEmpty() ? "throw" : ("throw " + expr);
        if (opts.returnAsTerminal && opts.includeTerminals) {
            emitCmd(padDepth, "terminal", text);
        } else {
            emitProcess(padDepth, text);
        }
    }

    private void parseBreakContinue(int padDepth) {
        String kw = next().text;
        String tgt = "";
        if (peek().type == JavaToken.Type.IDENT) {
            tgt = next().text;
        }
        if (peek().is(";")) {
            next();
        }
        String text = tgt.isEmpty() ? kw : (kw + " " + tgt);
        emitProcess(padDepth, text);
    }

    // --- 読み込みヘルパ ---

    private String readExpressionUntilSemicolon() {
        int startPos = peek().start;
        int endPos = startPos;
        int d = 0;
        while (!atEnd()) {
            JavaToken t = peek();
            if (d == 0 && t.is(";")) {
                next();
                break;
            }
            if (d == 0 && t.is("}")) {
                break;
            }
            if (t.is("(") || t.is("[") || t.is("{")) {
                d++;
            } else if (t.is(")") || t.is("]") || t.is("}")) {
                d--;
            }
            endPos = t.end;
            next();
        }
        return cleanSrcSlice(src, startPos, endPos);
    }

    private void skipUntilSemicolon() {
        while (!atEnd() && !peek().is(";")) {
            next();
        }
        if (peek().is(";")) {
            next();
        }
    }

    private void skipUntilSemicolonRespectingBlocks() {
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

    // --- 出力 ---

    private void emitProcess(int depth, String text) {
        indent(depth);
        out.append(sanitize(text)).append('\n');
    }

    private void emitCmd(int depth, String cmd, String arg) {
        indent(depth);
        out.append(':').append(cmd);
        if (arg != null && !arg.isEmpty()) {
            out.append(' ').append(sanitize(arg));
        }
        out.append('\n');
    }

    private void indent(int depth) {
        for (int i = 0; i < depth; i++) {
            out.append('\t');
        }
    }

    // --- 文字列ユーティリティ ---

    private static String sanitize(String s) {
        if (s == null) {
            return "";
        }
        String r = s.replaceAll("[\\r\\n]+", " ");
        r = r.replaceAll("\\s+", " ");
        return r.trim();
    }

    static int findEnhancedForColon(String s) {
        int depth = 0;
        boolean sawQuestion = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipQuoted(s, i, c);
                continue;
            }
            if (c == '(' || c == '[' || c == '<') {
                depth++;
            } else if (c == ')' || c == ']' || c == '>') {
                depth--;
            } else if (c == ';') {
                if (depth == 0) {
                    return -1;
                }
            } else if (c == '?') {
                if (depth == 0) {
                    sawQuestion = true;
                }
            } else if (c == ':') {
                if (depth == 0 && !sawQuestion) {
                    return i;
                }
                if (depth == 0) {
                    sawQuestion = false;
                }
            }
        }
        return -1;
    }

    private static int skipQuoted(String s, int i, char q) {
        int j = i + 1;
        while (j < s.length()) {
            char c = s.charAt(j);
            if (c == '\\' && j + 1 < s.length()) {
                j += 2;
                continue;
            }
            if (c == q) {
                return j;
            }
            j++;
        }
        return j;
    }

    /**
     * 文字列を引数のセパレータでトップレベル分割する。
     * <p>{@code (){}[]} のネストは追跡するが、{@code <>} は比較演算子と
     * ジェネリクスの区別が困難なため追跡しない (現在の用途では {@code <>} の中に
     * セパレータが含まれることはない)。</p>
     */
    static String[] splitTopLevel(String s, char sep) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                int e = skipQuoted(s, i, c);
                cur.append(s, i, Math.min(e + 1, s.length()));
                i = e;
                continue;
            }
            if (c == '(' || c == '[' || c == '{') {
                depth++;
                cur.append(c);
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
                cur.append(c);
            } else if (c == sep && depth == 0) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }
}
