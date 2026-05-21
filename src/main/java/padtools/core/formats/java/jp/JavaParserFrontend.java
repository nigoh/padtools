package padtools.core.formats.java.jp;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.util.ErrorListener;

import java.util.ArrayList;
import java.util.List;

/**
 * JavaParser を用いた Java ソースの解析フロントエンド。
 *
 * <p>{@link CompilationUnit} を {@link TypeDeclAdapter} / {@link MemberAdapter} /
 * {@link ModuleAdapter} で既存モデル ({@link JavaClassInfo}) に変換して返す。
 * P1 では構造（型・フィールド・メソッド署名・imports・アノテーション・モジュール）のみを移送し、
 * メソッド本体の statement tree は後続フェーズで追加する。</p>
 *
 * <p>解析は寛容（{@link ParserConfiguration.LanguageLevel#BLEEDING_EDGE}）で行い、
 * 構文エラーがあっても部分結果を返す。問題は {@link ErrorListener} に通知する。</p>
 */
public final class JavaParserFrontend {

    private JavaParserFrontend() {
    }

    /** ソースを解析して {@link JavaClassInfo} のリストを返す。 */
    public static List<JavaClassInfo> parse(String source, ErrorListener listener) {
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        List<JavaClassInfo> out = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return out;
        }
        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.BLEEDING_EDGE);
        ParseResult<CompilationUnit> result = new JavaParser(cfg).parse(source);
        if (!result.isSuccessful()) {
            for (Problem p : result.getProblems()) {
                l.onError(null, lineOf(p), p.getMessage());
            }
        }
        CompilationUnit cu = result.getResult().orElse(null);
        if (cu == null) {
            return out;
        }
        String pkg = cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse("");
        List<String> imports = new ArrayList<>();
        for (ImportDeclaration imp : cu.getImports()) {
            imports.add(importString(imp));
        }
        cu.getModule().ifPresent(m -> out.add(ModuleAdapter.adapt(m)));
        for (TypeDeclaration<?> td : cu.getTypes()) {
            TypeDeclAdapter.adapt(td, pkg, null, imports, out);
        }
        return out;
    }

    private static String importString(ImportDeclaration imp) {
        String n = imp.getNameAsString();
        if (imp.isAsterisk()) {
            n = n + ".*";
        }
        if (imp.isStatic()) {
            n = "static " + n;
        }
        return n;
    }

    private static int lineOf(Problem p) {
        return p.getLocation()
                .flatMap(loc -> loc.getBegin().getRange())
                .map(r -> r.begin.line)
                .orElse(-1);
    }
}
