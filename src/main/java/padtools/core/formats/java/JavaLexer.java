package padtools.core.formats.java;

import java.util.ArrayList;
import java.util.List;

/**
 * Java ソース → JavaToken 列への変換を行う簡易字句解析器。
 *
 * <p>完全な Java 言語仕様を網羅したものではないが、コメント、文字列・文字リテラル、
 * 識別子、数値、複合演算子 (>>, <=, &&, など) を扱える。
 * 制御構造解析に必要なトークンが正しく切り出せれば十分である。</p>
 */
final class JavaLexer {

    private static final String[] LONG_OPS3 = {">>>", "<<=", ">>=", "..."};
    private static final String[] LONG_OPS2 = {
            "==", "!=", "<=", ">=", "&&", "||", "++", "--",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "<<", ">>", "->", "::"
    };

    private final String src;
    private final int len;
    private int pos;
    private int line;

    JavaLexer(String src) {
        this.src = src;
        this.len = src.length();
        this.pos = 0;
        this.line = 1;
    }

    List<JavaToken> tokenize() {
        List<JavaToken> out = new ArrayList<>();
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '\n') {
                line++;
                pos++;
                continue;
            }
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }
            if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '/') {
                while (pos < len && src.charAt(pos) != '\n') {
                    pos++;
                }
                continue;
            }
            if (c == '/' && pos + 1 < len && src.charAt(pos + 1) == '*') {
                skipBlockComment();
                continue;
            }
            if (c == '"') {
                out.add(readString('"', JavaToken.Type.STRING));
                continue;
            }
            if (c == '\'') {
                out.add(readString('\'', JavaToken.Type.CHAR));
                continue;
            }
            if (Character.isDigit(c)) {
                out.add(readNumber());
                continue;
            }
            if (isIdentStart(c)) {
                out.add(readIdent());
                continue;
            }
            out.add(readSymbol());
        }
        out.add(new JavaToken(JavaToken.Type.EOF, "", line, len, len));
        return out;
    }

    String getSource() {
        return src;
    }

    private void skipBlockComment() {
        pos += 2;
        while (pos + 1 < len
                && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
            if (src.charAt(pos) == '\n') {
                line++;
            }
            pos++;
        }
        pos = Math.min(pos + 2, len);
    }

    private JavaToken readString(char quote, JavaToken.Type type) {
        int start = pos;
        int startLine = line;
        pos++;
        while (pos < len) {
            char c = src.charAt(pos);
            if (c == '\\' && pos + 1 < len) {
                if (src.charAt(pos + 1) == '\n') {
                    line++;
                }
                pos += 2;
                continue;
            }
            if (c == quote) {
                pos++;
                break;
            }
            if (c == '\n') {
                line++;
                break;
            }
            pos++;
        }
        return new JavaToken(type, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readNumber() {
        int start = pos;
        int startLine = line;
        while (pos < len) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_') {
                pos++;
            } else {
                break;
            }
        }
        return new JavaToken(JavaToken.Type.NUMBER, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readIdent() {
        int start = pos;
        int startLine = line;
        while (pos < len) {
            char c = src.charAt(pos);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
                pos++;
            } else {
                break;
            }
        }
        return new JavaToken(JavaToken.Type.IDENT, src.substring(start, pos), startLine, start, pos);
    }

    private JavaToken readSymbol() {
        int startLine = line;
        int symStart = pos;
        if (pos + 3 <= len) {
            String s3 = src.substring(pos, pos + 3);
            for (String op : LONG_OPS3) {
                if (op.equals(s3)) {
                    pos += 3;
                    return new JavaToken(JavaToken.Type.OP, s3, startLine, symStart, pos);
                }
            }
        }
        if (pos + 2 <= len) {
            String s2 = src.substring(pos, pos + 2);
            for (String op : LONG_OPS2) {
                if (op.equals(s2)) {
                    pos += 2;
                    return new JavaToken(JavaToken.Type.OP, s2, startLine, symStart, pos);
                }
            }
        }
        char c = src.charAt(pos);
        pos++;
        String s = String.valueOf(c);
        JavaToken.Type t = isPunct(c) ? JavaToken.Type.PUNCT : JavaToken.Type.OP;
        return new JavaToken(t, s, startLine, symStart, pos);
    }

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_' || c == '$';
    }

    private static boolean isPunct(char c) {
        return c == '(' || c == ')' || c == '{' || c == '}'
                || c == '[' || c == ']' || c == ','
                || c == ';' || c == '.' || c == ':'
                || c == '@' || c == '?';
    }
}
