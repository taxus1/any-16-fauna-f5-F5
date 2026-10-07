package com.somepro.infrastructure.persistence.sample;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.github.pagehelper.PageHelper;
import com.somepro.domain.report.model.AbnormalReport;
import com.somepro.domain.sample.model.SampleTest;
import com.somepro.domain.sample.repository.SampleTestRepository;
import com.somepro.domain.shared.model.PageResult;
import com.somepro.infrastructure.config.ReactiveOperatorContext;
import com.somepro.infrastructure.persistence.audit.AuditContextHolder;
import com.somepro.infrastructure.persistence.report.AbnormalReportMapper;
import com.somepro.infrastructure.persistence.report.po.AbnormalReportPO;
import com.somepro.infrastructure.persistence.sample.converter.SampleTestPoConverter;
import com.somepro.infrastructure.persistence.sample.po.SampleTestPO;
import com.somepro.infrastructure.persistence.support.BizNoGenerator;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 采样送检样本仓储适配器（基础设施层）。
 *
 * 编号分配：sampleNo 按 SM-YYYY-NNNN 生成（年份按登记当下，序号 4 位零填充），
 * 并发撞号由 {@link BizNoGenerator} 重试，唯一索引兜底，一个号只落一条；
 * 已删除样本占用的编号不复用（selectMaxSeq 的自定义 @Select 不拼 del_flag）。
 *
 * 结果回填在事务内两头一起动：
 * 1) 样本行按「仍待检」条件更新（WHERE id=? AND result='PENDING'）——
 *    同一条样本的结果只翻得动一次，并发录/重复录只放行一下，后到者 rows=0 返回 false；
 * 2) 挂的上报按「仍在办」条件更新推到已采样（WHERE id=? AND status IN (REPORTED, HANDLING)）——
 *    上报已不在在办状态（已采样/已救护/已结案）时不动它，结果照样落样本行；
 *    上报那头的处置时刻由审计列 update_time 自动记下。
 */
@Repository
public class SampleTestRepositoryImpl implements SampleTestRepository {

    /** 编号前缀：SM-（完整形如 SM-2026-） */
    private static final String NO_PREFIX = "SM-";

    private final SampleTestMapper sampleMapper;
    private final AbnormalReportMapper reportMapper;
    private final TransactionTemplate transactionTemplate;

    public SampleTestRepositoryImpl(SampleTestMapper sampleMapper,
                                    AbnormalReportMapper reportMapper,
                                    PlatformTransactionManager transactionManager) {
        this.sampleMapper = sampleMapper;
        this.reportMapper = reportMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public Mono<SampleTest> create(SampleTest sample) {
        return blocking(() -> {
            String prefix = NO_PREFIX + LocalDate.now().getYear() + "-";
            return BizNoGenerator.insertWithRetry(
                    () -> sampleMapper.selectMaxSeq(prefix, prefix.length() + 1),
                    prefix,
                    no -> doInsert(sample, no));
        });
    }

    @Override
    public Mono<SampleTest> findById(Long id) {
        return blocking(() -> {
            SampleTestPO po = sampleMapper.selectById(id);
            return po == null ? null : SampleTestPoConverter.toDomain(po);
        });
    }

    @Override
    public Mono<Boolean> recordResult(SampleTest sample) {
        return blocking(() -> transactionTemplate.execute(txStatus -> {
            // 样本行：只有仍待检的那一行才翻得动（并发录同一样本只放行一下）；
            // SET 只带结果与检测时刻，del_flag=0 由 @TableLogic 拼上。
            SampleTestPO samplePo = new SampleTestPO();
            samplePo.setResult(sample.getResult());
            samplePo.setTestedAt(sample.getTestedAt());
            int rows = sampleMapper.update(samplePo, Wrappers.<SampleTestPO>lambdaUpdate()
                    .eq(SampleTestPO::getId, sample.getId())
                    .eq(SampleTestPO::getResult, SampleTest.RESULT_PENDING));
            if (rows != 1) {
                return false;
            }
            // 上报那头：结果一录，从在办（已上报/处置中）推到已采样；
            // 已不在在办状态的（已采样/已救护/已结案）不动它，结果照落样本行。
            AbnormalReportPO reportPo = new AbnormalReportPO();
            reportPo.setStatus(AbnormalReport.STATUS_SAMPLED);
            reportMapper.update(reportPo, Wrappers.<AbnormalReportPO>lambdaUpdate()
                    .eq(AbnormalReportPO::getId, sample.getReportId())
                    .in(AbnormalReportPO::getStatus,
                            AbnormalReport.STATUS_REPORTED, AbnormalReport.STATUS_HANDLING));
            return true;
        }));
    }

    @Override
    public Mono<PageResult<SampleTest>> page(int pageNum, int pageSize,
                                             Long reportId, String sampleType, String result) {
        return this.<PageResult<SampleTest>>blocking(() -> {
            try {
                PageHelper.startPage(pageNum, pageSize);
                LambdaQueryWrapper<SampleTestPO> wrapper = Wrappers.<SampleTestPO>lambdaQuery()
                        .eq(reportId != null, SampleTestPO::getReportId, reportId)
                        .eq(hasText(sampleType), SampleTestPO::getSampleType, sampleType)
                        .eq(hasText(result), SampleTestPO::getResult, result)
                        .orderByAsc(SampleTestPO::getId);
                List<SampleTestPO> rows = sampleMapper.selectList(wrapper);
                long total = rows instanceof com.github.pagehelper.Page
                        ? ((com.github.pagehelper.Page<?>) rows).getTotal()
                        : rows.size();
                List<SampleTest> content = rows.stream()
                        .map(SampleTestPoConverter::toDomain)
                        .collect(Collectors.toList());
                return new PageResult<>(content, total, pageNum, pageSize);
            } finally {
                PageHelper.clearPage();
            }
        });
    }

    private SampleTest doInsert(SampleTest sample, String sampleNo) {
        sample.setSampleNo(sampleNo);
        SampleTestPO po = SampleTestPoConverter.toPo(sample);
        po.setId(IdUtil.getSnowflakeNextId());
        sampleMapper.insert(po);
        return SampleTestPoConverter.toDomain(po);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 阻塞 DB 调用 → 响应式链路的桥接器：先取 Reactor Context 里的操作人，
     * 再切到 boundedElastic 执行 JDBC，操作人放进 AuditContextHolder 供审计填充。
     */
    private <T> Mono<T> blocking(Supplier<T> supplier) {
        return Mono.deferContextual(ctx -> {
            String operator = ReactiveOperatorContext.getOperator(ctx);
            return Mono.fromCallable(() -> {
                AuditContextHolder.setOperator(operator);
                try {
                    return supplier.get();
                } finally {
                    AuditContextHolder.clear();
                }
            }).subscribeOn(Schedulers.boundedElastic());
        });
    }
}
