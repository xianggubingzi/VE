package com.VE.videoEditor.Effect.interfaces;

import com.VE.videoEditor.Effect.AutoFixEffect;
import com.VE.videoEditor.Effect.BlackAndWhiteEffect;
import com.VE.videoEditor.Effect.BrightnessEffect;
import com.VE.videoEditor.Effect.ContrastEffect;
import com.VE.videoEditor.Effect.CrossProcessEffect;
import com.VE.videoEditor.Effect.DocumentaryEffect;
import com.VE.videoEditor.Effect.DuotoneEffect;
import com.VE.videoEditor.Effect.FillLightEffect;
import com.VE.videoEditor.Effect.GrainEffect;
import com.VE.videoEditor.Effect.GreyScaleEffect;
import com.VE.videoEditor.Effect.InvertColorsEffect;
import com.VE.videoEditor.Effect.LamoishEffect;
import com.VE.videoEditor.Effect.NoEffect;
import com.VE.videoEditor.Effect.PosterizeEffect;
import com.VE.videoEditor.Effect.SaturationEffect;
import com.VE.videoEditor.Effect.SepiaEffect;
import com.VE.videoEditor.Effect.SharpnessEffect;
import com.VE.videoEditor.Effect.TemperatureEffect;
import com.VE.videoEditor.Effect.TintEffect;
import com.VE.videoEditor.Effect.VignetteEffect;

import android.graphics.Color;

public class VideoEffect {
    public static final int EFFECT_NONE = 1;
    public static final int EFFECT_AUTO_FIX = 2;
    public static final int EFFECT_BLACK_WHITE = 3;
    public static final int EFFECT_BRIGHTNESS = 4;
    public static final int EFFECT_CONTRAST = 5;
    public static final int EFFECT_CROSS_PROCESS = 6;
    public static final int EFFECT_DOCUMENTARY = 7;
    public static final int EFFECT_DUOTONE = 8;
    public static final int EFFECT_FILL_LIGHT = 9;
    public static final int EFFECT_GRAIN = 10;
    public static final int EFFECT_GREY_SCALE = 11;
    public static final int EFFECT_INVERT_COLOR = 12;
    public static final int EFFECT_LAMOISH = 13;
    public static final int EFFECT_POSTERIZE = 14;
    public static final int EFFECT_SATURATION = 15;
    public static final int EFFECT_SEPIA = 16;
    public static final int EFFECT_SHARPNESS = 17;
    public static final int EFFECT_TEMPERATURE = 18;
    public static final int EFFECT_TINT = 19;
    public static final int EFFECT_VIGNETTE = 20;
    public static final int EFFECT_MAX = EFFECT_VIGNETTE;

    public static ShaderInterface createEffect(int effectNo, int arg1) {
        ShaderInterface effect;
        float den = 100.0f;
        switch (effectNo) {
        case EFFECT_NONE:
            effect = new NoEffect();
            break;
        case EFFECT_AUTO_FIX:
            den = 50.0f;
            effect = new AutoFixEffect(arg1 / den);
            break;
        case EFFECT_BLACK_WHITE:
            effect = new BlackAndWhiteEffect();
            break;
        case EFFECT_BRIGHTNESS:
            den = 50.0f;
            effect = new BrightnessEffect(arg1 / den);
            break;
        case EFFECT_CONTRAST:
            den = 50.0f;
            effect = new ContrastEffect(arg1 / den);
            break;
        case EFFECT_CROSS_PROCESS:
            effect = new CrossProcessEffect();
            break;
        case EFFECT_DOCUMENTARY:
            effect = new DocumentaryEffect();
            break;
        case EFFECT_DUOTONE:
            effect = new DuotoneEffect(Color.BLUE, Color.RED);
            break;
        case EFFECT_FILL_LIGHT:
            effect = new FillLightEffect(arg1 / den);
            break;
        case EFFECT_GRAIN:
            effect = new GrainEffect(arg1 / den);
            break;
        case EFFECT_GREY_SCALE:
            effect = new GreyScaleEffect();
            break;
        case EFFECT_INVERT_COLOR:
            effect = new InvertColorsEffect();
            break;
        case EFFECT_LAMOISH:
            effect = new LamoishEffect();
            break;
        case EFFECT_POSTERIZE:
            effect = new PosterizeEffect();
            break;
        case EFFECT_SATURATION:
            den = 50.0f;
            effect = new SaturationEffect(arg1 / den - 1.0f);
            break;
        case EFFECT_SEPIA:
            effect = new SepiaEffect();
            break;
        case EFFECT_SHARPNESS:
            effect = new SharpnessEffect(arg1 / den);
            break;
        case EFFECT_TEMPERATURE:
            effect = new TemperatureEffect(arg1 / den);
            break;
        case EFFECT_TINT:
            effect = new TintEffect(Color.GREEN);
            break;
        case EFFECT_VIGNETTE:
            effect = new VignetteEffect(arg1 / den);
            break;
        default:
            effect = new NoEffect();
            break;
        }
        return effect;
    }

