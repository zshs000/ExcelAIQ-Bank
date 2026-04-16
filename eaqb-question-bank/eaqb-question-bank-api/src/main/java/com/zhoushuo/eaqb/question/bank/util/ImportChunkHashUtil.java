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

    /**
     * 按导入协议版本计算分块内容哈希。
     * 先校验版本与入参，再路由到对应版本实现，避免两端算法漂移。
     */
    public static String computeHash(String hashVersion, List<ImportQuestionRowDTO> rows) {
        if (!isSupportedHashVersion(hashVersion)) {
            throw new IllegalArgumentException("Unsupported hashVersion: " + hashVersion);
        }
        if (rows == null) {
            throw new IllegalArgumentException("rows must not be null");
        }
        return computeV2(rows);
    }

    /**
     * v2 规则：
     * 对每一行按 content -> answer -> analysis 的固定顺序写入摘要，
     * 每个字段采用「4 字节长度前缀 + UTF-8 字节」编码。
     * 这样可以消除旧版“纯分隔符拼接”带来的字段边界歧义，避免不同内容误算同 hash。
     */
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

    /**
     * 将单个字段按 v2 规则“追加”到摘要输入流：
     * 先写入字段长度（4 字节），再写入字段内容字节。
     * 方法名叫 updateField，是因为底层使用 MessageDigest.update(...) 累积输入。
     */
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
