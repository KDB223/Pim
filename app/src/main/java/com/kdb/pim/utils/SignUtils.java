package com.kdb.pim.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class to find the SHA-1 signing certificates of the app from {@link PackageManager}
 */
public class SignUtils {

    /**
     * Extract SHA-1 signatures of the app
     * @param context {@link Context} required for {@link PackageManager}
     * @return The first SHA-1 signature found, in hexadecimal form
     */
    public static String getSignature(Context context) {
        try {
            PackageInfo packageInfo;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
                for (Signature signature : packageInfo.signingInfo.getSigningCertificateHistory()) {
                    String sha = getSha(signature);
                    if (sha != null)
                        return sha;
                }
            } else {
                packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), PackageManager.GET_SIGNATURES);
                for (Signature signature : packageInfo.signatures) {
                    String sha = getSha(signature);
                    if (sha != null)
                        return sha;
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Helper method to get the signature in SHA-1 format, in hexadecimal form
     * @param signature The {@link Signature} to extract SHA-1 string from
     * @return SHA-1 signature in hex form
     */
    private static String getSha(Signature signature) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA");
            md.update(signature.toByteArray());
            byte[] digest = md.digest();
            final StringBuilder toRet = new StringBuilder();
            for (byte value : digest) {
                int b = value & 0xff;
                String hex = Integer.toHexString(b);
                if (hex.length() == 1) toRet.append("0");
                toRet.append(hex);
            }
            return toRet.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return null;
    }
}
