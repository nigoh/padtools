package padtools.core.formats.java;

/**
 * Java ソース字句解析器が生成するトークン。
 *
 * <p>{@code start}/{@code end} はソース文字列上の半開区間 [start, end)
 * を表す。これにより、後段の構文解析器はトークン間の空白を含めて
 * 元のソースをスライスすることでフォーマットを保ったまま式を再構築できる。</p>
 */
final class JavaToken {

    enum Type { IDENT, NUMBER, STRING, CHAR, PUNCT, OP, EOF }

    final Type type;
    final String text;
    final int line;
    final int start;
    final int end;

    JavaToken(Type type, String text, int line, int start, int end) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.start = start;
        this.end = end;
    }

    boolean is(String s) {
        return text.equals(s);
    }

    boolean isKw(String kw) {
        return type == Type.IDENT && text.equals(kw);
    }
}
