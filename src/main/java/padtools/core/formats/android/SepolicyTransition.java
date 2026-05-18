package padtools.core.formats.android;

/**
 * SELinux の {@code type_transition <src> <tgt>:<class> <newtype>;} 宣言。
 *
 * <p>AOSP の process / file ドメイン遷移を表す。
 * 例: {@code type_transition init bootanim_exec:process bootanim;} は init が
 * bootanim_exec を実行するときに bootanim ドメインに遷移することを示す。</p>
 */
public final class SepolicyTransition {

    private final String sourceType;
    private final String targetType;
    private final String objectClass;
    private final String newType;

    public SepolicyTransition(String sourceType, String targetType,
                               String objectClass, String newType) {
        this.sourceType = sourceType == null ? "" : sourceType;
        this.targetType = targetType == null ? "" : targetType;
        this.objectClass = objectClass == null ? "" : objectClass;
        this.newType = newType == null ? "" : newType;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getObjectClass() {
        return objectClass;
    }

    public String getNewType() {
        return newType;
    }

    @Override
    public String toString() {
        return "type_transition " + sourceType + " " + targetType + ":" + objectClass
                + " " + newType;
    }
}
