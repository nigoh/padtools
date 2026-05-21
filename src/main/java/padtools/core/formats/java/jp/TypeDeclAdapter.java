package padtools.core.formats.java.jp;

import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import padtools.core.formats.uml.JavaClassInfo;

/**
 * JavaParser の型宣言（class/interface/enum/@interface/record）を {@link JavaClassInfo} に変換する。
 *
 * <p>ネストした型は既存パーサーと同様に「別の top-level エントリ」として {@code out} に並べ、
 * {@code enclosingClass} に外側の単純名チェーン（{@code "Outer"} / {@code "Outer.Mid"}）を設定する。</p>
 */
final class TypeDeclAdapter {

    private TypeDeclAdapter() {
    }

    /** トップレベル/ネスト型を変換する。 */
    static void adapt(TypeDeclaration<?> td, String enclosing, JpContext ctx) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(ctx.packageName);
        c.setSimpleName(td.getNameAsString());
        c.setEnclosingClass(enclosing);
        c.getImports().addAll(ctx.imports);
        c.getModifiers().addAll(JpText.modifiers(td));
        c.getAnnotations().addAll(JpText.annotations(td));
        c.setKind(kindOf(td));
        c.setComment(ctx.comments.before(td));
        applyExtendsImplements(td, c);
        String childEnclosing = enclosing == null || enclosing.isEmpty()
                ? td.getNameAsString() : enclosing + "." + td.getNameAsString();
        String savedEnclosing = ctx.currentEnclosing;
        ctx.currentEnclosing = childEnclosing;
        // フィールドを先に確定させてから本体を解析する
        // (コンストラクタ内 this.field = ... の inline 取り込みが後方宣言でも効くように)。
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof FieldDeclaration) {
                MemberAdapter.addField(c, (FieldDeclaration) bd, ctx);
            }
        }
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof MethodDeclaration) {
                MemberAdapter.addMethod(c, (MethodDeclaration) bd, ctx);
            } else if (bd instanceof ConstructorDeclaration) {
                MemberAdapter.addConstructor(c, (ConstructorDeclaration) bd, ctx);
            } else if (bd instanceof com.github.javaparser.ast.body
                    .AnnotationMemberDeclaration) {
                MemberAdapter.addAnnotationMember(c,
                        (com.github.javaparser.ast.body.AnnotationMemberDeclaration) bd, ctx);
            }
        }
        ctx.currentEnclosing = savedEnclosing;
        if (td instanceof EnumDeclaration) {
            for (EnumConstantDeclaration ec : ((EnumDeclaration) td).getEntries()) {
                c.getEnumConstants().add(ec.getNameAsString());
            }
        }
        ctx.out.add(c);
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof TypeDeclaration) {
                adapt((TypeDeclaration<?>) bd, childEnclosing, ctx);
            }
        }
    }

    /** メソッド本体内のローカルクラス/レコードを別 top-level エントリとして追加する。 */
    static void adaptLocal(TypeDeclaration<?> td, JpContext ctx) {
        adapt(td, ctx.currentEnclosing, ctx);
    }

    private static JavaClassInfo.Kind kindOf(TypeDeclaration<?> td) {
        if (td instanceof RecordDeclaration) {
            return JavaClassInfo.Kind.RECORD;
        }
        if (td instanceof EnumDeclaration) {
            return JavaClassInfo.Kind.ENUM;
        }
        if (td instanceof AnnotationDeclaration) {
            return JavaClassInfo.Kind.ANNOTATION;
        }
        if (td instanceof ClassOrInterfaceDeclaration
                && ((ClassOrInterfaceDeclaration) td).isInterface()) {
            return JavaClassInfo.Kind.INTERFACE;
        }
        return JavaClassInfo.Kind.CLASS;
    }

    private static void applyExtendsImplements(TypeDeclaration<?> td, JavaClassInfo c) {
        if (td instanceof ClassOrInterfaceDeclaration) {
            ClassOrInterfaceDeclaration cid = (ClassOrInterfaceDeclaration) td;
            if (cid.isInterface()) {
                cid.getExtendedTypes().forEach(t -> c.getInterfaces().add(t.toString()));
            } else {
                if (!cid.getExtendedTypes().isEmpty()) {
                    c.setSuperClass(cid.getExtendedTypes().get(0).toString());
                }
                cid.getImplementedTypes().forEach(t -> c.getInterfaces().add(t.toString()));
            }
            // sealed の permits は interfaces に併合（既存モデルの扱いに合わせる）
            cid.getPermittedTypes().forEach(t -> c.getInterfaces().add(t.toString()));
        } else if (td instanceof RecordDeclaration) {
            ((RecordDeclaration) td).getImplementedTypes()
                    .forEach(t -> c.getInterfaces().add(t.toString()));
        } else if (td instanceof EnumDeclaration) {
            ((EnumDeclaration) td).getImplementedTypes()
                    .forEach(t -> c.getInterfaces().add(t.toString()));
        }
    }
}
