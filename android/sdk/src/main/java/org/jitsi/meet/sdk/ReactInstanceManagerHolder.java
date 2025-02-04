/*
 * Copyright @ 2017-present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jitsi.meet.sdk;

import android.app.Activity;
import android.app.Application;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import com.facebook.react.ReactInstanceManager;
import com.facebook.react.ReactPackage;
import com.facebook.react.bridge.NativeModule;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.common.LifecycleState;
import com.facebook.react.jscexecutor.JSCExecutorFactory;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.uimanager.ViewManager;
import com.oney.WebRTCModule.EglUtils;
import com.oney.WebRTCModule.RTCVideoViewManager;
import com.oney.WebRTCModule.WebRTCModule;

import org.devio.rn.splashscreen.SplashScreenModule;
import org.webrtc.EglBase;
import org.webrtc.HardwareVideoDecoderFactory;
import org.webrtc.HardwareVideoEncoderFactory;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class ReactInstanceManagerHolder {
    /**
     * FIXME (from linter): Do not place Android context classes in static
     * fields (static reference to ReactInstanceManager which has field
     * mApplicationContext pointing to Context); this is a memory leak (and
     * also breaks Instant Run).
     *
     * React Native bridge. The instance manager allows embedding applications
     * to create multiple root views off the same JavaScript bundle.
     */
    private static ReactInstanceManager reactInstanceManager;

    private static List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
        List<NativeModule> nativeModules
            = new ArrayList<>(Arrays.<NativeModule>asList(
                new AndroidSettingsModule(reactContext),
                new AppInfoModule(reactContext),
                new AudioModeModule(reactContext),
                new DropboxModule(reactContext),
                new ExternalAPIModule(reactContext),
                new JavaScriptSandboxModule(reactContext),
                new LocaleDetector(reactContext),
                new LogBridgeModule(reactContext),
                new SplashScreenModule(reactContext),
                new PictureInPictureModule(reactContext),
                new ProximityModule(reactContext),
                new WiFiStatsModule(reactContext),
                new org.jitsi.meet.sdk.net.NAT64AddrInfoModule(reactContext)));

        if (AudioModeModule.useConnectionService()) {
            nativeModules.add(new RNConnectionService(reactContext));
        }

        // Initialize the WebRTC module by hand, since we want to override some
        // initialization options.
        WebRTCModule.Options options = new WebRTCModule.Options();

        AudioDeviceModule adm = JavaAudioDeviceModule.builder(reactContext)
            .createAudioDeviceModule();
        options.setAudioDeviceModule(adm);

        boolean hasH264HWDecoder = false;
        boolean hasVP8HWDecoder = false;
        // boolean hasVP9HWDecoder = false;

        boolean hasH264HWEncoder = false;
        boolean hasVP8HWEncoder = false;
        // boolean hasVP9HWEncoder = false;

        VideoDecoderFactory videoDecoderFactory = null;
        VideoEncoderFactory videoEncoderFactory = null;

        EglBase.Context eglContext = EglUtils.getRootEglBaseContext();

        if (eglContext != null) {
            MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
            MediaCodecInfo[] mediaCodecInfos = mediaCodecList.getCodecInfos();

            for (MediaCodecInfo mediaCodecInfo : mediaCodecInfos) {
                String mediaCodecUpperName = mediaCodecInfo.getName().toUpperCase();
                if(mediaCodecUpperName.startsWith("OMX.") &&
                    !mediaCodecUpperName.startsWith("OMX.GOOGLE") &&
                    !mediaCodecUpperName.endsWith("SW")) {
                    if(mediaCodecUpperName.contains("H264") || mediaCodecUpperName.contains("AVC")) {
                        hasH264HWDecoder |= !mediaCodecInfo.isEncoder();
                        hasH264HWEncoder |= mediaCodecInfo.isEncoder();
                    } else if (mediaCodecUpperName.contains("VP8")) {
                        hasVP8HWDecoder |= !mediaCodecInfo.isEncoder();
                        hasVP8HWEncoder |= mediaCodecInfo.isEncoder();
                    }
                    //  else if(mediaCodecUpperName.contains(("VP9"))) {
                    //     hasVP9HWDecoder|= !mediaCodecInfo.isEncoder();
                    //     hasVP9HWEncoder |= mediaCodecInfo.isEncoder();
                    // }
                }
            }
        }

        if (hasH264HWDecoder && hasVP8HWDecoder/* && hasVP9HWDecoder*/) {
            videoDecoderFactory = new HardwareVideoDecoderFactory(eglContext);
        } else {
            videoDecoderFactory = new SoftwareVideoDecoderFactory();
        }

        if (hasH264HWEncoder && hasVP8HWEncoder/* && hasVP9HWEncoder*/) {
            videoEncoderFactory = new HardwareVideoEncoderFactory(eglContext, false, false);
        } else  {
            videoEncoderFactory = new SoftwareVideoEncoderFactory();
        }

        options.setVideoDecoderFactory(videoDecoderFactory);
        options.setVideoEncoderFactory(videoEncoderFactory);

        nativeModules.add(new WebRTCModule(reactContext, options));

        return nativeModules;
    }

    private static List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
        return Arrays.<ViewManager>asList(
            // WebRTC, see createNativeModules for details.
            new RTCVideoViewManager()
        );
    }

    static List<ReactPackage> getReactNativePackages() {
        List<ReactPackage> packages
            = new ArrayList<>(Arrays.asList(
            new com.reactnativecommunity.asyncstorage.AsyncStoragePackage(),
            new com.ocetnik.timer.BackgroundTimerPackage(),
            new com.calendarevents.RNCalendarEventsPackage(),
            new com.corbt.keepawake.KCKeepAwakePackage(),
            new com.facebook.react.shell.MainReactPackage(),
            new com.reactnativecommunity.clipboard.ClipboardPackage(),
            new com.giphyreactnativesdk.GiphyReactNativeSdkPackage(),
            new com.reactnativecommunity.netinfo.NetInfoPackage(),
            new com.reactnativepagerview.PagerViewPackage(),
            new com.oblador.performance.PerformancePackage(),
            new com.reactnativecommunity.slider.ReactSliderPackage(),
            new com.brentvatne.react.ReactVideoPackage(),
            new com.swmansion.reanimated.ReanimatedPackage(),
            new org.reactnative.maskedview.RNCMaskedViewPackage(),
            new com.reactnativecommunity.webview.RNCWebViewPackage(),
            new com.kevinresol.react_native_default_preference.RNDefaultPreferencePackage(),
            new com.learnium.RNDeviceInfo.RNDeviceInfo(),
            new com.swmansion.gesturehandler.react.RNGestureHandlerPackage(),
            new org.linusu.RNGetRandomValuesPackage(),
            new com.rnimmersive.RNImmersivePackage(),
            new com.swmansion.rnscreens.RNScreensPackage(),
            new com.zmxv.RNSound.RNSoundPackage(),
            new com.th3rdwave.safeareacontext.SafeAreaContextPackage(),
            new com.horcrux.svg.SvgPackage(),
            new ReactPackageAdapter() {
                @Override
                public List<NativeModule> createNativeModules(ReactApplicationContext reactContext) {
                    return ReactInstanceManagerHolder.createNativeModules(reactContext);
                }
                @Override
                public List<ViewManager> createViewManagers(ReactApplicationContext reactContext) {
                    return ReactInstanceManagerHolder.createViewManagers(reactContext);
                }
            }));

        // AmplitudeReactNativePackage
        try {
            Class<?> amplitudePackageClass = Class.forName("com.amplitude.reactnative.AmplitudeReactNativePackage");
            Constructor constructor = amplitudePackageClass.getConstructor();
            packages.add((ReactPackage)constructor.newInstance());
        } catch (Exception e) {
            // Ignore any error, the module is not compiled when LIBRE_BUILD is enabled.
        }

        // RNGoogleSignInPackage
        try {
            Class<?> googlePackageClass = Class.forName("com.reactnativegooglesignin.RNGoogleSigninPackage");
            Constructor constructor = googlePackageClass.getConstructor();
            packages.add((ReactPackage)constructor.newInstance());
        } catch (Exception e) {
            // Ignore any error, the module is not compiled when LIBRE_BUILD is enabled.
        }

        return packages;
    }

    static JSCExecutorFactory getReactNativeJSFactory() {
        // Keep on using JSC, the jury is out on Hermes.
        return new JSCExecutorFactory("", "");
    }

    /**
     * Helper function to send an event to JavaScript.
     *
     * @param eventName {@code String} containing the event name.
     * @param data {@code Object} optional ancillary data for the event.
     */
    static void emitEvent(
            String eventName,
            @Nullable Object data) {
        ReactInstanceManager reactInstanceManager
            = ReactInstanceManagerHolder.getReactInstanceManager();

        if (reactInstanceManager != null) {
            ReactContext reactContext
                = reactInstanceManager.getCurrentReactContext();

            if (reactContext != null) {
                reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                    .emit(eventName, data);
            }
        }
    }

    /**
     * Finds a native React module for given class.
     *
     * @param nativeModuleClass the native module's class for which an instance
     * is to be retrieved from the {@link #reactInstanceManager}.
     * @param <T> the module's type.
     * @return {@link NativeModule} instance for given interface type or
     * {@code null} if no instance for this interface is available, or if
     * {@link #reactInstanceManager} has not been initialized yet.
     */
    static <T extends NativeModule> T getNativeModule(
            Class<T> nativeModuleClass) {
        ReactContext reactContext
            = reactInstanceManager != null
                ? reactInstanceManager.getCurrentReactContext() : null;

        return reactContext != null
                ? reactContext.getNativeModule(nativeModuleClass) : null;
    }

    /**
     * Gets the current {@link Activity} linked to React Native.
     *
     * @return An activity attached to React Native.
     */
    static Activity getCurrentActivity() {
        ReactContext reactContext
            = reactInstanceManager != null
            ? reactInstanceManager.getCurrentReactContext() : null;
        return reactContext != null ? reactContext.getCurrentActivity() : null;
    }

    static ReactInstanceManager getReactInstanceManager() {
        return reactInstanceManager;
    }

    /**
     * Internal method to initialize the React Native instance manager. We
     * create a single instance in order to load the JavaScript bundle a single
     * time. All {@code ReactRootView} instances will be tied to the one and
     * only {@code ReactInstanceManager}.
     *
     * This method is only meant to be called when integrating with {@code JitsiReactNativeHost}.
     *
     * @param app {@code Application} current running Application.
     */
    static void initReactInstanceManager(Application app) {
        if (reactInstanceManager != null) {
            return;
        }

        Log.d(ReactInstanceManagerHolder.class.getCanonicalName(), "initializing RN with Application");

        reactInstanceManager
            = ReactInstanceManager.builder()
                .setApplication(app)
                .setBundleAssetName("index.android.bundle")
                .setJSMainModulePath("index.android")
                .setJavaScriptExecutorFactory(getReactNativeJSFactory())
                .addPackages(getReactNativePackages())
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.BEFORE_CREATE)
                .build();
    }

    /**
     * Internal method to initialize the React Native instance manager. We
     * create a single instance in order to load the JavaScript bundle a single
     * time. All {@code ReactRootView} instances will be tied to the one and
     * only {@code ReactInstanceManager}.
     *
     * @param activity {@code Activity} current running Activity.
     */
    static void initReactInstanceManager(Activity activity) {
        if (reactInstanceManager != null) {
            return;
        }

        Log.d(ReactInstanceManagerHolder.class.getCanonicalName(), "initializing RN with Activity");

        reactInstanceManager
            = ReactInstanceManager.builder()
                .setApplication(activity.getApplication())
                .setCurrentActivity(activity)
                .setBundleAssetName("index.android.bundle")
                .setJSMainModulePath("index.android")
                .setJavaScriptExecutorFactory(getReactNativeJSFactory())
                .addPackages(getReactNativePackages())
                .setUseDeveloperSupport(BuildConfig.DEBUG)
                .setInitialLifecycleState(LifecycleState.RESUMED)
                .build();
    }
}
