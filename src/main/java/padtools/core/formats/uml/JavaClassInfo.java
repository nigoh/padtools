package padtools.core.formats.uml;

import java.util.ArrayList;
import java.util.List;

/**
 * クラス・インタフェース・enum・@interface・AIDL interface の宣言情報。
 */
public class JavaClassInfo {

    /** 種別。 */
    public enum Kind { CLASS, INTERFACE, ENUM, ANNOTATION, AIDL_INTERFACE }

    private String packageName = "";
    private String simpleName = "";
    private Kind kind = Kind.CLASS;
    private final List<String> modifiers = new ArrayList<>();
    private final List<String> annotations = new ArrayList<>();
    private String superClass;
    private final List<String> interfaces = new ArrayList<>();
    private final List<JavaFieldInfo> fields = new ArrayList<>();
    private final List<JavaMethodInfo> methods = new ArrayList<>();
    private final List<String> enumConstants = new ArrayList<>();
    private String enclosingClass;
    private String aaosCategory;
    private String androidComponentType;
    private String comment;
    private boolean detailed = true;

    /** 完全修飾名。{@code com.foo.Outer.Inner} 形式。 */
    public String getQualifiedName() {
        StringBuilder sb = new StringBuilder();
        if (packageName != null && !packageName.isEmpty()) {
            sb.append(packageName).append('.');
        }
        if (enclosingClass != null && !enclosingClass.isEmpty()) {
            sb.append(enclosingClass).append('.');
        }
        sb.append(simpleName);
        return sb.toString();
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName == null ? "" : packageName;
    }

    public String getSimpleName() {
        return simpleName;
    }

    public void setSimpleName(String simpleName) {
        this.simpleName = simpleName;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public String getSuperClass() {
        return superClass;
    }

    public void setSuperClass(String superClass) {
        this.superClass = superClass;
    }

    public List<String> getInterfaces() {
        return interfaces;
    }

    public List<JavaFieldInfo> getFields() {
        return fields;
    }

    public List<JavaMethodInfo> getMethods() {
        return methods;
    }

    public boolean isAbstract() {
        return modifiers.contains("abstract") || kind == Kind.INTERFACE
                || kind == Kind.AIDL_INTERFACE;
    }

    public String getEnclosingClass() {
        return enclosingClass;
    }

    public void setEnclosingClass(String enclosingClass) {
        this.enclosingClass = enclosingClass;
    }

    public String getAaosCategory() {
        return aaosCategory;
    }

    public void setAaosCategory(String aaosCategory) {
        this.aaosCategory = aaosCategory;
    }

    /**
     * AndroidManifest.xml 上の宣言種別 (例: {@code "Activity"} / {@code "Service"} /
     * {@code "BroadcastReceiver"} / {@code "ContentProvider"})。manifest と紐付かなければ null。
     */
    public String getAndroidComponentType() {
        return androidComponentType;
    }

    public void setAndroidComponentType(String androidComponentType) {
        this.androidComponentType = androidComponentType;
    }

    /** enum 定数名のリスト (kind が ENUM の場合のみ意味を持つ)。 */
    public List<String> getEnumConstants() {
        return enumConstants;
    }

    /** JavaDoc / 直前コメントを整形した文字列。未取得時は null。 */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * Stage B (詳細含む) としてロード済みかどうか。false の場合 fields/methods/comment 等は
     * 取得されておらず、必要なら {@link padtools.core.formats.uml.ClassIndex#detail} で昇格させる。
     */
    public boolean isDetailed() {
        return detailed;
    }

    public void setDetailed(boolean detailed) {
        this.detailed = detailed;
    }
}
