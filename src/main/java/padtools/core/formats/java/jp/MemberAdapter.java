package padtools.core.formats.java.jp;

import com.github.javaparser.ast.NodeList;
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
    static void addField(JavaClassInfo owner, FieldDeclaration fd) {
        Visibility vis = JpText.visibility(fd);
        boolean isStatic = fd.isStatic();
        boolean isFinal = fd.isFinal();
        java.util.List<String> anns = JpText.annotations(fd);
        for (VariableDeclarator v : fd.getVariables()) {
            JavaFieldInfo f = new JavaFieldInfo();
            f.setName(v.getNameAsString());
            f.setType(v.getType().toString());
            f.setVisibility(vis);
            f.setStatic(isStatic);
            f.setFinal(isFinal);
            f.getAnnotations().addAll(anns);
            owner.getFields().add(f);
        }
    }

    static void addMethod(JavaClassInfo owner, MethodDeclaration md) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(md.getNameAsString());
        m.setReturnType(md.getType().toString());
        addParams(m, md.getParameters());
        m.setVisibility(JpText.visibility(md));
        m.setStatic(md.isStatic());
        boolean interfaceImplicitAbstract = owner.getKind() == JavaClassInfo.Kind.INTERFACE
                && !md.getBody().isPresent() && !md.isDefault() && !md.isStatic();
        m.setAbstract(md.isAbstract() || interfaceImplicitAbstract);
        m.getAnnotations().addAll(JpText.annotations(md));
        md.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
        owner.getMethods().add(m);
    }

    static void addConstructor(JavaClassInfo owner, ConstructorDeclaration cd) {
        JavaMethodInfo m = new JavaMethodInfo();
        m.setName(cd.getNameAsString());
        m.setConstructor(true);
        addParams(m, cd.getParameters());
        m.setVisibility(JpText.visibility(cd));
        m.getAnnotations().addAll(JpText.annotations(cd));
        cd.getThrownExceptions().forEach(t -> m.getThrowsTypes().add(t.toString()));
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
