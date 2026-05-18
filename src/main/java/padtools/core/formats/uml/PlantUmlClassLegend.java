package padtools.core.formats.uml;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * クラス図出力の凡例ブロックを生成する補助クラス。
 *
 * <p>{@link PlantUmlClassDiagram} の出力末尾に置く {@code legend right ... endlegend} を
 * 構築する。実際にダイアグラムへ現れた要素 (可視性・修飾子・ステレオタイプ・関係線・
 * コメント等) だけを列挙して、不要な行を増やさないようにする。</p>
 */
final class PlantUmlClassLegend {

    /** 凡例生成時に対象クラス群から集める利用統計。 */
    static final class Stats {
        Set<String> stereos = new LinkedHashSet<>();
        Set<String> androidStereos = new LinkedHashSet<>();
        Set<String> jetpackStereos = new LinkedHashSet<>();
        boolean hasAbstractClass;
        boolean hasInterface;
        boolean hasEnum;
        boolean hasAnnotationKind;
        boolean hasStatic;
        boolean hasAbstractMember;
        boolean hasInheritance;
        boolean hasImplements;
        boolean hasUsage;
        boolean hasFinal;
        boolean hasComment;
        boolean hasMemberAnnotation;
        boolean hasEnumConstant;
    }

    static void emit(StringBuilder out, List<JavaClassInfo> classes,
                     PlantUmlClassDiagram.Options o) {
        Stats s = collect(classes, o);
        out.append("legend right\n");
        emitVisibility(out, o);
        emitMemberModifiers(out, o, s);
        emitKinds(out, s);
        emitStereotypes(out, s);
        emitRelations(out, o, s);
        emitNotes(out, o, s);
        out.append("endlegend\n");
    }

    private static Stats collect(List<JavaClassInfo> classes,
                                  PlantUmlClassDiagram.Options o) {
        Stats s = new Stats();
        Set<String> known = new HashSet<>();
        for (JavaClassInfo c : classes) {
            known.add(c.getQualifiedName());
            if (c.getAndroidComponentType() != null && !c.getAndroidComponentType().isEmpty()) {
                s.androidStereos.add(c.getAndroidComponentType());
            }
        }
        for (JavaClassInfo c : classes) {
            collectStereotypes(c, o, s);
            collectKindFlags(c, s);
            if (c.getSuperClass() != null && !c.getSuperClass().isEmpty()) {
                s.hasInheritance = true;
            }
            if (!c.getInterfaces().isEmpty()) {
                s.hasImplements = true;
            }
            if (c.getComment() != null && !c.getComment().isEmpty()) {
                s.hasComment = true;
            }
            if (c.getKind() == JavaClassInfo.Kind.ENUM && !c.getEnumConstants().isEmpty()) {
                s.hasEnumConstant = true;
            }
            collectMemberFlags(c, o, known, s);
        }
        return s;
    }

    private static void collectStereotypes(JavaClassInfo c,
                                            PlantUmlClassDiagram.Options o, Stats s) {
        if (o.markAaosCategories) {
            String cat = c.getAaosCategory();
            if (cat == null) {
                cat = AaosPattern.categorize(c);
            }
            if (cat != null) {
                s.stereos.add(cat);
            }
        }
        if (c.getKind() == JavaClassInfo.Kind.AIDL_INTERFACE) {
            s.stereos.add("aidl");
        }
        if (o.jetpack != null && o.jetpack.enabled) {
            s.jetpackStereos.addAll(c.getJetpackStereotypes());
        }
    }

    private static void collectKindFlags(JavaClassInfo c, Stats s) {
        switch (c.getKind()) {
            case INTERFACE:
            case AIDL_INTERFACE:
                s.hasInterface = true;
                break;
            case ENUM:
                s.hasEnum = true;
                break;
            case ANNOTATION:
                s.hasAnnotationKind = true;
                break;
            case CLASS:
            default:
                if (c.isAbstract()) {
                    s.hasAbstractClass = true;
                }
                break;
        }
    }

    private static void collectMemberFlags(JavaClassInfo c,
                                            PlantUmlClassDiagram.Options o,
                                            Set<String> known, Stats s) {
        for (JavaFieldInfo f : c.getFields()) {
            if (f.isStatic()) {
                s.hasStatic = true;
            }
            if (f.isFinal()) {
                s.hasFinal = true;
            }
            if (f.getComment() != null && !f.getComment().isEmpty()) {
                s.hasComment = true;
            }
            if (PlantUmlClassDiagram.hasVisibleAnnotation(f.getAnnotations(), o)) {
                s.hasMemberAnnotation = true;
            }
            if (!s.hasUsage) {
                String tgt = PlantUmlClassDiagram.pickUsageTarget(f.getType(), known);
                if (tgt != null && !tgt.equals(c.getQualifiedName())
                        && !tgt.equals(c.getSimpleName())) {
                    s.hasUsage = true;
                }
            }
        }
        for (JavaMethodInfo m : c.getMethods()) {
            if (m.isStatic()) {
                s.hasStatic = true;
            }
            if (m.isAbstract()) {
                s.hasAbstractMember = true;
            }
            if (m.getComment() != null && !m.getComment().isEmpty()) {
                s.hasComment = true;
            }
            if (PlantUmlClassDiagram.hasVisibleAnnotation(m.getAnnotations(), o)) {
                s.hasMemberAnnotation = true;
            }
        }
    }

