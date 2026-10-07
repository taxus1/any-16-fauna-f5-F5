package com.somepro.domain.sample.model;

import com.somepro.common.exception.BizException;
import com.somepro.domain.shared.model.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 采样送检样本聚合根（领域层）：异常上报挂上来以后，该采样的采样、该送检的送检，
 * 一条样本一条记录，检测结果回来回填到这条上。
 *
 * 业务规则：
 * - 一条样本挂在一条异常上报（reportId）下；样本类型四选一：{@link #TYPE_BLOOD 血液} /
 *   {@link #TYPE_SWAB 拭子} / {@link #TYPE_TISSUE 组织} / {@link #TYPE_FECES 粪便}；
 * - 检测结果四态：{@link #RESULT_PENDING 待检} / {@link #RESULT_POSITIVE 阳性} /
 *   {@link #RESULT_NEGATIVE 阴性} / {@link #RESULT_INCONCLUSIVE 不确定}，
 *   新登记的样本一律先落在待检；
 * - 结果只录一回：还悬着（待检）的才录得了，录过结果的同一条样本别来回翻；
 *   录进来的结果只认阳性/阴性/不确定（「待检」不是结果，录它等于没录）；
 * - 送检时刻、检测时刻不传都取当下。
 *
 * 「上报还在上报或处置中才采得了」「结果一录，上报从在办推到已采样」要查/动上报，
 * 由应用层与仓储层编排把关；领域对象只保证自身字段不变量。
 *
 * 编号 sampleNo 由仓储层在落库时分配（SM-YYYY-NNNN 式），领域对象只持有不生成。
 */
@Getter
@Setter
public class SampleTest extends BaseEntity {

    /** 样本类型：血液 */
    public static final String TYPE_BLOOD = "BLOOD";
    /** 样本类型：拭子 */
    public static final String TYPE_SWAB = "SWAB";
    /** 样本类型：组织 */
    public static final String TYPE_TISSUE = "TISSUE";
    /** 样本类型：粪便 */
    public static final String TYPE_FECES = "FECES";

    /** 检测结果：待检（新登记默认） */
    public static final String RESULT_PENDING = "PENDING";
    /** 检测结果：阳性 */
    public static final String RESULT_POSITIVE = "POSITIVE";
    /** 检测结果：阴性 */
    public static final String RESULT_NEGATIVE = "NEGATIVE";
    /** 检测结果：不确定 */
    public static final String RESULT_INCONCLUSIVE = "INCONCLUSIVE";

    private static final Set<String> SAMPLE_TYPES = Set.of(
            TYPE_BLOOD, TYPE_SWAB, TYPE_TISSUE, TYPE_FECES);

    /** 录结果只认这三种：待检不是结果，录它等于没录 */
    private static final Set<String> OUTCOMES = Set.of(
            RESULT_POSITIVE, RESULT_NEGATIVE, RESULT_INCONCLUSIVE);

    private Long id;

    /** 样本编号（如 SM-2026-0001），全局唯一 */
    private String sampleNo;

    /** 挂在哪条异常上报下（t_abnormal_report.id） */
    private Long reportId;

    /** 样本类型：BLOOD / SWAB / TISSUE / FECES */
    private String sampleType;

    /** 送检时刻（不传则取登记当下） */
    private LocalDateTime sentAt;

    /** 检测机构 */
    private String labName;

    /** 检测项目 */
    private String testItem;

    /** 检测结果：PENDING / POSITIVE / NEGATIVE / INCONCLUSIVE，新登记默认待检 */
    private String result;

    /** 检测时刻（录结果时不传则取录入当下） */
    private LocalDateTime testedAt;

    /**
     * 工厂方法：登记一条采样送检样本，立起来先落在待检。
     *
     * @param sentAt 送检时刻，null 时取登记当下
     */
    public static SampleTest create(Long reportId, String sampleType, LocalDateTime sentAt,
                                    String labName, String testItem) {
        SampleTest sample = new SampleTest();
        sample.attachReport(reportId);
        sample.changeSampleType(sampleType);
        sample.sentAt = sentAt != null ? sentAt : LocalDateTime.now();
        sample.labName = blankToNull(labName);
        sample.testItem = blankToNull(testItem);
        sample.result = RESULT_PENDING;
        return sample;
    }

    public void attachReport(Long reportId) {
        if (reportId == null) {
            throw new BizException("所属上报不能为空");
        }
        this.reportId = reportId;
    }

    public void changeSampleType(String sampleType) {
        if (sampleType == null || !SAMPLE_TYPES.contains(sampleType.trim())) {
            throw new BizException("样本类型非法，仅支持 BLOOD/SWAB/TISSUE/FECES");
        }
        this.sampleType = sampleType.trim();
    }

    /**
     * 录检测结果：只有还悬着（待检）的才录得了，同一条样本别来回翻；
     * 结果一录，检测时刻一并记下（不传取录入当下）。
     */
    public void recordResult(String result, LocalDateTime testedAt) {
        if (!RESULT_PENDING.equals(this.result)) {
            throw new BizException("这条样本的检测结果已录入，同一条样本别来回翻");
        }
        if (result == null || !OUTCOMES.contains(result.trim())) {
            throw new BizException("检测结果非法，仅支持 POSITIVE/NEGATIVE/INCONCLUSIVE");
        }
        this.result = result.trim();
        this.testedAt = testedAt != null ? testedAt : LocalDateTime.now();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
