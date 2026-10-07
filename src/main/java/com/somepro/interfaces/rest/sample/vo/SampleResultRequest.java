package com.somepro.interfaces.rest.sample.vo;

import jakarta.validation.constraints.NotBlank;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 检测结果录入请求（VO，用户接口层）。
 *
 * 结果只录一回：样本得还悬着（待检）才录得了，同一条样本别来回翻；
 * 结果值只认 POSITIVE/NEGATIVE/INCONCLUSIVE（领域对象把守）；testedAt 可空（取录入当下）。
 */
public record SampleResultRequest(
        @NotBlank(message = "检测结果不能为空") String result,
        LocalDateTime testedAt) implements Serializable {
}