    private static void emitVisibility(StringBuilder out, PlantUmlClassDiagram.Options o) {
        if (!o.showVisibility) {
            return;
        }
        out.append("== 可視性 ==\n");
        out.append("+ public\n");
        out.append("- private\n");
        out.append("# protected\n");
        out.append("~ package-private\n");
    }

    private static void emitMemberModifiers(StringBuilder out,
                                             PlantUmlClassDiagram.Options o, Stats s) {
        if (!s.hasStatic && !s.hasAbstractMember && !(o.showFinal && s.hasFinal)) {
            return;
        }
        out.append("== メンバー修飾 ==\n");
        if (s.hasStatic) {
            out.append("{static}    静的 (static)\n");
        }
        if (s.hasAbstractMember) {
            out.append("{abstract}  抽象 (abstract)\n");
        }
        if (o.showFinal && s.hasFinal) {
            out.append("{final}     不変 (final)\n");
        }
    }

    private static void emitKinds(StringBuilder out, Stats s) {
        if (!s.hasAbstractClass && !s.hasInterface && !s.hasEnum && !s.hasAnnotationKind) {
            return;
        }
        out.append("== クラス種別 ==\n");
        out.append("class        通常クラス\n");
        if (s.hasAbstractClass) {
            out.append("abstract     抽象クラス\n");
        }
        if (s.hasInterface) {
            out.append("interface    インタフェース\n");
        }
        if (s.hasEnum) {
            out.append("enum         列挙型\n");
        }
        if (s.hasAnnotationKind) {
            out.append("annotation   アノテーション型\n");
        }
    }

    private static void emitStereotypes(StringBuilder out, Stats s) {
        if (!s.stereos.isEmpty()) {
            out.append("== AAOS ステレオタイプ ==\n");
            for (String x : s.stereos) {
                out.append("<<").append(x).append(">> ")
                        .append(PlantUmlClassDiagram.stereoDesc(x)).append('\n');
            }
        }
        if (!s.androidStereos.isEmpty()) {
            out.append("== Android コンポーネント ==\n");
            for (String x : s.androidStereos) {
                out.append("<<").append(x).append(">> ")
                        .append(PlantUmlClassDiagram.androidStereoDesc(x)).append('\n');
            }
        }
        if (!s.jetpackStereos.isEmpty()) {
            out.append("== Jetpack ステレオタイプ ==\n");
            for (String x : s.jetpackStereos) {
                out.append("<<").append(x).append(">> ")
                        .append(PlantUmlClassDiagram.jetpackStereoDesc(x)).append('\n');
            }
        }
    }

    private static void emitRelations(StringBuilder out,
                                       PlantUmlClassDiagram.Options o, Stats s) {
        boolean any = (o.showInheritance && (s.hasInheritance || s.hasImplements))
                || (o.showUsageRelations && s.hasUsage);
        if (!any) {
            return;
        }
        out.append("== 関係 ==\n");
        if (o.showInheritance && s.hasInheritance) {
            out.append("A <|-- B  : B extends A (継承)\n");
        }
        if (o.showInheritance && s.hasImplements) {
            out.append("A <|.. B  : B implements A (実装)\n");
        }
        if (o.showUsageRelations && s.hasUsage) {
            out.append("A --> B   : A uses B (利用関係)\n");
        }
    }

    private static void emitNotes(StringBuilder out,
                                   PlantUmlClassDiagram.Options o, Stats s) {
        boolean showsComment = o.showComments && s.hasComment;
        boolean showsAnnotation = o.showAnnotations && s.hasMemberAnnotation;
        boolean showsEnumConst = o.showEnumConstants && s.hasEnumConstant;
        if (!showsComment && !showsAnnotation && !showsEnumConst) {
            return;
        }
        out.append("== 注釈 ==\n");
        if (showsComment) {
            if (o.commentStyle == PlantUmlClassDiagram.CommentStyle.NOTE) {
                out.append("note            JavaDoc / 直前コメント\n");
            } else {
                out.append(".. text ..      JavaDoc / 直前コメント\n");
            }
        }
        if (showsAnnotation) {
            out.append("@Foo            アノテーション\n");
        }
        if (showsEnumConst) {
            out.append("ENUM_CONST      enum 定数\n");
        }
    }

    private PlantUmlClassLegend() {
    }
}
