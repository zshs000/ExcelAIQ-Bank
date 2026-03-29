package com.zhoushuo.eaqb.oss.biz.strategy;

import org.springframework.web.multipart.MultipartFile;

/**
 * 对象存储策略抽象。
 *
 * <p>这一层只关心“怎么和具体存储厂商交互”，不关心业务侧为什么上传这个文件。
 * 调用方负责传入已经明确好的业务语义：</p>
 *
 * <ul>
 *     <li>Excel：传入业务决定好的 objectName，策略内部再拼成完整 objectKey。</li>
 *     <li>头像 / 背景图：策略内部按固定槽位规则生成 objectKey。</li>
 *     <li>下载：调用方传入持久化的 objectKey，策略负责生成限时下载访问凭证。</li>
 * </ul>
 *
 * <p>上传方法统一返回最终 objectKey，而不是厂商访问 URL，避免上层感知 MinIO / Aliyun 的 URL 结构差异。</p>
 */
public interface FileStrategy {

    /**
     * 上传 Excel 文件。
     *
     * @param file 上传的 Excel 文件
     * @param bucketName 目标 bucket
     * @param objectName 业务方决定的对象名，例如 {@code 9001.xlsx}
     * @return 完整 objectKey，例如 {@code excel/123/9001.xlsx}
     */
    String uploadExcel(MultipartFile file, String bucketName, String objectName);

    /**
     * 上传用户头像到固定槽位。
     *
     * @param file 头像文件
     * @param bucketName 目标 bucket
     * @return 完整 objectKey，例如 {@code image/123/avatar}
     */
    String uploadAvatar(MultipartFile file, String bucketName);

    /**
     * 上传用户背景图到固定槽位。
     *
     * @param file 背景图文件
     * @param bucketName 目标 bucket
     * @return 完整 objectKey，例如 {@code image/123/background}
     */
    String uploadBackground(MultipartFile file, String bucketName);

    /**
     * 根据 objectKey 生成限时下载访问凭证。
     *
     * @param bucketName 目标 bucket
     * @param objectKey 完整对象路径
     * @return 预签名下载 URL
     */
    String getPresignedDownloadUrl(String bucketName, String objectKey);
}
