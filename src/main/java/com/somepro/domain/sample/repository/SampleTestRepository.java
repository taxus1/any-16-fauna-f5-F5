package com.somepro.domain.sample.repository;

import com.somepro.domain.sample.model.SampleTest;
import com.somepro.domain.shared.model.PageResult;
import reactor.core.publisher.Mono;

/**
 * 采样送检样本的仓储端口（领域层定义，基础设施层实现）。
 */
public interface SampleTestRepository {

    /**
     * 登记落库；sampleNo 由实现侧按 SM-YYYY-NNNN 生成，并发撞号自动重取，
     * 唯一索引兜底，一个号只落一条，不甩底层冲突。
     */
    Mono<SampleTest> create(SampleTest sample);

    /**
     * 按 id 查看在册样本（del_flag=0）。
     */
    Mono<SampleTest> findById(Long id);

    /**
     * 检测结果回填落库，事务内两头一起动：
     * 1) 样本行按「仍待检」条件更新（WHERE id=? AND result='PENDING'），
     *    同一条样本只翻得动一次，并发/重复录只放行一下；
     * 2) 结果一录，挂的上报从在办（已上报/处置中）条件更新推到已采样；
     *    上报已不在在办状态的（已采样/已救护/已结案）不动，结果照录。
     *
     * @return true 结果落上；false 样本已不在待检（被并发先录了）
     */
    Mono<Boolean> recordResult(SampleTest sample);

    /**
     * 条件分页：上报/样本类型/结果随意拼，全空翻整份在册样本。
     * 已删除（del_flag=1）的不出现（@TableLogic 自动过滤），每行带样本编号。
     */
    Mono<PageResult<SampleTest>> page(int pageNum, int pageSize,
                                      Long reportId, String sampleType, String result);
}
