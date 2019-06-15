package cn.nlifew.scrollcollapsinglayout.utils;

import cn.nlifew.scrollcollapsinglayout.application.ThisApp;

public final class Utils {

    private Utils() {
        throw new RuntimeException("Do NOT to instance.");
    }


    private static float sDensity ;
    private static float sScaledDensity;


    public static int dp2px(int dp) {
        if (sDensity == 0) {
            sDensity = ThisApp.getContext().getResources()
                    .getDisplayMetrics().density;
        }
        return (int) (0.5f + dp * sDensity);
    }

}
