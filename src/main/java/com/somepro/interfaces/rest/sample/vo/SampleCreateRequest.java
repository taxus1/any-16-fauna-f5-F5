package com.somepro.interfaces.rest.sample.vo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 采样登记请求（VO，用户接口层）。
 *
 * 前置由应用层把关：挂的上报得在册、还没结案（还在上报或处置中的才采得了）。
 * 样本编号由服务端生成；结果不用前端填，新登记的一律待检；sentAt 可空（取登记当下）。
 */
public record SampleCreateRequest(
        @NotNull(message = "所属上报不能为空") Long reportId,
        @NotBlank(message = "样本类型不能为空") String sampleType,
        LocalDateTime sentAt,
        String labName,
        String testItem) implements Serializable {
}
