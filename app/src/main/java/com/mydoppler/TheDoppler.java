package com.mydoppler;

import com.mydoppler.ToolPackage.Doppler;
/**
 * Created by CullenGao on 16/2/20.
 */
public class TheDoppler {
    private static Doppler doppler;

    public static Doppler getDoppler() {
        if (doppler == null) {
            doppler = new Doppler();
        }
        return doppler;
    }
}

