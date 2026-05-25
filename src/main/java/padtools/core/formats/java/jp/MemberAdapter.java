// SPDX-License-Identifier: MIT
// Copyright (c) 2015-2026 naou and contributors

package padtools.core.formats.java.jp;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import padtools.core.formats.uml.JavaClassInfo;
import padtools.core.formats.uml.JavaFieldInfo;
import padtools.core.formats.uml.JavaMethodInfo;
import padtools.core.formats.uml.Visibility;

/**
 * JavaParser のメンバ宣言（フィールド/メソッド/コンストラクタ）を既存モデルへ変換する。
 *
 * <p>P1 では署名（名前・型・可視性・修飾子・throws・アノテーション）のみを移送し、
 * 本体の statement tree は扱わない（P2 の {@code StatementAdapter} で追加する）。</p>
 */
final class MemberAdapter {

    private MemberAdapter() {
    }

    /** {@code int a, b;} のように複数宣言子があれば 1 変数ごとに {@link JavaFieldInfo} を作る。 */
    static void addField(JavaClassInfo owner, FieldDeclaration fd, JpContext ctx) {
        Visibility vis = JpText.visibility(fd);
        boolean isStatic = fd.isStatic();
        boolean isFinal = fd.isFinal();
        java.util.List<String> anns = JpText.annotations(fd);
        String comment = ctx.comments.before(fd);
        for (VariableDeclarator v : fd.getVariables()) {
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(v.getNameAsString());
            f.setType(v.getType().toString());
            f.setVisibility(vis);
            f.setStatic(isStatic);
            f.setFinal(isFinal);
            f.getAnnotations().addAll(anns);
            f.setComment(comment);
            if (!ctx.headersOnly) {
                v.getInitializer().ifPresent(
                        init -> ExpressionAdapter.buildInline(
                                init, f.getType(), f.getName(), f.getInlineMethods(), ctx));
            }
            owner.getFields().add(f);
        }
    }

    static void addMethod(JavaClassInfo owner, MethodDeclaration md, JpContext ctx) {
        owner.getMethods().add(toMethod(md, ctx, owner));
    }

    /** 匿名クラス本体のメソッド用（所有クラスのフィールド代入取り込みは行わない）。 */
    static JavaMethodInfo toMethod(MethodDeclaration md, JpContext ctx) {
        return toMethod(md, ctx, null);
    }

    /** {@link MethodDeclaration} を本体込みで {@link JavaMethodInfo} に変換する。 */
    static JavaMethodInfo toMethod(MethodDeclaration md, JpContext ctx, JavaClassInfo owner) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(md.getNameAsString());
        md.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setReturnType(md.getType().toString());
        addParams(m, md.getParameters());
        m.setVisibility(JpText.visibility(md));
        m.setStatic(md.isStatic());
        boolean interfaceImplicitAbstract = !md.getBody().isPresent()
                && !md.isDefault() && !md.isStatic() && md.findCompilationUnit().isPresent()
                && isInterfaceMethod(md);
        m.setAbstract(md.isAbstract() || interfaceImplicitAbstract);
        m.getAnnotations().addAll(JpText.annotations(md));
        md.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
        m.setComment(ctx.comments.before(md));
        if (!ctx.headersOnly) {
            md.getBody().ifPresent(b -> {
                StatementAdapter.emitBody(b, m.getStatements(), ctx, owner);
                m.getBodyComments().addAll(ctx.comments.within(b));
            });
        }
        return m;
    }

    private static boolean isInterfaceMethod(MethodDeclaration md) {
        return md.getParentNode()
                .filter(p -> p instanceof com.github.javaparser.ast.body
                        .ClassOrInterfaceDeclaration)
                .map(p -> ((com.github.javaparser.ast.body.ClassOrInterfaceDeclaration) p)
                        .isInterface())
                .orElse(false);
    }

    /** アノテーション属性 ({@code int[] value() default {};}) を引数なしメソッドとして扱う。 */
    static void addAnnotationMember(JavaClassInfo owner, AnnotationMemberDeclaration amd,
                                    JpContext ctx) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(amd.getNameAsString());
        amd.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setReturnType(amd.getType().toString());
        m.setVisibility(Visibility.PUBLIC);
        m.setAbstract(true);
        m.getAnnotations().addAll(JpText.annotations(amd));
        m.setComment(ctx.comments.before(amd));
        owner.getMethods().add(m);
    }

    static void addConstructor(JavaClassInfo owner, ConstructorDeclaration cd, JpContext ctx) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(cd.getNameAsString());
        cd.getBegin().ifPresent(p -> m.setStartLine(p.line));
        m.setConstructor(true);
        addParams(m, cd.getParameters());
        m.setVisibility(JpText.visibility(cd));
        m.getAnnotations().addAll(JpText.annotations(cd));
        cd.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
        m.setComment(ctx.comments.before(cd));
        if (!ctx.headersOnly) {
            StatementAdapter.emitBody(cd.getBody(), m.getStatements(), ctx, owner);
            m.getBodyComments().addAll(ctx.comments.within(cd.getBody()));
        }
        owner.getMethods().add(m);
    }

    private static void addParams(JavaMethodInfo m, NodeList<Parameter> params) {
        for (Parameter p : params) {
            String type = p.getType().toString();
            if (p.isVarArgs()) {
                type = type + "...";
            }
            m.getParameterTypes().add(type);
            m.getParameterNames().add(p.getNameAsString());
        }
    }
}
