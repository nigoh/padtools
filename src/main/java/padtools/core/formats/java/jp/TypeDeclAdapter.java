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

import java.util.List;

/**
 * JavaParser の型宣言（class/interface/enum/@interface/record）を {@link JavaClassInfo} に変換する。
 *
 * <p>ネストした型は既存パーサーと同様に「別の top-level エントリ」として {@code out} に並べ、
 * {@code enclosingClass} に外側の単純名チェーン（{@code "Outer"} / {@code "Outer.Mid"}）を設定する。</p>
 */
final class TypeDeclAdapter {

    private TypeDeclAdapter() {
    }

    static void adapt(TypeDeclaration<?> td, String packageName, String enclosing,
                      List<String> imports, List<JavaClassInfo> out) {
        JavaClassInfo c = new JavaClassInfo();
        c.setPackageName(packageName);
        c.setSimpleName(td.getNameAsString());
        c.setEnclosingClass(enclosing);
        c.getImports().addAll(imports);
        c.getModifiers().addAll(JpText.modifiers(td));
        c.getAnnotations().addAll(JpText.annotations(td));
        c.setKind(kindOf(td));
        applyExtendsImplements(td, c);
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof FieldDeclaration) {
                MemberAdapter.addField(c, (FieldDeclaration) bd);
            } else if (bd instanceof MethodDeclaration) {
                MemberAdapter.addMethod(c, (MethodDeclaration) bd);
            } else if (bd instanceof ConstructorDeclaration) {
                MemberAdapter.addConstructor(c, (ConstructorDeclaration) bd);
            }
        }
        if (td instanceof EnumDeclaration) {
            for (EnumConstantDeclaration ec : ((EnumDeclaration) td).getEntries()) {
                c.getEnumConstants().add(ec.getNameAsString());
            }
        }
        out.add(c);
        String childEnclosing = enclosing == null || enclosing.isEmpty()
                ? td.getNameAsString() : enclosing + "." + td.getNameAsString();
        for (BodyDeclaration<?> bd : td.getMembers()) {
            if (bd instanceof TypeDeclaration) {
                adapt((TypeDeclaration<?>) bd, packageName, childEnclosing, imports, out);
            }
        }
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
