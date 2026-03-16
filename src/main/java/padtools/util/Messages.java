package padtools.util;

import java.util.ResourceBundle;

/**
 * i18nメッセージリソースを提供するユーティリティクラス。
 */
public class Messages {
    private static final ResourceBundle bundle = ResourceBundle.getBundle("messages");

    public static String get(String key) {
        try {
            return bundle.getString(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }
}
