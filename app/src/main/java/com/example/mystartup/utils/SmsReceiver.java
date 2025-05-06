package com.example.mystartup.utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
    private OtpListener otpListener;

    public interface OtpListener {
        void onOtpReceived(String otp);
    }

    public void setOtpListener(OtpListener otpListener) {
        this.otpListener = otpListener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent.getAction() != null && intent.getAction().equals(SMS_RECEIVED)) {
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    // Get the SMS message
                    Object[] pdus = (Object[]) bundle.get("pdus");
                    String format = bundle.getString("format");
                    
                    if (pdus != null) {
                        // Check for the sender or specific message content related to OTP
                        for (Object pdu : pdus) {
                            SmsMessage smsMessage;
                            
                            // Check Android version for the appropriate way to create SmsMessage
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
                            } else {
                                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
                            }
                            
                            String sender = smsMessage.getDisplayOriginatingAddress();
                            String messageBody = smsMessage.getMessageBody();
                            
                            Log.d(TAG, "SMS received from: " + sender);
                            
                            // Check if this message is likely an OTP message
                            // Look for common OTP keywords in the message
                            if (isOtpMessage(messageBody)) {
                                String otp = extractOtp(messageBody);
                                if (otp != null && otpListener != null) {
                                    Log.d(TAG, "OTP detected: " + otp);
                                    otpListener.onOtpReceived(otp);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing SMS: " + e.getMessage());
        }
    }

    private boolean isOtpMessage(String message) {
        // Check if the message contains OTP-related keywords
        String lowerCaseMsg = message.toLowerCase();
        return lowerCaseMsg.contains("verification") || 
               lowerCaseMsg.contains("code") || 
               lowerCaseMsg.contains("otp") || 
               lowerCaseMsg.contains("verify") ||
               lowerCaseMsg.contains("authentication") ||
               lowerCaseMsg.contains("auth code");
    }

    private String extractOtp(String message) {
        // Pattern to find a 6-digit number in the message
        Pattern pattern = Pattern.compile("\\b\\d{6}\\b");
        Matcher matcher = pattern.matcher(message);
        
        if (matcher.find()) {
            return matcher.group(0);
        }
        
        return null;
    }
} 