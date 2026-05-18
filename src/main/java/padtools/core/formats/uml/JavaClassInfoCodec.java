package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link JavaClassInfo} のヘッダ部分のみを単純な行指向テキストへ符号化/復元する。
 *
 * <p>1 クラス 1 行、TAB 区切りで次のフィールドを格納する:</p>
 * <pre>
 *   qn TAB simpleName TAB packageName TAB kind TAB enclosing TAB
 *   superClass TAB interfaces(comma) TAB modifiers(comma) TAB
 *   annotations(comma) TAB aaosCategory TAB androidComponentType
 * </pre>
 *
 * <p>fields / methods / comments / enumConstants は破棄される (永続キャッシュは
 * Stage A 用)。詳細はキャッシュロード後に {@link ClassIndex#detail} 経由で再パースする。</p>
 *
 * <p>TAB / 改行を含む値は {@code \t} / {@code \n} / {@code \\} にエスケープする。</p>
 */
public final class JavaClassInfoCodec {

    private JavaClassInfoCodec() {
    }

    public static String encodeHeader(JavaClassInfo c) {
        StringBuilder sb = new StringBuilder();
        appendField(sb, c.getQualifiedName());
        appendField(sb, c.getSimpleName());
        appendField(sb, c.getPackageName());
        appendField(sb, c.getKind() != null ? c.getKind().name() : "CLASS");
        appendField(sb, c.getEnclosingClass());
        appendField(sb, c.getSuperClass());
        appendField(sb, joinComma(c.getInterfaces()));
        appendField(sb, joinComma(c.getModifiers()));
        appendField(sb, joinComma(c.getAnnotations()));
        appendField(sb, c.getAaosCategory());
        appendFieldLast(sb, c.getAndroidComponentType());
        return sb.toString();
    }

    public static JavaClassInfo decodeHeader(String line) {
        if (line == null || line.isEmpty()) {
            return null;
        }
        String[] parts = splitTab(line, 11);
        if (parts.length < 11) {
            return null;
        }
        JavaClassInfo c = new JavaClassInfo();
        // parts[0] = qn (再構築するので不要だが念のため検証可能)
        c.setSimpleName(unescape(parts[1]));
        c.setPackageName(unescape(parts[2]));
        try {
            c.setKind(JavaClassInfo.Kind.valueOf(unescape(parts[3])));
        } catch (IllegalArgumentException ex) {
            c.setKind(JavaClassInfo.Kind.CLASS);
        }
        c.setEnclosingClass(emptyToNull(unescape(parts[4])));
        c.setSuperClass(emptyToNull(unescape(parts[5])));
        for (String iface : splitComma(parts[6])) {
            c.getInterfaces().add(iface);
        }
        for (String mod : splitComma(parts[7])) {
            c.getModifiers().add(mod);
        }
        for (String anno : splitComma(parts[8])) {
            c.getAnnotations().add(anno);
        }
        c.setAaosCategory(emptyToNull(unescape(parts[9])));
        c.setAndroidComponentType(emptyToNull(unescape(parts[10])));
        c.setDetailed(false);
        return c;
    }

    // --- helpers ---

    private static void appendField(StringBuilder sb, String v) {
        sb.append(escape(v));
        sb.append('\t');
    }

    private static void appendFieldLast(StringBuilder sb, String v) {
        sb.append(escape(v));
    }

    private static String joinComma(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            String v = values.get(i);
            // カンマと TAB をエスケープ
            sb.append(escapeComma(v));
        }
        return sb.toString();
    }

    private static List<String> splitComma(String s) {
        if (s == null || s.isEmpty()) {
            return Collections.emptyList();
        }
        String u = unescape(s);
        if (u.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < u.length(); i++) {
            char ch = u.charAt(i);
            if (esc) {
                if (ch == ',') {
                    cur.append(',');
                } else if (ch == '\\') {
                    cur.append('\\');
                } else {
                    cur.append('\\').append(ch);
                }
                esc = false;
            } else if (ch == '\\') {
                esc = true;
            } else if (ch == ',') {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private static String escape(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String escapeComma(String v) {
        if (v == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case ',': sb.append("\\,"); break;
                case '\t': sb.append("\\t"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String unescape(String v) {
        if (v == null || v.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(v.length());
        boolean esc = false;
        for (int i = 0; i < v.length(); i++) {
            char ch = v.charAt(i);
            if (esc) {
                switch (ch) {
                    case 't': sb.append('\t'); break;
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append('\\').append(ch);
                }
                esc = false;
            } else if (ch == '\\') {
                esc = true;
            } else {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    private static String[] splitTab(String line, int expected) {
        // 物理 TAB のみを区切りに使う。エスケープ済みの \t (バックスラッシュ + t) は
        // フィールド内に温存し、後段の unescape で実 TAB に戻す。
        List<String> parts = new ArrayList<>(expected);
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (esc) {
                cur.append(ch);
                esc = false;
            } else if (ch == '\\') {
                cur.append('\\');
                esc = true;
            } else if (ch == '\t') {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(new String[0]);
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }
}
