package com.tencent.shrinker.util;

public class Util {

    /**
     *
     * 类名是否是R$*.*
     *
     * @return true if name matches pattern like {@code .+/R$.+}
     */
    public static boolean isRClass(String className) {
        int $ = className.lastIndexOf('$');
        int slash = className.lastIndexOf('/', $);
        return $ > slash
                && $ < className.length()
                && (className.charAt(slash + 1) | className.charAt($ - 1)) == 'R';
    }

}
