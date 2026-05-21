package padtools.core.formats.java.jp;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.JavaParseSupport;
import padtools.core.formats.uml.Visibility;

import java.util.List;

/**
 * 式から {@link JavaMethodInfo.Call} を抽出し、ラムダ/匿名クラス/メソッド参照の
 * インライン本体を取り込む。既存パーサーの receiver 文字列・firstArgLabel・SAM 名の
 * 規約に合わせる。
 */
final class ExpressionAdapter {

    private ExpressionAdapter() {
    }

    /**
     * 式 {@code e} 内のメソッド呼び出し/メソッド参照を {@code out} に Call として追加する。
     * 呼び出しの第 1 引数がラムダ/匿名クラスならその本体は inlineMethods に取り込み、
     * 兄弟 Call としては出さない。
     */
    static void emitCalls(Expression e, List<JavaMethodInfo.Statement> out, JpContext ctx) {
        if (e != null) {
            walk(e, out, ctx);
        }
    }

    private static void walk(Node n, List<JavaMethodInfo.Statement> out, JpContext ctx) {
        if (n instanceof SwitchExpr) {
            // switch 式は StatementAdapter が Block 化して扱うのでここでは降下しない
            return;
        }
        if (n instanceof MethodCallExpr) {
            walkCall((MethodCallExpr) n, out, ctx);
            return;
        }
        if (n instanceof MethodReferenceExpr) {
            MethodReferenceExpr mr = (MethodReferenceExpr) n;
            if (!"new".equals(mr.getIdentifier())) {
                out.add(new JavaMethodInfo.Call(mr.getScope().toString(), mr.getIdentifier()));
            }
            mr.getScope().getChildNodes().forEach(c -> walk(c, out, ctx));
            return;
        }
        for (Node c : n.getChildNodes()) {
            walk(c, out, ctx);
        }
    }

    private static void walkCall(MethodCallExpr mc, List<JavaMethodInfo.Statement> out,
                                 JpContext ctx) {
        JavaMethodInfo.Call call = new JavaMethodInfo.Call(receiver(mc), mc.getNameAsString());
        setFirstArgLabel(call, mc);
        boolean firstInlined = false;
        if (!mc.getArguments().isEmpty()) {
            Expression a0 = mc.getArgument(0);
            if (isInlineArg(a0)) {
                buildInline(a0, null, mc.getNameAsString(), call.getInlineMethods(), ctx);
                firstInlined = true;
            }
        }
        out.add(call);
        mc.getScope().ifPresent(s -> walk(s, out, ctx));
        for (int i = 0; i < mc.getArguments().size(); i++) {
            if (i == 0 && firstInlined) {
                continue;
            }
            walk(mc.getArgument(i), out, ctx);
        }
    }

    /** 呼び出しの receiver 文字列（{@code ""} / {@code "foo"} / {@code "a.b"} / {@code "this.x"}）。 */
    static String receiver(MethodCallExpr mc) {
        return mc.getScope().map(Node::toString).orElse("");
    }

    private static boolean isInlineArg(Expression e) {
        if (e instanceof LambdaExpr) {
            return true;
        }
        return e instanceof ObjectCreationExpr
                && ((ObjectCreationExpr) e).getAnonymousClassBody().isPresent();
    }

    /**
     * ラムダ/匿名クラスのインライン本体を {@code sink} に取り込む。
     * SAM 名は型ヒント・名前ヒントから {@link JavaParseSupport#resolveSamMethodName} で推定する。
     */
    static void buildInline(Expression arg, String typeHint, String nameHint,
                            List<JavaMethodInfo> sink, JpContext ctx) {
        if (arg instanceof LambdaExpr) {
            LambdaExpr le = (LambdaExpr) arg;
            JavaMethodInfo m = new JavaMethodInfo();
            m.setName(JavaParseSupport.resolveSamMethodName(typeHint, nameHint));
            m.setVisibility(Visibility.PUBLIC);
            if (le.getBody() instanceof BlockStmt) {
                StatementAdapter.emitBody((BlockStmt) le.getBody(), m.getStatements(), ctx);
            } else {
                le.getExpressionBody().ifPresent(
                        ex -> emitCalls(ex, m.getStatements(), ctx));
            }
            sink.add(m);
        } else if (arg instanceof ObjectCreationExpr
                && ((ObjectCreationExpr) arg).getAnonymousClassBody().isPresent()) {
            for (MethodDeclaration md
                    : anonMethods((ObjectCreationExpr) arg)) {
                sink.add(MemberAdapter.toMethod(md, ctx));
            }
        } else if (arg instanceof MethodReferenceExpr) {
            MethodReferenceExpr mr = (MethodReferenceExpr) arg;
            if (!"new".equals(mr.getIdentifier())) {
                JavaMethodInfo m = new JavaMethodInfo();
                m.setName(JavaParseSupport.resolveSamMethodName(typeHint, nameHint));
                m.setVisibility(Visibility.PUBLIC);
                m.getStatements().add(new JavaMethodInfo.Call(
                        mr.getScope().toString(), mr.getIdentifier()));
                sink.add(m);
            }
        }
    }

    private static List<MethodDeclaration> anonMethods(ObjectCreationExpr oce) {
        List<MethodDeclaration> out = new java.util.ArrayList<>();
        oce.getAnonymousClassBody().ifPresent(body -> body.forEach(bd -> {
            if (bd instanceof MethodDeclaration) {
                out.add((MethodDeclaration) bd);
            }
        }));
        return out;
    }

    /**
     * 第 1 引数が定数シンボル（ドット区切りで末尾が UPPER_CASE）なら firstArgLabel に設定する。
     * {@code FOO + 1} や {@code X.method()} のような複合式は対象外。
     */
    private static void setFirstArgLabel(JavaMethodInfo.Call call, MethodCallExpr mc) {
        if (mc.getArguments().isEmpty()) {
            return;
        }
        Expression a0 = mc.getArgument(0);
        String full;
        if (a0 instanceof NameExpr) {
            full = ((NameExpr) a0).getNameAsString();
        } else if (a0 instanceof FieldAccessExpr && isPureNameChain(a0)) {
            full = a0.toString();
        } else {
            return;
        }
        int dot = full.lastIndexOf('.');
        String last = dot < 0 ? full : full.substring(dot + 1);
        if (JavaParseSupport.looksLikeConstantSymbol(last)) {
            call.setFirstArgLabel(full);
        }
    }

    /** {@code a.b.C} のように名前とフィールドアクセスのみで構成される式か。 */
    private static boolean isPureNameChain(Expression e) {
        if (e instanceof NameExpr) {
            return true;
        }
        if (e instanceof FieldAccessExpr) {
            return isPureNameChain(((FieldAccessExpr) e).getScope());
        }
        return false;
    }
}
