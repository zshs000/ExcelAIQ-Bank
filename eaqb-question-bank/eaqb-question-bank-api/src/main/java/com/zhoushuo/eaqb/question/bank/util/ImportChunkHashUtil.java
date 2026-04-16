package com.zhoushuo.eaqb.question.bank.util;

import com.zhoushuo.eaqb.question.bank.req.ImportQuestionRowDTO;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

public final class ImportChunkHashUtil {

    public static final String HASH_VERSION_V2 = "v2";

    private ImportChunkHashUtil() {
    }

    public static boolean isSupportedHashVersion(String hashVersion) {
        return Objects.equals(HASH_VERSION_V2, hashVersion);
    }

    public static String computeHash(String hashVersion, List<ImportQuestionRowDTO> rows) {
        if (!isSupportedHashVersion(hashVersion)) {
            throw new IllegalArgumentException("Unsupported hashVersion: " + hashVersion);
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null");
        }
        return computeV2(rows);
    }

    private static String computeV2(List<ImportQuestionRowDTO> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ImportQuestionRowDTO row : rows) {
                if (row == null) {
                    updateField(digest, null);
                    updateField(digest, null);
                    updateField(digest, null);
                    continue;
                }
                updateField(digest, row.getContent());
                updateField(digest, row.getAnswer());
                updateField(digest, row.getAnalysis());
            }
            return toHex(digest.digest());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute import chunk hash", e);
        }
    }

    private static void updateField(MessageDigest digest, String value) {
        byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int v = b & 0xFF;
            if (v < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(v));
        }
        return builder.toString();
    }
}
