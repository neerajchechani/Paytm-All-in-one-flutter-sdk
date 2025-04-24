package in.appyflow.paytm;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;

import com.paytm.pgsdk.PaytmOrder;
import com.paytm.pgsdk.PaytmPaymentTransactionCallback;
import com.paytm.pgsdk.TransactionManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

/**
 * PaytmPlugin
 */
public class PaytmPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    private static final int PAYTM_REQUEST_CODE = 7567;
    private MethodChannel channel;
    private static final String TAG = "PaytmPlugin";
    private static Result flutterResult;
    private static Activity activity;

    @Override
    public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
        channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "paytm");
        channel.setMethodCallHandler(this);
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        flutterResult = result;

        if (call.method.equals("payWithPaytm")) {
            String mId = call.argument("mId");
            String orderId = call.argument("orderId");
            String txnToken = call.argument("txnToken");
            String txnAmount = call.argument("txnAmount");
            String callBackUrl = call.argument("callBackUrl");
            boolean isStaging = Boolean.TRUE.equals(call.argument("isStaging"));
            boolean appInvokeEnabled = Boolean.TRUE.equals(call.argument("appInvokeEnabled"));

            beginPayment(mId, orderId, txnToken, txnAmount, callBackUrl, isStaging, appInvokeEnabled);
        } else {
            result.notImplemented();
        }
    }

    private void beginPayment(String mId, String orderId, String txnToken, String txnAmount, String callBackUrl, boolean isStaging, boolean appInvokeEnabled) {
        String host = isStaging ? "https://securegw-stage.paytm.in/" : "https://securegw.paytm.in/";
        String callback = (callBackUrl == null || callBackUrl.trim().isEmpty())
                ? host + "theia/paytmCallback?ORDER_ID=" + orderId
                : callBackUrl;

        PaytmOrder paytmOrder = new PaytmOrder(orderId, mId, txnToken, txnAmount, callback);

        TransactionManager transactionManager = new TransactionManager(paytmOrder, new PaytmPaymentTransactionCallback() {
            @Override
            public void onTransactionResponse(Bundle bundle) {
                sendResponse(buildSuccessMap(bundle));
            }

            @Override
            public void networkNotAvailable() {
                sendResponse(buildErrorMap("Network Not Available"));
            }

            @Override
            public void onErrorProceed(String s) {}

            @Override
            public void clientAuthenticationFailed(String s) {
                sendResponse(buildErrorMap(s));
            }

            @Override
            public void someUIErrorOccurred(String s) {
                sendResponse(buildErrorMap(s));
            }

            @Override
            public void onErrorLoadingWebPage(int code, String msg, String fallbackUrl) {
                sendResponse(buildErrorMap(msg + " , " + fallbackUrl));
            }

            @Override
            public void onBackPressedCancelTransaction() {
                sendResponse(buildErrorMap("Back Pressed Transaction Cancelled"));
            }

            @Override
            public void onTransactionCancel(String msg, Bundle bundle) {
                Map<String, Object> paramMap = buildSuccessMap(bundle);
                paramMap.put("error", true);
                paramMap.put("errorMessage", msg);
                sendResponse(paramMap);
            }
        });

        transactionManager.setAppInvokeEnabled(appInvokeEnabled);
        transactionManager.setShowPaymentUrl(host + "theia/api/v1/showPaymentPage");
        transactionManager.startTransaction(activity, PAYTM_REQUEST_CODE);
    }

    private Map<String, Object> buildSuccessMap(Bundle bundle) {
        Map<String, Object> responseMap = new HashMap<>();
        for (String key : bundle.keySet()) {
            responseMap.put(key, bundle.getString(key));
        }
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("error", false);
        resultMap.put("response", responseMap);
        return resultMap;
    }

    private Map<String, Object> buildErrorMap(String errorMsg) {
        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("error", true);
        resultMap.put("errorMessage", errorMsg);
        return resultMap;
    }

    private static void sendResponse(Map<String, Object> paramMap) {
        if (flutterResult != null) {
            flutterResult.success(paramMap);
            flutterResult = null;
        }
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
        channel.setMethodCallHandler(null);
    }

    // Handle activity lifecycle
    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();

        binding.addActivityResultListener((requestCode, resultCode, data) -> {
            if (requestCode == PAYTM_REQUEST_CODE && data != null) {
                Map<String, Object> paramMap = new HashMap<>();
                if (data.getStringExtra("response") != null && !data.getStringExtra("response").isEmpty()) {
                    paramMap.put("error", false);
                    for (String key : Objects.requireNonNull(data.getExtras()).keySet()) {
                        paramMap.put(key, data.getExtras().getString(key));
                    }
                } else {
                    paramMap.put("error", true);
                    paramMap.put("errorMessage", data.getStringExtra("nativeSdkForMerchantMessage"));
                }
                sendResponse(paramMap);
                return true;
            }
            return false;
        });
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
        activity = null;
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
        activity = binding.getActivity();
        onAttachedToActivity(binding);
    }

    @Override
    public void onDetachedFromActivity() {
        activity = null;
    }
}
