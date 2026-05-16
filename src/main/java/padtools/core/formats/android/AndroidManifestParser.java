package padtools.core.formats.android;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import padtools.util.ErrorListener;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * AndroidManifest.xml をパースして {@link AndroidManifestInfo} を返すクラス。
 *
 * <p>{@code javax.xml.parsers.DocumentBuilderFactory} を用いた DOM パース。
 * 外部エンティティ解決を無効化し、XXE 攻撃を防止する。</p>
 */
public final class AndroidManifestParser {

    /** XML 名前空間: Android。 */
    public static final String NS_ANDROID = "http://schemas.android.com/apk/res/android";

    /** デフォルト ErrorListener (silent) でパース。 */
    public static AndroidManifestInfo parse(String xml) {
        return parse(xml, null);
    }

    /** ErrorListener 付きでパース。XML 不正は listener に通知して空の情報を返す。 */
    public static AndroidManifestInfo parse(String xml, ErrorListener listener) {
        if (xml == null) {
            throw new IllegalArgumentException("xml is null");
        }
        ErrorListener l = listener != null ? listener : ErrorListener.silent();
        AndroidManifestInfo info = new AndroidManifestInfo();
        Document doc;
        try {
            DocumentBuilder builder = createSecureBuilder();
            doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            l.onError(null, -1, "manifest parse failed: " + ex.getMessage());
            return info;
        }
        Element root = doc.getDocumentElement();
        if (root == null || !"manifest".equals(root.getNodeName())) {
            l.onError(null, -1, "root element is not <manifest>");
            return info;
        }
        info.setPackageName(attr(root, "package", ""));
        parseUsesPermissions(root, info);
        parseUsesFeatures(root, info);
        Element application = firstChildElement(root, "application");
        if (application != null) {
            parseApplication(application, info);
        }
        return info;
    }

    private static DocumentBuilder createSecureBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
        } catch (Exception ignore) {
            // 一部の features は古い XML パーサで未対応。可能なものだけ設定
        }
        return factory.newDocumentBuilder();
    }

    private static void parseUsesPermissions(Element root, AndroidManifestInfo info) {
        NodeList nodes = root.getElementsByTagName("uses-permission");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String name = attr(e, "name", "");
            if (name.isEmpty()) {
                continue;
            }
            AndroidPermissionInfo p = new AndroidPermissionInfo(name);
            Integer max = parseInt(attr(e, "maxSdkVersion", null));
            if (max != null) {
                p.setMaxSdkVersion(max);
            }
            info.getPermissions().add(p);
        }
    }

    private static void parseUsesFeatures(Element root, AndroidManifestInfo info) {
        NodeList nodes = root.getElementsByTagName("uses-feature");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String name = attr(e, "name", "");
            if (!name.isEmpty()) {
                info.getFeatures().add(name);
            }
        }
    }

    private static void parseApplication(Element app, AndroidManifestInfo info) {
        String cls = attr(app, "name", null);
        if (cls != null) {
            info.setApplicationClass(info.resolveClassName(cls));
        }
        info.setApplicationLabel(attr(app, "label", null));
        info.setApplicationTheme(attr(app, "theme", null));
        info.setApplicationDebuggable(parseBool(attr(app, "debuggable", null)));
        info.setApplicationAllowBackup(parseBool(attr(app, "allowBackup", null)));
        // application 直下の meta-data
        for (Element md : childElements(app, "meta-data")) {
            String name = attr(md, "name", "");
            String value = attr(md, "value", attr(md, "resource", ""));
            if (!name.isEmpty()) {
                info.getApplicationMetaData().put(name, value);
            }
        }
        // 各コンポーネント
        for (Element a : childElements(app, "activity")) {
            info.getActivities().add(buildComponent(a, AndroidComponentInfo.Kind.ACTIVITY, info));
        }
        for (Element a : childElements(app, "activity-alias")) {
            info.getActivities().add(buildComponent(a, AndroidComponentInfo.Kind.ACTIVITY, info));
        }
        for (Element s : childElements(app, "service")) {
            info.getServices().add(buildComponent(s, AndroidComponentInfo.Kind.SERVICE, info));
        }
        for (Element r : childElements(app, "receiver")) {
            info.getReceivers().add(buildComponent(r, AndroidComponentInfo.Kind.RECEIVER, info));
        }
        for (Element p : childElements(app, "provider")) {
            info.getProviders().add(buildComponent(p, AndroidComponentInfo.Kind.PROVIDER, info));
        }
    }

    private static AndroidComponentInfo buildComponent(Element e,
                                                        AndroidComponentInfo.Kind kind,
                                                        AndroidManifestInfo info) {
        String raw = attr(e, "name", "");
        AndroidComponentInfo c = new AndroidComponentInfo(kind, info.resolveClassName(raw));
        c.setExported(parseBool(attr(e, "exported", null)));
        c.setEnabled(parseBool(attr(e, "enabled", null)));
        c.setTaskAffinity(attr(e, "taskAffinity", null));
        c.setProcess(attr(e, "process", null));
        c.setPermission(attr(e, "permission", null));
        c.setAuthorities(attr(e, "authorities", null));
        for (Element f : childElements(e, "intent-filter")) {
            c.getIntentFilters().add(buildIntentFilter(f));
        }
        for (Element md : childElements(e, "meta-data")) {
            String name = attr(md, "name", "");
            String value = attr(md, "value", attr(md, "resource", ""));
            if (!name.isEmpty()) {
                c.getMetaData().put(name, value);
            }
        }
        return c;
    }

    private static AndroidIntentFilter buildIntentFilter(Element e) {
        AndroidIntentFilter f = new AndroidIntentFilter();
        f.setPriority(parseInt(attr(e, "priority", null)));
        for (Element a : childElements(e, "action")) {
            String name = attr(a, "name", "");
            if (!name.isEmpty()) {
                f.getActions().add(name);
            }
        }
        for (Element c : childElements(e, "category")) {
            String name = attr(c, "name", "");
            if (!name.isEmpty()) {
                f.getCategories().add(name);
            }
        }
        for (Element d : childElements(e, "data")) {
            String scheme = attr(d, "scheme", "");
            String mime = attr(d, "mimeType", "");
            if (!scheme.isEmpty()) {
                f.getDataSchemes().add(scheme);
            }
            if (!mime.isEmpty()) {
                f.getDataMimeTypes().add(mime);
            }
        }
        return f;
    }

    // --- DOM ヘルパ ---

    private static String attr(Element e, String localName, String fallback) {
        // android: 名前空間付き属性を優先、無ければ素の属性名を試す
        String v = e.getAttributeNS(NS_ANDROID, localName);
        if (v == null || v.isEmpty()) {
            v = e.getAttribute(localName);
        }
        return (v == null || v.isEmpty()) ? fallback : v;
    }

    private static Element firstChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                return (Element) n;
            }
        }
        return null;
    }

    private static java.util.List<Element> childElements(Element parent, String name) {
        java.util.List<Element> list = new java.util.ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(name)) {
                list.add((Element) n);
            }
        }
        return list;
    }

    private static Boolean parseBool(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        if ("true".equalsIgnoreCase(s)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(s)) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseInt(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private AndroidManifestParser() {
    }
}
