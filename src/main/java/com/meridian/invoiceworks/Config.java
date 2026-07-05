package com.meridian.invoiceworks;

import java.io.InputStream;
import java.util.Properties;

/**
 * Loads config.properties off the classpath. If it cannot be found we fall back
 * to hardcoded defaults that match the shipped file.
 */
public class Config {

    private static Properties props;

    private static Properties load() {
        if (props != null) {
            return props;
        }
        props = new Properties();
        InputStream in = Config.class.getClassLoader().getResourceAsStream("config.properties");
        if (in != null) {
            try {
                props.load(in);
            } catch (Exception e) {
                // swallow - defaults below will apply
            } finally {
                try { in.close(); } catch (Exception ignored) { }
            }
        }
        return props;
    }

    public static boolean getBool(String key, boolean dflt) {
        String v = load().getProperty(key);
        if (v == null) {
            return dflt;
        }
        return Boolean.parseBoolean(v.trim());
    }
}