    public static String getEffectName(int effectNo) {
        String name;
        switch (effectNo) {
        case EFFECT_NONE:
            name = new String("EFFECT_NONE");
            break;
        case EFFECT_AUTO_FIX:
            name = new String("EFFECT_AUTO_FIX");
            break;
        case EFFECT_BLACK_WHITE:
            name = new String("EFFECT_BLACK_WHITE");
            break;
        case EFFECT_BRIGHTNESS:
            name = new String("EFFECT_BRIGHTNESS");
            break;
        case EFFECT_CONTRAST:
            name = new String("EFFECT_CONTRAST");
            break;
        case EFFECT_CROSS_PROCESS:
            name = new String("EFFECT_CROSS_PROCESS");
            break;
        case EFFECT_DOCUMENTARY:
            name = new String("EFFECT_DOCUMENTARY");
            break;
        case EFFECT_DUOTONE:
            name = new String("EFFECT_DUOTONE");
            break;
        case EFFECT_FILL_LIGHT:
            name = new String("EFFECT_FILL_LIGHT");
            break;
        case EFFECT_GRAIN:
            name = new String("EFFECT_GRAIN");
            break;
        case EFFECT_GREY_SCALE:
            name = new String("EFFECT_GREY_SCALE");
            break;
        case EFFECT_INVERT_COLOR:
            name = new String("EFFECT_INVERT_COLOR");
            break;
        case EFFECT_LAMOISH:
            name = new String("EFFECT_LAMOISH");
            break;
        case EFFECT_POSTERIZE:
            name = new String("EFFECT_POSTERIZE");
            break;
        case EFFECT_SATURATION:
            name = new String("EFFECT_SATURATION");
            break;
        case EFFECT_SEPIA:
            name = new String("EFFECT_SEPIA");
            break;
        case EFFECT_SHARPNESS:
            name = new String("EFFECT_SHARPNESS");
            break;
        case EFFECT_TEMPERATURE:
            name = new String("EFFECT_TEMPERATURE");
            break;
        case EFFECT_TINT:
            name = new String("EFFECT_TINT");
            break;
        case EFFECT_VIGNETTE:
            name = new String("EFFECT_VIGNETTE");
            break;
        default:
            name = new String("EFFECT_NONE");
            break;
        }
        return name;
    }

    public static String getEffectCnName(int effectNo) {
        String name;
        switch (effectNo) {
        case EFFECT_NONE:
            name = new String("无滤镜");
            break;
        case EFFECT_AUTO_FIX:
            name = new String("自动填充");
            break;
        case EFFECT_BLACK_WHITE:
            name = new String("黑白");
            break;
        case EFFECT_BRIGHTNESS:
            name = new String("亮度");
            break;
        case EFFECT_CONTRAST:
            name = new String("对比度");
            break;
        case EFFECT_CROSS_PROCESS:
            name = new String("负片冲印");
            break;
        case EFFECT_DOCUMENTARY:
            name = new String("纪录片");
            break;
        case EFFECT_DUOTONE:
            name = new String("双色");
            break;
        case EFFECT_FILL_LIGHT:
            name = new String("光照");
            break;
        case EFFECT_GRAIN:
            name = new String("雪花");
            break;
        case EFFECT_GREY_SCALE:
            name = new String("灰度级");
            break;
        case EFFECT_INVERT_COLOR:
            name = new String("反色");
            break;
        case EFFECT_LAMOISH:
            name = new String("LAMOISH");
            break;
        case EFFECT_POSTERIZE:
            name = new String("多色调");
            break;
        case EFFECT_SATURATION:
            name = new String("饱和度");
            break;
        case EFFECT_SEPIA:
            name = new String("复古");
            break;
        case EFFECT_SHARPNESS:
            name = new String("锐化");
            break;
        case EFFECT_TEMPERATURE:
            name = new String("色温");
            break;
        case EFFECT_TINT:
            name = new String("浅色调");
            break;
        case EFFECT_VIGNETTE:
            name = new String("聚焦");
            break;
        default:
            name = new String("EFFECT_NONE");
            break;
        }
        return name;
    }
}
