package com.somepro.application.sample;

import com.somepro.common.exception.BizException;
import com.somepro.domain.report.model.AbnormalReport;
import com.somepro.domain.report.repository.AbnormalReportRepository;
import com.somepro.domain.sample.model.SampleTest;
import com.somepro.domain.sample.repository.SampleTestRepository;
import com.somepro.domain.shared.model.PageResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 采样送检应用层：编排样本用例（采样登记、检测结果录入、查看、条件分页）。
 *
 * 采样登记一道前置：挂的那条上报得在册、还没结案 —— 还在上报或处置中的才采得了，
 * 已结案的别再采（已救护/已采样的也不在「上报或处置中」，一并拦下）。
 *
 * 检测结果录入两头一起动（仓储层事务内）：样本行从待检翻成结果，挂的上报从在办
 * （已上报/处置中）推到已采样；结果还悬着没出的（样本仍待检），上报那头先别动。
 * 结果只录一回：同一条样本别来回翻，领域对象先拦一道，仓储条件更新再兜一道并发。
 */
@Service
public class SampleTestAppService {

    /** 采得动样的上报状态：还在上报或处置中（已结案/已救护/已采样的都采不了） */
    private static final Set<String> SAMPLEABLE_STATUSES = Set.of(
            AbnormalReport.STATUS_REPORTED, AbnormalReport.STATUS_HANDLING);

    private final SampleTestRepository sampleRepository;
    private final AbnormalReportRepository reportRepository;

    public SampleTestAppService(SampleTestRepository sampleRepository,
                                AbnormalReportRepository reportRepository) {
        this.sampleRepository = sampleRepository;
        this.reportRepository = reportRepository;
    }

    /**
     * 登记一条采样送检样本：编号 SM-YYYY-NNNN 由仓储层生成；送检时刻不传取登记当下；
     * 立起来落在待检。登记本身不动上报状态 —— 结果还悬着没出，上报那头先别动。
     */
    public Mono<SampleTest> register(Long reportId, String sampleType, LocalDateTime sentAt,
                                     String labName, String testItem) {
        return requireSampleableReport(reportId)
                .flatMap(report -> {
                    SampleTest sample = SampleTest.create(report.getId(), sampleType, sentAt, labName, testItem);
                    return sampleRepository.create(sample);
                });
    }

    /**
     * 录检测结果：样本得还悬着（待检）才录得了，同一条样本别来回翻。
     * 落库时两头一起动：样本翻成结果，上报从在办推到已采样（仓储层事务内）。
     */
    public Mono<SampleTest> recordResult(Long id, String result, LocalDateTime testedAt) {
        return sampleRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("样本不存在")))
                .flatMap(sample -> {
                    sample.recordResult(result, testedAt);
                    return sampleRepository.recordResult(sample)
                            .flatMap(recorded -> recorded
                                    // 重查一遍：检测时刻与审计列在落库时刷新，内存里的还是录入前的
                                    ? sampleRepository.findById(sample.getId())
                                    : Mono.error(new BizException("这条样本的检测结果已录入，同一条样本别来回翻")));
                });
    }

    /** 查看单条在册样本。 */
    public Mono<SampleTest> detail(Long id) {
        return sampleRepository.findById(id)
                .switchIfEmpty(Mono.error(new BizException("样本不存在")));
    }

    /** 条件分页：上报/样本类型/结果随意拼，全空翻整份在册样本，每行带样本编号。 */
    public Mono<PageResult<SampleTest>> pageSamples(int pageNum, int pageSize,
                                                    Long reportId, String sampleType, String result) {
        return sampleRepository.page(pageNum, pageSize, reportId,
                normalize(sampleType), normalize(result));
    }

    /** 采样的前提：上报得在册、还没结案 —— 还在上报或处置中的才采得了。 */
    private Mono<AbnormalReport> requireSampleableReport(Long reportId) {
        if (reportId == null) {
            return Mono.error(new BizException("所属上报不能为空"));
        }
        return reportRepository.findById(reportId)
                .switchIfEmpty(Mono.error(new BizException("异常上报不存在或已作废，不能采样")))
                .flatMap(report -> {
                    if (!SAMPLEABLE_STATUSES.contains(report.getStatus())) {
                        return Mono.error(new BizException("只有还在上报或处置中的上报才能采样"));
                    }
                    return Mono.just(report);
                });
    }

    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
